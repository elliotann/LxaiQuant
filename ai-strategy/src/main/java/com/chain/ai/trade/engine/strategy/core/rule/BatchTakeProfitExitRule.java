package com.chain.ai.trade.engine.strategy.core.rule;

import com.chain.ai.trade.engine.strategy.enums.ExitRuleType;
import com.chain.ai.trade.extension.core.constants.ExitType;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BatchTakeProfitExitRule extends AbstractExitRule {
    private final BarSeries series;
    private final ClosePriceIndicator closePrice;
    private final List<Num> profitPercents;

    public BatchTakeProfitExitRule(BarSeries series, ClosePriceIndicator closePrice, List<Double> profitPercents) {
        super(ExitRuleType.RISK_MANAGEMENT, ExitType.TAKE_PROFIT, "分批止盈规则");
        this.series = series;
        this.closePrice = closePrice;
        this.profitPercents = new ArrayList<>();
        if (profitPercents != null) {
            for (Double percent : profitPercents) {
                if (percent != null && percent > 0) {
                    this.profitPercents.add(series.numFactory().numOf(percent));
                }
            }
        }
        this.profitPercents.sort(Comparator.naturalOrder());
        addParameter("profitPercents", this.profitPercents.stream().map(Num::doubleValue).toList());
    }

    @Override
    public boolean evaluate(int index, TradingRecord tradingRecord) {
        if (!enabled || tradingRecord == null) {
            return false;
        }
        if (profitPercents.isEmpty()) {
            return false;
        }
        Position position = tradingRecord.getCurrentPosition();
        if (position == null || position.isNew() || position.getEntry() == null) {
            return false;
        }
        Num entryPrice = position.getEntry().getPricePerAsset();
        if (entryPrice == null || entryPrice.isZero()) {
            return false;
        }
        Num currentPrice = closePrice.getValue(index);
        if (currentPrice == null) {
            return false;
        }
        Num profitPercent;
        if (position.getEntry().getType() == Trade.TradeType.BUY) {
            profitPercent = currentPrice.minus(entryPrice)
                    .dividedBy(entryPrice)
                    .multipliedBy(series.numFactory().numOf(100));
        } else {
            profitPercent = entryPrice.minus(currentPrice)
                    .dividedBy(entryPrice)
                    .multipliedBy(series.numFactory().numOf(100));
        }
        for (Num target : profitPercents) {
            if (profitPercent.isGreaterThanOrEqual(target)) {
                return true;
            }
        }
        return false;
    }
}
