package com.chain.ai.trade.engine2.realtime;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.common.utils.TradingUtil;
import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
import com.chain.ai.trade.engine.risk.adjuster.AdjustmentContext;
import com.chain.ai.trade.engine.risk.adjuster.AdjustmentResult;
import com.chain.ai.trade.engine.risk.adjuster.PositionAdjuster;
import com.chain.ai.trade.engine.risk.adjuster.PositionAdjusterFactory;
import com.chain.ai.trade.engine.risk.adjuster.PositionAdjusterType;
import com.chain.ai.trade.engine2.backtest.BacktestResult;
import com.chain.ai.trade.engine2.core.AbstractEngine;
import com.chain.ai.trade.engine2.core.EntrySignal;
import com.chain.ai.trade.engine2.core.ExitSignal;
import com.chain.ai.trade.engine2.core.ScaleInSignal;
import com.chain.ai.trade.engine2.core.execution.FillResult;
import com.chain.ai.trade.engine2.persistence.RealtimeGateway;
import com.chain.ai.trade.engine2.rules.base.SmcStopPreviewer;
import com.chain.ai.trade.engine2.strategy.ScriptStrategy;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;



/**
 * 模拟/实盘引擎基类 — 事件驱动，继承 AbstractEngine。
 * <p>
 * executeLoop() 是永不退出的事件循环，通过 wait/notify 等待 syncSeries/syncBar 推送新数据。
 * 引擎本身不负责 K 线数据获取，由调度器通过 syncSeries/syncBar 推送。
 * </p>
 */
@Slf4j
public abstract class RealtimeEngine extends AbstractEngine {

    // ==================== 核心组件 ====================

    protected final RealtimeConfig config;
    protected final RealtimeContext context;
    protected final RealtimeGateway gateway;

    /** 运行状态标志 */
    protected volatile boolean running = true;

    /** 已追加到 series 的 Bar 总数（syncSeries/syncBar 维护） */
    protected int barIndex = 0;

    /** 已处理的 Bar 数量（executeLoop 维护） */
    protected int processedIndex = 0;

    /** 引擎启动时间 */
    protected long startTime = 0;

    /** 上次持仓同步时间戳（毫秒） */
    private long lastPositionSyncTime = 0;

    /** 进度回调（Paper 测试用于 WebSocket 进度推送） */
    protected Consumer<Integer> progressCallback;

    /** SMC 止损预计算器（供以损定量使用，开仓前预计算止损位） */
    protected SmcStopPreviewer stopPreviewer;
    private static final long TIMEZONE_OFFSET_MS = 8 * 60 * 60 * 1000L;
    // ==================== 构造函数 ====================

    public RealtimeEngine(BarSeries series, ScriptStrategy strategy,
                          RealtimeConfig config, RealtimeContext context,
                          RealtimeGateway gateway) {
        super(series, strategy);
        this.config = config;
        this.context = context;
        this.gateway = gateway;
    }

    // ==================== 对外接口 ====================

    /**
     * 设置进度回调（Paper 测试用）
     */
    public void onProgress(Consumer<Integer> callback) {
        this.progressCallback = callback;
    }

    /**
     * 设置 SMC 止损预计算器（供以损定量仓位计算使用）
     */
    public void setStopPreviewer(SmcStopPreviewer stopPreviewer) {
        this.stopPreviewer = stopPreviewer;
    }

    /**
     * 初始化 — 引擎就绪后推进 barIndex，越过预热期。
     * <p>
     * createEngine 加载的初始 Bar 仅用于指标预热，实盘不逐根回放做交易决策。
     * 此方法将 barIndex 直接推到 series 末尾，后续 syncSeries/syncBar 从增量 Bar 开始处理。
     * </p>
     */
    public void init() {
        log.info("引擎初始化: symbol={}, totalBars={}, warmup={}",
                config.getSymbol(), series.getBarCount(), config.getWarmupPeriod());
        barIndex = series.getBarCount();
        processedIndex = barIndex;
        log.info("引擎初始化完成: symbol={}, barIndex={}", config.getSymbol(), barIndex);
    }

    /**
     * 同步最新 BarSeries — 由调度器每轮调用，批量追加新 Bar。
     * <p>
     * 适用于首次加载或批量回补场景。
     * </p>
     */
    public synchronized void syncSeries(BarSeries fresh) {
        if (!running || fresh == null || fresh.isEmpty()) return;

        Instant lastKnownTime = series.isEmpty() ? null : series.getLastBar().getBeginTime();
        boolean appended = false;

        for (int i = 0; i < fresh.getBarCount(); i++) {
            Bar bar = fresh.getBar(i);
            Instant barTime = bar.getBeginTime();

            if (lastKnownTime == null || barTime.isAfter(lastKnownTime)) {
                series.addBar(bar);
                lastKnownTime = barTime;
                barIndex++;
                appended = true;
            } else if (barTime.equals(lastKnownTime)) {
                // 同一根 K 线 → 更新（替换最后一条）
                series.addBar(bar, true);
                appended = true;
            }
        }

        if (appended) {
            notifyAll();
            if (log.isTraceEnabled()) {
                log.trace("syncSeries 通知: symbol={}, barIndex={}, processedIndex={}",
                        config.getSymbol(), barIndex, processedIndex);
            }
        }
    }

    /**
     * 同步最新一根 K 线 — 🔥 直接在当前线程处理，消除 wait/notify 调度延迟。
     * <p>
     * 适用于实时推送场景，每次只推最新一根 K 线。
     * 支持追加（新Bar）和更新（同一Bar）两种模式。
     * </p>
     */
    public synchronized void syncBar(Bar bar) {
        if (!running || bar == null) return;

        Bar lastBar = series.isEmpty() ? null : series.getLastBar();
        Instant barTime = bar.getBeginTime();

        if (lastBar == null || barTime.isAfter(lastBar.getBeginTime())) {
            // 新 Bar → 追加
            series.addBar(bar);
            barIndex++;
            if (log.isDebugEnabled()) {
                log.debug("syncBar 新Bar: symbol={}, barIndex={}, time={}",
                        config.getSymbol(), barIndex, barTime);
            }
        } else if (barTime.equals(lastBar.getBeginTime())) {
            // 同一根 K 线 → 更新，回退索引重新处理
            series.addBar(bar, true);
            if (processedIndex == series.getBarCount() && processedIndex > 0) {
                processedIndex--;
            }
            if (log.isDebugEnabled()) {
                log.debug("syncBar 更新Bar: symbol={}, time={}, processedIndex={}, barCount={}",
                        config.getSymbol(), barTime, processedIndex, series.getBarCount());
            }
        } else {
            return; // 旧数据，跳过
        }

        // 🔥 直接在当前线程处理，不经过 wait/notify
        processPendingBars();
    }

    /**
     * 🔥 直接处理所有待处理的 Bar（由 syncBar 在当前线程调用）。
     * <p>
     * 相比 wait/notify 模式，消除了线程调度延迟，K 线到达即处理。
     * </p>
     */
    private void processPendingBars() {
        int warmup = config.getWarmupPeriod();
        int end = series.getBarCount();
        int processed = 0;
        while (processedIndex < end && running) {
            if (processedIndex >= warmup) {
                try {
                    processBar(processedIndex, series.getBar(processedIndex));
                } catch (Exception e) {
                    log.error("处理 Bar 异常: symbol={}, index={}", config.getSymbol(), processedIndex, e);
                }
            }
            processedIndex++;
            processed++;
        }
        if (processed > 0 && log.isDebugEnabled()) {
            log.debug("[BAR] 处理完成: symbol={}, processed={}, totalProcessed={}",
                    config.getSymbol(), processed, processedIndex);
        }
    }

    /**
     * 停止引擎
     */
    public void stop() {
        running = false;
        synchronized (this) {
            notifyAll();
        }
        log.info("引擎停止信号已发送: symbol={}", config.getSymbol());
    }

    public boolean isRunning() {
        return running;
    }

    public RealtimeContext getContext() {
        return context;
    }

    public RealtimeConfig getConfig() {
        return config;
    }

    public int getBarIndex() {
        return barIndex;
    }

    public long getUptimeMillis() {
        return startTime == 0 ? 0 : System.currentTimeMillis() - startTime;
    }

    // ==================== AbstractEngine 模板方法实现 ====================

    @Override
    protected void beforeRun() {
        this.startTime = System.currentTimeMillis();
        log.info("引擎启动: symbol={}, interval={}, initialCapital={}, leverage={}",
                config.getSymbol(), config.getInterval(),
                config.getInitialCapital(), config.getLeverage());
    }

    /**
     * 🔥 保活循环 — Bar 处理已移至 syncBar 直接调用，此处仅等待停止信号。
     * <p>
     * syncBar 在当前线程（XXL-JOB 调度线程）直接调用 processPendingBars，
     * 消除了 wait/notify 的线程调度延迟，K 线到达即处理。
     * </p>
     */
    @Override
    protected void executeLoop() {
        log.info("引擎保活循环启动（直接处理模式）: symbol={}, barCount={}, processedIndex={}, thread={}",
                config.getSymbol(), series.getBarCount(), processedIndex, Thread.currentThread().getName());

        while (running) {
            synchronized (this) {
                try {
                    wait(30000); // 等待停止信号
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.debug("引擎保活循环被中断: symbol={}", config.getSymbol());
                    return;
                }
            }

            // 定期同步 DB 持仓，清理幽灵仓位（每5分钟一次）
            long now = System.currentTimeMillis();
            if (lastPositionSyncTime == 0 || now - lastPositionSyncTime > 300_000) {
                syncPositionFromDb();
                lastPositionSyncTime = now;
            }
        }

        log.info("引擎保活循环退出: symbol={}, processedIndex={}",
                config.getSymbol(), processedIndex);
    }

    /**
     * 从 DB 同步持仓状态，清除内存中存在但 DB 中不存在的幽灵仓位。
     * 同时恢复 DB 中存在但内存中丢失的持仓（引擎重启场景）。
     */
    private void syncPositionFromDb() {
        try {
            Object[] result = gateway.loadOpenPositions(config.getSymbol(), false);
            @SuppressWarnings("unchecked")
            List<TradePosition> dbOrders = result != null ? (List<TradePosition>) result[0] : null;
            Set<String> dbPositionIds = dbOrders != null
                    ? dbOrders.stream().map(TradePosition::getPositionId).collect(Collectors.toSet())
                    : java.util.Collections.emptySet();

            // 检查内存中的持仓是否在 DB 中存在
            if (context.hasLongPosition()) {
                String memId = context.getLongPosition().getPositionId();
                if (!dbPositionIds.contains(memId)) {
                    log.warn("[持仓同步] 内存多头仓位在DB中不存在，清除幽灵仓位: posId={}, qty={}",
                            memId, context.getLongQuantity());
                    context.clearPosition(SignalType.LONG);
                }
            }
            if (context.hasShortPosition()) {
                String memId = context.getShortPosition().getPositionId();
                if (!dbPositionIds.contains(memId)) {
                    log.warn("[持仓同步] 内存空头仓位在DB中不存在，清除幽灵仓位: posId={}, qty={}",
                            memId, context.getShortQuantity());
                    context.clearPosition(SignalType.SHORT);
                }
            }

            // DB 中有但内存中没有 → 恢复（引擎重启或意外丢失场景）
            if (dbOrders != null && !dbOrders.isEmpty()) {
                boolean hasLong = context.hasLongPosition();
                boolean hasShort = context.hasShortPosition();
                boolean needRestore = false;
                for (TradePosition dbPos : dbOrders) {
                    SignalType dir = dbPos.getOrderSideEnum() == com.chain.ai.trade.common.entity.constants.OrderSideEnum.BUY
                            ? SignalType.LONG : SignalType.SHORT;
                    if (dir == SignalType.LONG && !hasLong) { needRestore = true; break; }
                    if (dir == SignalType.SHORT && !hasShort) { needRestore = true; break; }
                }
                if (needRestore) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, List<com.chain.ai.trade.order.entity.dos.TradeEntry>> itemsMap =
                            result[1] != null
                                    ? (java.util.Map<String, List<com.chain.ai.trade.order.entity.dos.TradeEntry>>) result[1]
                                    : java.util.Collections.emptyMap();
                    context.recoverFromOrders(dbOrders, itemsMap);
                    log.info("[持仓同步] DB有仓位但内存缺失，已恢复");
                }
            }
        } catch (Exception e) {
            log.error("[持仓同步] 同步失败: symbol={}", config.getSymbol(), e);
        }
    }

    @Override
    protected void afterRun() {
        long uptime = System.currentTimeMillis() - startTime;
        log.info("引擎停止: symbol={}, totalBars={}, uptime={}ms, 当前持仓: long={}, short={}",
                config.getSymbol(), barIndex, uptime,
                context.getLongQuantity(),
                context.getShortQuantity());
    }

    @Override
    protected BacktestResult buildResult() {
        return null;
    }

    // ==================== 🔥 核心处理逻辑 ====================

    /**
     * 单根 K 线处理 — 完整的 6 步决策流程。
     * @param barIndex 当前 Bar 在 series 中的索引
     */
    protected void processBar(int barIndex, Bar bar) {
        BigDecimal openPrice = BigDecimal.valueOf(bar.getOpenPrice().doubleValue());
        BigDecimal highPrice = BigDecimal.valueOf(bar.getHighPrice().doubleValue());
        BigDecimal lowPrice = BigDecimal.valueOf(bar.getLowPrice().doubleValue());
        BigDecimal closePrice = BigDecimal.valueOf(bar.getClosePrice().doubleValue());
        double high = bar.getHighPrice().doubleValue();
        double low = bar.getLowPrice().doubleValue();
        long timestamp = bar.getBeginTime().toEpochMilli();
        LocalDateTime barTime = LocalDateTime.ofInstant(bar.getBeginTime(), ZoneId.systemDefault());
        log.info("processBar: robotId={}, accountId={}, barTime={}",
                config.getRobotId(), config.getAccountId(), DateUtil.formatDateTime(new java.util.Date(bar.getBeginTime().toEpochMilli() - TIMEZONE_OFFSET_MS)));

        // 记录本 Bar 入场前状态，用于防止平仓后立即同向重新入场
        boolean hadLongBeforeBar = context.hasLongPosition();
        boolean hadShortBeforeBar = context.hasShortPosition();
        boolean enteredThisBar = false;

        // Step 1: Paper 挂单处理（Live 模式跳过）
        List<FillResult> fills = context.getExecutionHandler()
                .processPendingOrdersOnBar(config.getSymbol(), high, low);
        for (FillResult fill : fills) {
            log.debug("挂单成交: clientOrderId={}, price={}, qty={}",
                    fill.getClientOrderId(), fill.getFillPrice(), fill.getFilledQuantity());
        }

        // Step 2: 逐笔止盈/止损检查（使用极值检测）
        if (context.hasPosition()) {
            boolean hit = context.closeEntriesByTpSl(barIndex, highPrice, lowPrice, closePrice, barTime);
            if (hit && !context.hasPosition()) {
                log.debug("止盈/止损触发，仓位已清空: index={}, price={}", barIndex, closePrice);
            }
        }

        // Step 3: 出场规则检查
        if (context.hasPosition()) {
            ExitSignal exitSignal;
            int exitLoopGuard = 0;
            while ((exitSignal = strategy.shouldExit(barIndex, bar, context)) != null) {
                if (++exitLoopGuard > 100) {
                    log.warn("出场循环超过100次，强制退出: symbol={}, index={}",
                            config.getSymbol(), barIndex);
                    break;
                }

                BigDecimal exitPrice = exitSignal.getPrice() != null
                        ? exitSignal.getPrice()
                        : openPrice;
                SignalType exitDir = exitSignal.getDirection();

                if ((exitDir == SignalType.CLOSE_LONG && context.hasLongPosition())
                        || (exitDir == SignalType.CLOSE_SHORT && context.hasShortPosition())) {
                    context.closePosition(
                            barIndex,
                            exitPrice,
                            exitDir,
                            exitSignal.getExitType(),
                            exitSignal.getClosePercent(),
                            barTime
                    );
                    log.debug("出场执行: index={}, direction={}, price={}, closePercent={}, exitType={}",
                            barIndex, exitDir, exitPrice, exitSignal.getClosePercent(), exitSignal.getExitType());
                }

                if (!context.hasPosition()) {
                    break;
                }
            }
        }

        // Step 4: 入场检查（防止平仓后同向立即重新入场）
        boolean exitedLong = hadLongBeforeBar && !context.hasLongPosition();
        boolean exitedShort = hadShortBeforeBar && !context.hasShortPosition();
        if (!context.hasLongPosition() || !context.hasShortPosition()) {
            EntrySignal entrySignal = strategy.shouldEntry(barIndex, bar, context);

            if (entrySignal != null) {
                // 信号频率控制检查
                if (!allowSignal(entrySignal.getDirection(), bar.getBeginTime().toEpochMilli())) {
                    entrySignal = null;
                }
            }

            if (entrySignal != null) {
                BigDecimal signalStrength = BigDecimal.valueOf(entrySignal.getSignalStrength());
                SignalType direction = entrySignal.getDirection();
                boolean isLong = direction == SignalType.LONG;

                // 防止平仓后同根K线同向重新入场
                if (isLong && exitedLong) {
                    log.debug("跳过同向入场: LONG已在当前Bar平仓, index={}", barIndex);
                    entrySignal = null;
                }
                if (!isLong && exitedShort) {
                    log.debug("跳过同向入场: SHORT已在当前Bar平仓, index={}", barIndex);
                    entrySignal = null;
                }

                if (entrySignal != null) {
                    // 预计算 SMC 止损/止盈价（供以损定量 + 持久化 + 交易所下单），
                    // 止损/止盈价格由 SmcStopPreviewer 完全根据 PC 前端策略配置决定
                    BigDecimal stopLoss = null;
                    BigDecimal takeProfit = null;
                    if (stopPreviewer != null) {
                        try {
                            Double sl = stopPreviewer.computeStopDaily(openPrice.doubleValue(), isLong, timestamp);
                            if (sl != null && Double.isFinite(sl)) stopLoss = BigDecimal.valueOf(sl);
                            Double tp = stopPreviewer.computeTakeProfit(openPrice.doubleValue(), isLong, timestamp);
                            if (tp != null && Double.isFinite(tp)) takeProfit = BigDecimal.valueOf(tp);
                        } catch (Exception e) {
                            log.debug("SMC TP/SL 预计算失败，将不预设止盈止损: {}", e.getMessage());
                        }
                    }

                if (isLong && !context.hasLongPosition()) {
                    BigDecimal contractCount = calcContractCount(openPrice, signalStrength, SignalType.LONG,
                            stopLoss != null ? stopLoss.doubleValue() : null);
                    if (contractCount.signum() > 0) {
                        context.openPosition(
                                SignalType.LONG,
                                openPrice,
                                contractCount,
                                takeProfit,
                                stopLoss,
                                barTime,
                                entrySignal.getSignalId(),
                                barIndex
                        );
                        enteredThisBar = true;
                        log.info("开多: index={}, price={}, qty={}, sl={}, tp={}",
                                barIndex, openPrice, contractCount, stopLoss, takeProfit);
                    } else {
                        log.info("[ENTRY] 开多跳过-合约数量为0: index={}, price={}, qty={}",
                                barIndex, openPrice, contractCount);
                    }
                } else if (!isLong && !context.hasShortPosition()) {
                    BigDecimal contractCount = calcContractCount(openPrice, signalStrength, SignalType.SHORT,
                            stopLoss != null ? stopLoss.doubleValue() : null);
                    if (contractCount.signum() > 0) {
                        context.openPosition(
                                SignalType.SHORT,
                                openPrice,
                                contractCount,
                                takeProfit,
                                stopLoss,
                                barTime,
                                entrySignal.getSignalId(),
                                barIndex
                        );
                        enteredThisBar = true;
                        log.info("开空: index={}, price={}, qty={}, sl={}, tp={}",
                                barIndex, openPrice, contractCount, stopLoss, takeProfit);
                    } else {
                        log.info("[ENTRY] 开空跳过-合约数量为0: index={}, price={}, qty={}",
                                barIndex, openPrice, contractCount);
                    }
                }
                }
            }
        }

        // Step 5: 加仓检查
        if (context.hasPosition() && !enteredThisBar) {
            ScaleInSignal scaleInSignal = strategy.shouldScaleIn(barIndex, bar, series, context);

            if (scaleInSignal != null) {
                SignalType direction = scaleInSignal.getDirection();

                if ((direction == SignalType.LONG && context.hasLongPosition())
                        || (direction == SignalType.SHORT && context.hasShortPosition())) {
                    BigDecimal addPrice = scaleInSignal.getPrice() != null
                            ? scaleInSignal.getPrice()
                            : openPrice;
                    BigDecimal scaleInStrength = BigDecimal.valueOf(scaleInSignal.getSignalStrength());

                    // 加仓止盈止损预计算（与首次开仓口径统一）
                    BigDecimal addTakeProfit = null;
                    BigDecimal addStopLoss = null;
                    if (stopPreviewer != null) {
                        boolean addIsLong = direction == SignalType.LONG;
                        try {
                            Double sl = stopPreviewer.computeStopDaily(addPrice.doubleValue(), addIsLong, timestamp);
                            if (sl != null && Double.isFinite(sl)) addStopLoss = BigDecimal.valueOf(sl);
                        } catch (Exception e) {
                            log.debug("加仓 SMC 止损预计算失败: {}", e.getMessage());
                        }
                        try {
                            Double tp = stopPreviewer.computeTakeProfit(addPrice.doubleValue(), addIsLong, timestamp);
                            if (tp != null && Double.isFinite(tp)) addTakeProfit = BigDecimal.valueOf(tp);
                        } catch (Exception e) {
                            log.debug("加仓 SMC 止盈预计算失败: {}", e.getMessage());
                        }
                    }
                    // 优先使用 scaleInSignal 中携带的逐笔 TP/SL，否则回退到 stopPreviewer 计算结果
                    BigDecimal entryTp = scaleInSignal.getTakeProfitPrice() != null
                            ? scaleInSignal.getTakeProfitPrice() : addTakeProfit;
                    BigDecimal entrySl = scaleInSignal.getStopLossPrice() != null
                            ? scaleInSignal.getStopLossPrice() : addStopLoss;

                    BigDecimal addQty = calcScaleInContractCount(addPrice, scaleInStrength, direction,
                            addStopLoss != null ? addStopLoss.doubleValue() : null);

                    // 双向持仓主导逻辑：加仓后该方向必须大于反向仓位至少 1 张
                    addQty = applyPositionControl(addQty, direction);

                    if (addQty.signum() > 0) {
                        context.addToPosition(
                                direction,
                                addPrice,
                                addQty,
                                entryTp,
                                entrySl,
                                barTime,
                                barIndex,
                                scaleInSignal.getSignalId()
                        );
                        log.debug("加仓: index={}, direction={}, price={}, qty={}",
                                barIndex, direction, addPrice, addQty);
                    }
                }
            }
        }

        // Step 6: 权益曲线采样
        context.sampleEquity(barIndex, timestamp, closePrice);
    }

    // ==================== 仓位计算 ====================

    /**
     * 带预计算止损的仓位计算，统一委托给 PositionAdjusterFactory。
     */
    protected BigDecimal calcContractCount(BigDecimal price, BigDecimal signalStrength,
                                            SignalType direction, Double preComputedStopLoss) {
        return calcContractCount(price, signalStrength, direction, preComputedStopLoss, null);
    }

    /**
     * 带预计算止损止盈的仓位计算，统一委托给 PositionAdjusterFactory。
     */
    private BigDecimal calcContractCount(BigDecimal price, BigDecimal signalStrength,
                                          SignalType direction, Double preComputedStopLoss,
                                          Double preComputedTakeProfit) {
        try {
            PositionAdjusterType type = "RISK".equalsIgnoreCase(config.getPositionMode())
                    ? PositionAdjusterType.RISK
                    : PositionAdjusterType.QUALITY;

            PositionAdjuster adjuster = PositionAdjusterFactory.getAdjuster(type).orElse(null);
            if (adjuster == null) {
                return fallbackCalc(price, signalStrength);
            }

            double qualityScore = signalStrength.doubleValue();
            double basePosition = config.getPositionAmount().doubleValue();

            TradingSignalDto signal = TradingSignalDto.builder()
                    .symbol(config.getSymbol())
                    .triggerPrice(price.doubleValue())
                    .signalStrength(qualityScore)
                    .direction(direction.name())
                    .stopLossPrice(preComputedStopLoss != null && preComputedStopLoss > 0 ? preComputedStopLoss : null)
                    .takeProfitPrice(preComputedTakeProfit != null && preComputedTakeProfit > 0 ? preComputedTakeProfit : null)
                    .build();

            AdjustmentContext adjContext = AdjustmentContext.builder()
                    .accountBalance(config.getInitialCapital().doubleValue())
                    .build();
            adjContext.getMetadata().put("riskPercent", 1.0);
            adjContext.getMetadata().put("leverage", (double) config.getLeverage());
            if (preComputedStopLoss != null && preComputedStopLoss > 0) {
                adjContext.getMetadata().put("stopLossPrice", preComputedStopLoss);
            }

            AdjustmentResult result = adjuster.adjust(signal, qualityScore, basePosition, adjContext);

            double positionSize = result.getPositionSize();
            double contractSize = config.getContractSpec().getContractSize().doubleValue();
            double contractQuantity = TradingUtil.convertUsdtToContractSize(
                    positionSize,
                    price.doubleValue(),
                    config.getLeverage(),
                    contractSize
            );

            BigDecimal qty = BigDecimal.valueOf(contractQuantity);
            log.debug("仓位计算: type={}, basePos={}, adjustedPos={}, qty={}",
                    type, basePosition, positionSize, qty);
            return qty;

        } catch (Exception e) {
            log.warn("仓位调节器调用失败，使用 fallback 计算: error={}", e.getMessage());
            return fallbackCalc(price, signalStrength);
        }
    }

    /**
     * 仓位计算（无预计算止损），统一委托给 PositionAdjusterFactory。
     */
    protected BigDecimal calcContractCount(BigDecimal price, BigDecimal signalStrength, SignalType direction) {
        return calcContractCount(price, signalStrength, direction, null, null);
    }

    /**
     * 加仓专用仓位计算，使用 {@link PositionAdjusterType#SCALE_IN} 调节器。
     */
    private BigDecimal calcScaleInContractCount(BigDecimal price, BigDecimal signalStrength,
                                                 SignalType direction, Double preComputedStopLoss) {
        try {
            PositionAdjuster adjuster = PositionAdjusterFactory.getAdjuster(PositionAdjusterType.SCALE_IN).orElse(null);
            if (adjuster == null) {
                return fallbackCalc(price, signalStrength);
            }

            double qualityScore = signalStrength.doubleValue();
            double basePosition = config.getPositionAmount().doubleValue();

            TradingSignalDto signal = TradingSignalDto.builder()
                    .symbol(config.getSymbol())
                    .triggerPrice(price.doubleValue())
                    .signalStrength(qualityScore)
                    .direction(direction.name())
                    .stopLossPrice(preComputedStopLoss != null && preComputedStopLoss > 0 ? preComputedStopLoss : null)
                    .build();

            AdjustmentContext adjContext = AdjustmentContext.builder()
                    .accountBalance(config.getInitialCapital().doubleValue())
                    .build();
            adjContext.getMetadata().put("riskPercent", 1.0);
            adjContext.getMetadata().put("leverage", (double) config.getLeverage());
            if (preComputedStopLoss != null && preComputedStopLoss > 0) {
                adjContext.getMetadata().put("stopLossPrice", preComputedStopLoss);
            }

            AdjustmentResult result = adjuster.adjust(signal, qualityScore, basePosition, adjContext);

            double positionSize = result.getPositionSize();
            double contractSize = config.getContractSpec().getContractSize().doubleValue();
            double contractQuantity = TradingUtil.convertUsdtToContractSize(
                    positionSize, price.doubleValue(), config.getLeverage(), contractSize);

            log.debug("加仓仓位计算: type=SCALE_IN, basePos={}, adjustedPos={}, qty={}",
                    basePosition, positionSize, contractQuantity);
            return BigDecimal.valueOf(contractQuantity);

        } catch (Exception e) {
            log.warn("加仓仓位调节器调用失败，使用 fallback 计算: error={}", e.getMessage());
            return fallbackCalc(price, signalStrength);
        }
    }

    /**
     * 仓位控制 — 双向持仓主导逻辑：加仓后该方向数量必须大于反向仓位至少 1 张。
     */
    private BigDecimal applyPositionControl(BigDecimal addQty, SignalType side) {
        if (context.hasLongPosition() && context.hasShortPosition()) {
            BigDecimal longQty = context.getLongQuantity();
            BigDecimal shortQty = context.getShortQuantity();

            if (side == SignalType.LONG) {
                BigDecimal afterAdd = longQty.add(addQty);
                if (afterAdd.compareTo(shortQty) <= 0) {
                    BigDecimal minAdd = shortQty.subtract(longQty).add(BigDecimal.ONE);
                    if (minAdd.signum() > 0) {
                        log.info("加仓主导调整(LONG): 当前多={}, 空={}, 原加仓={}, 调整为={}",
                                longQty, shortQty, addQty, minAdd);
                        addQty = minAdd;
                    }
                }
            } else {
                BigDecimal afterAdd = shortQty.add(addQty);
                if (afterAdd.compareTo(longQty) <= 0) {
                    BigDecimal minAdd = longQty.subtract(shortQty).add(BigDecimal.ONE);
                    if (minAdd.signum() > 0) {
                        log.info("加仓主导调整(SHORT): 当前多={}, 空={}, 原加仓={}, 调整为={}",
                                longQty, shortQty, addQty, minAdd);
                        addQty = minAdd;
                    }
                }
            }
        }
        return addQty;
    }

    private BigDecimal fallbackCalc(BigDecimal price, BigDecimal signalStrength) {
        if (signalStrength == null || signalStrength.compareTo(BigDecimal.ZERO) <= 0) {
            signalStrength = BigDecimal.ONE;
        }

        BigDecimal numerator = config.getPositionAmount()
                .multiply(signalStrength)
                .multiply(BigDecimal.valueOf(config.getLeverage()));

        BigDecimal denominator = price.multiply(config.getContractSpec().getContractSize());

        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return numerator.divide(denominator, 0, RoundingMode.DOWN);
    }
}