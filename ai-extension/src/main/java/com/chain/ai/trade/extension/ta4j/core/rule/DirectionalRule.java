package com.chain.ai.trade.extension.ta4j.core.rule;

import org.ta4j.core.Rule;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;

public interface DirectionalRule extends Rule {
    ExitSignal getSignal(int index, TradingRecord tradingRecord);

    @Override
    default boolean isSatisfied(int index, TradingRecord tradingRecord) {
        return getSignal(index, tradingRecord) != null;
    }

    default TradeType getDirection(int index, TradingRecord tradingRecord) {
        ExitSignal signal = getSignal(index, tradingRecord);
        return signal != null ? signal.getDirection() : null;
    }
}
