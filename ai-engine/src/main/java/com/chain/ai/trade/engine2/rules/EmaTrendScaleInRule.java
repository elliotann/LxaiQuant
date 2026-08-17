package com.chain.ai.trade.engine2.rules;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine2.core.ScaleInReason;
import com.chain.ai.trade.engine2.core.ScaleInSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * EMA 趋势过滤规则 — 快 EMA 与慢 EMA 的关系连续满足 N 根 K 线。
 * <p>
 * 对应 V1 参数 {@code profitAddEmaTrendEnabled} / {@code profitAddEmaFastPeriod} /
 * {@code profitAddEmaSlowPeriod} / {@code profitAddEmaMinConsecutiveBars}。
 * <p>
 * 根据传入的信号方向判断趋势：
 * <ul>
 *   <li>LONG → 检查快 EMA > 慢 EMA（多头趋势）</li>
 *   <li>SHORT → 检查快 EMA < 慢 EMA（空头趋势）</li>
 * </ul>
 */
public class EmaTrendScaleInRule implements ScaleInRule {

    private final int fastPeriod;
    private final int slowPeriod;
    private final int minConsecutiveBars;

    /**
     * 构造函数
     *
     * @param fastPeriod          快 EMA 周期（如 9）
     * @param slowPeriod          慢 EMA 周期（如 21）
     * @param minConsecutiveBars  最小连续满足 K 线数（如 3）
     */
    public EmaTrendScaleInRule(int fastPeriod, int slowPeriod, int minConsecutiveBars) {
        if (fastPeriod <= 0 || slowPeriod <= 0 || fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("fastPeriod must be > 0, slowPeriod > 0, and fastPeriod < slowPeriod");
        }
        if (minConsecutiveBars <= 0) {
            throw new IllegalArgumentException("minConsecutiveBars must be > 0");
        }
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.minConsecutiveBars = minConsecutiveBars;
    }

    @Override
    public ScaleInSignal shouldScaleIn(int index, Bar bar, BarSeries series, TradingContext context, SignalType signalDirection) {
        // 计算 EMA
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator fastEma = new EMAIndicator(close, fastPeriod);
        EMAIndicator slowEma = new EMAIndicator(close, slowPeriod);

        // 检查是否连续满足条件
        int consecutive = 0;
        int start = Math.max(index - minConsecutiveBars + 1, 0);

        for (int i = start; i <= index; i++) {
            double fastVal = fastEma.getValue(i).doubleValue();
            double slowVal = slowEma.getValue(i).doubleValue();

            if (Double.isNaN(fastVal) || Double.isNaN(slowVal)) {
                consecutive = 0;
                continue;
            }

            boolean condition = (signalDirection == SignalType.LONG)
                    ? fastVal > slowVal      // 多头：快 > 慢
                    : fastVal < slowVal;     // 空头：快 < 慢

            if (condition) {
                consecutive++;
                if (consecutive >= minConsecutiveBars) {
                    return new ScaleInSignal(signalDirection, ScaleInReason.TREND_ADD);
                }
            } else {
                consecutive = 0;
            }
        }
        return null;
    }
}
