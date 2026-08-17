package com.chain.ai.trade.extension.ta4j.indicator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 最终修复版Range Filter - 精确匹配TradingView (带时间戳的方向记录)
 */
public class ExactRangeFilterDWIndicator extends CachedIndicator<Num> {

    private static final Logger log = LoggerFactory.getLogger(ExactRangeFilterDWIndicator.class);

    private final HighPriceIndicator high;
    private final LowPriceIndicator low;
    private final ClosePriceIndicator close;

    private final RangeScale rangeScale;
    private final FilterType filterType;
    private final MovementSource movementSource;
    private final Num rangeQuantity;
    private final int rangePeriod;
    private final boolean smoothRange;
    private final int smoothPeriod;
    private final boolean averageFilterChanges;
    private final int averageSamples;

    // 状态存储
    private List<Num> filterValues;
    private List<Num> smoothedRanges;
    private List<Num> averageChangeEMAs;
    private List<Num> typicalPrices;
    private List<Integer> directions;
    private List<ZonedDateTime> directionTimestamps; // 新增：方向变化时间戳
    private List<Num> atrValues;

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    // 新增：方向变化记录类
    public static class DirectionChange {
        private final int index;
        private final ZonedDateTime timestamp;
        private final int direction;
        private final Num filterValue;
        private final Num typicalPrice;

        public DirectionChange(int index, ZonedDateTime timestamp, int direction,
                               Num filterValue, Num typicalPrice) {
            this.index = index;
            this.timestamp = timestamp;
            this.direction = direction;
            this.filterValue = filterValue;
            this.typicalPrice = typicalPrice;
        }

        // Getters
        public int getIndex() { return index; }
        public ZonedDateTime getTimestamp() { return timestamp; }
        public int getDirection() { return direction; }
        public Num getFilterValue() { return filterValue; }
        public Num getTypicalPrice() { return typicalPrice; }

        @Override
        public String toString() {
            return String.format("DirectionChange{index=%d, time=%s, direction=%d, filter=%.4f, price=%.4f}",
                    index, timestamp, direction, filterValue.doubleValue(), typicalPrice.doubleValue());
        }
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

    public ExactRangeFilterDWIndicator(BarSeries series, RangeScale rangeScale, FilterType filterType,
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

        // 初始化列表
        initializeLists(series.getBarCount());


    }

    private void initializeLists(int size) {
        this.filterValues = new ArrayList<>(size);
        this.smoothedRanges = new ArrayList<>(size);
        this.averageChangeEMAs = new ArrayList<>(size);
        this.typicalPrices = new ArrayList<>(size);
        this.directions = new ArrayList<>(size);
        this.directionTimestamps = new ArrayList<>(size); // 初始化时间戳列表
        this.atrValues = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            filterValues.add(null);
            smoothedRanges.add(null);
            averageChangeEMAs.add(null);
            typicalPrices.add(null);
            directions.add(0);
            directionTimestamps.add(null);
            atrValues.add(null);
        }
    }

    @Override
    protected Num calculate(int index) {
        if (index < 0 || index >= getBarSeries().getBarCount()) {
            return getBarSeries().numFactory().numOf(0);
        }

        if (filterValues.get(index) != null) {
            return filterValues.get(index);
        }

        if (index > 0 && filterValues.get(index - 1) == null) {
            calculate(index - 1);
        }

        // 计算典型价格
        Num typicalPrice = calculateTypicalPrice(index);
        typicalPrices.set(index, roundToTwoDecimals(typicalPrice));

        // 计算范围大小
        Num rangeSize = calculateRangeSize(index, typicalPrice);

        // 计算平滑范围
        Num r = calculateSmoothedRange(rangeSize, index);
        smoothedRanges.set(index, r);

        // 获取高值和低值
        Num hVal, lVal;
        if (movementSource == MovementSource.CLOSE) {
            hVal = roundToTwoDecimals(close.getValue(index));
            lVal = hVal;
        } else {
            hVal = roundToTwoDecimals(high.getValue(index));
            lVal = roundToTwoDecimals(low.getValue(index));
        }

        // 计算过滤器
        Num filter = calculateRangeFilter(hVal, lVal, r, index);

        if (averageFilterChanges) {
            filter = applyConditionalEMA(filter, index);
        }

        // 计算方向（带时间戳）
        int direction = calculateDirectionWithTimestamp(filter, index);

        // 保存结果
        filterValues.set(index, roundToTwoDecimals(filter));
        directions.set(index, direction);

        return filterValues.get(index);
    }

    /**
     * 计算方向并记录时间戳
     */
    private int calculateDirectionWithTimestamp(Num currentFilter, int index) {
        if (index == 0) {
            // 记录初始方向的时间戳
            directionTimestamps.set(index, ZonedDateTime.ofInstant(getBarSeries().getBar(index).getEndTime(), ZoneId.systemDefault()));
            return 0;
        }

        if (filterValues.get(index - 1) == null) {
            calculate(index - 1);
        }

        Num prevFilter = filterValues.get(index - 1);
        int prevDirection = directions.get(index - 1);
        int newDirection = prevDirection;

        if (currentFilter.isGreaterThan(prevFilter)) {
            newDirection = 1;
        } else if (currentFilter.isLessThan(prevFilter)) {
            newDirection = -1;
        }

        // 如果方向发生变化，记录时间戳
        if (newDirection != prevDirection) {
            directionTimestamps.set(index, ZonedDateTime.ofInstant(getBarSeries().getBar(index).getEndTime(), ZoneId.systemDefault()));
            log.debug("方向变化: 索引 {} -> 时间 {}, 方向 {} -> {}",
                    index, getBarSeries().getBar(index).getEndTime(), prevDirection, newDirection);
        } else {
            // 方向未变化，复制前一个时间戳
            directionTimestamps.set(index, directionTimestamps.get(index - 1));
        }

        return newDirection;
    }

    /**
     * 获取带时间戳的方向信息
     */
    public DirectionChange getDirectionWithTimestamp(int index) {
        if (index < 0 || index >= directions.size()) {
            return null;
        }
        Bar bar = getBarSeries().getBar(index);
        return new DirectionChange(
                index,
                ZonedDateTime.ofInstant(bar.getEndTime(), ZoneId.systemDefault()),
                directions.get(index),
                filterValues.get(index),
                typicalPrices.get(index)
        );
    }

    /**
     * 获取最近的方向变化记录
     */
    public List<DirectionChange> getRecentDirectionChanges(int lookbackPeriod) {
        List<DirectionChange> changes = new ArrayList<>();
        int startIndex = Math.max(0, directions.size() - lookbackPeriod);

        int lastDirection = 0;
        for (int i = startIndex; i < directions.size(); i++) {
            int currentDirection = directions.get(i);
            if (i == startIndex || currentDirection != lastDirection) {
                changes.add(getDirectionWithTimestamp(i));
                lastDirection = currentDirection;
            }
        }
        return changes;
    }

    /**
     * 获取所有方向变化点（过滤掉连续相同方向）
     */
    public List<DirectionChange> getAllDirectionChanges() {
        List<DirectionChange> changes = new ArrayList<>();
        if (directions.isEmpty()) return changes;

        changes.add(getDirectionWithTimestamp(0));
        int lastDirection = directions.get(0);

        for (int i = 1; i < directions.size(); i++) {
            int currentDirection = directions.get(i);
            if (currentDirection != lastDirection) {
                changes.add(getDirectionWithTimestamp(i));
                lastDirection = currentDirection;
            }
        }
        return changes;
    }

    /**
     * 获取指定时间段内的方向变化
     */
    public List<DirectionChange> getDirectionChangesInPeriod(ZonedDateTime startTime, ZonedDateTime endTime) {
        List<DirectionChange> changes = new ArrayList<>();
        for (int i = 0; i < directions.size(); i++) {
            Bar bar = getBarSeries().getBar(i);
            ZonedDateTime barTime = ZonedDateTime.ofInstant(bar.getEndTime(), ZoneId.systemDefault());

            if (!barTime.isBefore(startTime) && !barTime.isAfter(endTime)) {
                // 检查是否是方向变化点
                if (i == 0 || directions.get(i) != directions.get(i - 1)) {
                    changes.add(getDirectionWithTimestamp(i));
                }
            }
        }
        return changes;
    }

    // ========== 原有的其他方法保持不变 ==========

    private Num roundToTwoDecimals(Num value) {
        if (value == null) return getBarSeries().numFactory().numOf(0);
        try {
            double doubleValue = value.doubleValue();
            double roundedValue = Math.round(doubleValue * 100.0) / 100.0;
            return getBarSeries().numFactory().numOf(roundedValue);
        } catch (Exception e) {
            log.warn("精度处理失败: {}, 使用原值", value);
            return value;
        }
    }

    private Num calculateTypicalPrice(int index) {
        if (movementSource == MovementSource.CLOSE) {
            return roundToTwoDecimals(close.getValue(index));
        } else {
            Num highVal = roundToTwoDecimals(high.getValue(index));
            Num lowVal = roundToTwoDecimals(low.getValue(index));
            return highVal.plus(lowVal).dividedBy(getBarSeries().numFactory().numOf(2));
        }
    }

    private Num calculateRangeSize(int index, Num typicalPrice) {
        switch (rangeScale) {
            case AVERAGE_CHANGE:
                return calculateAverageChangeRange(typicalPrice, index);
            case ATR:
                return rangeQuantity.multipliedBy(calculateATR(index));
            case STANDARD_DEVIATION:
                return rangeQuantity.multipliedBy(calculateStandardDeviation(index));
            case PERCENT:
                Num closePrice = roundToTwoDecimals(close.getValue(index));
                return closePrice.multipliedBy(rangeQuantity).dividedBy(getBarSeries().numFactory().numOf(100));
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

    private Num calculateAverageChangeRange(Num typicalPrice, int index) {
        if (index == 0) {
            averageChangeEMAs.set(index, getBarSeries().numFactory().numOf(0));
            return rangeQuantity.multipliedBy(getBarSeries().numFactory().numOf(0));
        }

        if (typicalPrices.get(index - 1) == null) {
            calculate(index - 1);
        }

        Num prevTypicalPrice = typicalPrices.get(index - 1);
        Num absChange = typicalPrice.minus(prevTypicalPrice).abs();

        Num currentEMA;
        if (index == 1) {
            currentEMA = absChange;
        } else {
            if (averageChangeEMAs.get(index - 1) == null) {
                calculate(index - 1);
            }
            Num prevEMA = averageChangeEMAs.get(index - 1);
            Num alpha = getBarSeries().numFactory().numOf(2).dividedBy(getBarSeries().numFactory().numOf(rangePeriod + 1));
            currentEMA = absChange.minus(prevEMA)
                    .multipliedBy(alpha)
                    .plus(prevEMA);
        }

        averageChangeEMAs.set(index, currentEMA);
        return rangeQuantity.multipliedBy(currentEMA);
    }

    private Num calculateATR(int index) {
        if (index == 0) {
            Num highVal = roundToTwoDecimals(high.getValue(index));
            Num lowVal = roundToTwoDecimals(low.getValue(index));
            Num tr = highVal.minus(lowVal);
            atrValues.set(index, tr);
            return tr;
        }

        Num trueRange = calculateTrueRange(index);

        if (index == 1) {
            atrValues.set(index, trueRange);
            return trueRange;
        }

        if (atrValues.get(index - 1) == null) {
            calculate(index - 1);
        }

        Num prevATR = atrValues.get(index - 1);
        Num alpha = getBarSeries().numFactory().numOf(2).dividedBy(getBarSeries().numFactory().numOf(rangePeriod + 1));
        Num atr = trueRange.minus(prevATR)
                .multipliedBy(alpha)
                .plus(prevATR);

        atrValues.set(index, atr);
        return atr;
    }

    private Num calculateTrueRange(int index) {
        Num highVal = roundToTwoDecimals(high.getValue(index));
        Num lowVal = roundToTwoDecimals(low.getValue(index));

        if (index > 0 && filterValues.get(index - 1) == null) {
            calculate(index - 1);
        }

        Num prevClose = roundToTwoDecimals(close.getValue(index - 1));

        Num hl = highVal.minus(lowVal);
        Num hc = highVal.minus(prevClose).abs();
        Num lc = lowVal.minus(prevClose).abs();

        return hl.max(hc.max(lc));
    }

    private Num calculateStandardDeviation(int index) {
        int startIndex = Math.max(0, index - rangePeriod + 1);
        int period = index - startIndex + 1;

        if (period < 2) {
            return getBarSeries().numFactory().numOf(0);
        }

        for (int i = startIndex; i <= index; i++) {
            if (typicalPrices.get(i) == null) {
                calculate(i);
            }
        }

        Num sum = getBarSeries().numFactory().numOf(0);
        for (int i = startIndex; i <= index; i++) {
            sum = sum.plus(typicalPrices.get(i));
        }
        Num mean = sum.dividedBy(getBarSeries().numFactory().numOf(period));

        Num sumSquares = getBarSeries().numFactory().numOf(0);
        for (int i = startIndex; i <= index; i++) {
            Num diff = typicalPrices.get(i).minus(mean);
            sumSquares = sumSquares.plus(diff.multipliedBy(diff));
        }

        Num variance = sumSquares.dividedBy(getBarSeries().numFactory().numOf(period - 1));
        return variance.sqrt();
    }

    private Num calculateSmoothedRange(Num rangeSize, int index) {
        if (!smoothRange) {
            return rangeSize;
        }

        if (index == 0) {
            return rangeSize;
        }

        if (smoothedRanges.get(index - 1) == null) {
            calculate(index - 1);
        }

        Num prevSmoothed = smoothedRanges.get(index - 1);
        Num alpha = getBarSeries().numFactory().numOf(2).dividedBy(getBarSeries().numFactory().numOf(smoothPeriod + 1));

        return rangeSize.minus(prevSmoothed)
                .multipliedBy(alpha)
                .plus(prevSmoothed);
    }

    private Num calculateRangeFilter(Num h, Num l, Num r, int index) {
        if (index == 0) {
            return roundToTwoDecimals(close.getValue(index));
        }

        if (filterValues.get(index - 1) == null) {
            calculate(index - 1);
        }

        Num prevFilter = filterValues.get(index - 1);

        if (filterType == FilterType.TYPE1) {
            Num upperBand = h.minus(r);
            Num lowerBand = l.plus(r);

            if (upperBand.isGreaterThan(prevFilter)) {
                return upperBand;
            } else if (lowerBand.isLessThan(prevFilter)) {
                return lowerBand;
            } else {
                return prevFilter;
            }
        } else {
            Num upperThreshold = prevFilter.plus(r);
            Num lowerThreshold = prevFilter.minus(r);

            if (h.isGreaterThanOrEqual(upperThreshold)) {
                Num diff = h.minus(prevFilter);
                Num steps = diff.dividedBy(r).floor();
                return prevFilter.plus(steps.multipliedBy(r));
            } else if (l.isLessThanOrEqual(lowerThreshold)) {
                Num diff = prevFilter.minus(l);
                Num steps = diff.dividedBy(r).floor();
                return prevFilter.minus(steps.multipliedBy(r));
            } else {
                return prevFilter;
            }
        }
    }

    private Num applyConditionalEMA(Num currentValue, int index) {
        if (!averageFilterChanges || index == 0) {
            return currentValue;
        }

        if (filterValues.get(index - 1) == null) {
            calculate(index - 1);
        }

        boolean conditionChanged = !currentValue.equals(filterValues.get(index - 1));

        if (!conditionChanged) {
            return filterValues.get(index - 1);
        }

        Num prevValue = filterValues.get(index - 1);
        Num alpha = getBarSeries().numFactory().numOf(2).dividedBy(getBarSeries().numFactory().numOf(averageSamples + 1));

        return currentValue.minus(prevValue)
                .multipliedBy(alpha)
                .plus(prevValue);
    }

    // ========== 公共Getter方法 ==========

    public Num getHighBand(int index) {
        if (index < 0 || index >= filterValues.size() || filterValues.get(index) == null) {
            return getBarSeries().numFactory().numOf(0);
        }
        Num filter = getValue(index);
        Num r = smoothedRanges.get(index);
        return filter.plus(r);
    }

    public Num getLowBand(int index) {
        if (index < 0 || index >= filterValues.size() || filterValues.get(index) == null) {
            return getBarSeries().numFactory().numOf(0);
        }
        Num filter = getValue(index);
        Num r = smoothedRanges.get(index);
        return filter.minus(r);
    }

    public int getDirection(int index) {
        if (index < 0 || index >= directions.size()) {
            return 0;
        }
        return directions.get(index);
    }

    public ZonedDateTime getDirectionTimestamp(int index) {
        if (index < 0 || index >= directionTimestamps.size()) {
            return null;
        }
        return directionTimestamps.get(index);
    }

    public Num getTypicalPrice(int index) {
        if (index < 0 || index >= typicalPrices.size() || typicalPrices.get(index) == null) {
            return getBarSeries().numFactory().numOf(0);
        }
        return typicalPrices.get(index);
    }

    public Num getSmoothedRange(int index) {
        if (index < 0 || index >= smoothedRanges.size() || smoothedRanges.get(index) == null) {
            return getBarSeries().numFactory().numOf(0);
        }
        return smoothedRanges.get(index);
    }

    public int getUnstableBars() {
        return Math.max(rangePeriod, smoothPeriod) + 5;
    }
}