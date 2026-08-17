package com.chain.ai.trade.extension.ta4j.indicator;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.num.Num;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smart Money Concepts Indicator - Full Implementation
 * 包含所有8个模块的数值输出，适用于量化策略。
 */
public class SmartMoneyConceptsIndicator extends CachedIndicator<SmartMoneyConceptsIndicator.Result> {

    private final Config config;
    private final int swingsLength;
    private final int internalLength = 5;
    private final BarSeries dailySeries;   // 可选，用于日线水平
    private final BarSeries weeklySeries;  // 可选，用于周线水平
    private final BarSeries monthlySeries; // 可选，用于月线水平

    // 存储历史数据
    private final List<Double> parsedHighs = new ArrayList<>();
    private final List<Double> parsedLows = new ArrayList<>();
    private final List<Double> highs = new ArrayList<>();
    private final List<Double> lows = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    private final TRIndicator trueRangeIndicator;
    private final ATRIndicator atrIndicator;
    private final RunningTotalIndicator runningTotalTr;
    private final List<Double> cumulativeAbsDelta = new ArrayList<>();

    // 状态变量
    private Pivot swingHigh;
    private Pivot swingLow;
    private Pivot internalHigh;
    private Pivot internalLow;
    private Trend swingTrend;
    private Trend internalTrend;
    private final List<OrderBlock> swingOrderBlocks = new ArrayList<>();
    private final List<OrderBlock> internalOrderBlocks = new ArrayList<>();
    private final List<SwingLabel> swingLabels = new ArrayList<>();
    private TrailingExtremes trailing;

    // 用于跨K线追踪 higher low / lower high
    private double lastHigherLow = Double.NaN;
    private double lastLowerHigh = Double.NaN;

    // EQH/EQL 缓存
    private Pivot equalHigh;
    private Pivot equalLow;

    // 状态维护
    private int lastCalculatedIndex = -1;

    // FVG 存储（用于检测价格进入）
    private final List<FairValueGap> fairValueGaps = new ArrayList<>();

    // 缓存 leg 值
    private int currentLeg = 0;
    private int prevLegInternal = 0;
    private int prevLegEqual = 0;
    private int swingLeg = 0;
    private int prevSwingLeg = 0;
    private boolean bullishBar = true;
    private boolean bearishBar = true;

    /**
     * 构造函数
     * @param series       主图Bar序列
     * @param config       配置参数
     * @param dailySeries  日线序列（可为null）
     * @param weeklySeries 周线序列（可为null）
     * @param monthlySeries 月线序列（可为null）
     */
    public SmartMoneyConceptsIndicator(BarSeries series, Config config,
                                       BarSeries dailySeries, BarSeries weeklySeries, BarSeries monthlySeries) {
        super(series);
        this.config = config;
        this.swingsLength = config.getSwingsLength();
        this.dailySeries = dailySeries;
        this.weeklySeries = weeklySeries;
        this.monthlySeries = monthlySeries;

        this.trueRangeIndicator = new TRIndicator(series);
        this.atrIndicator = new ATRIndicator(series, 200);
        this.runningTotalTr = new RunningTotalIndicator(trueRangeIndicator, Integer.MAX_VALUE);

        // 初始化状态
        this.swingHigh = new Pivot(Double.NaN, Double.NaN, false, 0L, 0);
        this.swingLow = new Pivot(Double.NaN, Double.NaN, false, 0L, 0);
        this.internalHigh = new Pivot(Double.NaN, Double.NaN, false, 0L, 0);
        this.internalLow = new Pivot(Double.NaN, Double.NaN, false, 0L, 0);
        this.equalHigh = new Pivot(Double.NaN, Double.NaN, false, 0L, 0);
        this.equalLow = new Pivot(Double.NaN, Double.NaN, false, 0L, 0);
        this.swingTrend = new Trend(0);
        this.internalTrend = new Trend(0);
        this.trailing = new TrailingExtremes();
    }

    @Override
    protected Result calculate(int index) {
        // 保证状态化指标的顺序计算，防止跳过某些关键K线导致的结构缺失
        if (index > lastCalculatedIndex + 1) {
            for (int i = lastCalculatedIndex + 1; i < index; i++) {
                this.getValue(i);
            }
        }
        lastCalculatedIndex = index;

        // 首次计算时清除之前的摆动点标签缓存
        if (index == getCountOfUnstableBars()) {
            swingLabels.clear();
        }

        ensureParsedUpTo(index);
        if (index < getCountOfUnstableBars()) {
            return new Result();
        }

        Bar bar = getBarSeries().getBar(index);
        double open = bar.getOpenPrice().doubleValue();
        double high = bar.getHighPrice().doubleValue();
        double low = bar.getLowPrice().doubleValue();
        double close = bar.getClosePrice().doubleValue();
        long timestamp = bar.getBeginTime().toEpochMilli() - 8 * 60 * 60 * 1000L; // 正确获取毫秒时间戳

        // 2. 更新摆动点（Swing, Internal, EqualHigh/Low）
        updateSwingPoints(index, swingsLength, false, false);    // swing
        updateSwingPoints(index, internalLength, false, true);   // internal
        if (config.isShowEqualHighsLows()) {
            updateSwingPoints(index, config.getEqualHighsLowsLength(), true, false);
        }

        // 3. 摆动点赋值（不依赖趋势方向，直接从 Pivot 链读取）
        Result result = new Result();
        // 从上一 K 线 Result 复制事件类型（CachedIndicator 保证 getValue(index-1) 已计算）
        if (index > 0) {
            Result prev = getValue(index - 1);
            result.setLastInternalEventType(prev.getLastInternalEventType());
            result.setLastSwingEventType(prev.getLastSwingEventType());
        }
        result.setLastSwingHigh(swingHigh.currentLevel);
        result.setLastSwingLow(swingLow.currentLevel);
        result.setPrevSwingHigh(swingHigh.lastLevel);
        result.setPrevSwingLow(swingLow.lastLevel);
        result.setLastHigherLow(lastHigherLow);
        result.setLastLowerHigh(lastLowerHigh);

        // 打印当前摆动点状态，用于对比TV调试
        java.time.ZonedDateTime dt = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.of("Asia/Shanghai"));
        /*if (dt.getYear() == 2026 && dt.getMonthValue() == 5 && dt.getDayOfMonth() >= 23) {
            System.out.println("[SMC STATE] idx=" + index + " dt=" + dt
                + " open=" + String.format("%.2f", open)
                + " high=" + String.format("%.2f", high)
                + " low=" + String.format("%.2f", low)
                + " close=" + String.format("%.2f", close)
                + " SH_current=" + (Double.isNaN(swingHigh.currentLevel) ? "NaN" : String.format("%.2f", swingHigh.currentLevel))
                + " SH_time=" + swingHigh.barTime
                + " SH_last=" + (Double.isNaN(swingHigh.lastLevel) ? "NaN" : String.format("%.2f", swingHigh.lastLevel))
                + " SL_current=" + (Double.isNaN(swingLow.currentLevel) ? "NaN" : String.format("%.2f", swingLow.currentLevel))
                + " SL_time=" + swingLow.barTime
                + " SL_last=" + (Double.isNaN(swingLow.lastLevel) ? "NaN" : String.format("%.2f", swingLow.lastLevel))
                + " lastHL=" + (Double.isNaN(lastHigherLow) ? "NaN" : String.format("%.2f", lastHigherLow))
                + " lastLH=" + (Double.isNaN(lastLowerHigh) ? "NaN" : String.format("%.2f", lastLowerHigh))
                + " swingTrend=" + swingTrend.bias + " internalTrend=" + internalTrend.bias);
            // HL/LL 结构对比：当 LL 低于 lastHL 时，说明空头结构仍在延续
            *//*if (!Double.isNaN(swingLow.currentLevel) && !Double.isNaN(swingLow.lastLevel) && !Double.isNaN(lastHigherLow)) {
                String structuralNote = swingLow.currentLevel < lastHigherLow ? " LL_BELOW_HL (bearish)" : " HL_ABOVE_LL (bullish)";
                System.out.println("[SMC STRUCT] idx=" + index + " dt=" + dt + " SL_current=" + String.format("%.2f", swingLow.currentLevel) + " lastHL=" + String.format("%.2f", lastHigherLow) + structuralNote);
            }*//*
        }*/

        // 4. 检测结构突破 (BOS/CHOCH)
        detectStructureBreaks(index, result, true);   // internal
        /*if (dt.getYear() == 2026 && dt.getMonthValue() == 5 && dt.getDayOfMonth() >= 01 && dt.getHour() >= 00) {
            double prevClose = index > 0 ? getBarSeries().getBar(index - 1).getClosePrice().doubleValue() : 0;
            System.out.println("[BOS_BEFORE] idx=" + index
                + " dt=" + dt
                + " close=" + String.format("%.2f", close)
                + " prevClose=" + String.format("%.2f", prevClose)
                + " swingLow=" + (Double.isNaN(swingLow.currentLevel) ? "NaN" : String.format("%.2f", swingLow.currentLevel))
                + " swingLow.barTime=" + swingLow.barTime
                + " swingLow.crossed=" + swingLow.crossed
                + " swingHigh=" + (Double.isNaN(swingHigh.currentLevel) ? "NaN" : String.format("%.2f", swingHigh.currentLevel))
                + " swingHigh.crossed=" + swingHigh.crossed
                + " trend.bias=" + swingTrend.bias);
        }*/
        detectStructureBreaks(index, result, false);  // swing

        // 5. 处理订单块（删除已突破的）
        deleteBrokenOrderBlocks(index, true, result);
        deleteBrokenOrderBlocks(index, false, result);

        // 6. 设置趋势方向
        result.setInternalTrend(internalTrend.bias);
        result.setSwingTrend(swingTrend.bias);

        // 7. 更新 trailing extremes（当前波段高低点）
        updateTrailingExtremes(index, high, low, timestamp);
        result.setTrailingHigh(trailing.top);
        result.setTrailingLow(trailing.bottom);
        result.setTrailingHighTime(trailing.lastTopTime);
        result.setTrailingLowTime(trailing.lastBottomTime);

        // 8. EQH/EQL 检测（产生警报）
        detectEqualHighLow(index, result);

        // 9. FVG 检测与删除
        if (config.isShowFairValueGaps()) {
            detectFairValueGaps(index, result);
            deleteFairValueGaps(index, result);
        }

        // 10. MTF 水平（输出当前价格相对于这些水平的位置）
        computeMultiTimeframeLevels(index, result);

        // 11. 溢价/折扣区域（输出区域边界和当前价格所在区域）
        computePremiumDiscountZones(index, result);

        // 12. 高低点强弱标签（固定含义，不依赖趋势方向）
        result.setStrongHigh(swingHigh.lastLevel);
        result.setWeakHigh(trailing.top);
        result.setStrongLow(swingLow.lastLevel);
        result.setWeakLow(trailing.bottom);

        // 13. 趋势蜡烛颜色（输出方向）
        result.setCandleColor(internalTrend.bias == 1 ? 1 : (internalTrend.bias == -1 ? -1 : 0));

        // 14. 输出订单块列表（供外部使用）
        result.setSwingOrderBlocks(new ArrayList<>(swingOrderBlocks));
        result.setInternalOrderBlocks(new ArrayList<>(internalOrderBlocks));

        return result;
    }

    @Override
    public int getCountOfUnstableBars() {
        int swingUnstable = swingsLength;
        int equalUnstable = config.isShowEqualHighsLows() ? config.getEqualHighsLowsLength() : 0;
        int fvgUnstable = 2; // FVG需要至少2根bar
        return Math.max(swingUnstable, Math.max(equalUnstable, fvgUnstable));
    }

    // ------------------- 核心算法 -------------------
    private void ensureParsedUpTo(int index) {
        int size = highs.size();
        if (size > index) return;
        for (int i = size; i <= index; i++) {
            Bar b = getBarSeries().getBar(i);
            double high = b.getHighPrice().doubleValue();
            double low = b.getLowPrice().doubleValue();
            long timestamp = b.getBeginTime().toEpochMilli() - 8 * 60 * 60 * 1000L;

            double absDelta = 0.0;
            if (i > 0) {
                Bar prev = getBarSeries().getBar(i - 1);
                double prevOpen = prev.getOpenPrice().doubleValue();
                double prevClose2 = prev.getClosePrice().doubleValue();
                if (prevOpen != 0.0) {
                    double barDeltaPercent = (prevClose2 - prevOpen) / (prevOpen * 100.0);
                    absDelta = Math.abs(barDeltaPercent);
                }
            }
            double prevCumAbs = cumulativeAbsDelta.isEmpty() ? 0.0 : cumulativeAbsDelta.get(cumulativeAbsDelta.size() - 1);
            cumulativeAbsDelta.add(prevCumAbs + absDelta);

            double volatility = computeVolatility(i);
            boolean highVolatility = (high - low) >= (2 * volatility);
            double parsedHigh = highVolatility ? low : high;
            double parsedLow = highVolatility ? high : low;

            parsedHighs.add(parsedHigh);
            parsedLows.add(parsedLow);
            highs.add(high);
            lows.add(low);
            times.add(timestamp);
        }
    }

    private void updateSwingPoints(int index, int size, boolean equalHighLow, boolean internal) {
        if (index < size) return;

        if (internal || equalHighLow) {
            // internal/equalHighLow级别：保留leg/newLeg机制
            int prevLegForThisCall = equalHighLow ? prevLegEqual : prevLegInternal;
            leg(index, size);
            boolean newLeg = (currentLeg != prevLegForThisCall);
            if (equalHighLow) prevLegEqual = currentLeg;
            else prevLegInternal = currentLeg;

            if (newLeg) {
                boolean startBullish = (currentLeg == 1);
                if (startBullish) {
                    Pivot pivot = equalHighLow ? equalLow : internalLow;
                    double lowValue = lows.get(index - size);
                    pivot.lastLevel = pivot.currentLevel;
                    pivot.currentLevel = lowValue;
                    pivot.crossed = false;
                    pivot.barTime = times.get(index - size);
                    pivot.barIndex = index - size;
                    pivot.oppositeBarTime = (equalHighLow ? equalHigh : internalHigh).barTime;
                } else {
                    Pivot pivot = equalHighLow ? equalHigh : internalHigh;
                    double highValue = highs.get(index - size);
                    pivot.lastLevel = pivot.currentLevel;
                    pivot.currentLevel = highValue;
                    pivot.crossed = false;
                    pivot.barTime = times.get(index - size);
                    pivot.barIndex = index - size;
                    pivot.oppositeBarTime = (equalHighLow ? equalLow : internalLow).barTime;
                }
            }
            return;
        }

        // swing级别：使用独立swingLeg/newLeg机制，与internal完全一致
        int pivotIdx = index - size;
        double pivotHigh = highs.get(pivotIdx);
        double pivotLow = lows.get(pivotIdx);
        double highestRight = highest(pivotIdx + 1, size);
        double lowestRight = lowest(pivotIdx + 1, size);

        // 调试日志（仅输出，不用于状态更新）
        long curTs = times.get(index);
        java.time.ZonedDateTime curDt = java.time.Instant.ofEpochMilli(curTs).atZone(java.time.ZoneId.of("Asia/Shanghai"));
        boolean dbgEnabled = curDt.getYear() == 2026 && curDt.getMonthValue() == 5 && curDt.getDayOfMonth() >= 1;
        dbgEnabled=false;
        if (dbgEnabled) {
            java.time.ZonedDateTime pivotDt = java.time.Instant.ofEpochMilli(times.get(pivotIdx)).atZone(java.time.ZoneId.of("Asia/Shanghai"));
            System.out.println("[SMC_SWING_CHK] idx=" + index + " dt=" + curDt
                + " pivotIdx=" + pivotIdx + " pivotDt=" + pivotDt
                + " pivotHigh=" + String.format("%.2f", pivotHigh)
                + " highestRight(" + (pivotIdx+1) + ".." + Math.min(pivotIdx+size, highs.size()-1) + ")=" + String.format("%.2f", highestRight)
                + " isSwingHigh=" + (pivotHigh > highestRight)
                + " pivotLow=" + String.format("%.2f", pivotLow)
                + " lowestRight=" + String.format("%.2f", lowestRight)
                + " isSwingLow=" + (pivotLow < lowestRight));
        }

        // newLeg检测（使用独立swingLeg）
        int prevSwingLegBefore = prevSwingLeg;
        swingLeg(index, size);
        boolean newSwingLeg = (swingLeg != prevSwingLegBefore);
        prevSwingLeg = swingLeg;

        if (newSwingLeg) {
            boolean startBullish = (swingLeg == 1);
            if (startBullish) {
                double lowValue = lows.get(index - size);
                swingLow.lastLevel = swingLow.currentLevel;
                swingLow.currentLevel = lowValue;
                swingLow.crossed = false;
                swingLow.barTime = times.get(index - size);
                swingLow.barIndex = index - size;
                swingLow.oppositeBarTime = swingHigh.barTime;

                trailing.bottom = lowValue;
                trailing.barTime = swingLow.barTime;
                trailing.barIndex = swingLow.barIndex;
                trailing.lastBottomTime = swingLow.barTime;

                if (!Double.isNaN(swingLow.lastLevel) && swingLow.currentLevel > swingLow.lastLevel) {
                    lastHigherLow = swingLow.currentLevel;
                }
                String tag = swingLow.currentLevel < swingLow.lastLevel ? "LL" : "HL";
                swingLabels.add(new SwingLabel(swingLow.barTime, swingLow.currentLevel, tag));
                if (dbgEnabled) {
                    java.time.ZonedDateTime pivotDt = java.time.Instant.ofEpochMilli(swingLow.barTime).atZone(java.time.ZoneId.of("Asia/Shanghai"));
                    System.out.println("[SMC SWING LOW] idx=" + swingLow.barIndex + " time=" + swingLow.barTime + " pivotDt=" + pivotDt + " detIdx=" + index + " detDt=" + curDt + " price=" + String.format("%.2f", swingLow.currentLevel) + " lastPrice=" + (Double.isNaN(swingLow.lastLevel) ? "NaN" : String.format("%.2f", swingLow.lastLevel)) + " tag=" + tag + " lastHigherLow=" + (Double.isNaN(lastHigherLow) ? "NaN" : String.format("%.2f", lastHigherLow)));
                }
            } else {
                double highValue = highs.get(index - size);
                swingHigh.lastLevel = swingHigh.currentLevel;
                swingHigh.currentLevel = highValue;
                swingHigh.crossed = false;
                swingHigh.barTime = times.get(index - size);
                swingHigh.barIndex = index - size;
                swingHigh.oppositeBarTime = swingLow.barTime;

                trailing.top = highValue;
                trailing.barTime = swingHigh.barTime;
                trailing.barIndex = swingHigh.barIndex;
                trailing.lastTopTime = swingHigh.barTime;

                if (!Double.isNaN(swingHigh.lastLevel) && swingHigh.currentLevel < swingHigh.lastLevel) {
                    lastLowerHigh = swingHigh.currentLevel;
                }
                String tag = swingHigh.currentLevel > swingHigh.lastLevel ? "HH" : "LH";
                swingLabels.add(new SwingLabel(swingHigh.barTime, swingHigh.currentLevel, tag));
                if (dbgEnabled) {
                    java.time.ZonedDateTime pivotDt = java.time.Instant.ofEpochMilli(swingHigh.barTime).atZone(java.time.ZoneId.of("Asia/Shanghai"));
                    System.out.println("[SMC SWING HIGH] idx=" + swingHigh.barIndex + " time=" + swingHigh.barTime + " pivotDt=" + pivotDt + " detIdx=" + index + " detDt=" + curDt + " price=" + String.format("%.2f", swingHigh.currentLevel) + " lastPrice=" + (Double.isNaN(swingHigh.lastLevel) ? "NaN" : String.format("%.2f", swingHigh.lastLevel)) + " tag=" + tag + " lastLowerHigh=" + (Double.isNaN(lastLowerHigh) ? "NaN" : String.format("%.2f", lastLowerHigh)));
                }
            }
        }
    }

    private int leg(int index, int size) {
        if (index < size) return currentLeg;
        int pivotIdx = index - size;
        int windowStart = pivotIdx + 1;
        int windowLen = size;
        if (windowStart >= highs.size()) return currentLeg;

        double pivotHigh = highs.get(pivotIdx);
        double pivotLow = lows.get(pivotIdx);
        double highest = highest(windowStart, windowLen);
        double lowest = lowest(windowStart, windowLen);

        int prevLeg = currentLeg;
        if (pivotHigh > highest) {
            currentLeg = 0;
        } else if (pivotLow < lowest) {
            currentLeg = 1;
        }

        // leg调试：打印每次leg评估的完整比较数据
        try {
          /*   long ts = times.get(pivotIdx);
             java.time.ZonedDateTime legDt = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.of("Asia/Shanghai"));
          if (legDt.getYear() == 2026 && legDt.getMonthValue() == 5 && legDt.getDayOfMonth() >= 23 && legDt.getHour() >= 15) {
                int rightEnd = Math.min(windowStart + windowLen, highs.size());
                System.out.println("[LEG] idx=" + index + " pivotIdx=" + pivotIdx + " pivotDt=" + legDt
                    + " pivotHigh=" + String.format("%.2f", pivotHigh)
                    + " highestRight(" + windowStart + ".." + (rightEnd-1) + ")=" + String.format("%.2f", highest)
                    + " pivotHigh>highest=" + (pivotHigh > highest)
                    + " pivotLow=" + String.format("%.2f", pivotLow)
                    + " lowestRight=" + String.format("%.2f", lowest)
                    + " pivotLow<lowest=" + (pivotLow < lowest)
                    + " prevLeg=" + prevLeg + " currentLeg=" + currentLeg
                    + " legChanged=" + (prevLeg != currentLeg));
            }*/
        } catch (Exception e) { /* ignore */ }

        return currentLeg;
    }

    private int swingLeg(int index, int size) {
        if (index < size) return swingLeg;
        int pivotIdx = index - size;
        int windowStart = pivotIdx + 1;
        int windowLen = size;
        if (windowStart >= highs.size()) return swingLeg;

        double pivotHigh = highs.get(pivotIdx);
        double pivotLow = lows.get(pivotIdx);
        double highest = highest(windowStart, windowLen);
        double lowest = lowest(windowStart, windowLen);

        if (pivotHigh > highest) {
            swingLeg = 0;
        } else if (pivotLow < lowest) {
            swingLeg = 1;
        }
        return swingLeg;
    }

    private double highest(int fromIdx, int length) {
        double max = Double.NEGATIVE_INFINITY;
        int end = Math.min(fromIdx + length, highs.size());
        for (int i = fromIdx; i < end; i++) {
            max = Math.max(max, highs.get(i));
        }
        return max;
    }

    private double lowest(int fromIdx, int length) {
        double min = Double.POSITIVE_INFINITY;
        int end = Math.min(fromIdx + length, lows.size());
        for (int i = fromIdx; i < end; i++) {
            min = Math.min(min, lows.get(i));
        }
        return min;
    }

    private void detectStructureBreaks(int index, Result result, boolean internal) {
        Pivot highPivot = internal ? internalHigh : swingHigh;
        Pivot lowPivot = internal ? internalLow : swingLow;
        Trend trend = internal ? internalTrend : swingTrend;
        double close = getBarSeries().getBar(index).getClosePrice().doubleValue();
        double prevClose = index > 0 ? getBarSeries().getBar(index - 1).getClosePrice().doubleValue() : close;

        // 方向性 K 线形态（全局持久变量，仅 internalFilterConfluence 启用时更新）
        if (config.isInternalFilterConfluence()) {
            Bar bar = getBarSeries().getBar(index);
            double open = bar.getOpenPrice().doubleValue();
            double high = bar.getHighPrice().doubleValue();
            double low = bar.getLowPrice().doubleValue();
            double closeBar = bar.getClosePrice().doubleValue();
            bullishBar = high - Math.max(closeBar, open) > Math.min(closeBar, open - low);
            bearishBar = high - Math.max(closeBar, open) < Math.min(closeBar, open - low);
        }

        // 向上突破高点
        if (!highPivot.crossed && !Double.isNaN(highPivot.currentLevel)) {
            // 内部结构需额外检查：高点与摆动高点不同 且 看涨 K 线
            boolean extraCondition = true;
            if (internal) {
                extraCondition = (highPivot.currentLevel != swingHigh.currentLevel) &&
                        (config.isInternalFilterConfluence() ? bullishBar : true);
            }
            if (extraCondition && prevClose <= highPivot.currentLevel && close > highPivot.currentLevel) {
                highPivot.crossed = true;
                boolean isChoCh = (trend.bias == -1);
                String sigType;
                if (isChoCh) {
                    if (internal) { result.setInternalBullishCHOCH(true); sigType = "internalBullishCHOCH"; }
                    else { result.setSwingBullishCHOCH(true); sigType = "swingBullishCHOCH"; }
                } else {
                    if (internal) { result.setInternalBullishBOS(true); sigType = "internalBullishBOS"; }
                    else { result.setSwingBullishBOS(true); sigType = "swingBullishBOS"; }
                }
                // 持久化事件类型
                if (internal) {
                    result.setLastInternalEventType(isChoCh ? 2 : 1);
                } else {
                    result.setLastSwingEventType(isChoCh ? 2 : 1);
                }
                long pivotTs = highPivot.barTime;
                result.getPivotTimestamps().put(sigType, pivotTs);
                result.getPivotLevels().put(sigType, highPivot.currentLevel);
                trend.bias = 1;

                if ((internal && config.isShowInternalOrderBlocks()) || (!internal && config.isShowSwingOrderBlocks())) {
                    storeOrderBlock(index, highPivot, internal, 1, result);
                }
            }
        }

        // 向下突破低点
        if (!lowPivot.crossed && !Double.isNaN(lowPivot.currentLevel)) {
            boolean extraCondition = true;
            if (internal) {
                extraCondition = (lowPivot.currentLevel != swingLow.currentLevel) &&
                        (config.isInternalFilterConfluence() ? bearishBar : true);
            }
            // BOS调试：打印每次低点突破条件评估
            if (!internal) {
                try {
                    long bt = times.get(index);
                    java.time.ZonedDateTime dbgDt = java.time.Instant.ofEpochMilli(bt).atZone(java.time.ZoneId.of("Asia/Shanghai"));
                   /* if (dbgDt.getYear() == 2026 && dbgDt.getMonthValue() == 5 && dbgDt.getDayOfMonth() >= 23 && dbgDt.getHour() >= 15) {
                        System.out.println("[BOS_BEAR] idx=" + index
                            + " dt=" + dbgDt
                            + " close=" + String.format("%.2f", close)
                            + " prevClose=" + String.format("%.2f", prevClose)
                            + " lowPivot=" + (Double.isNaN(lowPivot.currentLevel) ? "NaN" : String.format("%.2f", lowPivot.currentLevel))
                            + " lowPivot.barTime=" + lowPivot.barTime
                            + " lowPivot.crossed=" + lowPivot.crossed
                            + " trend.bias=" + trend.bias
                            + " extraCond=" + extraCondition
                            + " cond=" + (extraCondition && prevClose >= lowPivot.currentLevel && close < lowPivot.currentLevel));
                    }*/
                } catch (Exception e) { /* ignore */ }
            }
            if (extraCondition && prevClose >= lowPivot.currentLevel && close < lowPivot.currentLevel) {
                lowPivot.crossed = true;
                boolean isChoCh = (trend.bias == 1);
                String sigType;
                if (isChoCh) {
                    if (internal) { result.setInternalBearishCHOCH(true); sigType = "internalBearishCHOCH"; }
                    else { result.setSwingBearishCHOCH(true); sigType = "swingBearishCHOCH"; }
                } else {
                    if (internal) { result.setInternalBearishBOS(true); sigType = "internalBearishBOS"; }
                    else { result.setSwingBearishBOS(true); sigType = "swingBearishBOS"; }
                }
                // 持久化事件类型
                if (internal) {
                    result.setLastInternalEventType(isChoCh ? 2 : 1);
                } else {
                    result.setLastSwingEventType(isChoCh ? 2 : 1);
                }
                long pivotTs = lowPivot.barTime;
                result.getPivotTimestamps().put(sigType, pivotTs);
                result.getPivotLevels().put(sigType, lowPivot.currentLevel);
                trend.bias = -1;

                if ((internal && config.isShowInternalOrderBlocks()) || (!internal && config.isShowSwingOrderBlocks())) {
                    storeOrderBlock(index, lowPivot, internal, -1, result);
                }
            }
        }
    }

    private void storeOrderBlock(int index, Pivot pivot, boolean internal, int bias, Result result) {
        int startIdx = pivot.barIndex;
        int endIdx = index - 1;                  // 区间 [startIdx, endIdx]
        if (endIdx < startIdx) {
            endIdx = startIdx;
        }
        double extremeValue;
        int extremeIdx = startIdx;
        if (bias == -1) { // bearish: 取 parsedHighs 的最大值
            extremeValue = Double.NEGATIVE_INFINITY;
            for (int i = startIdx; i <= endIdx; i++) {
                double val = parsedHighs.get(i);
                if (val > extremeValue) {
                    extremeValue = val;
                    extremeIdx = i;
                }
            }
        } else { // bullish: 取 parsedLows 的最小值
            extremeValue = Double.POSITIVE_INFINITY;
            for (int i = startIdx; i <= endIdx; i++) {
                double val = parsedLows.get(i);
                if (val < extremeValue) {
                    extremeValue = val;
                    extremeIdx = i;
                }
            }
        }
        // ✅ 关键：订单块价格使用 parsedHighs/parsedLows，而非原始 highs/lows
        double blockHigh = parsedHighs.get(extremeIdx);
        double blockLow = parsedLows.get(extremeIdx);
        long blockTime = times.get(extremeIdx);
        OrderBlock ob = new OrderBlock(blockHigh, blockLow, blockTime, bias);
        List<OrderBlock> blocks = internal ? internalOrderBlocks : swingOrderBlocks;
        blocks.add(0, ob);
        while (blocks.size() > 100) {
            blocks.remove(blocks.size() - 1);
        }
    }

    private void deleteBrokenOrderBlocks(int index, boolean internal, Result result) {
        List<OrderBlock> blocks = internal ? internalOrderBlocks : swingOrderBlocks;
        if (blocks.isEmpty()) return;
        Bar bar = getBarSeries().getBar(index);
        double close = bar.getClosePrice().doubleValue();
        double high = bar.getHighPrice().doubleValue();
        double low = bar.getLowPrice().doubleValue();
        boolean useClose = "Close".equals(config.getOrderBlockMitigation());
        double bearishMitigation = useClose ? close : high;
        double bullishMitigation = useClose ? close : low;
        for (int i = 0; i < blocks.size(); i++) {
            OrderBlock ob = blocks.get(i);
            boolean broken = false;
            if (ob.bias == -1 && bearishMitigation > ob.barHigh) {
                broken = true;
                if (internal) result.setInternalBearishOrderBlockBreak(true);
                else result.setSwingBearishOrderBlockBreak(true);
            } else if (ob.bias == 1 && bullishMitigation < ob.barLow) {
                broken = true;
                if (internal) result.setInternalBullishOrderBlockBreak(true);
                else result.setSwingBullishOrderBlockBreak(true);
            }
            if (broken) blocks.remove(i);
        }
    }

    private void updateTrailingExtremes(int index, double high, double low, long timestamp) {
        if (Double.isNaN(trailing.top)) trailing.top = high;
        else trailing.top = Math.max(trailing.top, high);
        if (trailing.top == high) trailing.lastTopTime = timestamp;
        if (Double.isNaN(trailing.bottom)) trailing.bottom = low;
        else trailing.bottom = Math.min(trailing.bottom, low);
        if (trailing.bottom == low) trailing.lastBottomTime = timestamp;
    }

    private double computeVolatility(int index) {
        if (index == 0) return Double.NaN;
        if ("Atr".equals(config.getOrderBlockFilter())) {
            Num atr = atrIndicator.getValue(index);
            return atr != null && !atr.isNaN() ? atr.doubleValue() : 0.0;
        } else {
            Num total = runningTotalTr.getValue(index);
            double totalTr = total != null && !total.isNaN() ? total.doubleValue() : 0.0;
            return totalTr / index;
        }
    }

    // ------------------- EQH/EQL 检测 -------------------
    private void detectEqualHighLow(int index, Result result) {
        if (!config.isShowEqualHighsLows()) return;
        Num atrNum = atrIndicator.getValue(index);
        double atrValue = atrNum != null && !atrNum.isNaN() ? atrNum.doubleValue() : 0.0;
        double threshold = config.getEqualHighsLowsThreshold() * atrValue;

        // 检查等高点
        if (!Double.isNaN(equalHigh.lastLevel) && !Double.isNaN(equalHigh.currentLevel) && Math.abs(equalHigh.currentLevel - equalHigh.lastLevel) < threshold) {
            result.setEqualHighs(true);
        }
        // 检查等低点
        if (!Double.isNaN(equalLow.lastLevel) && !Double.isNaN(equalLow.currentLevel) && Math.abs(equalLow.currentLevel - equalLow.lastLevel) < threshold) {
            result.setEqualLows(true);
        }
    }

    // ------------------- FVG 检测与删除 -------------------
    private void detectFairValueGaps(int index, Result result) {
        if (index < 2) return;
        Bar bar0 = getBarSeries().getBar(index);
        Bar bar1 = getBarSeries().getBar(index - 1);
        Bar bar2 = getBarSeries().getBar(index - 2);
        double currentHigh = bar0.getHighPrice().doubleValue();
        double currentLow = bar0.getLowPrice().doubleValue();
        double close1 = bar1.getClosePrice().doubleValue();
        double open1 = bar1.getOpenPrice().doubleValue();
        double high2 = bar2.getHighPrice().doubleValue();
        double low2 = bar2.getLowPrice().doubleValue();

        double barDeltaPercent = open1 != 0.0 ? (close1 - open1) / (open1 * 100.0) : 0.0;
        boolean newTimeframe = true;
        double threshold = 0.0;
        if (config.isFairValueGapsAutoThreshold()) {
            if (cumulativeAbsDelta.size() > index) {
                threshold = cumulativeAbsDelta.get(index) / Math.max(1, index) * 2.0;
            }
        }

        boolean bullishFVG = currentLow > high2 && close1 > high2 && barDeltaPercent > threshold && newTimeframe;
        boolean bearishFVG = currentHigh < low2 && close1 < low2 && -barDeltaPercent > threshold && newTimeframe;

        if (bullishFVG) {
            result.setBullishFairValueGap(true);
            result.setLastBullishFVGTop(currentLow);
            result.setLastBullishFVGBottom(high2);
            fairValueGaps.add(new FairValueGap(currentLow, high2, 1, index, index));
        }
        if (bearishFVG) {
            result.setBearishFairValueGap(true);
            result.setLastBearishFVGTop(currentHigh);
            result.setLastBearishFVGBottom(low2);
            fairValueGaps.add(new FairValueGap(currentHigh, low2, -1, index, index));
        }
    }

    private void deleteFairValueGaps(int index, Result result) {
        Bar bar = getBarSeries().getBar(index);
        double low = bar.getLowPrice().doubleValue();
        double high = bar.getHighPrice().doubleValue();
        for (int i = fairValueGaps.size() - 1; i >= 0; i--) {
            FairValueGap fvg = fairValueGaps.get(i);
            if ((fvg.bias == 1 && low < fvg.bottom) || (fvg.bias == -1 && high > fvg.top)) {
                fairValueGaps.remove(i);
                if (fvg.bias == 1) result.setBullishFVGBroken(true);
                else result.setBearishFVGBroken(true);
            }
        }
    }

    // ------------------- 多时间框架水平 -------------------
    private void computeMultiTimeframeLevels(int index, Result result) {
        // 日线
        if (dailySeries != null && config.isShowDailyLevels()) {
            double dailyHigh = getPreviousPeriodHigh(dailySeries, getBarSeries().getBar(index).getEndTime());
            double dailyLow = getPreviousPeriodLow(dailySeries, getBarSeries().getBar(index).getEndTime());
            result.setDailyHigh(dailyHigh);
            result.setDailyLow(dailyLow);
        }
        // 周线
        if (weeklySeries != null && config.isShowWeeklyLevels()) {
            double weeklyHigh = getPreviousPeriodHigh(weeklySeries, getBarSeries().getBar(index).getEndTime());
            double weeklyLow = getPreviousPeriodLow(weeklySeries, getBarSeries().getBar(index).getEndTime());
            result.setWeeklyHigh(weeklyHigh);
            result.setWeeklyLow(weeklyLow);
        }
        // 月线
        if (monthlySeries != null && config.isShowMonthlyLevels()) {
            double monthlyHigh = getPreviousPeriodHigh(monthlySeries, getBarSeries().getBar(index).getEndTime());
            double monthlyLow = getPreviousPeriodLow(monthlySeries, getBarSeries().getBar(index).getEndTime());
            result.setMonthlyHigh(monthlyHigh);
            result.setMonthlyLow(monthlyLow);
        }
    }

    private double getPreviousPeriodHigh(BarSeries periodSeries, Instant currentTime) {
        for (int i = periodSeries.getBarCount() - 1; i >= 0; i--) {
            if (periodSeries.getBar(i).getEndTime().isBefore(currentTime)) {
                return periodSeries.getBar(i).getHighPrice().doubleValue();
            }
        }
        return Double.NaN;
    }

    private double getPreviousPeriodLow(BarSeries periodSeries, Instant currentTime) {
        for (int i = periodSeries.getBarCount() - 1; i >= 0; i--) {
            if (periodSeries.getBar(i).getEndTime().isBefore(currentTime)) {
                return periodSeries.getBar(i).getLowPrice().doubleValue();
            }
        }
        return Double.NaN;
    }

    // ------------------- 溢价/折扣区域 -------------------
    private void computePremiumDiscountZones(int index, Result result) {
        if (!config.isShowPremiumDiscountZones()) return;
        if (Double.isNaN(trailing.top) || Double.isNaN(trailing.bottom)) return;
        double high = trailing.top;
        double low = trailing.bottom;
        double premiumTop = high;
        double premiumBottom = 0.95 * high + 0.05 * low;
        double discountTop = 0.95 * low + 0.05 * high;
        double discountBottom = low;
        double equilibriumCenter = (high + low) / 2;
        double equilibriumTop = 0.525 * high + 0.475 * low;
        double equilibriumBottom = 0.525 * low + 0.475 * high;

        result.setPremiumZoneTop(premiumTop);
        result.setPremiumZoneBottom(premiumBottom);
        result.setDiscountZoneTop(discountTop);
        result.setDiscountZoneBottom(discountBottom);
        result.setEquilibriumZoneTop(equilibriumTop);
        result.setEquilibriumZoneBottom(equilibriumBottom);
        result.setEquilibriumCenter(equilibriumCenter);

        double close = getBarSeries().getBar(index).getClosePrice().doubleValue();
        if (close >= premiumBottom && close <= premiumTop) result.setCurrentZone("Premium");
        else if (close >= discountBottom && close <= discountTop) result.setCurrentZone("Discount");
        else if (close >= equilibriumBottom && close <= equilibriumTop) result.setCurrentZone("Equilibrium");
        else result.setCurrentZone("Neutral");
    }

    // ------------------- 内部类定义 -------------------

    public static class Config {
        private int swingsLength = 50;
        private boolean showInternalOrderBlocks = true;
        private boolean showSwingOrderBlocks = false;
        private int internalOrderBlocksCount = 5;
        private int swingOrderBlocksCount = 5;
        private String orderBlockFilter = "Atr"; // "Atr" or "Range"
        private String orderBlockMitigation = "High/Low"; // "Close" or "High/Low"
        private boolean internalFilterConfluence = false;
        private boolean showEqualHighsLows = true;
        private int equalHighsLowsLength = 3;
        private double equalHighsLowsThreshold = 0.1;
        private boolean showFairValueGaps = false;
        private boolean fairValueGapsAutoThreshold = true;
        private boolean showDailyLevels = false;
        private boolean showWeeklyLevels = false;
        private boolean showMonthlyLevels = false;
        private boolean showPremiumDiscountZones = false;

        // getters and setters
        public int getSwingsLength() { return swingsLength; }
        public void setSwingsLength(int swingsLength) { this.swingsLength = swingsLength; }
        public boolean isShowInternalOrderBlocks() { return showInternalOrderBlocks; }
        public void setShowInternalOrderBlocks(boolean showInternalOrderBlocks) { this.showInternalOrderBlocks = showInternalOrderBlocks; }
        public boolean isShowSwingOrderBlocks() { return showSwingOrderBlocks; }
        public void setShowSwingOrderBlocks(boolean showSwingOrderBlocks) { this.showSwingOrderBlocks = showSwingOrderBlocks; }
        public int getInternalOrderBlocksCount() { return internalOrderBlocksCount; }
        public void setInternalOrderBlocksCount(int internalOrderBlocksCount) { this.internalOrderBlocksCount = internalOrderBlocksCount; }
        public int getSwingOrderBlocksCount() { return swingOrderBlocksCount; }
        public void setSwingOrderBlocksCount(int swingOrderBlocksCount) { this.swingOrderBlocksCount = swingOrderBlocksCount; }
        public String getOrderBlockFilter() { return orderBlockFilter; }
        public void setOrderBlockFilter(String orderBlockFilter) { this.orderBlockFilter = orderBlockFilter; }
        public String getOrderBlockMitigation() { return orderBlockMitigation; }
        public void setOrderBlockMitigation(String orderBlockMitigation) { this.orderBlockMitigation = orderBlockMitigation; }
        public boolean isInternalFilterConfluence() { return internalFilterConfluence; }
        public void setInternalFilterConfluence(boolean internalFilterConfluence) { this.internalFilterConfluence = internalFilterConfluence; }
        public boolean isShowEqualHighsLows() { return showEqualHighsLows; }
        public void setShowEqualHighsLows(boolean showEqualHighsLows) { this.showEqualHighsLows = showEqualHighsLows; }
        public int getEqualHighsLowsLength() { return equalHighsLowsLength; }
        public void setEqualHighsLowsLength(int equalHighsLowsLength) { this.equalHighsLowsLength = equalHighsLowsLength; }
        public double getEqualHighsLowsThreshold() { return equalHighsLowsThreshold; }
        public void setEqualHighsLowsThreshold(double equalHighsLowsThreshold) { this.equalHighsLowsThreshold = equalHighsLowsThreshold; }
        public boolean isShowFairValueGaps() { return showFairValueGaps; }
        public void setShowFairValueGaps(boolean showFairValueGaps) { this.showFairValueGaps = showFairValueGaps; }
        public boolean isFairValueGapsAutoThreshold() { return fairValueGapsAutoThreshold; }
        public void setFairValueGapsAutoThreshold(boolean fairValueGapsAutoThreshold) { this.fairValueGapsAutoThreshold = fairValueGapsAutoThreshold; }
        public boolean isShowDailyLevels() { return showDailyLevels; }
        public void setShowDailyLevels(boolean showDailyLevels) { this.showDailyLevels = showDailyLevels; }
        public boolean isShowWeeklyLevels() { return showWeeklyLevels; }
        public void setShowWeeklyLevels(boolean showWeeklyLevels) { this.showWeeklyLevels = showWeeklyLevels; }
        public boolean isShowMonthlyLevels() { return showMonthlyLevels; }
        public void setShowMonthlyLevels(boolean showMonthlyLevels) { this.showMonthlyLevels = showMonthlyLevels; }
        public boolean isShowPremiumDiscountZones() { return showPremiumDiscountZones; }
        public void setShowPremiumDiscountZones(boolean showPremiumDiscountZones) { this.showPremiumDiscountZones = showPremiumDiscountZones; }
    }

    public static class Result {
        // 趋势
        private int internalTrend;
        private int swingTrend;

        // 结构信号
        private boolean internalBullishBOS;
        private boolean internalBearishBOS;
        private boolean internalBullishCHOCH;
        private boolean internalBearishCHOCH;
        private boolean swingBullishBOS;
        private boolean swingBearishBOS;
        private boolean swingBullishCHOCH;
        private boolean swingBearishCHOCH;

        // 订单块突破
        private boolean internalBullishOrderBlockBreak;
        private boolean internalBearishOrderBlockBreak;
        private boolean swingBullishOrderBlockBreak;
        private boolean swingBearishOrderBlockBreak;

        // EQH/EQL
        private boolean equalHighs;
        private boolean equalLows;

        // FVG
        private boolean bullishFairValueGap;
        private boolean bearishFairValueGap;
        private boolean bullishFVGBroken;
        private boolean bearishFVGBroken;
        private double lastBullishFVGTop = Double.NaN;
        private double lastBullishFVGBottom = Double.NaN;
        private double lastBearishFVGTop = Double.NaN;
        private double lastBearishFVGBottom = Double.NaN;

        // 多时间框架水平
        private double dailyHigh = Double.NaN;
        private double dailyLow = Double.NaN;
        private double weeklyHigh = Double.NaN;
        private double weeklyLow = Double.NaN;
        private double monthlyHigh = Double.NaN;
        private double monthlyLow = Double.NaN;

        // 溢价/折扣区域
        private double premiumZoneTop = Double.NaN;
        private double premiumZoneBottom = Double.NaN;
        private double discountZoneTop = Double.NaN;
        private double discountZoneBottom = Double.NaN;
        private double equilibriumZoneTop = Double.NaN;
        private double equilibriumZoneBottom = Double.NaN;
        private double equilibriumCenter = Double.NaN;
        private String currentZone = "Neutral";

        // 强弱高低点
        private double strongHigh = Double.NaN;
        private double weakHigh = Double.NaN;
        private double strongLow = Double.NaN;
        private double weakLow = Double.NaN;

        // 摆动点（不依赖趋势方向，直接从 Pivot 链赋值）
        private double lastSwingHigh = Double.NaN;
        private double lastSwingLow = Double.NaN;
        private double prevSwingHigh = Double.NaN;
        private double prevSwingLow = Double.NaN;

        // 结构信号（内部直接计算，外部无需推导）
        private double lastHigherLow = Double.NaN;
        private double lastLowerHigh = Double.NaN;

        // 波段高低点
        private double trailingHigh = Double.NaN;
        private double trailingLow = Double.NaN;
        private long trailingHighTime;
        private long trailingLowTime;

        // 蜡烛颜色（1=看涨，-1=看跌，0=中性）
        private int candleColor;

        // 订单块列表（供外部绘图或进一步分析）
        private List<OrderBlock> swingOrderBlocks = new ArrayList<>();
        private List<OrderBlock> internalOrderBlocks = new ArrayList<>();

        // BOS/CHOCH 信号对应的 pivot 信息
        private Map<String, Long> pivotTimestamps = new HashMap<>();
        private Map<String, Double> pivotLevels = new HashMap<>();

        /** 最近一次内部事件类型：0=无事件, 1=BOS, 2=CHoCH */
        private int lastInternalEventType;
        /** 最近一次摆动事件类型：0=无事件, 1=BOS, 2=CHoCH */
        private int lastSwingEventType;

        // getters and setters
        public int getInternalTrend() { return internalTrend; }
        public void setInternalTrend(int internalTrend) { this.internalTrend = internalTrend; }
        public int getSwingTrend() { return swingTrend; }
        public void setSwingTrend(int swingTrend) { this.swingTrend = swingTrend; }
        public boolean isInternalBullishBOS() { return internalBullishBOS; }
        public void setInternalBullishBOS(boolean internalBullishBOS) { this.internalBullishBOS = internalBullishBOS; }
        public boolean isInternalBearishBOS() { return internalBearishBOS; }
        public void setInternalBearishBOS(boolean internalBearishBOS) { this.internalBearishBOS = internalBearishBOS; }
        public boolean isInternalBullishCHOCH() { return internalBullishCHOCH; }
        public void setInternalBullishCHOCH(boolean internalBullishCHOCH) { this.internalBullishCHOCH = internalBullishCHOCH; }
        public boolean isInternalBearishCHOCH() { return internalBearishCHOCH; }
        public void setInternalBearishCHOCH(boolean internalBearishCHOCH) { this.internalBearishCHOCH = internalBearishCHOCH; }
        public boolean isSwingBullishBOS() { return swingBullishBOS; }
        public void setSwingBullishBOS(boolean swingBullishBOS) { this.swingBullishBOS = swingBullishBOS; }
        public boolean isSwingBearishBOS() { return swingBearishBOS; }
        public void setSwingBearishBOS(boolean swingBearishBOS) { this.swingBearishBOS = swingBearishBOS; }
        public boolean isSwingBullishCHOCH() { return swingBullishCHOCH; }
        public void setSwingBullishCHOCH(boolean swingBullishCHOCH) { this.swingBullishCHOCH = swingBullishCHOCH; }
        public boolean isSwingBearishCHOCH() { return swingBearishCHOCH; }
        public void setSwingBearishCHOCH(boolean swingBearishCHOCH) { this.swingBearishCHOCH = swingBearishCHOCH; }
        public boolean isInternalBullishOrderBlockBreak() { return internalBullishOrderBlockBreak; }
        public void setInternalBullishOrderBlockBreak(boolean internalBullishOrderBlockBreak) { this.internalBullishOrderBlockBreak = internalBullishOrderBlockBreak; }
        public boolean isInternalBearishOrderBlockBreak() { return internalBearishOrderBlockBreak; }
        public void setInternalBearishOrderBlockBreak(boolean internalBearishOrderBlockBreak) { this.internalBearishOrderBlockBreak = internalBearishOrderBlockBreak; }
        public boolean isSwingBullishOrderBlockBreak() { return swingBullishOrderBlockBreak; }
        public void setSwingBullishOrderBlockBreak(boolean swingBullishOrderBlockBreak) { this.swingBullishOrderBlockBreak = swingBullishOrderBlockBreak; }
        public boolean isSwingBearishOrderBlockBreak() { return swingBearishOrderBlockBreak; }
        public void setSwingBearishOrderBlockBreak(boolean swingBearishOrderBlockBreak) { this.swingBearishOrderBlockBreak = swingBearishOrderBlockBreak; }
        public boolean isEqualHighs() { return equalHighs; }
        public void setEqualHighs(boolean equalHighs) { this.equalHighs = equalHighs; }
        public boolean isEqualLows() { return equalLows; }
        public void setEqualLows(boolean equalLows) { this.equalLows = equalLows; }
        public boolean isBullishFairValueGap() { return bullishFairValueGap; }
        public void setBullishFairValueGap(boolean bullishFairValueGap) { this.bullishFairValueGap = bullishFairValueGap; }
        public boolean isBearishFairValueGap() { return bearishFairValueGap; }
        public void setBearishFairValueGap(boolean bearishFairValueGap) { this.bearishFairValueGap = bearishFairValueGap; }
        public boolean isBullishFVGBroken() { return bullishFVGBroken; }
        public void setBullishFVGBroken(boolean bullishFVGBroken) { this.bullishFVGBroken = bullishFVGBroken; }
        public boolean isBearishFVGBroken() { return bearishFVGBroken; }
        public void setBearishFVGBroken(boolean bearishFVGBroken) { this.bearishFVGBroken = bearishFVGBroken; }
        public double getLastBullishFVGTop() { return lastBullishFVGTop; }
        public void setLastBullishFVGTop(double lastBullishFVGTop) { this.lastBullishFVGTop = lastBullishFVGTop; }
        public double getLastBullishFVGBottom() { return lastBullishFVGBottom; }
        public void setLastBullishFVGBottom(double lastBullishFVGBottom) { this.lastBullishFVGBottom = lastBullishFVGBottom; }
        public double getLastBearishFVGTop() { return lastBearishFVGTop; }
        public void setLastBearishFVGTop(double lastBearishFVGTop) { this.lastBearishFVGTop = lastBearishFVGTop; }
        public double getLastBearishFVGBottom() { return lastBearishFVGBottom; }
        public void setLastBearishFVGBottom(double lastBearishFVGBottom) { this.lastBearishFVGBottom = lastBearishFVGBottom; }
        public double getDailyHigh() { return dailyHigh; }
        public void setDailyHigh(double dailyHigh) { this.dailyHigh = dailyHigh; }
        public double getDailyLow() { return dailyLow; }
        public void setDailyLow(double dailyLow) { this.dailyLow = dailyLow; }
        public double getWeeklyHigh() { return weeklyHigh; }
        public void setWeeklyHigh(double weeklyHigh) { this.weeklyHigh = weeklyHigh; }
        public double getWeeklyLow() { return weeklyLow; }
        public void setWeeklyLow(double weeklyLow) { this.weeklyLow = weeklyLow; }
        public double getMonthlyHigh() { return monthlyHigh; }
        public void setMonthlyHigh(double monthlyHigh) { this.monthlyHigh = monthlyHigh; }
        public double getMonthlyLow() { return monthlyLow; }
        public void setMonthlyLow(double monthlyLow) { this.monthlyLow = monthlyLow; }
        public double getPremiumZoneTop() { return premiumZoneTop; }
        public void setPremiumZoneTop(double premiumZoneTop) { this.premiumZoneTop = premiumZoneTop; }
        public double getPremiumZoneBottom() { return premiumZoneBottom; }
        public void setPremiumZoneBottom(double premiumZoneBottom) { this.premiumZoneBottom = premiumZoneBottom; }
        public double getDiscountZoneTop() { return discountZoneTop; }
        public void setDiscountZoneTop(double discountZoneTop) { this.discountZoneTop = discountZoneTop; }
        public double getDiscountZoneBottom() { return discountZoneBottom; }
        public void setDiscountZoneBottom(double discountZoneBottom) { this.discountZoneBottom = discountZoneBottom; }
        public double getEquilibriumZoneTop() { return equilibriumZoneTop; }
        public void setEquilibriumZoneTop(double equilibriumZoneTop) { this.equilibriumZoneTop = equilibriumZoneTop; }
        public double getEquilibriumZoneBottom() { return equilibriumZoneBottom; }
        public void setEquilibriumZoneBottom(double equilibriumZoneBottom) { this.equilibriumZoneBottom = equilibriumZoneBottom; }
        public double getEquilibriumCenter() { return equilibriumCenter; }
        public void setEquilibriumCenter(double equilibriumCenter) { this.equilibriumCenter = equilibriumCenter; }
        public String getCurrentZone() { return currentZone; }
        public void setCurrentZone(String currentZone) { this.currentZone = currentZone; }
        public double getStrongHigh() { return strongHigh; }
        public void setStrongHigh(double strongHigh) { this.strongHigh = strongHigh; }
        public double getWeakHigh() { return weakHigh; }
        public void setWeakHigh(double weakHigh) { this.weakHigh = weakHigh; }
        public double getStrongLow() { return strongLow; }
        public void setStrongLow(double strongLow) { this.strongLow = strongLow; }
        public double getWeakLow() { return weakLow; }
        public void setWeakLow(double weakLow) { this.weakLow = weakLow; }
        public double getLastSwingHigh() { return lastSwingHigh; }
        public void setLastSwingHigh(double v) { this.lastSwingHigh = v; }
        public double getLastSwingLow() { return lastSwingLow; }
        public void setLastSwingLow(double v) { this.lastSwingLow = v; }
        public double getPrevSwingHigh() { return prevSwingHigh; }
        public void setPrevSwingHigh(double v) { this.prevSwingHigh = v; }
        public double getPrevSwingLow() { return prevSwingLow; }
        public void setPrevSwingLow(double v) { this.prevSwingLow = v; }
        public double getLastHigherLow() { return lastHigherLow; }
        public void setLastHigherLow(double v) { this.lastHigherLow = v; }
        public double getLastLowerHigh() { return lastLowerHigh; }
        public void setLastLowerHigh(double v) { this.lastLowerHigh = v; }
        public double getTrailingHigh() { return trailingHigh; }
        public void setTrailingHigh(double trailingHigh) { this.trailingHigh = trailingHigh; }
        public double getTrailingLow() { return trailingLow; }
        public void setTrailingLow(double trailingLow) { this.trailingLow = trailingLow; }
        public long getTrailingHighTime() { return trailingHighTime; }
        public void setTrailingHighTime(long trailingHighTime) { this.trailingHighTime = trailingHighTime; }
        public long getTrailingLowTime() { return trailingLowTime; }
        public void setTrailingLowTime(long trailingLowTime) { this.trailingLowTime = trailingLowTime; }
        public int getCandleColor() { return candleColor; }
        public void setCandleColor(int candleColor) { this.candleColor = candleColor; }
        public Map<String, Long> getPivotTimestamps() { return pivotTimestamps; }
        public Map<String, Double> getPivotLevels() { return pivotLevels; }
        public List<OrderBlock> getSwingOrderBlocks() { return swingOrderBlocks; }
        public void setSwingOrderBlocks(List<OrderBlock> swingOrderBlocks) { this.swingOrderBlocks = swingOrderBlocks; }
        public List<OrderBlock> getInternalOrderBlocks() { return internalOrderBlocks; }
        public void setInternalOrderBlocks(List<OrderBlock> internalOrderBlocks) { this.internalOrderBlocks = internalOrderBlocks; }
        public int getLastInternalEventType() { return lastInternalEventType; }
        public void setLastInternalEventType(int lastInternalEventType) { this.lastInternalEventType = lastInternalEventType; }
        public int getLastSwingEventType() { return lastSwingEventType; }
        public void setLastSwingEventType(int lastSwingEventType) { this.lastSwingEventType = lastSwingEventType; }
    }

    // 内部数据结构
    private static class Pivot {
        double currentLevel;
        double lastLevel;
        boolean crossed;
        long barTime;
        int barIndex;
        long oppositeBarTime;
        Pivot(double currentLevel, double lastLevel, boolean crossed, long barTime, int barIndex) {
            this.currentLevel = currentLevel;
            this.lastLevel = lastLevel;
            this.crossed = crossed;
            this.barTime = barTime;
            this.barIndex = barIndex;
        }
    }

    private static class Trend {
        int bias;
        Trend(int bias) { this.bias = bias; }
    }

    private static class TrailingExtremes {
        double top = Double.NaN;
        double bottom = Double.NaN;
        long barTime;
        int barIndex;
        long lastTopTime;
        long lastBottomTime;
    }

    private static class FairValueGap {
        double top, bottom;
        int bias; // 1 bullish, -1 bearish
        int startIdx, endIdx;
        FairValueGap(double top, double bottom, int bias, int startIdx, int endIdx) {
            this.top = top;
            this.bottom = bottom;
            this.bias = bias;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
        }
    }

    public static class OrderBlock {
        public final double barHigh;
        public final double barLow;
        public final long barTime;
        public final int bias; //1为需求区，-1为供给区
        public OrderBlock(double barHigh, double barLow, long barTime, int bias) {
            this.barHigh = barHigh;
            this.barLow = barLow;
            this.barTime = barTime;
            this.bias = bias;
        }
    }

    public static class SwingLabel {
        public final long barTime;
        public final double price;
        public final String label;
        public SwingLabel(long barTime, double price, String label) {
            this.barTime = barTime;
            this.price = price;
            this.label = label;
        }
    }

    public List<SwingLabel> getSwingLabels() {
        return new ArrayList<>(swingLabels);
    }
}
