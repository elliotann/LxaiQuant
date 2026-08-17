package com.chain.ai.trade.engine.strategy.core.rule;

import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.extension.ta4j.core.rule.ExitSignal;
import com.chain.ai.trade.extension.ta4j.core.rule.DirectionalRule;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Trade;

public class ExitRuleAdapter implements DirectionalRule {
    private final Rule delegate;
    private final Trade.TradeType exitDirection;
    private final ExitType exitType;

    public ExitRuleAdapter(Rule delegate, Trade.TradeType exitDirection, ExitType exitType) {
        this.delegate = delegate;
        this.exitDirection = exitDirection;
        this.exitType = exitType;
    }

    @Override
    public Trade.TradeType getDirection(int index, TradingRecord tradingRecord) {
        if (delegate != null && delegate.isSatisfied(index, tradingRecord)) {
            return exitDirection;
        }
        return null;
    }

    @Override
    public ExitSignal getSignal(int index, TradingRecord tradingRecord) {
        if (delegate != null && delegate.isSatisfied(index, tradingRecord)) {
            return new ExitSignal(exitDirection, exitType);
        }
        return null;
    }
}
