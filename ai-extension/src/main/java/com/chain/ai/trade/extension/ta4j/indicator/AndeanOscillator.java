package com.chain.ai.trade.extension.ta4j.indicator;


import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;

import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

public class AndeanOscillator {

    private final BarSeries series;
    private final int length;
    private final int signalLength;
    private final boolean earlySignalMode;

    // 指标
    private final Indicator<Num> osc;
    private final Indicator<Num> signal;
    private final Indicator<Num> bull;
    private final Indicator<Num> bear;
    private final Indicator<Num> oscStdev;
    private final Indicator<Num> plusLevel;
    private final Indicator<Num> minusLevel;

    public AndeanOscillator(BarSeries series, int length, int signalLength, boolean earlySignalMode) {
        this.series = series;
        this.length = length;
        this.signalLength = signalLength;
        this.earlySignalMode = earlySignalMode;

        // 计算alpha
        double alphaValue = 2.0 / (length + 1);
        Num alpha = series.numFactory().numOf(alphaValue);

        // 创建自定义指标计算上包络线
        Indicator<Num> up1 = new RecursiveIndicator(series, alpha, "up1", length);
        Indicator<Num> up2 = new RecursiveIndicator(series, alpha, "up2", length);
        Indicator<Num> dn1 = new RecursiveIndicator(series, alpha, "dn1", length);
        Indicator<Num> dn2 = new RecursiveIndicator(series, alpha, "dn2", length);

        // 计算bull和bear分量
        this.bull = new BullIndicator(dn2, dn1, series, length);
        this.bear = new BearIndicator(up2, up1, series, length);

        // 计算振荡器
        this.osc = new DifferenceIndicator(bull, bear);

        // 计算信号线（EMA）
        this.signal = new EMAIndicator(osc, signalLength);

        // 计算标准差
        this.oscStdev = new StandardDeviationIndicator(osc, length);

        // 计算水平线
        this.plusLevel = oscStdev;
        this.minusLevel = new NegativeIndicator(oscStdev, series, 0);
    }

    // 递归指标实现
    private class RecursiveIndicator extends CachedIndicator<Num> {
        private final Indicator<Num> close;
        private final Indicator<Num> open;
        private final Num alpha;
        private final String type;
        private final int unstableBars;

        public RecursiveIndicator(BarSeries series, Num alpha, String type, int unstableBars) {
            super(series);
            this.close = new ClosePriceIndicator(series);
            this.open = new OpenPriceIndicator(series);
            this.alpha = alpha;
            this.type = type;
            this.unstableBars = unstableBars;
        }

        @Override
        protected Num calculate(int index) {
            // 使用迭代方式计算，避免递归调用导致的栈溢出
            Num currentValue = calculateInitialValue(0);

            for (int i = 1; i <= index; i++) {
                currentValue = calculateNextValue(i, currentValue);
            }

            return currentValue;
        }

        /**
         * 计算初始值（index = 0）
         */
        private Num calculateInitialValue(int index) {
            Num C = close.getValue(index);
            Num O = open.getValue(index);

            switch (type) {
                case "up1":
                    return max(C, O);
                case "up2":
                    return max(C.multipliedBy(C), O.multipliedBy(O));
                case "dn1":
                    return min(C, O);
                case "dn2":
                    return min(C.multipliedBy(C), O.multipliedBy(O));
                default:
                    return C;
            }
        }

        /**
         * 基于前一个值计算下一个值
         */
        private Num calculateNextValue(int index, Num prevValue) {
            Num C = close.getValue(index);
            Num O = open.getValue(index);

            switch (type) {
                case "up1":
                    Num termUp1 = prevValue.minus(prevValue.minus(C).multipliedBy(alpha));
                    return max(max(C, O), termUp1);
                case "up2":
                    Num C2 = C.multipliedBy(C);
                    Num O2 = O.multipliedBy(O);
                    Num termUp2 = prevValue.minus(prevValue.minus(C2).multipliedBy(alpha));
                    return max(max(C2, O2), termUp2);
                case "dn1":
                    Num termDn1 = prevValue.plus(C.minus(prevValue).multipliedBy(alpha));
                    return min(min(C, O), termDn1);
                case "dn2":
                    Num C2_dn = C.multipliedBy(C);
                    Num O2_dn = O.multipliedBy(O);
                    Num termDn2 = prevValue.plus(C2_dn.minus(prevValue).multipliedBy(alpha));
                    return min(min(C2_dn, O2_dn), termDn2);
                default:
                    return C;
            }
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }

        private Num max(Num a, Num b) {
            return a.isGreaterThan(b) ? a : b;
        }

        private Num max(Num a, Num b, Num c) {
            return max(max(a, b), c);
        }

        private Num min(Num a, Num b) {
            return a.isLessThan(b) ? a : b;
        }

        private Num min(Num a, Num b, Num c) {
            return min(min(a, b), c);
        }
    }

    // Bull分量指标
    private class BullIndicator extends CachedIndicator<Num> {
        private final Indicator<Num> dn2;
        private final Indicator<Num> dn1;
        private final BarSeries localSeries;
        private final int unstableBars;

        public BullIndicator(Indicator<Num> dn2, Indicator<Num> dn1, BarSeries series, int unstableBars) {
            super(dn2);
            this.dn2 = dn2;
            this.dn1 = dn1;
            this.localSeries = series;
            this.unstableBars = unstableBars;
        }

        @Override
        protected Num calculate(int index) {
            Num dn2Value = dn2.getValue(index);
            Num dn1Value = dn1.getValue(index);
            Num variance = dn2Value.minus(dn1Value.multipliedBy(dn1Value));

            if (variance.isLessThanOrEqual(localSeries.numFactory().numOf(0))) {
                return localSeries.numFactory().numOf(0);
            }

            // 使用Math.sqrt计算平方根
            return localSeries.numFactory().numOf(Math.sqrt(variance.doubleValue()));
        }



        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    // Bear分量指标
    private class BearIndicator extends CachedIndicator<Num> {
        private final Indicator<Num> up2;
        private final Indicator<Num> up1;
        private final BarSeries localSeries;
        private final int unstableBars;

        public BearIndicator(Indicator<Num> up2, Indicator<Num> up1, BarSeries series, int unstableBars) {
            super(up2);
            this.up2 = up2;
            this.up1 = up1;
            this.localSeries = series;
            this.unstableBars = unstableBars;
        }

        @Override
        protected Num calculate(int index) {
            Num up2Value = up2.getValue(index);
            Num up1Value = up1.getValue(index);
            Num variance = up2Value.minus(up1Value.multipliedBy(up1Value));

            if (variance.isLessThanOrEqual(localSeries.numFactory().numOf(0))) {
                return localSeries.numFactory().numOf(0);
            }

            // 使用Math.sqrt计算平方根
            return localSeries.numFactory().numOf(Math.sqrt(variance.doubleValue()));
        }



        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    // 负值指标（替代MultiplyIndicator）
    private class NegativeIndicator extends CachedIndicator<Num> {
        private final Indicator<Num> source;
        private final BarSeries localSeries;
        private final int unstableBars;

        public NegativeIndicator(Indicator<Num> source, BarSeries series, int unstableBars) {
            super(source);
            this.source = source;
            this.localSeries = series;
            this.unstableBars = unstableBars;
        }

        @Override
        protected Num calculate(int index) {
            return source.getValue(index).multipliedBy(localSeries.numFactory().numOf(-1));
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }
    }

    // Getters for indicators
    public Indicator<Num> getOsc() { return osc; }
    public Indicator<Num> getSignal() { return signal; }
    public Indicator<Num> getBull() { return bull; }
    public Indicator<Num> getBear() { return bear; }
    public Indicator<Num> getOscStdev() { return oscStdev; }
    public Indicator<Num> getPlusLevel() { return plusLevel; }
    public Indicator<Num> getMinusLevel() { return minusLevel; }

    // 交易信号检测
    public boolean isBullishSignal(int index) {
        if (earlySignalMode) {
            // 早期信号模式：OSC与信号线的交叉
            if (index < 1) return false;
            Num oscPrev = osc.getValue(index - 1);
            Num signalPrev = signal.getValue(index - 1);
            Num oscCurrent = osc.getValue(index);
            Num signalCurrent = signal.getValue(index);

            return oscPrev.isLessThan(signalPrev) && oscCurrent.isGreaterThan(signalCurrent);
        } else {
            // 零轴交叉模式
            if (index < 1) return false;
            Num oscPrev = osc.getValue(index - 1);
            Num oscCurrent = osc.getValue(index);

            return oscPrev.isLessThan(series.numFactory().numOf(0)) && oscCurrent.isGreaterThan(series.numFactory().numOf(0));
        }
    }

    public boolean isBearishSignal(int index) {
        if (earlySignalMode) {
            // 早期信号模式：OSC与信号线的交叉
            if (index < 1) return false;
            Num oscPrev = osc.getValue(index - 1);
            Num signalPrev = signal.getValue(index - 1);
            Num oscCurrent = osc.getValue(index);
            Num signalCurrent = signal.getValue(index);

            return oscPrev.isGreaterThan(signalPrev) && oscCurrent.isLessThan(signalCurrent);
        } else {
            // 零轴交叉模式
            if (index < 1) return false;
            Num oscPrev = osc.getValue(index - 1);
            Num oscCurrent = osc.getValue(index);

            return oscPrev.isGreaterThan(series.numFactory().numOf(0)) && oscCurrent.isLessThan(series.numFactory().numOf(0));
        }
    }

    // 获取信号强度（0到1之间）
    public Num getStrength(int index) {
        Num oscValue = osc.getValue(index);
        Num stdevValue = oscStdev.getValue(index);

        if (stdevValue.doubleValue() == 0) {
            return series.numFactory().numOf(0);
        }

        Num strength = oscValue.abs().dividedBy(stdevValue.multipliedBy(series.numFactory().numOf(2)));
        return min(strength, series.numFactory().numOf(1));
    }

    // 辅助方法：获取最小值
    private Num min(Num a, Num b) {
        return a.isLessThan(b) ? a : b;
    }

    // 获取零轴交叉信号（单独方法）
    public boolean isZeroCrossUp(int index) {
        if (index < 1) return false;
        Num oscPrev = osc.getValue(index - 1);
        Num oscCurrent = osc.getValue(index);
        return oscPrev.isLessThan(series.numFactory().numOf(0)) && oscCurrent.isGreaterThan(series.numFactory().numOf(0));
    }

    public boolean isZeroCrossDown(int index) {
        if (index < 1) return false;
        Num oscPrev = osc.getValue(index - 1);
        Num oscCurrent = osc.getValue(index);
        return oscPrev.isGreaterThan(series.numFactory().numOf(0)) && oscCurrent.isLessThan(series.numFactory().numOf(0));
    }

    // 获取早期信号（OSC与信号线交叉）
    public boolean isEarlyCrossUp(int index) {
        if (index < 1) return false;
        Num oscPrev = osc.getValue(index - 1);
        Num signalPrev = signal.getValue(index - 1);
        Num oscCurrent = osc.getValue(index);
        Num signalCurrent = signal.getValue(index);
        return oscPrev.isLessThan(signalPrev) && oscCurrent.isGreaterThan(signalCurrent);
    }

    public boolean isEarlyCrossDown(int index) {
        if (index < 1) return false;
        Num oscPrev = osc.getValue(index - 1);
        Num signalPrev = signal.getValue(index - 1);
        Num oscCurrent = osc.getValue(index);
        Num signalCurrent = signal.getValue(index);
        return oscPrev.isGreaterThan(signalPrev) && oscCurrent.isLessThan(signalCurrent);
    }

    // 获取颜色强度（用于可视化）
    public double getColorIntensity(int index) {
        Num strength = getStrength(index);
        return strength.doubleValue();
    }

    // 判断是否显示水平线
    public boolean shouldShowLevels() {
        return true;
    }

    // 获取所有指标值
    public IndicatorValues getValues(int index) {
        return new IndicatorValues(
                osc.getValue(index).doubleValue(),
                signal.getValue(index).doubleValue(),
                bull.getValue(index).doubleValue(),
                bear.getValue(index).doubleValue(),
                plusLevel.getValue(index).doubleValue(),
                minusLevel.getValue(index).doubleValue(),
                getStrength(index).doubleValue(),
                isBullishSignal(index),
                isBearishSignal(index)
        );
    }

    // 获取指标摘要
    public String getSummary(int index) {
        IndicatorValues values = getValues(index);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Andean Oscillator Summary ===\n");
        sb.append(String.format("OSC: %.4f\n", values.osc));
        sb.append(String.format("Signal: %.4f\n", values.signal));
        sb.append(String.format("Bull: %.4f, Bear: %.4f\n", values.bull, values.bear));
        sb.append(String.format("Levels: +1σ=%.4f, -1σ=%.4f\n", values.plusLevel, values.minusLevel));
        sb.append(String.format("Strength: %.2f%%\n", values.strength * 100));
        sb.append("Signal: ");
        sb.append(String.format("市场状态: %s\n", getMarketState(index)));

        if (values.bullishSignal) {
            sb.append("BULLISH");
        } else if (values.bearishSignal) {
            sb.append("BEARISH");
        } else {
            sb.append("NEUTRAL");
        }

        // 添加市场条件判断
        if (values.osc > values.plusLevel) {
            sb.append(" (OVERBOUGHT)");
        } else if (values.osc < values.minusLevel) {
            sb.append(" (OVERSOLD)");
        }

        return sb.toString();
    }

    // 指标值容器类
    public static class IndicatorValues {
        public final double osc;
        public final double signal;
        public final double bull;
        public final double bear;
        public final double plusLevel;
        public final double minusLevel;
        public final double strength;
        public final boolean bullishSignal;
        public final boolean bearishSignal;

        public IndicatorValues(double osc, double signal, double bull, double bear,
                               double plusLevel, double minusLevel, double strength,
                               boolean bullishSignal, boolean bearishSignal) {
            this.osc = osc;
            this.signal = signal;
            this.bull = bull;
            this.bear = bear;
            this.plusLevel = plusLevel;
            this.minusLevel = minusLevel;
            this.strength = strength;
            this.bullishSignal = bullishSignal;
            this.bearishSignal = bearishSignal;
        }

        @Override
        public String toString() {
            return String.format(
                    "OSC: %.4f, Signal: %.4f, Bull: %.4f, Bear: %.4f, +1σ: %.4f, -1σ: %.4f, Strength: %.2f%%, Bullish: %b, Bearish: %b",
                    osc, signal, bull, bear, plusLevel, minusLevel, strength * 100, bullishSignal, bearishSignal
            );
        }
    }

    // 创建AndeanOscillator的工厂方法
    public static AndeanOscillator create(BarSeries series, int length, int signalLength, boolean earlySignalMode) {
        return new AndeanOscillator(series, length, signalLength, earlySignalMode);
    }

    // 带默认参数的工厂方法
    public static AndeanOscillator createDefault(BarSeries series) {
        return new AndeanOscillator(series, 50, 9, true);
    }

    // 获取指标名称
    public String getName() {
        return earlySignalMode ? "Andean Oscillator (Early Signal Mode)" : "Andean Oscillator";
    }

    // 获取参数信息
    public String getParameters() {
        return String.format("Length: %d, Signal Length: %d, Early Signal: %b",
                length, signalLength, earlySignalMode);
    }

    // 判断是否趋势向上（多头趋势）
    public boolean isUptrend(int index) {
        Num oscValue = osc.getValue(index);
        Num zero = series.numFactory().numOf(0);

        // OSC大于0即为趋势向上
        return oscValue.isGreaterThan(zero);
    }

    // 判断是否趋势向下（空头趋势）
    public boolean isDowntrend(int index) {
        Num oscValue = osc.getValue(index);
        Num zero = series.numFactory().numOf(0);

        // OSC小于0即为趋势向下
        return oscValue.isLessThan(zero);
    }

    // 优化版：获取详细市场状态
    public String getMarketState(int index) {
        Num oscValue = osc.getValue(index);
        Num plusLevelValue = plusLevel.getValue(index);
        Num minusLevelValue = minusLevel.getValue(index);
        Num zero = series.numFactory().numOf(0);

        // 先判断是否为震荡行情：在plusLevel和minusLevel之间
        boolean isRanging = oscValue.isLessThanOrEqual(plusLevelValue) &&
                           oscValue.isGreaterThanOrEqual(minusLevelValue);

        if (isRanging) {
            // 震荡行情：根据OSC值判断偏多还是偏空
            if (oscValue.isGreaterThan(zero)) {
                return "震荡偏多";
            } else if (oscValue.isLessThan(zero)) {
                return "震荡偏空";
            } else {
                return "震荡行情";
            }
        } else {
            // 趋势行情：根据OSC值判断向上还是向下
            if (oscValue.isGreaterThan(zero)) {
                return "趋势向上";
            } else {
                return "趋势向下";
            }
        }
    }
    // 获取市场状态枚举
    public MarketState getMarketStateEnum(int index) {
        Num oscValue = osc.getValue(index);
        Num plusLevelValue = plusLevel.getValue(index);
        Num minusLevelValue = minusLevel.getValue(index);
        Num zero = series.numFactory().numOf(0);

        // 先判断是否为震荡行情：在plusLevel和minusLevel之间
        boolean isRanging = oscValue.isLessThanOrEqual(plusLevelValue) &&
                           oscValue.isGreaterThanOrEqual(minusLevelValue);

        if (isRanging) {
            // 震荡行情：根据OSC值判断偏多还是偏空
            if (oscValue.isGreaterThan(zero)) {
                return MarketState.RANGING_BULLISH;
            } else if (oscValue.isLessThan(zero)) {
                return MarketState.RANGING_BEARISH;
            } else {
                return MarketState.RANGING;
            }
        } else {
            // 趋势行情：根据OSC值判断向上还是向下
            if (oscValue.isGreaterThan(zero)) {
                return MarketState.UPTREND;
            } else {
                return MarketState.DOWNTREND;
            }
        }
    }

    // 获取趋势强度（0-1）
    public Num getTrendStrength(int index) {
        Num oscValue = osc.getValue(index);
        Num zero = series.numFactory().numOf(0);

        if (isUptrend(index)) {
            // 向上趋势强度：OSC值相对于0的距离比例（最大为1）
            return oscValue.dividedBy(oscValue.abs().plus(series.numFactory().numOf(0.0001))).min(series.numFactory().numOf(1));

        } else if (isDowntrend(index)) {
            // 向下趋势强度：OSC值相对于0的距离比例（最大为1）
            return oscValue.abs().dividedBy(oscValue.abs().plus(series.numFactory().numOf(0.0001))).min(series.numFactory().numOf(1));

        } else {
            // 震荡行情，趋势强度为0
            return series.numFactory().numOf(0);
        }
    }

    // ====================================================================
    // 单独的趋势判断方法 (基于OSC > 0 和 OSC < 0)
    // ====================================================================

    /**
     * 单独的趋势判断方法 - 基于OSC相对0值的位置
     *
     * @param index K线索引
     * @return TrendDirection 枚举值
     */
    public TrendDirection getSimpleTrendDirection(int index) {
        if (isUptrend(index)) {
            return TrendDirection.BULLISH; // 上升趋势: OSC > 0
        } else if (isDowntrend(index)) {
            return TrendDirection.BEARISH; // 下降趋势: OSC < 0
        } else {
            return TrendDirection.RANGING; // 震荡行情: OSC ≈ 0
        }
    }



    // 简单趋势方向枚举 (用于单独的趋势判断方法)
    public enum TrendDirection {
        BULLISH("上升趋势"),    // OSC > 0
        BEARISH("下降趋势"),    // OSC < 0
        RANGING("震荡行情");    // OSC ≈ 0

        private final String description;

        TrendDirection(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // 市场状态枚举
    public enum MarketState {
        UPTREND("趋势向上"),
        DOWNTREND("趋势向下"),
        RANGING("震荡行情"),
        RANGING_BULLISH("震荡偏多"),
        RANGING_BEARISH("震荡偏空");

        private final String description;

        MarketState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}