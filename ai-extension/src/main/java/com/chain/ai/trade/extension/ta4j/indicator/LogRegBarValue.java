package com.chain.ai.trade.extension.ta4j.indicator;

/**
 * 对数回归通道指标在每个 Bar 上的计算结果。
 */
public class LogRegBarValue {

    // 原始回归值
    private final double endValue;      // 当前回归线终点（最新 bar 的回归值）
    private final double startValue;    // 回归线起点（最旧 bar 的回归值）
    private final double slope;         // 原始斜率
    private final double diff;          // end - end[diffPeriod]
    private final double end3Value;     // end[diffPeriod] 的值
    private final double deviation;     // 标准差（基于 close 价格）
    private final LogarithmicRegressionChannelIndicator.SignalType signal;
    private final boolean longTermTrendUp;   // 长期趋势方向（end > start）

    // 衍生属性（满足指标描述需求）
    private final double slopeScaled;        // 斜率 * 100
    private final double upperBand;          // 上轨 = end + deviation * channelWidth
    private final double lowerBand;          // 下轨 = end - deviation * channelWidth
    private final String shortTermTrend;     // 短期趋势（基于 diff 正负）: "UP" / "DOWN" / "FLAT"
    private final double regressionRange;    // 通道价格范围 = upperBand - lowerBand

    /**
     * 完整构造器（由指标内部调用）
     */
    public LogRegBarValue(double endValue, double startValue, double slope,
                          double diff, double end3Value, double deviation,
                          LogarithmicRegressionChannelIndicator.SignalType signal,
                          boolean longTermTrendUp,
                          double channelWidth) {
        this.endValue = endValue;
        this.startValue = startValue;
        this.slope = slope;
        this.diff = diff;
        this.end3Value = end3Value;
        this.deviation = deviation;
        this.signal = signal;
        this.longTermTrendUp = longTermTrendUp;

        // 计算衍生属性
        this.slopeScaled = slope * 100.0;
        this.upperBand = endValue + deviation * channelWidth;
        this.lowerBand = endValue - deviation * channelWidth;
        if (diff > 0) this.shortTermTrend = "UP";
        else if (diff < 0) this.shortTermTrend = "DOWN";
        else this.shortTermTrend = "FLAT";
        this.regressionRange = this.upperBand - this.lowerBand;
    }

    /**
     * 返回一个空的无效值（用于数据不足的 bar）
     */
    public static LogRegBarValue empty() {
        return new LogRegBarValue(
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN,
                LogarithmicRegressionChannelIndicator.SignalType.HOLD,
                false, 1.0
        );
    }

    // ---------- Getters ----------
    public double getEndValue() { return endValue; }
    public double getStartValue() { return startValue; }
    public double getSlope() { return slope; }
    public double getDiff() { return diff; }
    public double getEnd3Value() { return end3Value; }
    public double getDeviation() { return deviation; }
    public LogarithmicRegressionChannelIndicator.SignalType getSignal() { return signal; }
    public boolean isLongTermTrendUp() { return longTermTrendUp; }

    public double getSlopeScaled() { return slopeScaled; }
    public double getUpperBand() { return upperBand; }
    public double getLowerBand() { return lowerBand; }
    public String getShortTermTrend() { return shortTermTrend; }
    public double getRegressionRange() { return regressionRange; }

    // 便捷信号判断
    public boolean isBuySignal() { return signal == LogarithmicRegressionChannelIndicator.SignalType.BUY; }
    public boolean isSellSignal() { return signal == LogarithmicRegressionChannelIndicator.SignalType.SELL; }
    public boolean isValid() { return !Double.isNaN(endValue); }

    @Override
    public String toString() {
        return String.format(
                "LogRegBarValue{end=%.4f, slope=%.6f, diff=%.4f, shortTrend=%s, signal=%s, upper=%.4f, lower=%.4f}",
                endValue, slope, diff, shortTermTrend, signal, upperBand, lowerBand
        );
    }
}