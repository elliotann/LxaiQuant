package com.chain.ai.trade.engine.strategy.core.rule;

import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;

public class PinVolumeExitRule extends AbstractRule {
    private final BarSeries series;
    private final OrderSideEnum direction;
    private final boolean mustWin;
    private final double commissionRate;
    private final SMAIndicator volumeSma;
    private final double wickRatioThreshold;
    private final double volumeMultiplier;

    public PinVolumeExitRule(BarSeries series, OrderSideEnum direction, boolean mustWin) {
        this(series, direction, mustWin, 0.0, 20, 2.0, 1.5);
    }

    public PinVolumeExitRule(BarSeries series, OrderSideEnum direction, boolean mustWin, double commissionRate) {
        this(series, direction, mustWin, commissionRate, 20, 2.0, 1.5);
    }

    public PinVolumeExitRule(BarSeries series, OrderSideEnum direction, boolean mustWin, double commissionRate,
                             int volumeSmaBarCount, double wickRatioThreshold, double volumeMultiplier) {
        this.series = series;
        this.direction = direction;
        this.mustWin = mustWin;
        this.commissionRate = commissionRate;
        this.volumeSma = new SMAIndicator(new VolumeIndicator(series), volumeSmaBarCount);
        this.wickRatioThreshold = wickRatioThreshold;
        this.volumeMultiplier = volumeMultiplier;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (index < 0 || index >= series.getBarCount()) return false;

        Num open = series.getBar(index).getOpenPrice();
        Num close = series.getBar(index).getClosePrice();
        Num high = series.getBar(index).getHighPrice();
        Num low = series.getBar(index).getLowPrice();
        Num vol = series.getBar(index).getVolume();

        Num volAvg = volumeSma.getValue(index);
        boolean volumeSurge = vol.isGreaterThan(volAvg.multipliedBy(series.numFactory().numOf(volumeMultiplier)));
        if (!volumeSurge) return false;

        double o = open.doubleValue();
        double c = close.doubleValue();
        double h = high.doubleValue();
        double l = low.doubleValue();

        double body = Math.abs(c - o);
        if (body <= 0.0) body = 1e-8;
        double upperWick = h - Math.max(o, c);
        double lowerWick = Math.min(o, c) - l;

        boolean pinForLong = upperWick / body >= wickRatioThreshold && c < o;
        boolean pinForShort = lowerWick / body >= wickRatioThreshold && c > o;

        boolean satisfied = direction == OrderSideEnum.BUY ? pinForLong : pinForShort;
        if (!satisfied) return false;

        if (!mustWin) return true;
        if (tradingRecord == null) return false;
        Position position = tradingRecord.getCurrentPosition();
        if (position == null || !position.isOpened()) return false;
        Trade entry = position.getEntry();
        if (entry == null) return false;

        Num entryPrice = entry.getPricePerAsset();
        double ep = entryPrice.doubleValue();
        double cp = close.doubleValue();
        double totalFee = (ep + cp) * commissionRate;

        if (direction == OrderSideEnum.BUY) {
            return (cp - ep) > totalFee;
        } else {
            return (ep - cp) > totalFee;
        }
    }
}
