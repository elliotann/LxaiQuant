package com.chain.ai.trade.engine2.rules.base;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.mtf.MultiTimeFrameProvider;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SMC 止损预计算器 — 独立于出场规则，供开仓前预计算止损位，用于以损定量仓位计算。
 * <p>
 * 与 SmcStructuredExitRule 共享同一个 MultiTimeFrameProvider 数据层，
 * 但完全独立运行，不依赖 TradingContext 或持仓状态。
 * </p>
 * <p>
 * 止损/止盈计算优先级：结构止盈止损（基于结构止盈止损启用时）> 固定百分比止盈止损（止损/止盈设置中的配置）。
 * </p>
 *
 * @author system
 * @since 2026-07-29
 */
public class SmcStopPreviewer {

    private static final Logger LOG = LoggerFactory.getLogger(SmcStopPreviewer.class);

    private final MultiTimeFrameProvider mtfProvider;
    private final String symbol;
    private final String stopLossDailyPeriod;
    private final double stopLossDailyBuffer;
    private final String referenceTakeProfitPeriod;

    /** 结构止损是否启用 */
    private final boolean structureEnabled;

    /** 固定百分比止损（小数，如 0.05 表示 5%），作为结构止损不可用时的兜底 */
    private final double fixedStopLossPercent;

    /** 固定百分比止盈（小数，如 0.03 表示 3%），作为结构止盈不可用时的兜底 */
    private final double fixedTakeProfitPercent;

    /** SMC 指标缓存 */
    private final Map<String, CachedSmc> indicatorCache = new HashMap<>();

    /**
     * @param mtfProvider               多周期数据提供者
     * @param symbol                    交易对
     * @param stopLossDailyPeriod       日线止损周期（如 "15"）
     * @param stopLossDailyBuffer       止损缓冲（小数，如 0.0008 表示 0.08%）
     * @param referenceTakeProfitPeriod 止盈参考周期（如 "60"）
     * @param structureEnabled          结构止损是否启用
     * @param fixedStopLossPercent      固定百分比止损（小数，如 0.05 表示 5%），0 表示不启用
     * @param fixedTakeProfitPercent    固定百分比止盈（小数，如 0.03 表示 3%），0 表示不启用
     */
    public SmcStopPreviewer(MultiTimeFrameProvider mtfProvider, String symbol,
                            String stopLossDailyPeriod, double stopLossDailyBuffer,
                            String referenceTakeProfitPeriod,
                            boolean structureEnabled, double fixedStopLossPercent,
                            double fixedTakeProfitPercent) {
        this.mtfProvider = mtfProvider;
        this.symbol = symbol;
        this.stopLossDailyPeriod = stopLossDailyPeriod;
        this.stopLossDailyBuffer = stopLossDailyBuffer;
        this.referenceTakeProfitPeriod = referenceTakeProfitPeriod;
        this.structureEnabled = structureEnabled;
        this.fixedStopLossPercent = fixedStopLossPercent;
        this.fixedTakeProfitPercent = fixedTakeProfitPercent;
    }

    /**
     * 预计算止损价。
     * <p>仅使用配置的固定百分比止损，未配置则返回 null。</p>
     *
     * @param entryPrice  入场价
     * @param isLong      是否多头
     * @param currentTime 当前时间戳（毫秒）
     * @return 止损价，null 表示未配置固定止损
     */
    public Double computeStopDaily(double entryPrice, boolean isLong, long currentTime) {
        if (fixedStopLossPercent <= 0) {
            return null;
        }
        Double fixedStop = isLong
                ? entryPrice * (1 - fixedStopLossPercent)
                : entryPrice * (1 + fixedStopLossPercent);
        LOG.debug("固定百分比止损: entry={}, fixedPercent={}, stop={}", entryPrice, fixedStopLossPercent, fixedStop);
        return fixedStop;
    }

    /**
     * 预计算止盈价（TP1）。
     * <p>同时计算结构止盈和固定百分比止盈，取利润空间（距入场价更远）更大的那个。</p>
     *
     * @param entryPrice  入场价
     * @param isLong      是否多头
     * @param currentTime 当前时间戳（毫秒）
     * @return 止盈价，null 表示数据不足
     */
    public Double computeTakeProfit(double entryPrice, boolean isLong, long currentTime) {
        Double smcTp = null;
        Double fixedTp = null;

        // 计算结构止盈
        if (structureEnabled && mtfProvider != null && referenceTakeProfitPeriod != null) {
            smcTp = computeSmcTakeProfit(entryPrice, isLong, currentTime);
        }

        // 计算固定百分比止盈
        if (fixedTakeProfitPercent > 0) {
            fixedTp = isLong
                    ? entryPrice * (1 + fixedTakeProfitPercent)
                    : entryPrice * (1 - fixedTakeProfitPercent);
        }

        // 两者都不可用
        if (smcTp == null && fixedTp == null) {
            return null;
        }

        // 只有一个可用，直接用
        if (smcTp == null) {
            LOG.debug("固定百分比止盈: entry={}, fixedPercent={}, tp={}", entryPrice, fixedTakeProfitPercent, fixedTp);
            return fixedTp;
        }
        if (fixedTp == null) {
            return smcTp;
        }

        // 取利润空间更大的：多头取高价，空头取低价
        boolean smcBetter = isLong ? (smcTp > fixedTp) : (smcTp < fixedTp);
        Double selected = smcBetter ? smcTp : fixedTp;
        LOG.debug("止盈对比: smc={}, fixed={}, 选择={} ({})",
                smcTp, fixedTp, selected, smcBetter ? "结构" : "固定百分比");
        return selected;
    }

    /**
     * 基于 SMC 结构计算止盈价（TP1），通过止盈参考周期的对立订单块。
     */
    private Double computeSmcTakeProfit(double entryPrice, boolean isLong, long currentTime) {
        SmartMoneyConceptsIndicator.Result smc = getCachedSmcResult(
                periodToMinute(referenceTakeProfitPeriod), currentTime);
        if (smc == null) return null;

        // TP1: 内部对立订单块
        int bias = isLong ? -1 : 1;
        List<SmartMoneyConceptsIndicator.OrderBlock> blocks = smc.getInternalOrderBlocks();
        if (blocks != null && !blocks.isEmpty()) {
            double best = isLong ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            for (SmartMoneyConceptsIndicator.OrderBlock ob : blocks) {
                if (ob.bias != bias) continue;
                double lo = Math.min(ob.barLow, ob.barHigh);
                double hi = Math.max(ob.barLow, ob.barHigh);
                if (isLong && lo > entryPrice && lo < best) best = lo;
                if (!isLong && hi < entryPrice && hi > best) best = hi;
            }
            if (Double.isFinite(best)) return best;
        }

        // fallback: 摆动高/低点
        if (isLong) {
            double hh = smc.getLastSwingHigh();
            return Double.isFinite(hh) && hh > entryPrice ? hh : null;
        } else {
            double ll = smc.getLastSwingLow();
            return Double.isFinite(ll) && ll < entryPrice ? ll : null;
        }
    }

    /**
     * 预计算第一道（日线级）止损价，供开仓前以损定量使用。
     *
     * @param entryPrice           入场价
     * @param isLong               是否多头
     * @param currentTime          当前时间戳（毫秒）
     * @param stopLossDailyPeriod  日线止损周期（如 "15"）
     * @param stopLossDailyBuffer  止损缓冲（小数，如 0.0008 表示 0.08%）
     * @return 止损价，null 表示数据不足无法计算
     */
    public Double computeStopDaily(double entryPrice, boolean isLong, long currentTime,
                                   String stopLossDailyPeriod, double stopLossDailyBuffer) {
        if (mtfProvider == null) return null;

        SmartMoneyConceptsIndicator.Result smc = getCachedSmcResult(
                periodToMinute(stopLossDailyPeriod), currentTime);
        if (smc == null) return null;

        return calculateStop(smc, entryPrice, isLong, stopLossDailyBuffer, true);
    }

    // ==================== SMC 指标缓存 ====================

    private SmartMoneyConceptsIndicator.Result getCachedSmcResult(String period, long currentTime) {
        if (period == null) return null;
        CandlestickIntervalEnum interval = parsePeriod(period);
        if (interval == null) return null;

        int idx = mtfProvider.getBarIndex(interval, currentTime);
        if (idx < 0) return null;

        BarSeries series = mtfProvider.getSeries(interval);
        if (series == null || series.getBarCount() < 50) return null;
        if (idx >= series.getBarCount()) return null;

        long currentVersion = mtfProvider.getSeriesVersion(interval);
        CachedSmc cached = indicatorCache.get(period);
        if (cached == null || cached.seriesVersion != currentVersion) {
            SmartMoneyConceptsIndicator ind = createSmcIndicator(series);
            indicatorCache.put(period, new CachedSmc(ind, currentVersion));
            cached = indicatorCache.get(period);
        }

        return cached.indicator.getValue(idx);
    }

    private Double calculateStop(SmartMoneyConceptsIndicator.Result smc, double entry,
                                  boolean isLong, double buffer, boolean isDaily) {
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

    // ==================== 工具方法 ====================

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

    // ==================== 内部类 ====================

    private static class CachedSmc {
        final SmartMoneyConceptsIndicator indicator;
        final long seriesVersion;

        CachedSmc(SmartMoneyConceptsIndicator indicator, long seriesVersion) {
            this.indicator = indicator;
            this.seriesVersion = seriesVersion;
        }
    }
}
