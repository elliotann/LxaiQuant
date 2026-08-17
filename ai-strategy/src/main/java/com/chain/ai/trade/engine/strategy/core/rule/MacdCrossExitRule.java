package com.chain.ai.trade.engine.strategy.core.rule;

import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;

import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

public class MacdCrossExitRule extends AbstractRule {
    private final Rule crossRule;
    private final boolean mustWin;
    private final BarSeries series;
    private final OrderSideEnum direction;
    private final double commissionRate;

    public MacdCrossExitRule(BarSeries series, OrderSideEnum direction, boolean mustWin) {
        this(series, direction, mustWin, 0.0);
    }

    public MacdCrossExitRule(BarSeries series, OrderSideEnum direction, boolean mustWin, double commissionRate) {
        this.series = series;
        this.direction = direction;
        this.mustWin = mustWin;
        this.commissionRate = commissionRate;
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        EMAIndicator signal = new EMAIndicator(macd, 9);
        
        if (direction == OrderSideEnum.BUY) {
            this.crossRule = new CrossedDownIndicatorRule(macd, signal);
        } else {
            this.crossRule = new CrossedUpIndicatorRule(macd, signal);
        }
    }

    public boolean isCrossSatisfied(int index) {
        return crossRule.isSatisfied(index, null);
    }

    public boolean isMustWin() {
        return mustWin;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = crossRule.isSatisfied(index, tradingRecord);
        if (!satisfied) return false;
        
        if (mustWin) {
            if (tradingRecord == null) return false;
            Position position = tradingRecord.getCurrentPosition();
            if (position == null || !position.isOpened()) return false;
            Trade entry = position.getEntry();
            if (entry == null) return false;
            
            Num entryPrice = entry.getPricePerAsset();
            Num currentPrice = series.getBar(index).getClosePrice();
            
            // 计算手续费成本 (开仓 + 平仓)
            // 简单估算：(entryPrice + currentPrice) * commissionRate
            // 这里的 currentPrice 用作预估平仓价格
            double entryPriceVal = entryPrice.doubleValue();
            double currentPriceVal = currentPrice.doubleValue();
            double totalFee = (entryPriceVal + currentPriceVal) * commissionRate;
            
            if (direction == OrderSideEnum.BUY) {
                // 做多盈利要求：(当前价 - 开仓价) > 手续费
                return (currentPriceVal - entryPriceVal) > totalFee;
            } else {
                // 做空盈利要求：(开仓价 - 当前价) > 手续费
                return (entryPriceVal - currentPriceVal) > totalFee;
            }
        }
        
        return true;
    }
}
