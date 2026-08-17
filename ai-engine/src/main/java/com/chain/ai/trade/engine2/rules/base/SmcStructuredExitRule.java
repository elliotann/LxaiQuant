package com.chain.ai.trade.engine2.rules.base;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.mtf.MultiTimeFrameProvider;
import com.chain.ai.trade.engine2.core.ExitSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import com.chain.ai.trade.engine2.rules.TradingRule;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import com.chain.ai.trade.extension.ta4j.indicator.smc.PositionRatioCalculator;
import com.chain.ai.trade.extension.ta4j.indicator.smc.WaveIndexCalculator;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.common.utils.SpringContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;



/**
 * 基于 SMC 结构化出场规则
 * <p>
 * 与 SmcDynamicExitRule 完全隔离，互不影响。
 * 由前端「基于结构止盈止损」配置驱动。
 * 支持手动模式和自动模式。
 * </p>
 * <p>
 * 手动模式：用户配置所有出场参数（止损层级、TP比例、移动缓冲等）
 * 自动模式：系统根据 4H波次 + 1H波次 + 1H位置比 自动决策（不依赖综合评分）
 * </p>
 * <p>
 * 日志级别说明：
 * - INFO: 关键决策（自动模式等级、止损触发、止盈触发、保本激活）
 * - DEBUG: 详细状态（波次/位置比变化、三层止损价格、移动止损更新）
 * </p>
 *
 * @author system
 * @version 2.2
 * @since 2026-07-29
 */
public class SmcStructuredExitRule implements TradingRule {

    private static final Logger LOG = LoggerFactory.getLogger(SmcStructuredExitRule.class);
    private static final long TIMEZONE_OFFSET_MS = 8 * 60 * 60 * 1000L;
    // ==================== 依赖 ====================
    private final MultiTimeFrameProvider mtfProvider;
    private final SignalType direction; // LONG / SHORT
    private String symbol;
    private String exchangeName;

    // ==================== 周期常量 ====================
    private static final String PERIOD_15M = "15";
    private static final String PERIOD_1H = "60";
    private static final String PERIOD_4H = "240";

    // ==================== 模式配置 ====================
    /** "manual" 手动模式 / "auto" 自动模式，默认自动模式 */
    private String mode = "auto";

    // ==================== 配置参数（由前端配置注入） ====================

    // ---- 仓位控制 ----
    private boolean positionManagementEnabled = true;
    private String positionMode = "RISK_BASED"; // FIXED / RISK_BASED
    private double fixedBasePositionPercent = 20.0;
    private double riskBasedRiskPercent = 1.0;
    private double maxPositionPercent = 20.0;
    private double minPositionSize = 0.01;

    // ---- 动态止损（三层） ----
    private boolean structuralExitEnabled = false;
    private boolean dynamicStopLossEnabled = true;
    private String stopLossDailyPeriod = "15M";
    private double stopLossDailyBuffer = 0.0008;
    private String stopLossBufferPeriod = "1H";
    private double stopLossBufferBuffer = 0.0012;
    private String stopLossUltimatePeriod = "4H";
    private double stopLossUltimateBuffer = 0.0025;
    private boolean structureBreakEnabled = true;
    private boolean autoEnableUltimate = true;

    // ---- 主动止盈 ----
    private boolean activeTakeProfitEnabled = true;
    private int swingClosePct = 50;        // 摆动点止盈平仓比例（TP1）
    private int ob1hClosePct = 50;         // 1H对立订单块止盈平仓比例（TP2）
    private Integer fvgClosePct = null;    // 1H FVG平仓比例（选填）
    private double minRiskReward = 1.2;
    private double maxRiskReward = 4.0;

    // ---- 移动止损 ----
    private boolean trailingEnabled = true;
    private double trailingBuffer = 0.0008;

    // ---- 保本止损 ----
    private boolean breakevenEnabled = true;
    private double breakevenBuffer = 0.0005;

    // ---- 参考时间框架 ----
    private String referenceStopLossPeriod = "15M";
    private String referenceTakeProfitPeriod = "1H";

    // ==================== 状态变量 ====================
    private Double currentStopPrice;        // 当前生效止损价
    private Double currentStopDaily;        // 第一道止损价
    private Double currentStopBuffer;       // 第二道止损价
    private Double currentStopUltimate;     // 第三道止损价
    private Double currentTp1;              // TP1 价格
    private Double currentTp2;              // TP2 价格
    private Double entryPrice;              // 入场均价
    private int lastBarIndex = -1;
    private Long lastAnchorTime = null;
    private boolean isLong;
    private String posId;

    // ---- 持仓状态 ----
    private boolean hadPosition = false;
    private int previousEntryCount = 0;
    private boolean tp1Hit = false;
    private boolean tp2Hit = false;
    private boolean breakevenActivated = false;
    private boolean structureBroken = false;

    // ---- 当前市场状态（用于自动模式决策） ----
    private int currentWave4h = 0;
    private int currentWave1h = 0;
    private int currentWave15m = 0;
    private double currentPositionRatio1h = 0.5;
    private double currentPositionRatio15m = 0.5;

    // ---- SMC 指标缓存 ----
    private final Map<String, CachedSmcIndicator> indicatorCache = new HashMap<>();
    /** SMC 历史结果列表缓存（用于波次计算），key = period */
    private final Map<String, List<SmartMoneyConceptsIndicator.Result>> historyCache = new HashMap<>();

    // ---- Redis 持久化（与旧类隔离） ----
    private static final String REDIS_PREFIX = "V2:STR:";
    private static final String REDIS_STOP = REDIS_PREFIX + "STOP:";
    private static final String REDIS_TP = REDIS_PREFIX + "TP:";
    private static final String REDIS_STATE = REDIS_PREFIX + "STATE:";
    private static final long TTL_SECONDS = TimeUnit.DAYS.toSeconds(7);

    // ==================== 构造函数 ====================

    public SmcStructuredExitRule(MultiTimeFrameProvider mtfProvider, String symbol, SignalType direction) {
        this.mtfProvider = mtfProvider;
        this.symbol = symbol;
        this.direction = direction;
    }

    // ==================== 配置注入方法 ====================

    public void setMode(String mode) { this.mode = mode; }

    public void setPositionManagementEnabled(boolean v) { this.positionManagementEnabled = v; }
    public void setPositionMode(String v) { this.positionMode = v; }
    public void setFixedBasePositionPercent(double v) { this.fixedBasePositionPercent = v; }
    public void setRiskBasedRiskPercent(double v) { this.riskBasedRiskPercent = v; }
    public void setMaxPositionPercent(double v) { this.maxPositionPercent = v; }
    public void setMinPositionSize(double v) { this.minPositionSize = v; }

    public void setStructuralExitEnabled(boolean v) { this.structuralExitEnabled = v; }
    public void setDynamicStopLossEnabled(boolean v) { this.dynamicStopLossEnabled = v; }
    public void setStopLossDailyPeriod(String v) { this.stopLossDailyPeriod = v; }
    public void setStopLossDailyBuffer(double v) { this.stopLossDailyBuffer = v; }
    public void setStopLossBufferPeriod(String v) { this.stopLossBufferPeriod = v; }
    public void setStopLossBufferBuffer(double v) { this.stopLossBufferBuffer = v; }
    public void setStopLossUltimatePeriod(String v) { this.stopLossUltimatePeriod = v; }
    public void setStopLossUltimateBuffer(double v) { this.stopLossUltimateBuffer = v; }
    public void setStructureBreakEnabled(boolean v) { this.structureBreakEnabled = v; }
    public void setAutoEnableUltimate(boolean v) { this.autoEnableUltimate = v; }

    public void setActiveTakeProfitEnabled(boolean v) { this.activeTakeProfitEnabled = v; }
    public void setSwingClosePct(int v) { this.swingClosePct = v; }
    public void setOb1hClosePct(int v) { this.ob1hClosePct = v; }
    public void setFvgClosePct(Integer v) { this.fvgClosePct = v; }
    public void setMinRiskReward(double v) { this.minRiskReward = v; }
    public void setMaxRiskReward(double v) { this.maxRiskReward = v; }

    public void setTrailingEnabled(boolean v) { this.trailingEnabled = v; }
    public void setTrailingBuffer(double v) { this.trailingBuffer = v; }

    public void setBreakevenEnabled(boolean v) { this.breakevenEnabled = v; }
    public void setBreakevenBuffer(double v) { this.breakevenBuffer = v; }

    public void setReferenceStopLossPeriod(String v) { this.referenceStopLossPeriod = v; }
    public void setReferenceTakeProfitPeriod(String v) { this.referenceTakeProfitPeriod = v; }

    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setExchangeName(String exchangeName) { this.exchangeName = exchangeName; }

    /**
     * 设置4H波次（由外部从权重引擎注入）
     */
    public void setWave4h(int wave4h) { this.currentWave4h = wave4h; }

    /**
     * 设置1H波次（由外部从权重引擎注入）
     */
    public void setWave1h(int wave1h) { this.currentWave1h = wave1h; }

    /**
     * 设置1H位置比（由外部从权重引擎注入）
     */
    public void setPositionRatio1h(double ratio) { this.currentPositionRatio1h = ratio; }

    // ==================== TradingRule 接口 ====================

    @Override
    public ExitSignal evaluate(int index, Bar bar, BarSeries series, TradingContext context) {
        String targetTime = "2025-01-01 16:30:00";
        String currentTime1 = DateUtil.formatDateTime(new java.util.Date(bar.getBeginTime().toEpochMilli() - TIMEZONE_OFFSET_MS));
        boolean hitTarget = targetTime.equals(currentTime1);
        if(hitTarget){
            System.out.println("here");
        }
        // 1. 无持仓不处理
        if (!context.hasPosition()) {
            if (hadPosition && posId != null) {
                clearPersistedState(posId);
                LOG.debug("持仓已平，清理状态: posId={}", posId);
            }
            hadPosition = false;
            posId = null;
            return null;
        }

        // 2. 检测新交易开始 → 重置状态
        if (!hadPosition) {
            hadPosition = true;
            resetState();
            LOG.info("新交易开始，重置状态: symbol={}, direction={}", symbol, direction);
        }

        // 3. 提取持仓信息
        if (symbol == null) symbol = context.getSymbol();
        if (direction == SignalType.LONG && context.hasLongPosition()) {
            isLong = true;
            entryPrice = context.getLongAvgPrice().doubleValue();
            posId = context.getLongPosition().getPositionId();
        } else if (direction == SignalType.SHORT && context.hasShortPosition()) {
            isLong = false;
            entryPrice = context.getShortAvgPrice().doubleValue();
            posId = context.getShortPosition().getPositionId();
        } else {
            return null;
        }

        if (entryPrice == null || entryPrice <= 0) return null;

        // 4. 加载持久化状态
        loadPersistedState(posId);

        // 5. 检测加仓 → 重置 TP 状态
        int currentEntryCount = isLong
                ? context.getLongPosition().getEntries().size()
                : context.getShortPosition().getEntries().size();
        if (previousEntryCount > 0 && currentEntryCount > previousEntryCount) {
            tp1Hit = false;
            tp2Hit = false;
            breakevenActivated = false;
            clearPersistedTP(posId);
            LOG.info("加仓检测到，重置TP状态: posId={}, entryCount={}", posId, currentEntryCount);
        }
        previousEntryCount = currentEntryCount;

        // 6. 获取当前K线信息
        long currentTime = bar.getBeginTime().toEpochMilli();
        Bar effectiveBar = (index < series.getBarCount()) ? series.getBar(index) : bar;
        Num high = effectiveBar.getHighPrice();
        Num low = effectiveBar.getLowPrice();
        double currentLow = low.doubleValue();
        double currentHigh = high.doubleValue();

        // 7. 仅在 Bar 变化时重算出场位
        if (index != lastBarIndex) {
            double currentPrice = effectiveBar.getClosePrice().doubleValue();
            updateSmcState(currentTime, currentPrice);
            updateStructuredLevels(currentTime, currentLow, currentHigh);
            lastBarIndex = index;
            lastAnchorTime = currentTime;

            // ★ 打印当前市场状态
            if (structuralExitEnabled) {
                LOG.info("【SMC状态】posId={} 4H波次={}, 1H波次={}, 15M波次={}, 1H位置比={}, 15M位置比={}",
                        posId, currentWave4h, currentWave1h, currentWave15m,
                        String.format("%.2f", currentPositionRatio1h),
                        String.format("%.2f", currentPositionRatio15m));
                LOG.info("【止损/止盈】posId={} 日常止损={}, 缓冲止损={}, 终极止损={}, TP1={}, TP2={}",
                        posId,
                        String.format("%.2f", currentStopDaily != null ? currentStopDaily : 0.0),
                        String.format("%.2f", currentStopBuffer != null ? currentStopBuffer : 0.0),
                        String.format("%.2f", currentStopUltimate != null ? currentStopUltimate : 0.0),
                        String.format("%.2f", currentTp1 != null ? currentTp1 : 0.0),
                        String.format("%.2f", currentTp2 != null ? currentTp2 : 0.0));
            }
        }

        // 8. 如果结构化出场未启用，直接返回（使用旧逻辑）
        if (!structuralExitEnabled) {
            return null;
        }

        // 9. 执行止损检查
        ExitSignal stopSignal = checkStopLoss(high, low);
        if (stopSignal != null) return stopSignal;

        // 10. 执行移动止损
        ExitSignal trailSignal = checkTrailingStop(high, low, currentTime);
        if (trailSignal != null) return trailSignal;

        // 11. 执行保本止损
        ExitSignal beSignal = checkBreakeven(high, low);
        if (beSignal != null) return beSignal;

        // 12. 执行止盈检查
        ExitSignal tpSignal = checkTakeProfit(high, low);
        if (tpSignal != null) return tpSignal;

        return null;
    }

    // ==================== 核心更新方法 ====================

    /**
     * 更新 SMC 市场状态（波次 + 位置比），用于自动模式决策
     */
    private void updateSmcState(long currentTime, double currentPrice) {
        // 触发4H/1H/15M的SMC指标计算（同时缓存history）
        getCachedSmcResult(PERIOD_4H, currentTime);
        getCachedSmcResult(PERIOD_1H, currentTime);
        getCachedSmcResult(PERIOD_15M, currentTime);

        // 4H 波次
        List<SmartMoneyConceptsIndicator.Result> history4h = historyCache.get(PERIOD_4H);
        if (history4h != null && !history4h.isEmpty()) {
            int lastIdx = history4h.size() - 1;
            SmartMoneyConceptsIndicator.Result r4h = history4h.get(lastIdx);
            boolean isBuy4h = r4h.getSwingTrend() == 1;
            currentWave4h = WaveIndexCalculator.calculate(history4h, lastIdx, isBuy4h);

            // 1H 波次 + 位置比
            List<SmartMoneyConceptsIndicator.Result> history1h = historyCache.get(PERIOD_1H);
            if (history1h != null && !history1h.isEmpty()) {
                int lastIdx1h = history1h.size() - 1;
                SmartMoneyConceptsIndicator.Result r1h = history1h.get(lastIdx1h);
                boolean isBuy1h = r1h.getSwingTrend() == 1;
                currentWave1h = WaveIndexCalculator.calculate(history1h, lastIdx1h, isBuy1h);
                currentPositionRatio1h = PositionRatioCalculator.calculate(r1h, isBuy4h, currentPrice);
            }

            // 15M 波次 + 位置比
            List<SmartMoneyConceptsIndicator.Result> history15m = historyCache.get(PERIOD_15M);
            if (history15m != null && !history15m.isEmpty()) {
                int lastIdx15m = history15m.size() - 1;
                SmartMoneyConceptsIndicator.Result r15m = history15m.get(lastIdx15m);
                boolean isBuy15m = r15m.getSwingTrend() == 1;
                currentWave15m = WaveIndexCalculator.calculate(history15m, lastIdx15m, isBuy15m);
                currentPositionRatio15m = PositionRatioCalculator.calculate(r15m, isBuy4h, currentPrice);
            }
        }
    }

    private void updateStructuredLevels(long currentTime, double currentLow, double currentHigh) {
        if (!structuralExitEnabled) return;

        SmartMoneyConceptsIndicator.Result smc15 = getCachedSmcResult(referenceStopLossPeriod, currentTime);
        SmartMoneyConceptsIndicator.Result smc1h = getCachedSmcResult(referenceTakeProfitPeriod, currentTime);
        SmartMoneyConceptsIndicator.Result smc4h = getCachedSmcResult(PERIOD_4H, currentTime);

        if (smc15 == null) {
            LOG.debug("SMC 15M数据不可用，跳过更新: posId={}", posId);
            return;
        }

        // 1. 计算三层止损（使用配置的周期和缓冲）
        String dailyPeriod = stopLossDailyPeriod;
        String bufferPeriod = stopLossBufferPeriod;
        String ultimatePeriod = stopLossUltimatePeriod;

        SmartMoneyConceptsIndicator.Result smcDaily = getCachedSmcResult(periodToMinute(dailyPeriod), currentTime);
        SmartMoneyConceptsIndicator.Result smcBuffer = getCachedSmcResult(periodToMinute(bufferPeriod), currentTime);
        SmartMoneyConceptsIndicator.Result smcUltimate = getCachedSmcResult(periodToMinute(ultimatePeriod), currentTime);

        currentStopDaily = calculateStop(smcDaily != null ? smcDaily : smc15, entryPrice, stopLossDailyBuffer, true);
        currentStopBuffer = smcBuffer != null
                ? calculateStop(smcBuffer, entryPrice, stopLossBufferBuffer, false)
                : currentStopDaily;
        currentStopUltimate = smcUltimate != null
                ? calculateStop(smcUltimate, entryPrice, stopLossUltimateBuffer, false)
                : currentStopBuffer;

        // 2. 默认使用第一道（仅首次或新止损更优时更新，保留移动止损的推进）
        if (currentStopPrice == null) {
            currentStopPrice = currentStopDaily;
        } else if (currentStopDaily != null && Double.isFinite(currentStopDaily)) {
            if (isLong && currentStopDaily > currentStopPrice) {
                LOG.debug("第一道止损上移: posId={}, {} -> {}", posId, currentStopPrice, currentStopDaily);
                currentStopPrice = currentStopDaily;
            } else if (!isLong && currentStopDaily < currentStopPrice) {
                LOG.debug("第一道止损下移: posId={}, {} -> {}", posId, currentStopPrice, currentStopDaily);
                currentStopPrice = currentStopDaily;
            }
        }

        // 3. 检测结构破坏
        if (structureBreakEnabled) {
            if (isLong) {
                double hl = smc15.getLastHigherLow();
                if (!Double.isNaN(hl) && currentLow < hl) {
                    structureBroken = true;
                    currentStopPrice = currentStopUltimate;
                    LOG.info("结构破坏触发（HL破位）: posId={}, HL={:.2f}, currentLow={:.2f}", posId, hl, currentLow);
                }
            } else {
                double lh = smc15.getLastLowerHigh();
                if (!Double.isNaN(lh) && currentHigh > lh) {
                    structureBroken = true;
                    currentStopPrice = currentStopUltimate;
                    LOG.info("结构破坏触发（LH破位）: posId={}, LH={:.2f}, currentHigh={:.2f}", posId, lh, currentHigh);
                }
            }
        }

        // 4. 计算 TP1（1H 对立订单块）
        currentTp1 = calculateTP1(smc1h != null ? smc1h : smc15, entryPrice);

        // 5. 计算 TP2（4H 前高/前低）
        currentTp2 = calculateTP2(smc4h != null ? smc4h : smc1h, entryPrice);

        // 6. 检查盈亏比约束
        if (currentTp1 != null && currentStopPrice != null) {
            double risk = Math.abs(entryPrice - currentStopPrice);
            double reward = Math.abs(currentTp1 - entryPrice);
            if (risk > 0 && reward / risk < minRiskReward) {
                double minReward = risk * minRiskReward;
                currentTp1 = isLong ? entryPrice + minReward : entryPrice - minReward;
                LOG.debug("TP1调整至最小盈亏比位置: posId={}, TP1={:.2f}", posId, currentTp1);
            }
        }

        if (currentTp2 != null && currentStopPrice != null) {
            double risk = Math.abs(entryPrice - currentStopPrice);
            double reward = Math.abs(currentTp2 - entryPrice);
            if (risk > 0 && reward / risk > maxRiskReward) {
                double maxReward = risk * maxRiskReward;
                currentTp2 = isLong ? entryPrice + maxReward : entryPrice - maxReward;
                LOG.debug("TP2限制最大盈亏比位置: posId={}, TP2={:.2f}", posId, currentTp2);
            }
        }

        // 7. 持久化状态
        persistState(posId);
    }

    // ==================== 止损计算 ====================

    private Double calculateStop(SmartMoneyConceptsIndicator.Result smc, double entry, double buffer, boolean isDaily) {
        if (smc == null) return null;
        if (isLong) {
            double base = isDaily ? smc.getLastSwingLow() : smc.getTrailingLow();
            if (Double.isNaN(base) || base >= entry) return null;
            double stop = base * (1 - buffer);
            return stop < entry ? stop : null;
        } else {
            double base = isDaily ? smc.getLastSwingHigh() : smc.getTrailingHigh();
            if (Double.isNaN(base) || base <= entry) return null;
            double stop = base * (1 + buffer);
            return stop > entry ? stop : null;
        }
    }

    // ==================== 止盈计算 ====================

    private Double calculateTP1(SmartMoneyConceptsIndicator.Result smc, double entry) {
        if (smc == null) return null;
        int bias = isLong ? -1 : 1;
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks == null || blocks.isEmpty()) return null;

        double best = isLong ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
            if (ob.bias != bias) continue;
            double lo = Math.min(ob.barLow, ob.barHigh);
            double hi = Math.max(ob.barLow, ob.barHigh);
            if (isLong && lo > entry && lo < best) best = lo;
            if (!isLong && hi < entry && hi > best) best = hi;
        }
        return Double.isFinite(best) ? best : null;
    }

    private Double calculateTP2(SmartMoneyConceptsIndicator.Result smc, double entry) {
        if (smc == null) return null;
        if (isLong) {
            double hh = smc.getLastSwingHigh();
            return Double.isFinite(hh) && hh > entry ? hh : null;
        } else {
            double ll = smc.getLastSwingLow();
            return Double.isFinite(ll) && ll < entry ? ll : null;
        }
    }

    // ==================== 止损检查（支持手动/自动模式） ====================

    private ExitSignal checkStopLoss(Num high, Num low) {
        if (!dynamicStopLossEnabled || currentStopPrice == null || !Double.isFinite(currentStopPrice)) {
            return null;
        }

        double effectiveStop = currentStopPrice;
        ExitType exitType = ExitType.STOP_LOSS_DAILY;

        // ===== 第一优先级：结构破坏 → 强制第三道 =====
        if (structureBroken && currentStopUltimate != null && Double.isFinite(currentStopUltimate)) {
            effectiveStop = currentStopUltimate;
            exitType = ExitType.STOP_LOSS_ULTIMATE;
        }
        // ===== 第二优先级：根据模式选择 =====
        else if ("auto".equals(mode)) {
            // 自动模式：根据开仓质量（波次+位置比）决定
            ExitConfig autoConfig = determineAutoExitConfig(currentWave4h, currentWave1h, currentPositionRatio1h);
            switch (autoConfig.stopLevel) {
                case "BUFFER":
                    effectiveStop = currentStopBuffer != null && Double.isFinite(currentStopBuffer)
                            ? currentStopBuffer : currentStopDaily;
                    exitType = ExitType.STOP_LOSS_BUFFER;
                    break;
                case "DAILY_TIGHT":
                    effectiveStop = currentStopDaily != null && Double.isFinite(currentStopDaily)
                            ? currentStopDaily * (isLong ? (1 - 0.0005) : (1 + 0.0005))
                            : currentStopDaily;
                    exitType = ExitType.STOP_LOSS_DAILY_TIGHT;
                    break;
                default: // DAILY
                    effectiveStop = currentStopDaily != null && Double.isFinite(currentStopDaily)
                            ? currentStopDaily : currentStopPrice;
                    exitType = ExitType.STOP_LOSS_DAILY;
                    break;
            }
        } else {
            // 手动模式：使用用户配置的缓冲，不依赖评分
            effectiveStop = currentStopDaily != null && Double.isFinite(currentStopDaily)
                    ? currentStopDaily : currentStopPrice;
            exitType = ExitType.STOP_LOSS_DAILY;
        }

        // 执行止损检查
        boolean triggered = isLong ? low.doubleValue() <= effectiveStop : high.doubleValue() >= effectiveStop;
        if (triggered) {
            SignalType signal = isLong ? SignalType.CLOSE_LONG : SignalType.CLOSE_SHORT;
            LOG.info("止损触发: posId={}, type={}, price={:.2f}, 当前价={:.2f}",
                    posId, exitType, effectiveStop, isLong ? low.doubleValue() : high.doubleValue());
            return new ExitSignal(signal, exitType, BigDecimal.valueOf(effectiveStop));
        }
        return null;
    }

    // ==================== 自动模式决策算法（含详细日志） ====================

    /**
     * 自动模式出口配置
     */
    private static class ExitConfig {
        String stopLevel;        // DAILY / BUFFER / DAILY_TIGHT
        int tp1ClosePct;
        int tp2ClosePct;
        double trailingBuffer;

        ExitConfig(String stopLevel, int tp1ClosePct, int tp2ClosePct, double trailingBuffer) {
            this.stopLevel = stopLevel;
            this.tp1ClosePct = tp1ClosePct;
            this.tp2ClosePct = tp2ClosePct;
            this.trailingBuffer = trailingBuffer;
        }
    }

    /**
     * 自动模式决策算法
     * 根据 4H波次 + 1H波次 + 1H位置比 决定出场策略
     */
    private ExitConfig determineAutoExitConfig(int wave4h, int wave1h, double posRatio1h) {
        boolean is4hGood = (wave4h == 2 || wave4h == 3);
        boolean is4hTest = (wave4h == 1 || wave4h == -1);
        boolean is1hGood = (wave1h == 2 || wave1h == 3);
        boolean is1hTest = (wave1h == 1 || wave1h == -1);
        boolean isIdealPos = isLong ? posRatio1h < 0.382 : posRatio1h > 0.618;

        // ★ 打印决策依据（DEBUG级别）
        if (LOG.isDebugEnabled()) {
            LOG.debug("【自动决策输入】4H波次={}, 1H波次={}, 位置比={:.2f}, is4hGood={}, is4hTest={}, is1hGood={}, is1hTest={}, isIdealPos={}",
                    wave4h, wave1h, posRatio1h, is4hGood, is4hTest, is1hGood, is1hTest, isIdealPos);
        }

        ExitConfig config;

        // ===== ★★★ 黄金共振 =====
        // 条件：4H确认/加速 + 1H确认/加速 + 位置理想
        if (is4hGood && is1hGood && isIdealPos) {
            config = new ExitConfig("BUFFER", 30, 70, 0.0006);
            LOG.info("【自动模式】★★★★★ 黄金共振 → 止损: 第二道(BUFFER), TP1=30%, TP2=70%, 移动缓冲=0.06%");
        }
        // ===== ★★☆ 优质开仓 =====
        // 条件：4H确认/加速 + 1H确认/加速（位置不理想但波次配合好）
        else if (is4hGood && is1hGood) {
            config = new ExitConfig("BUFFER", 50, 50, 0.0008);
            LOG.info("【自动模式】★★★★ 优质开仓 → 止损: 第二道(BUFFER), TP1=50%, TP2=50%, 移动缓冲=0.08%");
        }
        // ===== ★☆☆ 普通开仓 =====
        // 条件：4H确认/加速 + 1H试盘 + 位置理想
        else if (is4hGood && is1hTest && isIdealPos) {
            config = new ExitConfig("DAILY", 70, 30, 0.0008);
            LOG.info("【自动模式】★★★ 普通开仓 → 止损: 第一道(DAILY), TP1=70%, TP2=30%, 移动缓冲=0.08%");
        }
        // ===== ☆☆☆ 试盘开仓 =====
        // 条件：4H试盘 + 1H确认 + 位置理想
        else if (is4hTest && is1hGood && isIdealPos) {
            config = new ExitConfig("DAILY_TIGHT", 100, 0, 0);
            LOG.info("【自动模式】★★ 试盘开仓 → 止损: 第一道收紧(DAILY_TIGHT), TP1=100%, TP2=0%, 移动缓冲=0 (不启用)");
        }
        // ===== 默认：保守配置 =====
        else {
            config = new ExitConfig("DAILY", 80, 20, 0.0010);
            LOG.info("【自动模式】★ 默认保守配置 → 止损: 第一道(DAILY), TP1=80%, TP2=20%, 移动缓冲=0.10%");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("【自动决策输出】stopLevel={}, tp1Pct={}, tp2Pct={}, trailBuffer={}",
                    config.stopLevel, config.tp1ClosePct, config.tp2ClosePct, config.trailingBuffer);
        }

        return config;
    }

    /**
     * 获取自动模式的TP1平仓比例
     */
    private int getAutoTp1Pct(int wave4h, int wave1h, double posRatio1h) {
        return determineAutoExitConfig(wave4h, wave1h, posRatio1h).tp1ClosePct;
    }

    /**
     * 获取自动模式的TP2平仓比例
     */
    private int getAutoTp2Pct(int wave4h, int wave1h, double posRatio1h) {
        return determineAutoExitConfig(wave4h, wave1h, posRatio1h).tp2ClosePct;
    }

    /**
     * 获取自动模式的移动止损缓冲
     */
    private double getAutoTrailingBuffer(int wave4h, int wave1h, double posRatio1h) {
        return determineAutoExitConfig(wave4h, wave1h, posRatio1h).trailingBuffer;
    }

    // ==================== 移动止损检查 ====================

    private ExitSignal checkTrailingStop(Num high, Num low, long currentTime) {
        if (!trailingEnabled) return null;

        // 自动模式：如果算法决定不启用移动止损
        if ("auto".equals(mode)) {
            double buffer = getAutoTrailingBuffer(currentWave4h, currentWave1h, currentPositionRatio1h);
            if (buffer <= 0) {
                return null; // 试盘开仓不启用移动止损
            }
            return doTrailingStop(high, low, currentTime, buffer);
        } else {
            return doTrailingStop(high, low, currentTime, trailingBuffer);
        }
    }

    private ExitSignal doTrailingStop(Num high, Num low, long currentTime, double buffer) {
        if (currentStopPrice == null || !Double.isFinite(currentStopPrice)) return null;

        // 移动止损前置条件：移动后的价格必须至少保本（≥入场价），否则不移
        if (entryPrice == null || entryPrice <= 0) return null;

        SmartMoneyConceptsIndicator.Result smc = getCachedSmcResult(referenceStopLossPeriod, currentTime);
        if (smc == null) return null;

        double newStop = isLong ? smc.getLastHigherLow() : smc.getLastLowerHigh();
        if (Double.isNaN(newStop)) return null;

        // 结构点必须位于当前价的防守侧（做多低于当前低点、做空高于当前高点），
        // 否则不作为移动止损基准，避免结构点落在错误一侧导致开仓后立即触发
        if (isLong ? newStop >= low.doubleValue() : newStop <= high.doubleValue()) return null;

        double trailPrice = isLong ? newStop * (1 - buffer) : newStop * (1 + buffer);

        // 移动后的止损价必须至少达到保本位才允许移动
        boolean reachedBreakeven = isLong ? trailPrice >= entryPrice : trailPrice <= entryPrice;
        if (!reachedBreakeven) {
            return null;
        }

        // 只有向有利方向移动且新止损价在盘面之下（多头）/之上（空头）才更新
        // 防止远古摆动点导致止损移到盘面之上，造成立即误触发
        if (isLong && trailPrice > currentStopPrice && trailPrice < low.doubleValue()) {
            currentStopPrice = trailPrice;
            persistState(posId);
            LOG.info("移动止损上移: posId={}, 新止损={:.2f}", posId, currentStopPrice);
        } else if (!isLong && trailPrice < currentStopPrice && trailPrice > high.doubleValue()) {
            currentStopPrice = trailPrice;
            persistState(posId);
            LOG.info("移动止损下移: posId={}, 新止损={:.2f}", posId, currentStopPrice);
        }

        boolean triggered = isLong ? low.doubleValue() <= currentStopPrice : high.doubleValue() >= currentStopPrice;
        if (triggered) {
            SignalType signal = isLong ? SignalType.CLOSE_LONG : SignalType.CLOSE_SHORT;
            LOG.info("移动止损触发: posId={}, 价格={:.2f}", posId, currentStopPrice);
            return new ExitSignal(signal, ExitType.TRAILING_STOP, BigDecimal.valueOf(currentStopPrice));
        }
        return null;
    }

    // ==================== 保本止损检查 ====================

    private ExitSignal checkBreakeven(Num high, Num low) {
        if (!breakevenEnabled || currentTp1 == null || !Double.isFinite(currentTp1)) return null;
        if (breakevenActivated) return null;

        boolean tp1Reached = isLong ? high.doubleValue() >= currentTp1 : low.doubleValue() <= currentTp1;
        if (tp1Reached) {
            breakevenActivated = true;
            tp1Hit = true;
            currentStopPrice = entryPrice * (isLong ? (1 - breakevenBuffer) : (1 + breakevenBuffer));
            persistState(posId);
            LOG.info("保本止损激活: posId={}, 保本价={:.2f}, 入场价={:.2f}", posId, currentStopPrice, entryPrice);
        }

        if (breakevenActivated && currentStopPrice != null) {
            boolean beTriggered = isLong ? low.doubleValue() <= currentStopPrice : high.doubleValue() >= currentStopPrice;
            if (beTriggered) {
                SignalType signal = isLong ? SignalType.CLOSE_LONG : SignalType.CLOSE_SHORT;
                LOG.info("保本止损触发: posId={}, 价格={:.2f}", posId, currentStopPrice);
                return new ExitSignal(signal, ExitType.BREAKEVEN, BigDecimal.valueOf(currentStopPrice));
            }
        }
        return null;
    }

    // ==================== 止盈检查（支持手动/自动模式） ====================

    private ExitSignal checkTakeProfit(Num high, Num low) {
        if (!activeTakeProfitEnabled) return null;

        // ★ 如果 TP2 已经触发过，不再重复触发
        if (tp2Hit) {
            return null;
        }

        if (tp1Hit) {
            // TP2 检查
            if (currentTp2 != null && Double.isFinite(currentTp2)) {
                boolean tp2Reached = isLong ? high.doubleValue() >= currentTp2 : low.doubleValue() <= currentTp2;
                if (tp2Reached) {
                    tp2Hit = true;
                    persistState(posId);

                    int tp2Pct;
                    if ("auto".equals(mode)) {
                        tp2Pct = getAutoTp2Pct(currentWave4h, currentWave1h, currentPositionRatio1h);
                    } else {
                        tp2Pct = ob1hClosePct;
                    }
                    SignalType signal = isLong ? SignalType.CLOSE_LONG : SignalType.CLOSE_SHORT;
                    LOG.info("TP2止盈触发: posId={}, 价格={:.2f}, 平仓={}%", posId, currentTp2, tp2Pct);
                    return new ExitSignal(signal, ExitType.PROFIT_TARGET_TP2,
                            BigDecimal.valueOf(currentTp2), tp2Pct);
                }
            }
            return null;
        }

        // TP1 检查
        if (currentTp1 != null && Double.isFinite(currentTp1)) {
            boolean tp1Reached = isLong ? high.doubleValue() >= currentTp1 : low.doubleValue() <= currentTp1;
            if (tp1Reached) {
                int tp1Pct;
                if ("auto".equals(mode)) {
                    tp1Pct = getAutoTp1Pct(currentWave4h, currentWave1h, currentPositionRatio1h);
                } else {
                    tp1Pct = swingClosePct;
                }

                tp1Hit = true;
                breakevenActivated = true;
                currentStopPrice = entryPrice * (isLong ? (1 - breakevenBuffer) : (1 + breakevenBuffer));
                persistState(posId);

                SignalType signal = isLong ? SignalType.CLOSE_LONG : SignalType.CLOSE_SHORT;
                LOG.info("TP1止盈触发: posId={}, 价格={:.2f}, 平仓={}%", posId, currentTp1, tp1Pct);
                return new ExitSignal(signal, ExitType.PROFIT_TARGET_TP1,
                        BigDecimal.valueOf(currentTp1), tp1Pct);
            }
        }
        return null;
    }

    // ==================== 仓位计算（以损定量） ====================

    public double calculatePositionSize(double accountEquity, double score) {
        if (!positionManagementEnabled) return 0.0;

        if ("FIXED".equals(positionMode)) {
            return accountEquity * fixedBasePositionPercent / 100.0;
        } else {
            if (currentStopDaily == null || entryPrice == null) return 0.0;
            double stopDistance = Math.abs(entryPrice - currentStopDaily);
            if (stopDistance <= 0) return 0.0;

            double riskPercent = riskBasedRiskPercent / 100.0;
            double fixedRisk = accountEquity * riskPercent;
            double nominalPosition = fixedRisk / stopDistance;

            double maxPosition = accountEquity * maxPositionPercent / 100.0;
            double result = Math.min(nominalPosition, maxPosition);
            return Math.max(result, minPositionSize);
        }
    }

    /**
     * 获取当前第一道止损价（供外部仓位计算使用）
     */
    public Double getCurrentStopDaily() {
        return currentStopDaily;
    }

    /**
     * 获取当前生效止损价
     */
    public Double getCurrentStopPrice() {
        return currentStopPrice;
    }

    /**
     * 获取当前TP1价格
     */
    public Double getCurrentTp1() {
        return currentTp1;
    }

    /**
     * 获取当前TP2价格
     */
    public Double getCurrentTp2() {
        return currentTp2;
    }

    // ==================== SMC 指标获取 ====================

    private SmartMoneyConceptsIndicator.Result getCachedSmcResult(String period, long currentTime) {
        if (mtfProvider == null || period == null) return null;
        CandlestickIntervalEnum interval = parsePeriod(period);
        if (interval == null) return null;

        // ★ tailCount 模式下，必须先调 getBarIndex 触发 ensureSeriesCovers 加载数据
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

        long currentVersion = mtfProvider.getSeriesVersion(interval);
        CachedSmcIndicator cached = indicatorCache.get(period);
        if (cached == null || cached.seriesVersion != currentVersion) {
            SmartMoneyConceptsIndicator ind = createSmcIndicator(series);
            indicatorCache.put(period, new CachedSmcIndicator(ind, currentVersion));
            cached = indicatorCache.get(period);

            // ★ 同时构建历史结果列表缓存（用于波次计算）
            int barCount = series.getBarCount();
            List<SmartMoneyConceptsIndicator.Result> history = new ArrayList<>(barCount);
            for (int i = 0; i < barCount; i++) {
                history.add(cached.indicator.getValue(i));
            }
            historyCache.put(period, history);
        }

        return cached.indicator.getValue(idx);
    }

    private String periodToMinute(String period) {
        if (period == null) return "15";
        if (period.endsWith("M")) return period.replace("M", "");
        if (period.endsWith("H")) {
            int hours = Integer.parseInt(period.replace("H", ""));
            return String.valueOf(hours * 60);
        }
        if (period.endsWith("D")) {
            int days = Integer.parseInt(period.replace("D", ""));
            return String.valueOf(days * 1440);
        }
        return period;
    }

    private static CandlestickIntervalEnum parsePeriod(String period) {
        try {
            int min = Integer.parseInt(period);
            for (CandlestickIntervalEnum e : CandlestickIntervalEnum.values()) {
                if (e.getMinNum() == min) return e;
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

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

    // ==================== Redis 持久化 ====================

    private RedisCache getRedisCache() {
        try {
            return SpringContextUtil.getBean(RedisCache.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void loadPersistedState(String posId) {
        if (posId == null) return;
        RedisCache rc = getRedisCache();
        if (rc == null) return;

        Object stop = rc.get(REDIS_STOP + posId);
        if (stop != null) {
            try { currentStopPrice = Double.parseDouble(stop.toString()); } catch (Exception ignored) {}
        }
        Object tp1 = rc.get(REDIS_TP + posId + ":tp1");
        if (tp1 != null) tp1Hit = "1".equals(tp1.toString());
        Object tp2 = rc.get(REDIS_TP + posId + ":tp2");
        if (tp2 != null) tp2Hit = "1".equals(tp2.toString());
        Object be = rc.get(REDIS_TP + posId + ":be");
        if (be != null) breakevenActivated = "1".equals(be.toString());
        Object sb = rc.get(REDIS_STATE + posId + ":structure");
        if (sb != null) structureBroken = "1".equals(sb.toString());
    }

    private void persistState(String posId) {
        if (posId == null) return;
        RedisCache rc = getRedisCache();
        if (rc == null) return;

        if (currentStopPrice != null && Double.isFinite(currentStopPrice)) {
            rc.put(REDIS_STOP + posId, String.valueOf(currentStopPrice), TTL_SECONDS, TimeUnit.SECONDS);
        }
        rc.put(REDIS_TP + posId + ":tp1", tp1Hit ? "1" : "0", TTL_SECONDS, TimeUnit.SECONDS);
        rc.put(REDIS_TP + posId + ":tp2", tp2Hit ? "1" : "0", TTL_SECONDS, TimeUnit.SECONDS);
        rc.put(REDIS_TP + posId + ":be", breakevenActivated ? "1" : "0", TTL_SECONDS, TimeUnit.SECONDS);
        rc.put(REDIS_STATE + posId + ":structure", structureBroken ? "1" : "0", TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void clearPersistedTP(String posId) {
        if (posId == null) return;
        RedisCache rc = getRedisCache();
        if (rc == null) return;
        rc.vagueDel(REDIS_TP + posId + "*");
    }

    private void clearPersistedState(String posId) {
        if (posId == null) return;
        RedisCache rc = getRedisCache();
        if (rc == null) return;
        rc.vagueDel(REDIS_STOP + posId + "*");
        rc.vagueDel(REDIS_TP + posId + "*");
        rc.vagueDel(REDIS_STATE + posId + "*");
    }

    // ==================== 重置 ====================

    public void resetState() {
        currentStopPrice = null;
        currentStopDaily = null;
        currentStopBuffer = null;
        currentStopUltimate = null;
        currentTp1 = null;
        currentTp2 = null;
        lastBarIndex = -1;
        lastAnchorTime = null;
        tp1Hit = false;
        tp2Hit = false;
        breakevenActivated = false;
        structureBroken = false;
        previousEntryCount = 0;
        if (posId != null) clearPersistedState(posId);
        LOG.debug("状态已重置: posId={}", posId);
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