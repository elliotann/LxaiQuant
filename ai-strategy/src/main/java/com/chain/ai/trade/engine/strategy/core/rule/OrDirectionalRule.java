package com.chain.ai.trade.engine.strategy.core.rule;

import com.chain.ai.trade.extension.ta4j.core.rule.ExitSignal;
import com.chain.ai.trade.extension.ta4j.core.rule.DirectionalRule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Trade;

public class OrDirectionalRule implements DirectionalRule {
    private final DirectionalRule[] rules;

    public OrDirectionalRule(DirectionalRule... rules) {
        this.rules = rules != null ? rules : new DirectionalRule[0];
    }

    @Override
    public Trade.TradeType getDirection(int index, TradingRecord tradingRecord) {
        for (DirectionalRule rule : rules) {
            if (rule == null) continue;
            Trade.TradeType dir = rule.getDirection(index, tradingRecord);
            if (dir != null) {
                return dir;
            }
        }
        return null;
    }

    @Override
    public ExitSignal getSignal(int index, TradingRecord tradingRecord) {
        for (DirectionalRule rule : rules) {
            if (rule == null) continue;
            ExitSignal signal = rule.getSignal(index, tradingRecord);
            if (signal != null) {
                return signal;
            }
        }
        return null;
    }
}
