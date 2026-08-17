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
 * 亏损补仓规则 — 浮亏达阈值触发加仓。
 * <p>
 * 对应 V1 参数 {@code addPosOnLossPct}。
 */
public class LossScaleInRule implements ScaleInRule {
    private final double thresholdPct;

    public LossScaleInRule(double thresholdPct) {
        this.thresholdPct = thresholdPct;
    }

    @Override
    public ScaleInSignal shouldScaleIn(int index, Bar bar, BarSeries series, TradingContext context, SignalType signalDirection) {
        BigDecimal currentPrice = BigDecimal.valueOf(bar.getClosePrice().doubleValue());

        if (signalDirection == SignalType.LONG) {
            BigDecimal entryPrice = context.getLongAvgPrice();
            if (entryPrice.compareTo(BigDecimal.ZERO) <= 0) return null;
            double lossPct = entryPrice.subtract(currentPrice)
                    .divide(entryPrice, 8, RoundingMode.HALF_UP).doubleValue();
            if (lossPct >= thresholdPct) {
                return new ScaleInSignal(SignalType.LONG, ScaleInReason.DCA_ADD);
            }
        } else {
            BigDecimal entryPrice = context.getShortAvgPrice();
            if (entryPrice.compareTo(BigDecimal.ZERO) <= 0) return null;
            double lossPct = currentPrice.subtract(entryPrice)
                    .divide(entryPrice, 8, RoundingMode.HALF_UP).doubleValue();
            if (lossPct >= thresholdPct) {
                return new ScaleInSignal(SignalType.SHORT, ScaleInReason.DCA_ADD);
            }
        }
        return null;
    }
}
