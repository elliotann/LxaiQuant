package com.chain.ai.trade.extension.ta4j.indicator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class RangeFilterDWIndicator extends CachedIndicator<Num> {

    private static final Logger log = LoggerFactory.getLogger(RangeFilterDWIndicator.class);

    private final Indicator<Num> high;
    private final Indicator<Num> low;
    private final Indicator<Num> close;
    private final RangeScale rangeScale;
    private final FilterType filterType;
    private final MovementSource movementSource;
    private final int rangePeriod;
    private final int smoothPeriod;
    private final boolean smoothRange;
    private final boolean averageFilterChanges;
    private final int averageSamples;
    private final Num rangeQuantity;

    // State variables - 数组式管理
    private Num[] prevFilters;
    private Num[] prevTypicalPrices;
    private Num[] prevAverageChangeEMAs;
    private Num[] prevSmoothedRanges;
    private int[] directions;
    private Num[] condEMAValues;
    private int seriesSize;



    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    @Override
    public Stream<Num> stream() {
        return super.stream();
    }

    public enum RangeScale {
        POINTS, PIPS, TICKS, PERCENT, ATR, AVERAGE_CHANGE, STANDARD_DEVIATION, ABSOLUTE
    }

    public enum FilterType {
        TYPE1, TYPE2
    }

    public enum MovementSource {
        WICKS, CLOSE
    }

    public RangeFilterDWIndicator(BarSeries series, RangeScale rangeScale, FilterType filterType,
                                  MovementSource movementSource, Num rangeQuantity, int rangePeriod,
                                  boolean smoothRange, int smoothPeriod, boolean averageFilterChanges,
                                  int averageSamples) {
        super(series);

        this.high = new HighPriceIndicator(series);
        this.low = new LowPriceIndicator(series);
        this.close = new ClosePriceIndicator(series);
        this.rangeScale = rangeScale;
        this.filterType = filterType;
        this.movementSource = movementSource;
        this.rangeQuantity = rangeQuantity;
        this.rangePeriod = rangePeriod;
        this.smoothRange = smoothRange;
        this.smoothPeriod = smoothPeriod;
        this.averageFilterChanges = averageFilterChanges;
        this.averageSamples = averageSamples;

        // 初始化状态数组
        this.seriesSize = series.getBarCount();
        initializeStateArrays();

        log.debug("RangeFilterDWIndicator初始化 - 移动源: {}, 范围数量: {}, 范围周期: {}, 平滑周期: {}",
                movementSource, rangeQuantity, rangePeriod, smoothPeriod);
    }

    /**
     * 初始化状态数组
     */
    private void initializeStateArrays() {
        this.prevFilters = new Num[seriesSize];
        this.prevTypicalPrices = new Num[seriesSize];
        this.prevAverageChangeEMAs = new Num[seriesSize];
        this.prevSmoothedRanges = new Num[seriesSize];
        this.directions = new int[seriesSize];
        this.condEMAValues = new Num[seriesSize];

        // 初始化为零值
        Num zero = getBarSeries().numFactory().numOf(0);
        for (int i = 0; i < seriesSize; i++) {
            prevFilters[i] = zero;
            prevTypicalPrices[i] = zero;
            prevAverageChangeEMAs[i] = zero;
            prevSmoothedRanges[i] = zero;
            directions[i] = 0;
            condEMAValues[i] = zero;
        }
    }

    @Override
    protected Num calculate(int index) {
        if (index < 0 || index >= seriesSize) {
            log.warn("索引 {} 超出范围 [0, {}]，返回零值", index, seriesSize - 1);
            return getBarSeries().numFactory().numOf(0);
        }

        // 如果已经计算过，直接返回
        if (prevFilters[index] != null && !prevFilters[index].equals(getBarSeries().numFactory().numOf(0)) && index > 0) {
            return prevFilters[index];
        }

        if (index == 0) {
            // 初始值计算
            Num typicalPrice = calculateTypicalPrice(index);
            prevTypicalPrices[index] = typicalPrice;

            // 初始范围大小
            Num initialRange = calculateRangeSize(index, typicalPrice);
            Num initialFilter = typicalPrice;

            // 初始平滑范围
            prevSmoothedRanges[index] = initialRange;
            prevFilters[index] = initialFilter;
            prevAverageChangeEMAs[index] = getBarSeries().numFactory().numOf(0);
            directions[index] = 0;
            condEMAValues[index] = initialFilter;

            log.debug("初始化 - 索引: {}, 典型价格: {}, 初始过滤器: {}, 移动源: {}",
                    index, typicalPrice, initialFilter, movementSource);
            return initialFilter;
        }

        // 确保前一个索引已经计算
        if (prevFilters[index - 1] == null || prevFilters[index - 1].equals(getBarSeries().numFactory().numOf(0))) {
            // 递归计算前一个索引
            calculate(index - 1);
        }

        // 获取基于移动源的高值和低值
        HighLowValues hlValues = getHighLowValues(index);
        Num hVal = hlValues.high;
        Num lVal = hlValues.low;
        Num typicalPrice = calculateTypicalPrice(index);

        // 计算范围大小
        Num rangeSize = calculateRangeSize(index, typicalPrice);

        // 平滑范围
        Num r;
        if (smoothRange) {
            r = calculateSmoothedRange(rangeSize, index);
        } else {
            r = rangeSize;
            prevSmoothedRanges[index] = r;
        }

        // 计算过滤器
        Num currentFilter = calculateRangeFilter(hVal, lVal, r, index);

        // 应用条件EMA（如果启用）
        if (averageFilterChanges) {
            boolean filterChanged = !currentFilter.equals(prevFilters[index - 1]);
            currentFilter = conditionalEMA(currentFilter, filterChanged, index);
        }

        // 方向计算 - 严格按Pine Script逻辑
        int currentDirection;
        if (currentFilter.isGreaterThan(prevFilters[index - 1])) {
            currentDirection = 1;
        } else if (currentFilter.isLessThan(prevFilters[index - 1])) {
            currentDirection = -1;
        } else {
            // 保持前一个方向
            currentDirection = directions[index - 1];
        }

        // 更新状态
        directions[index] = currentDirection;
        prevFilters[index] = currentFilter;
        prevTypicalPrices[index] = typicalPrice;

        log.debug("过滤器计算 - 索引: {}, 移动源: {}, 高值: {}, 低值: {}, 范围: {}, 过滤器: {}, 方向: {}",
                index, movementSource, hVal, lVal, r, currentFilter, currentDirection);

        return currentFilter;
    }

    /**
     * 获取基于移动源的高值和低值
     */
    private HighLowValues getHighLowValues(int index) {
        if (movementSource == MovementSource.CLOSE) {
            // Close模式：高值和低值都使用收盘价
            Num closePrice = close.getValue(index);
            return new HighLowValues(closePrice, closePrice);
        } else {
            // Wicks模式：使用实际的最高价和最低价
            return new HighLowValues(high.getValue(index), low.getValue(index));
        }
    }

    /**
     * 高值和低值的容器类
     */
    private static class HighLowValues {
        final Num high;
        final Num low;

        HighLowValues(Num high, Num low) {
            this.high = high;
            this.low = low;
        }
    }

    /**
     * 修复的平滑范围计算
     */
    private Num calculateSmoothedRange(Num rangeSize, int index) {
        if (index == 0) {
            return rangeSize;
        }

        Num prevSmoothed = prevSmoothedRanges[index - 1];
        Num alpha = getBarSeries().numFactory().numOf(2).dividedBy(getBarSeries().numFactory().numOf(smoothPeriod + 1));
        Num smoothed = rangeSize.minus(prevSmoothed)
                .multipliedBy(alpha)
                .plus(prevSmoothed);

        prevSmoothedRanges[index] = smoothed;

        log.debug("平滑范围计算 - 索引: {}, 原始范围: {}, 前值: {}, 平滑后: {}",
                index, rangeSize, prevSmoothed, smoothed);

        return smoothed;
    }

    /**
     * 计算典型价格 (high + low) / 2
     */
    private Num calculateTypicalPrice(int index) {
        if (movementSource == MovementSource.CLOSE) {
            // Close模式：典型价格就是收盘价
            return close.getValue(index);
        } else {
            // Wicks模式：使用(high + low) / 2
            return high.getValue(index).plus(low.getValue(index)).dividedBy(getBarSeries().numFactory().numOf(2));
        }
    }

    /**
     * 修复的范围大小计算
     */
    private Num calculateRangeSize(int index, Num typicalPrice) {
        switch (rangeScale) {
            case AVERAGE_CHANGE:
                return calculateAverageChangeRange(typicalPrice, index);
            case ATR:
                return rangeQuantity.multipliedBy(calculateATR(index));
            case STANDARD_DEVIATION:
                Num stdev = calculateStandardDeviation(index);
                return rangeQuantity.multipliedBy(stdev);
            case PERCENT:
                return close.getValue(index).multipliedBy(rangeQuantity).dividedBy(getBarSeries().numFactory().numOf(100));
            case PIPS:
                return rangeQuantity.multipliedBy(getBarSeries().numFactory().numOf(0.0001));
            case POINTS:
                return rangeQuantity;
            case TICKS:
                return rangeQuantity.multipliedBy(getBarSeries().numFactory().numOf(0.01));
            case ABSOLUTE:
            default:
                return rangeQuantity;
        }
    }

    /**
     * 修复的平均变化计算
     */
    private Num calculateAverageChangeRange(Num typicalPrice, int index) {
        if (index == 0) {
            prevAverageChangeEMAs[index] = getBarSeries().numFactory().numOf(0);
            return rangeQuantity.multipliedBy(prevAverageChangeEMAs[index]);
        }

        // 计算绝对变化
        Num absChange = typicalPrice.minus(prevTypicalPrices[index - 1]).abs();

        // 使用EMA平滑绝对变化
        Num alpha = getBarSeries().numFactory().numOf(2).dividedBy(getBarSeries().numFactory().numOf(rangePeriod + 1));
        Num currentEMA;

        if (index == 1) {
            currentEMA = absChange;
        } else {
            currentEMA = absChange.minus(prevAverageChangeEMAs[index - 1])
                    .multipliedBy(alpha)
                    .plus(prevAverageChangeEMAs[index - 1]);
        }

        prevAverageChangeEMAs[index] = currentEMA;
        Num rangeSize = rangeQuantity.multipliedBy(currentEMA);

        log.debug("平均变化计算 - 索引: {}, 典型价格: {}, 绝对变化: {}, EMA: {}, 范围大小: {}",
                index, typicalPrice, absChange, currentEMA, rangeSize);

        return rangeSize;
    }

    /**
     * 修复的ATR计算
     */
    private Num calculateATR(int index) {
        int period = Math.min(rangePeriod, index + 1);
        Num sum = getBarSeries().numFactory().numOf(0);
        int count = 0;

        for (int i = 0; i < period; i++) {
            int currentIndex = index - i;
            if (currentIndex >= 0) {
                Num trueRange = calculateTrueRange(currentIndex);
                sum = sum.plus(trueRange);
                count++;
            }
        }

        return count > 0 ? sum.dividedBy(getBarSeries().numFactory().numOf(count)) : getBarSeries().numFactory().numOf(0);
    }

    private Num calculateTrueRange(int index) {
        if (index == 0) {
            return high.getValue(index).minus(low.getValue(index));
        }

        Num hl = high.getValue(index).minus(low.getValue(index));
        Num hc = high.getValue(index).minus(close.getValue(index - 1)).abs();
        Num lc = low.getValue(index).minus(close.getValue(index - 1)).abs();

        return hl.max(hc.max(lc));
    }

    private Num calculateStandardDeviation(int index) {
        int startIndex = Math.max(0, index - rangePeriod + 1);
        int endIndex = Math.min(index, seriesSize - 1);
        List<Num> values = new ArrayList<>();

        for (int i = startIndex; i <= endIndex; i++) {
            values.add(calculateTypicalPrice(i));
        }

        if (values.isEmpty()) {
            return getBarSeries().numFactory().numOf(0);
        }

        Num mean = values.stream().reduce(getBarSeries().numFactory().numOf(0), Num::plus).dividedBy(getBarSeries().numFactory().numOf(values.size()));

        Num variance = getBarSeries().numFactory().numOf(0);
        for (Num value : values) {
            Num diff = value.minus(mean);
            variance = variance.plus(diff.multipliedBy(diff));
        }
        variance = variance.dividedBy(getBarSeries().numFactory().numOf(values.size()));

        return variance.sqrt();
    }

    /**
     * 修复的过滤器计算
     */
    private Num calculateRangeFilter(Num h, Num l, Num r, int index) {
        Num prevFilter = index > 0 ? prevFilters[index - 1] : calculateTypicalPrice(index);
        Num currentFilter;

        if (filterType == FilterType.TYPE1) {
            // Type 1逻辑: h - r > prev then h - r, l + r < prev then l + r
            Num upperBand = h.minus(r);
            Num lowerBand = l.plus(r);

            if (upperBand.isGreaterThan(prevFilter)) {
                currentFilter = upperBand;
                log.debug("Type1 - 上破条件满足: {} > {}", upperBand, prevFilter);
            } else if (lowerBand.isLessThan(prevFilter)) {
                currentFilter = lowerBand;
                log.debug("Type1 - 下破条件满足: {} < {}", lowerBand, prevFilter);
            } else {
                currentFilter = prevFilter;
                log.debug("Type1 - 条件未满足, 保持: {}", prevFilter);
            }
        } else {
            // Type 2逻辑
            Num upperThreshold = prevFilter.plus(r);
            Num lowerThreshold = prevFilter.minus(r);

            if (h.isGreaterThanOrEqual(upperThreshold)) {
                Num diff = h.minus(prevFilter).abs();
                Num steps = diff.dividedBy(r).floor();
                currentFilter = prevFilter.plus(steps.multipliedBy(r));
                log.debug("Type2 - 上破: 步数={}, 新过滤器={}", steps, currentFilter);
            } else if (l.isLessThanOrEqual(lowerThreshold)) {
                Num diff = prevFilter.minus(l).abs();
                Num steps = diff.dividedBy(r).floor();
                currentFilter = prevFilter.minus(steps.multipliedBy(r));
                log.debug("Type2 - 下破: 步数={}, 新过滤器={}", steps, currentFilter);
            } else {
                currentFilter = prevFilter;
                log.debug("Type2 - 条件未满足, 保持: {}", prevFilter);
            }
        }

        return currentFilter;
    }

    private Num conditionalEMA(Num currentValue, boolean condition, int index) {
        if (index == 0) {
            condEMAValues[index] = currentValue;
            return currentValue;
        }

        Num prevEMA = condEMAValues[index - 1];

        if (condition) {
            Num alpha = getBarSeries().numFactory().numOf(2).dividedBy(getBarSeries().numFactory().numOf(averageSamples + 1));
            Num ema = currentValue.minus(prevEMA).multipliedBy(alpha).plus(prevEMA);
            condEMAValues[index] = ema;
            log.debug("条件EMA应用 - 条件满足, 新EMA: {}", ema);
            return ema;
        } else {
            condEMAValues[index] = prevEMA;
            log.debug("条件EMA应用 - 条件未满足, 保持: {}", prevEMA);
            return prevEMA;
        }
    }

    public int getDirection(int index) {
        if (index < 0 || index >= seriesSize) {
            return 0;
        }
        return directions[index];
    }

    public Num getHighBand(int index) {
        if (index < 0 || index >= seriesSize) {
            return getBarSeries().numFactory().numOf(0);
        }
        Num typicalPrice = calculateTypicalPrice(index);
        Num rangeSize = calculateRangeSize(index, typicalPrice);
        Num r = smoothRange ? prevSmoothedRanges[index] : rangeSize;
        return getValue(index).plus(r);
    }

    public Num getLowBand(int index) {
        if (index < 0 || index >= seriesSize) {
            return getBarSeries().numFactory().numOf(0);
        }
        Num typicalPrice = calculateTypicalPrice(index);
        Num rangeSize = calculateRangeSize(index, typicalPrice);
        Num r = smoothRange ? prevSmoothedRanges[index] : rangeSize;
        return getValue(index).minus(r);
    }

    /**
     * 获取移动源信息（用于调试）
     */
    public MovementSource getMovementSource() {
        return movementSource;
    }

    /**
     * 获取典型价格（用于调试）
     */
    public Num getTypicalPrice(int index) {
        if (index < 0 || index >= seriesSize) {
            return getBarSeries().numFactory().numOf(0);
        }
        return calculateTypicalPrice(index);
    }

    /**
     * 获取范围大小（用于调试）
     */
    public Num getRangeSize(int index) {
        if (index < 0 || index >= seriesSize) {
            return getBarSeries().numFactory().numOf(0);
        }
        Num typicalPrice = calculateTypicalPrice(index);
        return calculateRangeSize(index, typicalPrice);
    }

    /**
     * 获取平滑范围（用于调试）
     */
    public Num getSmoothedRange(int index) {
        if (index < 0 || index >= seriesSize) {
            return getBarSeries().numFactory().numOf(0);
        }
        return prevSmoothedRanges[index];
    }
}