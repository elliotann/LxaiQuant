package com.chain.ai.trade.engine2.rules.base;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.entity.constants.TrendType;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.mtf.MultiTimeFrameProvider;
import com.chain.ai.trade.engine2.core.ExitSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import com.chain.ai.trade.engine2.rules.TradingRule;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import com.chain.ai.trade.extension.ta4j.indicator.trend.SmcTrendUtils;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.common.utils.SpringContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.chain.ai.trade.common.entity.constants.TrendType.*;

/**
 * 基于多周期 SMC 的动态出场规则（V2 引擎版）。
 * <p>
 * 融合信号质量评分、订单块动态止损、主动止盈和分级目标止盈。
 * 适配 V2 引擎的 TradingRule 接口与 TradingContext。
 * </p>
 */
public class SmcDynamicExitRule implements TradingRule {

    private static final Logger LOG = LoggerFactory.getLogger(SmcDynamicExitRule.class);

    private final MultiTimeFrameProvider mtfProvider;
    private final SignalType direction; // LONG / SHORT
    private String symbol;
    private String exchangeName;

    private static final String ZONE_PERIOD = "15";
    private static final double MIN_TARGET_SPACE_RATIO = 0.002;

    // ---------- 配置选项 ----------
    private boolean useStructureBreak = true;
    private boolean usePremiumDiscountExit = true;
    private boolean useTargets = true;
    private double stopLossOffset = 0.005;
    private boolean initialStopOffsetEnabled = false;
    private InitialStopOffsetMode initialStopOffsetMode = InitialStopOffsetMode.PERCENT;
    private double initialStopOffsetPercent = 0.0;
    private double initialStopOffsetPoints = 0.0;

    private String targetPeriod = ZONE_PERIOD;
    private String stopLossPeriod = ZONE_PERIOD;
    private String structureBreakPeriod = ZONE_PERIOD;

    // ---------- 状态变量 ----------
    private Double currentStopPrice;
    private Double currentTargetPrice;
    private List<Double> targetPrices;
    private int lastBarIndex = -1;
    private Long lastAnchorTime = null;
    private double primaryTargetMaxDistancePercent = 0.015;

    /** 是否有持仓（用于检测新交易开始，自动重置状态） */
    private boolean hadPosition = false;

    /** 当前持仓ID（用于 Redis key），在 evaluate 中维护 */
    private String posId;

    // ---------- SMC 指标缓存（基于 seriesVersion，series 变化时自动重建） ----------
    private final transient Map<String, Boolean> orderBlockTested = new HashMap<>();
    private final Map<String, CachedSmcIndicator> indicatorCache = new HashMap<>();

    // ---------- 主动止盈 ----------
    private boolean activeTakeProfitEnabled = false;
    private int atpOb15mPercent = 30;
    private int atpOb1hPercent = 50;
    private int atpHigherPercent = 20;
    private String atpHigherPeriod = "240";
    private final Map<String, Boolean> activeTakeProfitFired = new HashMap<>();

    /** 上次评估时的持仓条目数（用于检测加仓重置 ATP） */
    private int previousEntryCount = 0;

    // ---------- Redis 持久化 key 前缀 ----------
    private static final String REDIS_OB = "V2:SMC:OB:";
    private static final String REDIS_ATP = "V2:SMC:ATP:";
    private static final String REDIS_LOT = "V2:SMC:LOT:";
    private static final long ATP_TTL_SECONDS = TimeUnit.DAYS.toSeconds(1); // 24h 过期

    /**
     * 获取 RedisCache 实例，不可用时返回 null（回测模式无需 Redis）
     */
    private RedisCache getRedisCache() {
        try {
            return SpringContextUtil.getBean(RedisCache.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Redis 加载持久化状态到内存 Map
     */
    private void loadPersistedState(String posId) {
        if (posId == null) return;
        RedisCache rc = getRedisCache();
        if (rc == null) return;

        // 加载 orderBlockTested
        Map<Object, Object> loaded = rc.getHash(REDIS_OB + posId);
        if (loaded != null && !loaded.isEmpty()) {
            orderBlockTested.clear();
            loaded.forEach((k, v) -> orderBlockTested.put(k.toString(), "true".equals(v)));
        }

        // 加载 activeTakeProfitFired
        List<String> levels = Arrays.asList("15m", "1h", "HIGHER");
        for (String level : levels) {
            if (rc.hasKey(REDIS_ATP + posId + ":" + level)) {
                activeTakeProfitFired.put(level, true);
            }
        }
    }

    /**
     * 清除指定持仓的所有 Redis 状态
     */
    private void clearPersistedState(String posId) {
        if (posId == null) return;
        RedisCache rc = getRedisCache();
        if (rc == null) return;
        rc.vagueDel(REDIS_OB + posId + "*");
        rc.vagueDel(REDIS_ATP + posId + "*");
        rc.vagueDel(REDIS_LOT + posId + "*");
    }

    // ========== 内部枚举与类 ==========

    private enum InitialStopOffsetMode { PERCENT, POINTS }

    private enum TargetLevel { ULTRA_FAR, FAR, MID, NEAR }

    private static class CachedSmcIndicator {
        final SmartMoneyConceptsIndicator indicator;
        final long seriesVersion;

        CachedSmcIndicator(SmartMoneyConceptsIndicator indicator, long seriesVersion) {
            this.indicator = indicator;
            this.seriesVersion = seriesVersion;
        }
    }

    // ========== 构造函数 ==========

    public SmcDynamicExitRule(MultiTimeFrameProvider mtfProvider, String symbol, SignalType direction) {
        this.mtfProvider = mtfProvider;
        this.symbol = symbol;
        this.direction = direction;
    }

    // ========== 配置 setter ==========

    public void setUseStructureBreak(boolean v) { this.useStructureBreak = v; }
    public void setUsePremiumDiscountExit(boolean v) { this.usePremiumDiscountExit = v; }
    public void setUseTargets(boolean v) { this.useTargets = v; }
    public void setStopLossOffset(double v) { this.stopLossOffset = v; }
    public void setTargetPeriod(String v) { this.targetPeriod = v; }
    public void setStopLossPeriod(String v) { this.stopLossPeriod = v; }
    public void setStructureBreakPeriod(String v) { this.structureBreakPeriod = v; }
    public void setInitialStopOffsetEnabled(boolean v) { this.initialStopOffsetEnabled = v; }
    public void setInitialStopOffsetPercent(double v) {
        this.initialStopOffsetMode = InitialStopOffsetMode.PERCENT;
        this.initialStopOffsetPercent = v;
    }
    public void setInitialStopOffsetPoints(double v) {
        this.initialStopOffsetMode = InitialStopOffsetMode.POINTS;
        this.initialStopOffsetPoints = v;
    }
    public void setPrimaryTargetMaxDistancePercent(double v) { this.primaryTargetMaxDistancePercent = v; }

    /** 设置交易对（影响缓存键） */
    public void setSymbol(String symbol) { this.symbol = symbol; }

    /** 设置交易所名称（影响缓存键，避免多交易所同名交易对冲突） */
    public void setExchangeName(String exchangeName) { this.exchangeName = exchangeName; }

    // ---------- 主动止配置 ----------
    public void setActiveTakeProfitEnabled(boolean v) { this.activeTakeProfitEnabled = v; }
    public void setAtpOb15mPercent(int v) { this.atpOb15mPercent = v; }
    public void setAtpOb1hPercent(int v) { this.atpOb1hPercent = v; }
    public void setAtpHigherPercent(int v) { this.atpHigherPercent = v; }
    public void setAtpHigherPeriod(String v) { this.atpHigherPeriod = v; }

    // ========== TradingRule 接口 ==========

    @Override
    public ExitSignal evaluate(int index, Bar bar, BarSeries series, TradingContext context) {
        if (!context.hasPosition()) {
            // 检测到持仓关闭，清理 Redis 状态
            if (hadPosition && posId != null) {
                clearPersistedState(posId);
            }
            hadPosition = false;
            posId = null;
            return null;
        }

        // 检测新交易开始 → 自动重置状态（防止主动止盈/订单块测试等状态跨交易泄漏）
        if (!hadPosition) {
            hadPosition = true;
            reset();
        }

        // 更新 symbol
        if (this.symbol == null) {
            this.symbol = context.getSymbol();
        }

        // 根据持仓方向获取均价和持仓ID
        boolean isLong;
        double avgEntry;
        if (direction == SignalType.LONG && context.hasLongPosition()) {
            avgEntry = context.getLongAvgPrice().doubleValue();
            isLong = true;
            posId = context.getLongPosition().getPositionId();
        } else if (direction == SignalType.SHORT && context.hasShortPosition()) {
            avgEntry = context.getShortAvgPrice().doubleValue();
            isLong = false;
            posId = context.getShortPosition().getPositionId();
        } else {
            return null;
        }

        if (!Double.isFinite(avgEntry) || avgEntry <= 0) return null;

        // 加载 Redis 持久化状态到内存（实盘对象重建时恢复）
        loadPersistedState(posId);

        // 检测加仓：持仓条目数增加 → 重置 ATP 状态（重新激活各级止盈）
        int currentEntryCount = isLong
                ? context.getLongPosition().getEntries().size()
                : context.getShortPosition().getEntries().size();
        if (previousEntryCount > 0 && currentEntryCount > previousEntryCount) {
            activeTakeProfitFired.clear();
            RedisCache rc = getRedisCache();
            if (rc != null) {
                rc.vagueDel(REDIS_ATP + posId + "*");
            }
        }
        previousEntryCount = currentEntryCount;

        // 获取当前 K 线的时间戳（UTC）
        long currentTime = bar.getBeginTime().toEpochMilli();

        // 尝试获取同索引 bar（用于高/低价判断）
        Bar effectiveBar = (index < series.getBarCount()) ? series.getBar(index) : bar;
        Num high = effectiveBar.getHighPrice();
        Num low = effectiveBar.getLowPrice();

        // 仅在 Bar 变化时重新计算动态止损止盈
        if (index != lastBarIndex) {
            updateDynamicLevels(currentTime, avgEntry);
            lastBarIndex = index;
            lastAnchorTime = currentTime;
        }

        // #1 止损检查（仅 initialStopOffsetEnabled 时启用订单块止损）
        if (initialStopOffsetEnabled && currentStopPrice != null && Double.isFinite(currentStopPrice)) {
            boolean stopTriggered = isLong
                    ? low.doubleValue() <= currentStopPrice
                    : high.doubleValue() >= currentStopPrice;
            if (stopTriggered) {
                return new ExitSignal(
                        isLong ? SignalType.CLOSE_LONG : SignalType.CLOSE_SHORT,
                        ExitType.ORDER_BLOCK,
                        BigDecimal.valueOf(currentStopPrice)
                );
            }
        }

        // #2 主动止盈（启用时不走常规 PROFIT_TARGET）
        if (activeTakeProfitEnabled) {
            ExitSignal atp = checkActiveTakeProfit(high, low, currentTime, avgEntry, isLong);
            if (atp != null) return atp;
        }
        // #3 常规止盈
        else if (useTargets && currentTargetPrice != null && Double.isFinite(currentTargetPrice)) {
            boolean targetReached = isLong
                    ? high.doubleValue() >= currentTargetPrice
                    : low.doubleValue() <= currentTargetPrice;
            if (targetReached) {
                return new ExitSignal(
                        isLong ? SignalType.CLOSE_LONG : SignalType.CLOSE_SHORT,
                        ExitType.PROFIT_TARGET,
                        BigDecimal.valueOf(currentTargetPrice)
                );
            }
        }

        return null;
    }

    // ========== 止损/止盈动态更新 ==========

    private void updateDynamicLevels(long currentTime, double avgEntry) {
        SmartMoneyConceptsIndicator.Result smc15 = getCachedSmcResult("15", currentTime);
        SmartMoneyConceptsIndicator.Result smc60 = getCachedSmcResult("60", currentTime);
        if (smc15 == null && smc60 == null) return;

        TrendType trendType = calculateTrendType(smc15, smc60);
        SmartMoneyConceptsIndicator.Result stopSmc = "15".equals(stopLossPeriod) ? smc15 : smc60;
        if (stopSmc == null) stopSmc = (smc15 != null) ? smc15 : smc60;

        // 信号质量评分
        double qualityScore = calculateSignalQuality(smc15, smc60, avgEntry, direction == SignalType.LONG);

        // 1. 基础止损
        currentStopPrice = calculateBaseStop(stopSmc, avgEntry);
        // 2. 订单块测试后动态调整
        adjustStopForOrderBlockTest(stopSmc, avgEntry);
        // 3. 低质量信号收紧止损
        if (qualityScore < 0.5) {
            tightenStopByQuality(stopSmc, avgEntry);
        }
        // 4. 初始止损偏移
        if (initialStopOffsetEnabled) {
            applyInitialStopOffset(stopSmc, avgEntry);
        }
        // 5. 目标价计算
        List<Double> targets = buildTargetList(smc15, smc60, avgEntry, trendType, qualityScore);
        if (targets != null && !targets.isEmpty()) {
            currentTargetPrice = targets.stream()
                    .min(Comparator.comparingDouble(t -> Math.abs(t - avgEntry)))
                    .orElse(null);
            targetPrices = targets;
        }
    }

    // ========== 信号质量评分 ==========

    private double calculateSignalQuality(SmartMoneyConceptsIndicator.Result smc15,
                                          SmartMoneyConceptsIndicator.Result smc60,
                                          double avgEntry, boolean isBuy) {
        if (smc15 == null || smc60 == null) return 0;
        TrendType trendType = calculateTrendType(smc15, smc60);
        return getTrendScore(trendType, isBuy) + Math.max(computePositionScore(smc15, avgEntry, isBuy), 0);
    }

    private double getTrendScore(TrendType tt, boolean isBuy) {
        switch (tt) {
            case STRONG_BULLISH:   return isBuy ? 2.0 : 0;
            case STRONG_BEARISH:   return isBuy ? 0 : 2.0;
            case BULLISH_PULLBACK: return isBuy ? 0.5 : 0;
            case BEARISH_PULLBACK: return isBuy ? 0 : 0.5;
            case POTENTIAL_BOTTOM: return isBuy ? 1.0 : 0;
            case POTENTIAL_TOP:    return isBuy ? 0 : 1.0;
            default: return 0;
        }
    }

    private double computePositionScore(SmartMoneyConceptsIndicator.Result smc, double price, boolean isBuy) {
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks == null) return 0;
        int requiredBias = isBuy ? 1 : -1;
        double minDist = Double.POSITIVE_INFINITY;
        SmartMoneyConceptsIndicator.OrderBlock nearest = null;
        for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
            if (ob.bias == requiredBias) {
                double lo = Math.min(ob.barLow, ob.barHigh);
                double hi = Math.max(ob.barLow, ob.barHigh);
                double dist = (price >= lo && price <= hi) ? 0
                        : (price < lo) ? (lo - price) / (hi - lo)
                        : (price - hi) / (hi - lo);
                if (dist < minDist) { minDist = dist; nearest = ob; }
            }
        }
        if (nearest == null) return 0;
        double lo = Math.min(nearest.barLow, nearest.barHigh);
        double hi = Math.max(nearest.barLow, nearest.barHigh);
        double range = hi - lo;
        if (range <= 0) return 0;
        if (price >= lo && price <= hi) {
            double pos = (price - lo) / range;
            return isBuy ? 1.0 + (1.0 - pos) : 1.0 + pos;
        } else if (price < lo) {
            return isBuy ? Math.min(1.0, (lo - price) / range) : -0.5 * Math.min(1.0, (lo - price) / range);
        } else {
            return isBuy ? -0.5 * Math.min(1.0, (price - hi) / range) : Math.min(1.0, (price - hi) / range);
        }
    }

    // ========== 止损计算与调整 ==========

    private Double calculateBaseStop(SmartMoneyConceptsIndicator.Result smc, double avgEntry) {
        boolean isLong = direction == SignalType.LONG;
        if (isLong) {
            double base = smc.getDiscountZoneBottom();
            if (Double.isNaN(base)) base = smc.getTrailingLow();
            if (Double.isFinite(base) && base < avgEntry) return base * (1 - stopLossOffset);
        } else {
            double base = smc.getPremiumZoneTop();
            if (Double.isNaN(base)) base = smc.getTrailingHigh();
            if (Double.isFinite(base) && base > avgEntry) return base * (1 + stopLossOffset);
        }
        return null;
    }

    private void adjustStopForOrderBlockTest(SmartMoneyConceptsIndicator.Result smc, double avgEntry) {
        boolean isLong = direction == SignalType.LONG;
        int targetBias = isLong ? 1 : -1;
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks == null) return;
        for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
            if (ob.bias != targetBias) continue;
            String key = ob.barTime + "_" + ob.bias;
            double lo = Math.min(ob.barLow, ob.barHigh);
            double hi = Math.max(ob.barLow, ob.barHigh);
            if (!orderBlockTested.containsKey(key) && avgEntry >= lo && avgEntry <= hi) {
                orderBlockTested.put(key, true);
                // 同时持久化到 Redis（实盘对象重建时恢复）
                if (posId != null) {
                    RedisCache rc = getRedisCache();
                    if (rc != null) {
                        rc.putHash(REDIS_OB + posId, key, "true");
                    }
                }
            }
            if (orderBlockTested.getOrDefault(key, false) && currentStopPrice != null) {
                if (isLong && currentStopPrice < avgEntry) {
                    currentStopPrice = avgEntry * (1 - stopLossOffset * 0.5);
                } else if (!isLong && currentStopPrice > avgEntry) {
                    currentStopPrice = avgEntry * (1 + stopLossOffset * 0.5);
                }
                break;
            }
        }
    }

    private void tightenStopByQuality(SmartMoneyConceptsIndicator.Result smc, double avgEntry) {
        if (direction == SignalType.LONG) {
            double ns = findNearestDemandZoneHigh(smc, avgEntry);
            if (Double.isFinite(ns) && ns > avgEntry && (currentStopPrice == null || ns > currentStopPrice)) {
                currentStopPrice = ns;
            }
        } else {
            double ns = findNearestSupplyZoneLow(smc, avgEntry);
            if (Double.isFinite(ns) && ns < avgEntry && (currentStopPrice == null || ns < currentStopPrice)) {
                currentStopPrice = ns;
            }
        }
    }

    private double findNearestDemandZoneHigh(SmartMoneyConceptsIndicator.Result smc, double avgEntry) {
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks == null) return Double.NaN;
        double best = Double.NEGATIVE_INFINITY;
        for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
            if (ob.bias == 1) {
                double h = Math.max(ob.barLow, ob.barHigh);
                if (h < avgEntry && h > best) best = h;
            }
        }
        return Double.isFinite(best) ? best : Double.NaN;
    }

    private double findNearestSupplyZoneLow(SmartMoneyConceptsIndicator.Result smc, double avgEntry) {
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks == null) return Double.NaN;
        double best = Double.POSITIVE_INFINITY;
        for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
            if (ob.bias == -1) {
                double l = Math.min(ob.barLow, ob.barHigh);
                if (l > avgEntry && l < best) best = l;
            }
        }
        return Double.isFinite(best) ? best : Double.NaN;
    }

    private void applyInitialStopOffset(SmartMoneyConceptsIndicator.Result smc, double avgEntry) {
        boolean isLong = direction == SignalType.LONG;
        if (isLong) {
            double base = findInitialStopBaseLong(smc, avgEntry);
            if (Double.isNaN(base)) base = smc.getTrailingLow();
            if (Double.isFinite(base)) {
                double offset = initialStopOffsetMode == InitialStopOffsetMode.POINTS
                        ? initialStopOffsetPoints : base * (initialStopOffsetPercent / 100.0);
                double stop = base - offset;
                if (stop < avgEntry && (currentStopPrice == null || stop > currentStopPrice)) {
                    currentStopPrice = stop;
                }
            }
        } else {
            double base = findInitialStopBaseShort(smc, avgEntry);
            if (Double.isNaN(base)) base = smc.getTrailingHigh();
            if (Double.isFinite(base)) {
                double offset = initialStopOffsetMode == InitialStopOffsetMode.POINTS
                        ? initialStopOffsetPoints : base * (initialStopOffsetPercent / 100.0);
                double stop = base + offset;
                if (stop > avgEntry && (currentStopPrice == null || stop < currentStopPrice)) {
                    currentStopPrice = stop;
                }
            }
        }
    }

    private double findInitialStopBaseLong(SmartMoneyConceptsIndicator.Result smc, double avgEntry) {
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks == null) return Double.NaN;
        double bestHigh = Double.NEGATIVE_INFINITY;
        double base = Double.NaN;
        for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
            if (ob.bias != 1) continue;
            double h = Math.max(ob.barLow, ob.barHigh);
            if (h < avgEntry && h > bestHigh) { bestHigh = h; base = Math.min(ob.barLow, ob.barHigh); }
        }
        return base;
    }

    private double findInitialStopBaseShort(SmartMoneyConceptsIndicator.Result smc, double avgEntry) {
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks == null) return Double.NaN;
        double bestLow = Double.POSITIVE_INFINITY;
        double base = Double.NaN;
        for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
            if (ob.bias != -1) continue;
            double l = Math.min(ob.barLow, ob.barHigh);
            if (l > avgEntry && l < bestLow) { bestLow = l; base = Math.max(ob.barLow, ob.barHigh); }
        }
        return base;
    }

    // ========== 目标价构建 ==========

    private List<Double> buildTargetList(SmartMoneyConceptsIndicator.Result smc15,
                                         SmartMoneyConceptsIndicator.Result smc60,
                                         double avgEntry, TrendType trend, double qualityScore) {
        TargetLevel level = selectTargetLevel(trend, qualityScore);
        List<Double> candidates = new ArrayList<>();
        if (level.ordinal() >= TargetLevel.FAR.ordinal() && smc60 != null) {
            addTargetsFromPeriod(smc60, avgEntry, candidates);
        }
        if (level.ordinal() >= TargetLevel.MID.ordinal() && smc15 != null) {
            addTargetsFromPeriod(smc15, avgEntry, candidates);
        }
        boolean isLong = direction == SignalType.LONG;
        double minTarget = isLong
                ? avgEntry * (1.0 + MIN_TARGET_SPACE_RATIO)
                : avgEntry * (1.0 - MIN_TARGET_SPACE_RATIO);
        return candidates.stream()
                .filter(Objects::nonNull).filter(Double::isFinite)
                .filter(t -> isLong ? t >= minTarget : t <= minTarget)
                .distinct()
                .sorted(Comparator.comparingDouble(t -> Math.abs(t - avgEntry)))
                .collect(Collectors.toList());
    }

    private void addTargetsFromPeriod(SmartMoneyConceptsIndicator.Result smc, double avgEntry, List<Double> out) {
        boolean isLong = direction == SignalType.LONG;
        int bias = isLong ? -1 : 1;
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks == null) return;
        for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
            if (ob.bias != bias) continue;
            double lo = Math.min(ob.barLow, ob.barHigh);
            double hi = Math.max(ob.barLow, ob.barHigh);
            if (isLong && lo > avgEntry) {
                out.add(lo); out.add((lo + hi) / 2);
            } else if (!isLong && hi < avgEntry) {
                out.add(hi); out.add((lo + hi) / 2);
            }
        }
    }

    private TargetLevel selectTargetLevel(TrendType trend, double qualityScore) {
        boolean strong = isExtremeTrend(trend);
        if (qualityScore > 1.5) {
            return strong ? TargetLevel.FAR : TargetLevel.MID;
        } else if (qualityScore < 0.5) {
            return TargetLevel.NEAR;
        } else {
            if (strong) return TargetLevel.FAR;
            if (trend == BULLISH_PULLBACK || trend == BEARISH_PULLBACK) return TargetLevel.MID;
            return TargetLevel.NEAR;
        }
    }

    private static boolean isExtremeTrend(TrendType tt) {
        return tt == STRONG_BULLISH || tt == STRONG_BEARISH || tt == POTENTIAL_TOP || tt == POTENTIAL_BOTTOM;
    }

    // ========== 主动止盈（三级对立订单块） ==========

    private ExitSignal checkActiveTakeProfit(Num high, Num low, long currentTime, double avgEntry, boolean isLong) {
        SignalType dir = isLong ? SignalType.CLOSE_LONG : SignalType.CLOSE_SHORT;

        // 未盈利不触发 ATP
        if ((isLong && high.doubleValue() <= avgEntry)
                || (!isLong && low.doubleValue() >= avgEntry)) {
            return null;
        }

        // Level 1: 15m
        BigDecimal hitPrice15 = checkOppositeObHit("15", currentTime, high, low, avgEntry, isLong);
        if (!activeTakeProfitFired.getOrDefault("ATP_15m", false) && atpOb15mPercent > 0
                && hitPrice15 != null) {
            markAtpFired("15m", hitPrice15);
            return new ExitSignal(dir, ExitType.ACTIVE_TAKE_PROFIT_OB15M, hitPrice15, atpOb15mPercent);
        }
        // Level 2: 1h
        BigDecimal hitPrice1h = checkOppositeObHit("60", currentTime, high, low, avgEntry, isLong);
        if (!activeTakeProfitFired.getOrDefault("ATP_1h", false) && atpOb1hPercent > 0
                && hitPrice1h != null) {
            markAtpFired("1h", hitPrice1h);
            return new ExitSignal(dir, ExitType.ACTIVE_TAKE_PROFIT_OB1H, hitPrice1h, atpOb1hPercent);
        }
        // Level 3: Higher
        BigDecimal hitPriceHigher = checkOppositeObHit(atpHigherPeriod, currentTime, high, low, avgEntry, isLong);
        if (!activeTakeProfitFired.getOrDefault("ATP_HIGHER", false) && atpHigherPercent > 0
                && atpHigherPeriod != null && !atpHigherPeriod.isBlank()
                && hitPriceHigher != null) {
            markAtpFired("HIGHER", hitPriceHigher);
            return new ExitSignal(dir, ExitType.ACTIVE_TAKE_PROFIT_HIGHER, hitPriceHigher, atpHigherPercent);
        }
        return null;
    }

    /**
     * 标记 ATP 已触发，同时持久化到 Redis
     */
    private void markAtpFired(String level, BigDecimal hitPrice) {
        activeTakeProfitFired.put("ATP_" + level, true);
        if (posId != null) {
            RedisCache rc = getRedisCache();
            if (rc != null) {
                rc.put(REDIS_ATP + posId + ":" + level, "1", ATP_TTL_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    private BigDecimal checkOppositeObHit(String period, long currentTime, Num high, Num low, double avgEntry, boolean isLong) {
        SmartMoneyConceptsIndicator.Result smc = getCachedSmcResult(period, currentTime);
        if (smc == null) return null;
        int targetBias = isLong ? -1 : 1;
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks == null || blocks.isEmpty()) return null;
        for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
            if (ob.bias != targetBias) continue;
            double obLo = Math.min(ob.barLow, ob.barHigh);
            double obHi = Math.max(ob.barLow, ob.barHigh);
            // 做多: 供给区（sell OB），优先下边沿；若下边沿低于入场价则用上边沿，hit条件与返回边沿一致
            // 做空: 需求区（buy OB），优先上边沿；若上边沿高于入场价则用下边沿，hit条件与返回边沿一致
            if (isLong && high.doubleValue() >= (obLo >= avgEntry ? obLo : obHi)) {
                return BigDecimal.valueOf(obLo >= avgEntry ? obLo : obHi);
            } else if (!isLong && low.doubleValue() <= (obHi <= avgEntry ? obHi : obLo)) {
                return BigDecimal.valueOf(obHi <= avgEntry ? obHi : obLo);
            }
        }
        return null;
    }

    // ========== 通过 MultiTimeFrameProvider 获取 SMC 分析结果 ==========

    private SmartMoneyConceptsIndicator.Result getCachedSmcResult(String period, long currentTime) {
        if (mtfProvider == null) return null;
        CandlestickIntervalEnum interval = parsePeriod(period);
        if (interval == null) return null;

        BarSeries series = mtfProvider.getSeries(interval);
        if (series == null || series.getBarCount() < 200) return null;

        long currentVersion = mtfProvider.getSeriesVersion(interval);
        CachedSmcIndicator cached = indicatorCache.get(period);
        if (cached == null || cached.seriesVersion != currentVersion) {
            SmartMoneyConceptsIndicator ind = createSmcIndicator(series);
            indicatorCache.put(period, new CachedSmcIndicator(ind, currentVersion));
            cached = indicatorCache.get(period);
        }

        int idx = mtfProvider.getBarIndex(interval, currentTime);
        if (idx < 0 || idx >= series.getBarCount()) return null;

        return cached.indicator.getValue(idx);
    }

    private static CandlestickIntervalEnum parsePeriod(String period) {
        int min;
        try {
            min = Integer.parseInt(period);
        } catch (NumberFormatException e) {
            return null;
        }
        for (CandlestickIntervalEnum e : CandlestickIntervalEnum.values()) {
            if (e.getMinNum() == min) return e;
        }
        return null;
    }

    private SmartMoneyConceptsIndicator createSmcIndicator(BarSeries series) {
        SmartMoneyConceptsIndicator.Config config = new SmartMoneyConceptsIndicator.Config();
        config.setSwingsLength(50);
        config.setShowInternalOrderBlocks(true);
        config.setShowSwingOrderBlocks(true);
        config.setShowEqualHighsLows(true);
        config.setShowPremiumDiscountZones(true);
        config.setShowFairValueGaps(false);
        return new SmartMoneyConceptsIndicator(series, config, null, null, null);
    }

    private TrendType calculateTrendType(SmartMoneyConceptsIndicator.Result smc15, SmartMoneyConceptsIndicator.Result smc60) {
        if (smc15 == null || smc60 == null) return RANGING;
        return SmcTrendUtils.identifyTrendType(
                smc60.getSwingTrend(), smc60.getInternalTrend(),
                smc15.getSwingTrend(), smc15.getInternalTrend()
        );
    }

    /** 重置状态（退出后清理） */
    public void reset() {
        currentStopPrice = null;
        currentTargetPrice = null;
        targetPrices = null;
        lastBarIndex = -1;
        lastAnchorTime = null;
        orderBlockTested.clear();
        activeTakeProfitFired.clear();
        previousEntryCount = 0;
        // 同时清理 Redis 中的残留状态
        if (posId != null) {
            clearPersistedState(posId);
        }
    }
}
