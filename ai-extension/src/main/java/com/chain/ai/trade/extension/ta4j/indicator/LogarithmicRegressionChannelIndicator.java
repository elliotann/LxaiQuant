package com.chain.ai.trade.extension.ta4j.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import java.util.ArrayList;
import java.util.List;

/**
 * 对数回归通道趋势指标（对应 TradingView "Logarithmic Regression Channel-Trend [BigBeluga]"）
 * <p>
 * 提供：
 * <ul>
 *   <li>回归线值（end / start）</li>
 *   <li>斜率（原始值及乘以100后的值）</li>
 *   <li>diff = end - end[3]，以及穿越信号（BUY/SELL）</li>
 *   <li>标准差及通道上下轨（end ± deviation * channelWidth）</li>
 *   <li>短期趋势状态（基于diff的正负）</li>
 *   <li>回归带价格范围（上轨-下轨）</li>
 * </ul>
 */
public class LogarithmicRegressionChannelIndicator extends AbstractIndicator<LogRegBarValue> {

    private final int length;          // 回归计算窗口大小
    private final int diffPeriod;      // 差值比较周期（一般为3）
    private final double channelWidth; // 通道宽度乘数（标准差倍数）
    private final List<LogRegBarValue> values;

    /**
     * @param series      K线序列
     * @param length      对数回归计算窗口长度
     * @param diffPeriod  差值周期（例如 3 表示 end - end[3]）
     * @param channelWidth 通道宽度（标准差倍数，例如 1.5）
     */
    public LogarithmicRegressionChannelIndicator(BarSeries series, int length, int diffPeriod, double channelWidth) {
        super(series);
        this.length = length;
        this.diffPeriod = diffPeriod;
        this.channelWidth = channelWidth;
        this.values = new ArrayList<>(series.getBarCount());
        calculateAll();
    }

    // =================== 公共接口 ===================
    @Override
    public LogRegBarValue getValue(int index) {
        if (index < 0 || index >= values.size()) {
            return LogRegBarValue.empty();
        }
        return values.get(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    /**
     * @return 回归计算窗口长度
     */
    public int getLength() {
        return length;
    }

    /**
     * @return 差值周期
     */
    public int getDiffPeriod() {
        return diffPeriod;
    }

    /**
     * @return 通道宽度乘数
     */
    public double getChannelWidth() {
        return channelWidth;
    }

    // =================== 预计算核心逻辑 ===================
    private void calculateAll() {
        int barCount = getBarSeries().getBarCount();
        // 用于存放历史 end 值（最近的在末尾，便于索引 diffPeriod）
        List<Double> endHistory = new ArrayList<>(barCount);
        double prevDiff = 0.0;

        for (int i = 0; i < barCount; i++) {
            // 数据不足 length，填充空值
            if (i < length - 1) {
                values.add(LogRegBarValue.empty());
                endHistory.add(Double.NaN);
                continue;
            }

            // 1. 计算对数回归
            LogRegressionResult reg = calculateLogRegression(i);
            double currentEnd = reg.getEnd();
            double currentStart = reg.getStart();
            double currentSlope = reg.getSlope();
            boolean longTermUp = currentEnd > currentStart;

            // 2. 记录当前 end
            endHistory.add(currentEnd);

            // 3. 计算 diff = end - end[diffPeriod]
            double diff = 0.0;
            double end3 = Double.NaN;
            if (endHistory.size() > diffPeriod) {
                end3 = endHistory.get(endHistory.size() - 1 - diffPeriod);
                diff = currentEnd - end3;
            }

            // 4. 计算标准差（基于 close 价格）
            double deviation = calculateStandardDeviation(i);

            // 5. 穿越信号判断（基于 prevDiff 和 diff）
            SignalType signal = SignalType.HOLD;
            if (i >= length) { // 确保至少有一个前值
                if (prevDiff <= 0 && diff > 0) {
                    signal = SignalType.BUY;
                } else if (prevDiff >= 0 && diff < 0) {
                    signal = SignalType.SELL;
                }
            }
            prevDiff = diff;

            // 6. 构建结果对象
            LogRegBarValue value = new LogRegBarValue(
                    currentEnd, currentStart, currentSlope,
                    diff, end3, deviation,
                    signal, longTermUp,
                    channelWidth
            );
            values.add(value);
        }
    }

    /**
     * 计算指定索引处的对数回归（索引从0开始，必须 >= length-1）
     */
    private LogRegressionResult calculateLogRegression(int currentIndex) {
        double sumX = 0.0, sumY = 0.0, sumXSqr = 0.0, sumXY = 0.0;

        for (int i = 0; i < length; i++) {
            int barIndex = currentIndex - i;
            double price = getBarSeries().getBar(barIndex).getClosePrice().doubleValue();
            if (price <= 0) {
                throw new IllegalArgumentException("价格必须为正数才能计算对数: " + price);
            }
            double logVal = Math.log(price);
            double per = i + 1.0;       // 对应 Pine Script 中的 per = i + 1
            sumX += per;
            sumY += logVal;
            sumXSqr += per * per;
            sumXY += logVal * per;
        }

        double denominator = length * sumXSqr - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) {
            return new LogRegressionResult(0.0, 0.0, 0.0, 0.0);
        }
        double slope = (length * sumXY - sumX * sumY) / denominator;
        double average = sumY / length;
        double intercept = average - slope * sumX / length + slope;
        double start = Math.exp(intercept + slope * length);   // 最旧 bar 的回归值
        double end = Math.exp(intercept);                      // 最新 bar 的回归值
        return new LogRegressionResult(slope, intercept, start, end);
    }

    /**
     * 计算标准差（基于 close 价格，长度为 length）
     */
    private double calculateStandardDeviation(int currentIndex) {
        if (currentIndex < length - 1) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < length; i++) {
            int barIndex = currentIndex - i;
            sum += getBarSeries().getBar(barIndex).getClosePrice().doubleValue();
        }
        double mean = sum / length;
        double variance = 0.0;
        for (int i = 0; i < length; i++) {
            int barIndex = currentIndex - i;
            double price = getBarSeries().getBar(barIndex).getClosePrice().doubleValue();
            variance += Math.pow(price - mean, 2);
        }
        variance /= length;
        return Math.sqrt(variance);
    }

    // =================== 内部辅助类 ===================
    public enum SignalType {
        BUY, SELL, HOLD
    }

    private static class LogRegressionResult {
        final double slope;
        final double intercept;
        final double start;
        final double end;

        LogRegressionResult(double slope, double intercept, double start, double end) {
            this.slope = slope;
            this.intercept = intercept;
            this.start = start;
            this.end = end;
        }

        double getSlope() { return slope; }
        double getIntercept() { return intercept; }
        double getStart() { return start; }
        double getEnd() { return end; }
        boolean isTrendUp() { return end > start; }
    }
}