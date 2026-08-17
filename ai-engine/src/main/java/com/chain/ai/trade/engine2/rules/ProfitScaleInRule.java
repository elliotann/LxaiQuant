package com.chain.ai.trade.engine2.rules;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine2.core.ScaleInReason;
import com.chain.ai.trade.engine2.core.ScaleInSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 浮盈加仓规则 — 从首笔开仓均价算起，浮盈达阈值触发。
 * <p>
 * 对应 V1 参数 {@code addPosOnProfitPct}。
 */
public class ProfitScaleInRule implements ScaleInRule {
    private final double thresholdPct;

    public ProfitScaleInRule(double thresholdPct) {
        this.thresholdPct = thresholdPct;
    }

    @Override
    public ScaleInSignal shouldScaleIn(int index, Bar bar, BarSeries series, TradingContext context, SignalType signalDirection) {
        BigDecimal currentPrice = BigDecimal.valueOf(bar.getOpenPrice().doubleValue());

        if (signalDirection == SignalType.LONG) {
            BigDecimal entryPrice = context.getLongAvgPrice();
            if (entryPrice.compareTo(BigDecimal.ZERO) <= 0) return null;
            double profitPct = currentPrice.subtract(entryPrice)
                    .divide(entryPrice, 8, RoundingMode.HALF_UP).doubleValue();
            if (profitPct >= thresholdPct) {
                return new ScaleInSignal(SignalType.LONG, ScaleInReason.TREND_ADD);
            }
        } else {
            BigDecimal entryPrice = context.getShortAvgPrice();
            if (entryPrice.compareTo(BigDecimal.ZERO) <= 0) return null;
            double profitPct = entryPrice.subtract(currentPrice)
                    .divide(entryPrice, 8, RoundingMode.HALF_UP).doubleValue();
            if (profitPct >= thresholdPct) {
                return new ScaleInSignal(SignalType.SHORT, ScaleInReason.TREND_ADD);
            }
        }
        return null;
    }
}
