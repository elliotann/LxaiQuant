package com.chain.ai.trade.engine.strategy.core.rule;

import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

public class ExtTrailingStopLossRule implements Rule {
    private final Indicator<Num> priceIndicator;
    private final int barCount;
    private final Num lossPercentage;
    private Num lastStop;
    private Integer lastPositionIndex;
    private Boolean lastIsBuy;

    public ExtTrailingStopLossRule(Indicator<Num> indicator, Num lossPercentage, int barCount) {
        this.priceIndicator = indicator;
        this.barCount = barCount;
        this.lossPercentage = lossPercentage;
    }

    public ExtTrailingStopLossRule(Indicator<Num> indicator, Num lossPercentage) {
        this(indicator, lossPercentage, Integer.MAX_VALUE);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        if (tradingRecord != null) {
            Position currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition.isOpened()) {
                Num currentPrice = priceIndicator.getValue(index);
                int positionIndex = currentPosition.getEntry().getIndex();
                int start = Math.max(positionIndex, index - getValueIndicatorBarCount(index, positionIndex));
                Num extreme = null;
                for (int i = start; i <= index; i++) {
                    Num v = priceIndicator.getValue(i);
                    if (extreme == null) extreme = v;
                    else {
                        if (currentPosition.getEntry().isBuy()) {
                            if (v.isGreaterThan(extreme)) extreme = v;
                        } else {
                            if (v.isLessThan(extreme)) extreme = v;
                        }
                    }
                }
                if (extreme != null) {
                    Num threshold = trailingStopLossPriceFromDistance(extreme, lossPercentage, currentPosition.getEntry().isBuy());
                    boolean newPosition = lastPositionIndex == null || !lastPositionIndex.equals(positionIndex)
                            || lastIsBuy == null || lastIsBuy.booleanValue() != currentPosition.getEntry().isBuy();
                    if (newPosition) {
                        lastStop = threshold;
                        lastPositionIndex = positionIndex;
                        lastIsBuy = currentPosition.getEntry().isBuy();
                    } else if (lastStop != null) {
                        if (currentPosition.getEntry().isBuy()) {
                            if (threshold.isLessThan(lastStop)) threshold = lastStop;
                        } else {
                            if (threshold.isGreaterThan(lastStop)) threshold = lastStop;
                        }
                        lastStop = threshold;
                    } else {
                        lastStop = threshold;
                    }
                    satisfied = currentPosition.getEntry().isBuy() ? currentPrice.isLessThanOrEqual(threshold)
                            : currentPrice.isGreaterThanOrEqual(threshold);
                }
            }
        }
        return satisfied;
    }

    private int getValueIndicatorBarCount(int index, int positionIndex) {
        int available = index - positionIndex;
        if (available < 0) return 0;
        return Math.min(available, barCount == Integer.MAX_VALUE ? available : barCount);
    }

    public static Num trailingStopLossPriceFromDistance(Num referencePrice, Num lossDistance, boolean isBuy) {
        return isBuy ? referencePrice.minus(lossDistance) : referencePrice.plus(lossDistance);
    }
}
