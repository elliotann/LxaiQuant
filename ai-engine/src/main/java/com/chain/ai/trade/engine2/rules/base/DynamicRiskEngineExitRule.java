package com.chain.ai.trade.engine2.rules.base;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.mtf.MultiTimeFrameProvider;
import com.chain.ai.trade.engine2.backtest.model.MemoryPosition;
import com.chain.ai.trade.engine2.core.ExitSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import com.chain.ai.trade.engine2.rules.TradingRule;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态风控引擎出场规则（独立于 SmcStructuredExitRule）。
 * <p>
 * 左侧防守线：移动止损（结构追踪）；右侧进攻线：移动止盈（结构追踪）。
 * 结构点取自参考周期 SMC 指标：摆动点（前高/前低、更高低点/更低高点）与订单块（OB）。
 * </p>
 */
public class DynamicRiskEngineExitRule implements TradingRule {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRiskEngineExitRule.class);

    private final MultiTimeFrameProvider mtfProvider;
    private final SignalType direction; // LONG / SHORT
    private String symbol;

    // ==================== 防守线配置 ====================
    private boolean stopEnabled = false;
    private String stopPeriod = "15";
    private boolean stopUseOb = true;
    private boolean stopUseSwing = true;
    private double stopOffsetBuffer = 0.0005; // 小数
    private boolean stopBreakWick = true;      // true=影线刺穿，false=收盘破位
    private String stopActivation = "open";    // open / break

    // ==================== 进攻线配置 ====================
    private boolean tpEnabled = false;
    private String tpPeriod = "60";
    private boolean tpUseOb = true;
    private boolean tpUseSwing = true;
    private double tpOffsetBuffer = 0.0005;    // 小数
    private boolean tpTriggerWick = true;      // true=影线刺穿，false=收盘突破
    private String tpActivation = "open";      // open / break
    private String tpExitMode = "all";         // all / half
    private boolean tpMinStepEnabled = false;
    private double tpMinStep = 0.001;          // 小数

    // ==================== 运行状态 ====================
    private Double currentStopPrice;   // 防守线当前止损价
    private Double currentTpPrice;     // 进攻线当前止盈目标价
    private Double initialStopPrice;   // 仓位初始止损价（动态线只能相对其向有利方向移动）
    private Double initialTpPrice;     // 仓位初始止盈价（动态线只能相对其向有利方向移动）
    private boolean stopActivated = false;
    private boolean tpActivated = false;
    private boolean halfClosed = false; // 进攻线 half 模式是否已平 50%
    private int lastBarIndex = -1;
    private int previousEntryCount = 0;
    private boolean isLong;
    private Double entryPrice;
    private String posId;

    private final Map<String, CachedSmcIndicator> indicatorCache = new HashMap<>();

    public DynamicRiskEngineExitRule(MultiTimeFrameProvider mtfProvider, String symbol, SignalType direction) {
        this.mtfProvider = mtfProvider;
        this.symbol = symbol;
        this.direction = direction;
    }

    // ==================== 配置注入 ====================

    public void setStopEnabled(boolean v) { this.stopEnabled = v; }
    public void setStopPeriod(String v) { this.stopPeriod = v; }
    public void setStopUseOb(boolean v) { this.stopUseOb = v; }
    public void setStopUseSwing(boolean v) { this.stopUseSwing = v; }
    public void setStopOffsetBuffer(double v) { this.stopOffsetBuffer = v; }
    public void setStopBreakWick(boolean v) { this.stopBreakWick = v; }
    public void setStopActivation(String v) { this.stopActivation = v; }

    public void setTpEnabled(boolean v) { this.tpEnabled = v; }
    public void setTpPeriod(String v) { this.tpPeriod = v; }
    public void setTpUseOb(boolean v) { this.tpUseOb = v; }
    public void setTpUseSwing(boolean v) { this.tpUseSwing = v; }
    public void setTpOffsetBuffer(double v) { this.tpOffsetBuffer = v; }
    public void setTpTriggerWick(boolean v) { this.tpTriggerWick = v; }
    public void setTpActivation(String v) { this.tpActivation = v; }
    public void setTpExitMode(String v) { this.tpExitMode = v; }
    public void setTpMinStepEnabled(boolean v) { this.tpMinStepEnabled = v; }
    public void setTpMinStep(double v) { this.tpMinStep = v; }

    // ==================== TradingRule 接口 ====================

    @Override
    public ExitSignal evaluate(int index, Bar bar, BarSeries series, TradingContext context) {
        // 1. 无持仓不处理
        if (!context.hasPosition()) {
            posId = null;
            return null;
        }

        // 2. 提取持仓信息
        if (symbol == null) symbol = context.getSymbol();
        MemoryPosition position;
        if (direction == SignalType.LONG && context.hasLongPosition()) {
            isLong = true;
            position = context.getLongPosition();
        } else if (direction == SignalType.SHORT && context.hasShortPosition()) {
            isLong = false;
            position = context.getShortPosition();
        } else {
            return null;
        }

        // 3. 新交易开始（持仓 positionId 变化）→ 重置运行状态，
        //    避免上一笔交易的止损/止盈线与激活状态泄漏到本笔
        String newPosId = position.getPositionId();
        if (!newPosId.equals(posId)) {
            resetState();
            posId = newPosId;
        }

        entryPrice = position.getAvgPrice().doubleValue();
        if (entryPrice == null || entryPrice <= 0) return null;

        // 仓位初始止盈/止损价：动态风控线的移动必须相对该锚点向有利方向进行
        initialStopPrice = position.getStopLossPrice() != null ? position.getStopLossPrice().doubleValue() : null;
        initialTpPrice = position.getTakeProfitPrice() != null ? position.getTakeProfitPrice().doubleValue() : null;
        if (lastBarIndex == -1) {
            LOG.info("动态风控初始锚点: posId={}, dir={}, initialStop={}, initialTp={}",
                    posId, isLong ? "LONG" : "SHORT", initialStopPrice, initialTpPrice);
        }

        // 4. 检测加仓 → 仅重置部分平仓标记，保留已激活的防守线/进攻线状态与价格
        int currentEntryCount = isLong
                ? context.getLongPosition().getEntries().size()
                : context.getShortPosition().getEntries().size();
        if (previousEntryCount > 0 && currentEntryCount > previousEntryCount) {
            // 加仓不重置激活状态与价格线：趋势一旦确认（open/break）应保持激活，
            // 否则 break 模式下加仓后价格已远离前高，激活条件永远无法再次满足，止损线会彻底失效
            halfClosed = false;
            LOG.info("加仓检测到，保留当前防守线状态: stopPrice={}, tpPrice={}, stopActive={}, tpActive={}",
                    currentStopPrice, currentTpPrice, stopActivated, tpActivated);
        }
        previousEntryCount = currentEntryCount;

        // 5. 仅在 Bar 变化时重算出场位
        Bar effectiveBar = (index < series.getBarCount()) ? series.getBar(index) : bar;
        if (index != lastBarIndex) {
            updateLevels(bar.getBeginTime().toEpochMilli(), effectiveBar.getLowPrice().doubleValue(),
                    effectiveBar.getHighPrice().doubleValue());
            lastBarIndex = index;
        }

        double high = effectiveBar.getHighPrice().doubleValue();
        double low = effectiveBar.getLowPrice().doubleValue();
        double close = effectiveBar.getClosePrice().doubleValue();

        // 6. 防守线（移动止损 + 保本）
        ExitSignal stopSignal = evaluateStop(high, low, close);
        if (stopSignal != null) return stopSignal;

        // 7. 进攻线（移动止盈）
        return evaluateTakeProfit(high, low, close);
    }

    // ==================== 出场位更新 ====================

    /**
     * 每根新 Bar 重算防守线止损价与进攻线止盈目标，并处理激活时机。
     */
    private void updateLevels(long currentTime, double low, double high) {
        // ---- 防守线 ----
        if (stopEnabled) {
            SmartMoneyConceptsIndicator.Result smc = getCachedSmcResult(stopPeriod, currentTime);
            if (smc != null) {
                if (!stopActivated) {
                    if (checkStopActivation(smc, low, high)) {
                        stopActivated = true;
                        LOG.info("防守线激活: posId={}, dir={}, activation={}", posId, isLong ? "LONG" : "SHORT", stopActivation);
                    }
                }
                if (stopActivated) {
                    double base = resolveStopBase(smc, entryPrice);
                    if (Double.isFinite(base)) {
                        double newStop = isLong ? base * (1 - stopOffsetBuffer) : base * (1 + stopOffsetBuffer);
                        Double oldStop = currentStopPrice;
                        if (currentStopPrice == null) {
                            // 首次建立止损必须位于防守侧（做多低于当前低点、做空高于当前高点），
                            // 且相对仓位初始止损价只能向有利方向移动（做多上移、做空下移）
                            boolean onDefenseSide = isLong ? newStop < low : newStop > high;
                            boolean betterThanInitial = initialStopPrice == null
                                    || (isLong ? newStop >= initialStopPrice : newStop <= initialStopPrice);
                            if (onDefenseSide && betterThanInitial) {
                                currentStopPrice = newStop;
                            }
                        } else if (isLong && newStop > currentStopPrice && newStop < low
                                && (currentTpPrice == null || newStop < currentTpPrice)) {
                            currentStopPrice = newStop;
                        } else if (!isLong && newStop < currentStopPrice && newStop > high
                                && (currentTpPrice == null || newStop > currentTpPrice)) {
                            currentStopPrice = newStop;
                        }
                        if (currentStopPrice != null && !currentStopPrice.equals(oldStop)) {
                            LOG.info("防守线移动: posId={}, dir={}, stopPrice: {} -> {}",
                                    posId, isLong ? "LONG" : "SHORT", oldStop, currentStopPrice);
                        }
                    }
                }
            }
        }

        // ---- 进攻线 ----
        if (tpEnabled) {
            SmartMoneyConceptsIndicator.Result smc = getCachedSmcResult(tpPeriod, currentTime);
            if (smc != null) {
                if (!tpActivated) {
                    if (checkTpActivation(smc, low, high)) {
                        tpActivated = true;
                        LOG.info("进攻线激活: posId={}, dir={}, activation={}", posId, isLong ? "LONG" : "SHORT", tpActivation);
                    }
                }
                if (tpActivated) {
                    double base = resolveTpBase(smc, entryPrice);
                    if (Double.isFinite(base)) {
                        // 突破阻力位后止盈：做多挂结构位上方，做空挂结构位下方
                        double newTp = isLong ? base * (1 + tpOffsetBuffer) : base * (1 - tpOffsetBuffer);
                        Double oldTp = currentTpPrice;
                        if (currentTpPrice == null) {
                            // 首次建立止盈必须位于进攻侧（做多高于当前高点、做空低于当前低点），
                            // 且相对仓位初始止盈价只能向有利方向移动（做多上移、做空下移）
                            boolean onAttackSide = isLong ? newTp > high : newTp < low;
                            boolean betterThanInitial = initialTpPrice == null
                                    || (isLong ? newTp >= initialTpPrice : newTp <= initialTpPrice);
                            if (onAttackSide && betterThanInitial) {
                                currentTpPrice = newTp;
                            }
                        } else {
                            boolean better = isLong ? newTp > currentTpPrice : newTp < currentTpPrice;
                            boolean onAttackSide = isLong ? newTp > high : newTp < low;
                            if ((!tpMinStepEnabled || stepReached(newTp, currentTpPrice)) && better && onAttackSide) {
                                currentTpPrice = newTp;
                            }
                        }
                        if (currentTpPrice != null && !currentTpPrice.equals(oldTp)) {
                            LOG.info("进攻线移动: posId={}, dir={}, tpPrice: {} -> {}",
                                    posId, isLong ? "LONG" : "SHORT", oldTp, currentTpPrice);
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断新止盈目标相对当前目标的移动步长是否达到最小步进（防噪音）。
     */
    private boolean stepReached(double newTp, double currentTp) {
        if (currentTp == 0) return true;
        return Math.abs(newTp - currentTp) / Math.abs(currentTp) >= tpMinStep;
    }

    // ==================== 防守线结构点 ====================

    /**
     * 防守线止损结构点：摆动点（更高低点/更低高点）优先，订单块兜底。
     */
    private double resolveStopBase(SmartMoneyConceptsIndicator.Result smc, double entry) {
        if (stopUseSwing) {
            double v = isLong ? smc.getLastHigherLow() : smc.getLastLowerHigh();
            // 结构点必须位于入场价的防守侧（做多在下、做空在上），否则回退到订单块
            if (Double.isFinite(v) && (isLong ? v < entry : v > entry)) return v;
        }
        if (stopUseOb) {
            return resolveStopOb(smc, entry);
        }
        return Double.NaN;
    }

    /**
     * 防守线订单块：做多找入场价下方最近的需求区下沿，做空找入场价上方最近的供给区上沿。
     */
    private double resolveStopOb(SmartMoneyConceptsIndicator.Result smc, double entry) {
        int bias = isLong ? 1 : -1;
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks == null || blocks.isEmpty()) return Double.NaN;
        double best = Double.NaN;
        for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
            if (ob.bias != bias) continue;
            double edge = isLong ? ob.barLow : ob.barHigh;
            boolean innerSide = isLong ? edge < entry : edge > entry;
            if (!innerSide) continue;
            if (Double.isNaN(best) || (isLong ? edge > best : edge < best)) best = edge;
        }
        return best;
    }

    // ==================== 进攻线结构点 ====================

    /**
     * 进攻线止盈目标：前高/前低优先，对立订单块兜底。
     */
    private double resolveTpBase(SmartMoneyConceptsIndicator.Result smc, double entry) {
        // break 模式下前高/前低已被突破（激活条件即突破），改用订单块作为止盈目标，
        // 避免把止盈挂到已被突破的摆动点上，导致开仓后立即触发
        boolean breakMode = "break".equals(tpActivation);
        if (tpUseSwing && !breakMode) {
            double v = isLong ? smc.getLastSwingHigh() : smc.getLastSwingLow();
            if (Double.isFinite(v) && (isLong ? v > entry : v < entry)) return v;
        }
        if (tpUseOb) {
            double ob = resolveTpOb(smc, entry);
            if (Double.isFinite(ob)) return ob;
        }
        if (tpUseSwing) {
            double v = isLong ? smc.getLastSwingHigh() : smc.getLastSwingLow();
            if (Double.isFinite(v) && (isLong ? v > entry : v < entry)) return v;
        }
        return Double.NaN;
    }

    /**
     * 进攻线对立订单块：做多找入场价上方最近的供给区下沿，做空找入场价下方最近的需求区上沿。
     */
    private double resolveTpOb(SmartMoneyConceptsIndicator.Result smc, double entry) {
        int bias = isLong ? -1 : 1;
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks == null || blocks.isEmpty()) return Double.NaN;
        double best = Double.NaN;
        for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
            if (ob.bias != bias) continue;
            double edge = isLong ? ob.barLow : ob.barHigh;
            boolean outerSide = isLong ? edge > entry : edge < entry;
            if (!outerSide) continue;
            if (Double.isNaN(best) || (isLong ? edge < best : edge > best)) best = edge;
        }
        return best;
    }

    // ==================== 激活时机判断 ====================

    /**
     * 防守线激活时机：open=开仓即动，break=突破前高/前低后动。
     */
    private boolean checkStopActivation(SmartMoneyConceptsIndicator.Result smc, double low, double high) {
        switch (stopActivation) {
            case "break":
                return isLong ? high > smc.getLastSwingHigh() : low < smc.getLastSwingLow();
            case "open":
            default:
                return true;
        }
    }

    /**
     * 进攻线激活时机：open=开仓即动，break=突破前高/前低后动。
     */
    private boolean checkTpActivation(SmartMoneyConceptsIndicator.Result smc, double low, double high) {
        switch (tpActivation) {
            case "break":
                return isLong ? high > smc.getLastSwingHigh() : low < smc.getLastSwingLow();
            case "open":
            default:
                return true;
        }
    }

    // ==================== 出场触发检查 ====================

    /**
     * 防守线触发检查：影线刺穿（high/low）或收盘破位（close）。
     */
    private ExitSignal evaluateStop(double high, double low, double close) {
        if (!stopEnabled || !stopActivated || currentStopPrice == null) return null;
        boolean triggered = stopBreakWick
                ? (isLong ? low <= currentStopPrice : high >= currentStopPrice)
                : (isLong ? close <= currentStopPrice : close >= currentStopPrice);
        if (!triggered) return null;

        ExitType type = ExitType.TRAILING_STOP;
        LOG.info("防守线触发: posId={}, type={}, price={}", posId, type, currentStopPrice);
        return new ExitSignal(isLong ? SignalType.CLOSE_LONG : SignalType.CLOSE_SHORT,
                type, BigDecimal.valueOf(currentStopPrice));
    }

    /**
     * 进攻线触发检查：影线刺穿或收盘突破；all 全平，half 先平 50%。
     */
    private ExitSignal evaluateTakeProfit(double high, double low, double close) {
        if (!tpEnabled || !tpActivated || currentTpPrice == null) return null;
        boolean triggered = tpTriggerWick
                ? (isLong ? high >= currentTpPrice : low <= currentTpPrice)
                : (isLong ? close >= currentTpPrice : close <= currentTpPrice);
        if (!triggered) return null;

        SignalType signal = isLong ? SignalType.CLOSE_LONG : SignalType.CLOSE_SHORT;
        double tpPrice = currentTpPrice;
        if ("half".equals(tpExitMode) && !halfClosed) {
            halfClosed = true;
            currentTpPrice = null; // 剩余仓位等待下一根 Bar 重新计算新目标
            LOG.info("进攻线半仓止盈: posId={}, price={}", posId, tpPrice);
            return new ExitSignal(signal, ExitType.TRAILING_STOP_GAIN,
                    BigDecimal.valueOf(tpPrice), 50);
        }
        LOG.info("进攻线止盈: posId={}, price={}", posId, tpPrice);
        return new ExitSignal(signal, ExitType.TRAILING_STOP_GAIN, BigDecimal.valueOf(tpPrice));
    }

    // ==================== SMC 指标获取 ====================

    /**
     * 获取指定周期 SMC 结果（带序列版本缓存）。
     */
    private SmartMoneyConceptsIndicator.Result getCachedSmcResult(String period, long currentTime) {
        if (mtfProvider == null || period == null) return null;
        CandlestickIntervalEnum interval = parsePeriod(period);
        if (interval == null) return null;

        int idx = mtfProvider.getBarIndex(interval, currentTime);
        if (idx < 0) return null;

        BarSeries series = mtfProvider.getSeries(interval);
        if (series == null || series.getBarCount() < 50) return null;

        // 防御越界（getBarIndex 理论上已保证，此处兜底）
        if (idx >= series.getBarCount()) idx = series.getBarCount() - 1;

        // 回退到已收盘 bar：getBarIndex 返回的 bar 可能尚未收盘（含未来数据），
        // 结构计算只能基于 endTime <= currentTime 的已收盘 K 线，否则会引入未来函数
        while (idx >= 0 && series.getBar(idx).getEndTime().isAfter(Instant.ofEpochMilli(currentTime))) {
            idx--;
        }
        if (idx < 0) return null;

        long version = mtfProvider.getSeriesVersion(interval);
        CachedSmcIndicator cached = indicatorCache.get(period);
        if (cached == null || cached.seriesVersion != version) {
            SmartMoneyConceptsIndicator ind = createSmcIndicator(series);
            cached = new CachedSmcIndicator(ind, version);
            indicatorCache.put(period, cached);
        }
        return cached.indicator.getValue(idx);
    }

    /**
     * 周期字符串（分钟）转 K 线周期枚举。
     */
    private static CandlestickIntervalEnum parsePeriod(String period) {
        try {
            int min = Integer.parseInt(period);
            for (CandlestickIntervalEnum e : CandlestickIntervalEnum.values()) {
                if (e.getMinNum() == min) return e;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    /**
     * 创建 SMC 指标实例。
     */
    private SmartMoneyConceptsIndicator createSmcIndicator(BarSeries series) {
        SmartMoneyConceptsIndicator.Config config = new SmartMoneyConceptsIndicator.Config();
        config.setSwingsLength(50);
        config.setShowInternalOrderBlocks(true);
        config.setShowSwingOrderBlocks(true);
        config.setInternalOrderBlocksCount(5);
        config.setSwingOrderBlocksCount(5);
        config.setOrderBlockFilter("Atr");
        config.setOrderBlockMitigation("High/Low");
        config.setShowEqualHighsLows(true);
        config.setEqualHighsLowsLength(3);
        config.setEqualHighsLowsThreshold(0.1);
        config.setShowFairValueGaps(true);
        config.setFairValueGapsAutoThreshold(true);
        config.setShowDailyLevels(false);
        config.setShowWeeklyLevels(false);
        config.setShowMonthlyLevels(false);
        config.setShowPremiumDiscountZones(true);
        return new SmartMoneyConceptsIndicator(series, config, null, null, null);
    }

    // ==================== 重置 ====================

    /**
     * 重置运行状态。
     */
    private void resetState() {
        currentStopPrice = null;
        currentTpPrice = null;
        initialStopPrice = null;
        initialTpPrice = null;
        stopActivated = false;
        tpActivated = false;
        halfClosed = false;
        lastBarIndex = -1;
        previousEntryCount = 0;
        entryPrice = null;
    }

    // ==================== 内部类 ====================

    private static class CachedSmcIndicator {
        final SmartMoneyConceptsIndicator indicator;
        final long seriesVersion;

        CachedSmcIndicator(SmartMoneyConceptsIndicator indicator, long seriesVersion) {
            this.indicator = indicator;
            this.seriesVersion = seriesVersion;
        }
    }
}
