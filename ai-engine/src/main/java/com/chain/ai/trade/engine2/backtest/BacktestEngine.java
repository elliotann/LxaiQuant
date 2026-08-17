package com.chain.ai.trade.engine2.backtest;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.utils.ProfitCalcUtils;
import com.chain.ai.trade.common.utils.TradingUtil;
import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
import com.chain.ai.trade.engine.risk.adjuster.AdjustmentContext;
import com.chain.ai.trade.engine.risk.adjuster.AdjustmentResult;
import com.chain.ai.trade.engine.risk.adjuster.PositionAdjuster;
import com.chain.ai.trade.engine.risk.adjuster.PositionAdjusterFactory;
import com.chain.ai.trade.engine.risk.adjuster.PositionAdjusterType;
import com.chain.ai.trade.engine2.core.EntrySignal;
import com.chain.ai.trade.engine2.core.AbstractEngine;
import com.chain.ai.trade.engine2.core.ExitSignal;
import com.chain.ai.trade.engine2.core.ScaleInSignal;
import com.chain.ai.trade.engine2.core.context.StrategyContext;
import com.chain.ai.trade.engine2.core.cost.CostModel;
import com.chain.ai.trade.engine2.core.cost.MakerTakerCostModel;
import com.chain.ai.trade.engine2.core.cost.PercentageCostModel;
import com.chain.ai.trade.engine2.persistence.PersistenceGateway;
import com.chain.ai.trade.engine2.rules.base.SmcStopPreviewer;
import com.chain.ai.trade.engine2.strategy.ScriptStrategy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.DoubleSummaryStatistics;

/**
 * 回测引擎 — for 循环遍历 BarSeries，调用 ScriptStrategy 决策，内存撮合。
 * <p>
 * 继承 {@link AbstractEngine} 模板，实现 {@link #executeLoop()} 主循环逻辑。
 * 与 PaperEngine/LiveEngine 的区别：
 * <ul>
 *   <li>同步执行：同一线程内 for 循环遍历全部 K 线</li>
 *   <li>内存撮合：使用 Bar 的开盘价模拟成交，不涉及真实订单</li>
 *   <li>批量持久化：回测结束后统一写入结果</li>
 * </ul>
 */
@Slf4j
public class BacktestEngine extends AbstractEngine {

    private final BacktestConfig config;
    private final BacktestContext context;

    /** 持久化门面（可选，为 null 时不执行持久化） */
    private final PersistenceGateway gateway;

    /** 回测任务 ID，用于持久化关联 */
    private final String flushTaskId;

    /** SMC 止损预计算器（供以损定量使用，开仓前预计算止损位） */
    private SmcStopPreviewer stopPreviewer;

    public BacktestEngine(BarSeries series, ScriptStrategy strategy, BacktestConfig config,
                          PersistenceGateway gateway, String flushTaskId) {
        super(series, strategy);
        this.config = config;
        this.gateway = gateway;
        this.flushTaskId = flushTaskId;
        // 创建 CostModel
        CostModel costModel = createCostModel(config);
        this.context = new BacktestContext(config.getSymbol(), config.getInitialCapital(),
                config.getContractSpec(), BigDecimal.valueOf(config.getSlippage()),
                config.getLeverage(), costModel);
    }

    /**
     * 设置 SMC 止损预计算器（供以损定量仓位计算使用）
     */
    public void setStopPreviewer(SmcStopPreviewer stopPreviewer) {
        this.stopPreviewer = stopPreviewer;
    }

    private CostModel createCostModel(BacktestConfig config) {
        // 如果配置了 commissionRate，使用百分比成本模型
        if (config.getCommissionRate() != null && config.getCommissionRate().compareTo(BigDecimal.ZERO) > 0) {
            return new PercentageCostModel(config.getCommissionRate());
        }
        // 否则使用默认的 Maker/Taker 费率（OKX 永续合约默认）
        return new MakerTakerCostModel(
                BigDecimal.valueOf(0.0002),  // Maker 0.02%
                BigDecimal.valueOf(0.0005),  // Taker 0.05%
                false
        );
    }

    @Override
    protected void initStrategy() {
        strategy.init(new StrategyContext(
                null, config.getSymbol(), null, series, null));

        log.info("回测开始: symbol={}, totalBars={}, warmup={}, initialCapital={}",
                config.getSymbol(), series.getBarCount(), config.getWarmupPeriod(), config.getInitialCapital());
    }

    @Override
    protected void beforeRun() {
        if (gateway != null) {
            gateway.openBatch();
            log.info("回测持久化 batch 已开启: taskId={}", flushTaskId);
        }
    }

    /**
     * 主循环 — for 循环遍历每根 K 线：逐笔 TP/SL → 出场 → 入场 → 加仓 → 采样权益曲线。
     * <p>
     * 加仓流程：有持仓且未平仓 → 检查 shouldScaleIn → 计算数量 → 仓位风控 → 执行加仓。
     */
    @Override
    protected void executeLoop() {
        int totalBars = series.getBarCount();
        int warmup = config.getWarmupPeriod();

        for (int i = warmup; i < totalBars; i++) {
            Bar bar = series.getBar(i);
            BigDecimal openPrice = BigDecimal.valueOf(bar.getOpenPrice().doubleValue());
            BigDecimal highPrice = BigDecimal.valueOf(bar.getHighPrice().doubleValue());
            BigDecimal lowPrice = BigDecimal.valueOf(bar.getLowPrice().doubleValue());
            BigDecimal closePrice = BigDecimal.valueOf(bar.getClosePrice().doubleValue());
            LocalDateTime barEndTime = LocalDateTime.ofInstant(bar.getBeginTime(), ZoneId.systemDefault());

            // 记录本 Bar 入场前状态，用于防止平仓后立即同向重新入场
            boolean hadLongBeforeBar = context.hasLongPosition();
            boolean hadShortBeforeBar = context.hasShortPosition();
            boolean enteredThisBar = false;

            // 1. 逐笔止盈/止损（使用极值检测，避免收盘价漏检）
            if (context.hasPosition()) {
                boolean hit = context.closeEntriesByTpSl(i, highPrice, lowPrice, closePrice, barEndTime);
                if (hit) {
                    log.debug("逐笔止盈止损: index={}", i);
                }
            }

            // 2. 有持仓 → 检查全线出场（支持分批：while 循环直到没有出场信号）
            if (context.hasPosition()) {
                ExitSignal exitSignal;
                while ((exitSignal = strategy.shouldExit(i, bar, context)) != null) {
                    BigDecimal exitPrice = exitSignal.getPrice() != null ? exitSignal.getPrice() : openPrice;
                    context.closePosition(i, exitPrice, exitSignal.getDirection(), exitSignal.getExitType(), exitSignal.getClosePercent(), barEndTime);
                    log.debug("平仓: index={}, exitPrice={}, exitSignal={}, exitType={}, closePercent={}",
                            i, exitPrice, exitSignal.getDirection(), exitSignal.getExitType(), exitSignal.getClosePercent());
                    if (!context.hasPosition()) {
                        break;
                    }
                }
            }

            // 3. 检查入场（防止平仓后同向立即重新入场）
            boolean exitedLong = hadLongBeforeBar && !context.hasLongPosition();
            boolean exitedShort = hadShortBeforeBar && !context.hasShortPosition();
            if (!context.hasLongPosition() || !context.hasShortPosition()) {
                EntrySignal entrySignal = strategy.shouldEntry(i, bar, context);
                Instant endTime = bar.getBeginTime();
                if (entrySignal != null) {
                    SignalType direction = entrySignal.getDirection();
                    boolean isLong = direction == SignalType.LONG;

                    // 防止平仓后同根K线同向重新入场
                    if (isLong && exitedLong) {
                        log.debug("跳过同向入场: LONG已在当前Bar平仓, index={}", i);
                        continue;
                    }
                    if (!isLong && exitedShort) {
                        log.debug("跳过同向入场: SHORT已在当前Bar平仓, index={}", i);
                        continue;
                    }

                    // 信号频率控制检查
                    if (!allowSignal(direction, bar.getBeginTime().toEpochMilli())) {
                        continue;
                    }

                    BigDecimal signalStrength = BigDecimal.valueOf(entrySignal.getSignalStrength());

                    // 预计算 SMC 止损/止盈价（供以损定量 + 持久化），
                    // 止损/止盈价格由 SmcStopPreviewer 完全根据 PC 前端策略配置决定
                    BigDecimal stopLoss = null;
                    BigDecimal takeProfit = null;
                    if (stopPreviewer != null) {
                        try {
                            Double sl = stopPreviewer.computeStopDaily(openPrice.doubleValue(), isLong,
                                    bar.getBeginTime().toEpochMilli());
                            if (sl != null && Double.isFinite(sl)) stopLoss = BigDecimal.valueOf(sl);
                            Double tp = stopPreviewer.computeTakeProfit(openPrice.doubleValue(), isLong,
                                    bar.getBeginTime().toEpochMilli());
                            if (tp != null && Double.isFinite(tp)) takeProfit = BigDecimal.valueOf(tp);
                        } catch (Exception e) {
                            log.debug("SMC TP/SL 预计算失败: {}", e.getMessage());
                        }
                    }

                    if (isLong && !context.hasLongPosition()) {
                        BigDecimal contractCount = calcContractCount(openPrice, signalStrength, SignalType.LONG,
                                stopLoss != null ? stopLoss.doubleValue() : null);
                        if (contractCount.signum() <= 0) {
                            log.warn("开多仓位计算为 0: index={}, price={}, signalStrength={}", i, openPrice, signalStrength);
                        } else {
                            context.openPosition(i, SignalType.LONG, openPrice, contractCount, takeProfit, stopLoss,
                                    LocalDateTime.ofInstant(endTime, ZoneId.systemDefault()),
                                    entrySignal.getSignalId());
                            enteredThisBar = true;
                            log.info("开多: index={}, price={}, qty={}, sl={}, tp={}",
                                    i, openPrice, contractCount, stopLoss, takeProfit);
                        }
                    } else if (!isLong && !context.hasShortPosition()) {
                        BigDecimal contractCount = calcContractCount(openPrice, signalStrength, SignalType.SHORT,
                                stopLoss != null ? stopLoss.doubleValue() : null);
                        if (contractCount.signum() <= 0) {
                            log.warn("开空仓位计算为 0: index={}, price={}, signalStrength={}", i, openPrice, signalStrength);
                        } else {
                            context.openPosition(i, SignalType.SHORT, openPrice, contractCount, takeProfit, stopLoss,
                                    LocalDateTime.ofInstant(endTime, ZoneId.systemDefault()),
                                    entrySignal.getSignalId());
                            enteredThisBar = true;
                            log.info("开空: index={}, price={}, qty={}, sl={}, tp={}",
                                    i, openPrice, contractCount, stopLoss, takeProfit);
                        }
                    }
                }
            }

            // 4. 有持仓且未平仓且本 bar 未开仓 → 检查加仓
            if (context.hasPosition() && !enteredThisBar) {
                ScaleInSignal scaleInSignal = strategy.shouldScaleIn(i, bar, series, context);
                if (scaleInSignal != null) {
                    SignalType direction = scaleInSignal.getDirection();
                    if ((direction == SignalType.LONG && context.hasLongPosition())
                            || (direction == SignalType.SHORT && context.hasShortPosition())) {
                        BigDecimal addPrice = scaleInSignal.getPrice() != null ? scaleInSignal.getPrice() : openPrice;
                        BigDecimal scaleInStrength = BigDecimal.valueOf(scaleInSignal.getSignalStrength());

                        // 加仓止盈止损预计算（与首次开仓口径统一）
                        BigDecimal addTakeProfit = null;
                        BigDecimal addStopLoss = null;
                        if (stopPreviewer != null) {
                            boolean addIsLong = direction == SignalType.LONG;
                            long addTimestamp = bar.getBeginTime().toEpochMilli();
                            try {
                                Double sl = stopPreviewer.computeStopDaily(addPrice.doubleValue(), addIsLong, addTimestamp);
                                if (sl != null && Double.isFinite(sl)) addStopLoss = BigDecimal.valueOf(sl);
                            } catch (Exception e) {
                                log.debug("加仓 SMC 止损预计算失败: {}", e.getMessage());
                            }
                            try {
                                Double tp = stopPreviewer.computeTakeProfit(addPrice.doubleValue(), addIsLong, addTimestamp);
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
                        addQty = applyPositionControl(addQty, addPrice, direction);
                        if (addQty.compareTo(BigDecimal.ZERO) > 0) {
                            context.addToPosition(i, direction, addPrice, addQty,
                                    entryTp, entrySl,
                                    LocalDateTime.ofInstant(bar.getBeginTime(), ZoneId.systemDefault()),
                                    scaleInSignal.getSignalId());
                            log.debug("加仓: index={}, direction={}, price={}, qty={}, reason={}",
                                    i, direction, addPrice, addQty, scaleInSignal.getReason());
                        }
                    }
                }
            }

            // 5. 采样权益曲线（用收盘价计算浮动盈亏）
            context.sampleEquity(i, bar.getBeginTime().toEpochMilli(), closePrice);
        }
    }

    /**
     * 仓位控制 — 加仓后不超过最大持仓量，且不超过可用资金。
     * <p>
     * 双向持仓主导逻辑：当多空同时有持仓时，加仓后该方向数量必须大于反向仓位至少 1 张。
     *
     * @param addQty   计划加仓数量
     * @param price    当前价格
     * @param side     持仓方向
     * @return 实际可加仓数量（可能小于原值）
     */
    private BigDecimal applyPositionControl(BigDecimal addQty, BigDecimal price, SignalType side) {
        // 双向持仓主导逻辑：加仓后该方向必须大于反向仓位至少 1 张
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

    /**
     * 带预计算止损的仓位计算，统一委托给 PositionAdjusterFactory。
     */
    private BigDecimal calcContractCount(BigDecimal price, BigDecimal signalStrength,
                                          SignalType direction, Double preComputedStopLoss) {
        return calcContractCount(price, signalStrength, direction, preComputedStopLoss, null);
    }

    /**
     * 仓位计算（无预计算止损），统一委托给 PositionAdjusterFactory。
     */
    private BigDecimal calcContractCount(BigDecimal price, BigDecimal signalStrength, SignalType direction) {
        return calcContractCount(price, signalStrength, direction, null, null);
    }

    /**
     * 带预计算止损止盈的仓位计算，统一委托给 PositionAdjusterFactory。
     */
    private BigDecimal calcContractCount(BigDecimal price, BigDecimal signalStrength,
                                          SignalType direction, Double preComputedStopLoss,
                                          Double preComputedTakeProfit) {
        PositionAdjusterType type = "RISK".equalsIgnoreCase(config.getPositionMode())
                ? PositionAdjusterType.RISK : PositionAdjusterType.QUALITY;

        PositionAdjuster adjuster = PositionAdjusterFactory.getAdjuster(type).orElse(null);
        if (adjuster == null) {
            return fallbackCalc(price, signalStrength);
        }

        try {
            // 如果有预计算止损价则使用，否则根据方向计算
            double stopLossPrice;
            if (preComputedStopLoss != null && preComputedStopLoss > 0) {
                stopLossPrice = preComputedStopLoss;
            } else {
                boolean isLong = direction == SignalType.LONG;
                double buffer = config.getDailyStopLossBuffer() / 100.0;
                stopLossPrice = isLong
                        ? price.doubleValue() * (1 - buffer)
                        : price.doubleValue() * (1 + buffer);
            }

            TradingSignalDto signal = TradingSignalDto.builder()
                    .symbol(config.getSymbol())
                    .triggerPrice(price.doubleValue())
                    .stopLossPrice(stopLossPrice)
                    .takeProfitPrice(preComputedTakeProfit != null && preComputedTakeProfit > 0 ? preComputedTakeProfit : null)
                    .build();

            double qualityScore = Math.min(signalStrength.doubleValue(), 1.0);

            double basePosition = config.getPositionAmount().doubleValue();
            double accountBalance = config.getAccountBalance() != null
                    && config.getAccountBalance().compareTo(BigDecimal.ZERO) > 0
                    ? config.getAccountBalance().doubleValue()
                    : basePosition;

            AdjustmentContext context = AdjustmentContext.builder()
                    .symbol(config.getSymbol())
                    .currentPrice(price.doubleValue())
                    .accountBalance(accountBalance)
                    .build();
            context.getMetadata().put("riskPercent", config.getSingleTradeRiskPct());
            if (preComputedStopLoss != null && preComputedStopLoss > 0) {
                context.getMetadata().put("stopLossPrice", preComputedStopLoss);
            }

            AdjustmentResult result = adjuster.adjust(signal, qualityScore, basePosition, context);
            double positionSize = result.getPositionSize();

            if (positionSize <= 0) {
                return BigDecimal.ZERO;
            }

            double contractSize = config.getContractSize().doubleValue();
            double contractQuantity = TradingUtil.convertUsdtToContractSize(
                    positionSize, price.doubleValue(), config.getLeverage(), contractSize);

            log.debug("仓位计算: positionMode={}, signalStrength={}, qualityScore={}, positionSize=${}, contractQty={}",
                    config.getPositionMode(), signalStrength, qualityScore,
                    String.format("%.2f", positionSize), String.format("%.4f", contractQuantity));

            return BigDecimal.valueOf(contractQuantity);
        } catch (Exception e) {
            log.error("仓位调节器调用失败，回退到原公式: {}", e.getMessage());
            return fallbackCalc(price, signalStrength);
        }
    }

    /**
     * 加仓专用仓位计算，使用 {@link PositionAdjusterType#SCALE_IN} 调节器。
     */
    private BigDecimal calcScaleInContractCount(BigDecimal price, BigDecimal signalStrength,
                                                 SignalType direction, Double preComputedStopLoss) {
        PositionAdjuster adjuster = PositionAdjusterFactory.getAdjuster(PositionAdjusterType.SCALE_IN).orElse(null);
        if (adjuster == null) {
            return fallbackCalc(price, signalStrength);
        }

        try {
            double stopLossPrice;
            if (preComputedStopLoss != null && preComputedStopLoss > 0) {
                stopLossPrice = preComputedStopLoss;
            } else {
                boolean isLong = direction == SignalType.LONG;
                double buffer = config.getDailyStopLossBuffer() / 100.0;
                stopLossPrice = isLong
                        ? price.doubleValue() * (1 - buffer)
                        : price.doubleValue() * (1 + buffer);
            }

            TradingSignalDto signal = TradingSignalDto.builder()
                    .symbol(config.getSymbol())
                    .triggerPrice(price.doubleValue())
                    .stopLossPrice(stopLossPrice)
                    .build();

            double qualityScore = Math.min(signalStrength.doubleValue(), 1.0);
            double basePosition = config.getPositionAmount().doubleValue();
            double accountBalance = config.getAccountBalance() != null
                    && config.getAccountBalance().compareTo(BigDecimal.ZERO) > 0
                    ? config.getAccountBalance().doubleValue()
                    : basePosition;

            AdjustmentContext context = AdjustmentContext.builder()
                    .symbol(config.getSymbol())
                    .currentPrice(price.doubleValue())
                    .accountBalance(accountBalance)
                    .build();
            context.getMetadata().put("riskPercent", config.getSingleTradeRiskPct());
            if (preComputedStopLoss != null && preComputedStopLoss > 0) {
                context.getMetadata().put("stopLossPrice", preComputedStopLoss);
            }

            AdjustmentResult result = adjuster.adjust(signal, qualityScore, basePosition, context);
            double positionSize = result.getPositionSize();

            if (positionSize <= 0) {
                return BigDecimal.ZERO;
            }

            double contractSize = config.getContractSize().doubleValue();
            double contractQuantity = TradingUtil.convertUsdtToContractSize(
                    positionSize, price.doubleValue(), config.getLeverage(), contractSize);

            log.debug("加仓仓位计算: positionMode=SCALE_IN, signalStrength={}, qualityScore={}, positionSize=${}, contractQty={}",
                    signalStrength, qualityScore,
                    String.format("%.2f", positionSize), String.format("%.4f", contractQuantity));
            return BigDecimal.valueOf(contractQuantity);
        } catch (Exception e) {
            log.error("加仓仓位调节器调用失败，回退到原公式: {}", e.getMessage());
            return fallbackCalc(price, signalStrength);
        }
    }

    /** fallback：调节器不可用时使用的原公式 */
    private BigDecimal fallbackCalc(BigDecimal price, BigDecimal signalStrength) {
        return config.getPositionAmount()
                .multiply(signalStrength)
                .multiply(BigDecimal.valueOf(config.getLeverage()))
                .divide(price.multiply(config.getContractSize()),
                        0, RoundingMode.DOWN);
    }

    /**
     * 后置处理 — 收盘后强制平仓（分别平多/空），并采样最终权益曲线点。
     */
    @Override
    protected void afterRun() {
        int totalBars = series.getBarCount();
        int lastIndex = totalBars - 1;
        Bar lastBar = series.getBar(lastIndex);
        BigDecimal closePrice = BigDecimal.valueOf(lastBar.getClosePrice().doubleValue());
        long lastBarTimestamp = lastBar.getBeginTime().toEpochMilli();
        LocalDateTime barEndTime = LocalDateTime.ofInstant(lastBar.getBeginTime(), ZoneId.systemDefault());
        if (context.hasLongPosition()) {
            context.closePosition(lastIndex, closePrice, SignalType.CLOSE_LONG, null, barEndTime);
            log.info("回测结束，强制平多: price={}", closePrice);
        }
        if (context.hasShortPosition()) {
            context.closePosition(lastIndex, closePrice, SignalType.CLOSE_SHORT, null, barEndTime);
            log.info("回测结束，强制平空: price={}", closePrice);
        }
        // 强制平仓后采样最终权益曲线点（注意：此时无浮动盈亏，权益为实际余额）
        context.sampleEquity(lastIndex, lastBarTimestamp, closePrice);
    }

    @Override
    protected BacktestResult buildResult() {
        int totalBars = series.getBarCount();
        BigDecimal lastClosePrice = BigDecimal.valueOf(series.getBar(totalBars - 1).getClosePrice().doubleValue());

        var trades = context.getTrades();
        var equityCurve = context.getEquityCurve();

        BigDecimal initialCapital = config.getInitialCapital();
        BigDecimal finalEquity = context.getEquity(lastClosePrice);
        BigDecimal totalPnl = finalEquity.subtract(initialCapital);

        // 统计胜率
        long winCount = trades.stream()
                .filter(t -> t.getPnl().compareTo(BigDecimal.ZERO) > 0)
                .count();
        double winRate = trades.isEmpty() ? 0 : (double) winCount / trades.size();

        // 最大回撤
        BigDecimal maxDrawdown = computeMaxDrawdown(equityCurve);

        // ── 进阶绩效指标 ──────────────────────────────────

        // 1. 交易维度统计
        int profitableTrades = (int) winCount;
        double avgWin = 0, avgLoss = 0, largestWin = 0, largestLoss = 0;
        if (!trades.isEmpty()) {
            DoubleSummaryStatistics winStats = trades.stream()
                    .filter(t -> t.getPnl().compareTo(BigDecimal.ZERO) > 0)
                    .mapToDouble(t -> t.getPnl().doubleValue())
                    .summaryStatistics();
            DoubleSummaryStatistics lossStats = trades.stream()
                    .filter(t -> t.getPnl().compareTo(BigDecimal.ZERO) <= 0)
                    .mapToDouble(t -> t.getPnl().doubleValue())
                    .summaryStatistics();
            avgWin = winStats.getCount() > 0 ? winStats.getAverage() : 0;
            avgLoss = lossStats.getCount() > 0 ? lossStats.getAverage() : 0;
            largestWin = winStats.getCount() > 0 ? winStats.getMax() : 0;
            largestLoss = lossStats.getCount() > 0 ? lossStats.getMin() : 0;
        }
        double profitLossRatio = avgLoss != 0 ? Math.abs(avgWin / avgLoss) : 0;

        // 2. 权益曲线维度统计（年化 Sharpe / Sortino / Volatility / AnnualReturn）
        double annualReturn = 0, sharpeRatio = 0, sortinoRatio = 0, volatility = 0;
        if (equityCurve.size() >= 2 && initialCapital.compareTo(BigDecimal.ZERO) > 0) {
            Bar firstBar = series.getBar(0);
            Bar lastBar = series.getBar(totalBars - 1);

            // 总时间跨度（秒）
            long totalSeconds = Duration.between(firstBar.getBeginTime(), lastBar.getEndTime()).getSeconds();

            // 年化因子 = 一年总秒数 / 每根K线周期秒数
            Duration barDuration = firstBar.getTimePeriod();
            double periodsPerYear = barDuration != null && barDuration.getSeconds() > 0
                    ? (double) Duration.ofDays(365).getSeconds() / barDuration.getSeconds()
                    : 365.0 * 24.0 * 6.0; // 默认 10min

            // 从权益曲线计算 period 收益率序列
            DoubleSummaryStatistics returnStats = new DoubleSummaryStatistics();
            for (int i = 1; i < equityCurve.size(); i++) {
                BigDecimal prev = equityCurve.get(i - 1).getEquity();
                BigDecimal cur = equityCurve.get(i).getEquity();
                if (prev.compareTo(BigDecimal.ZERO) > 0) {
                    returnStats.accept(cur.subtract(prev).doubleValue() / prev.doubleValue());
                }
            }

            long n = returnStats.getCount();
            if (n > 0) {
                double meanReturn = returnStats.getAverage();

                // 手工计算方差（需遍历两次：第一次求 mean，第二次求方差）
                double sumSq = 0, sumDownSq = 0;
                for (int i = 1; i < equityCurve.size(); i++) {
                    BigDecimal prev = equityCurve.get(i - 1).getEquity();
                    BigDecimal cur = equityCurve.get(i).getEquity();
                    if (prev.compareTo(BigDecimal.ZERO) <= 0) continue;
                    double r = cur.subtract(prev).doubleValue() / prev.doubleValue();
                    double diff = r - meanReturn;
                    sumSq += diff * diff;
                    if (r < 0) sumDownSq += diff * diff;
                }
                double stdDev = Math.sqrt(sumSq / n);
                double downStdDev = Math.sqrt(sumDownSq / n);
                double sqrtAnn = Math.sqrt(periodsPerYear);

                sharpeRatio = stdDev > 0 ? (meanReturn / stdDev) * sqrtAnn : 0;
                sortinoRatio = downStdDev > 0 ? (meanReturn / downStdDev) * sqrtAnn : 0;
                volatility = stdDev * sqrtAnn;
            }

            // 年化收益率
            if (totalSeconds > 0) {
                double totalReturn = totalPnl.divide(initialCapital, 6, RoundingMode.HALF_UP).doubleValue();
                double years = (double) totalSeconds / (365.25 * 24 * 3600);
                annualReturn = Math.pow(1.0 + totalReturn, 1.0 / years) - 1.0;
            }
        }

        log.info("回测完成: totalTrades={}, finalEquity={}, totalPnl={}, sharpe={}",
                trades.size(), finalEquity, totalPnl, String.format("%.2f", sharpeRatio));

        BacktestResult result = BacktestResult.builder()
                .symbol(config.getSymbol())
                .totalBars(totalBars)
                .totalTrades(trades.size())
                .finalEquity(finalEquity)
                .totalPnl(totalPnl)
                .maxDrawdown(maxDrawdown)
                .winRate(winRate)
                .totalCommission(context.getTotalCommissionPaid())
                .annualReturn(annualReturn)
                .sharpeRatio(sharpeRatio)
                .sortinoRatio(sortinoRatio)
                .volatility(volatility)
                .profitableTrades(profitableTrades)
                .profitLossRatio(profitLossRatio)
                .averageWin(avgWin)
                .averageLoss(avgLoss)
                .largestWinTrade(largestWin)
                .largestLossTrade(largestLoss)
                .trades(trades)
                .equityCurve(equityCurve)
                .entryRecords(context.getEntryRecords())
                .actionRecords(context.getActionRecords())
                // 🔥 新增：传入已平仓快照
                .closedPositions(context.getClosedPositions())
                .build();

        if (gateway != null) {
            gateway.flush(flushTaskId, config.getSymbol(), result, series);
            log.info("回测持久化完成: taskId={}, trades={}", flushTaskId, result.getTotalTrades());
        }

        return result;
    }

    /** 从权益曲线计算最大回撤 */
    private static BigDecimal computeMaxDrawdown(
            java.util.List<BacktestResult.EquityPoint> curve) {
        if (curve.isEmpty()) return BigDecimal.ZERO;

        BigDecimal peak = curve.get(0).getEquity();
        BigDecimal maxDd = BigDecimal.ZERO;
        for (var pt : curve) {
            BigDecimal eq = pt.getEquity();
            if (eq.compareTo(peak) > 0) {
                peak = eq;
            } else if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dd = peak.subtract(eq)
                        .divide(peak, 4, java.math.RoundingMode.HALF_UP);
                if (dd.compareTo(maxDd) > 0) {
                    maxDd = dd;
                }
            }
        }
        return maxDd;
    }
}