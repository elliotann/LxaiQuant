package com.chain.ai.trade.engine2.rules.composite;

import com.chain.ai.trade.engine2.core.ExitSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import com.chain.ai.trade.engine2.rules.TradingRule;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.List;

/**
 * 或组合规则。
 * <p>
 * 依次评估所有子规则，返回第一个非 null 的 ExitSignal。
 * 只要任一规则触发，则整体触发。用于实现多出场规则（止损/止盈/信号反转等）的"任一满足即出场"。
 * </p>
 */
public class OrTradingRule implements TradingRule {

    private final List<TradingRule> rules;

    public OrTradingRule(List<TradingRule> rules) {
        this.rules = rules;
    }

    @Override
    public ExitSignal evaluate(int index, Bar bar, BarSeries series, TradingContext context) {
        for (TradingRule rule : rules) {
            ExitSignal signal = rule.evaluate(index, bar, series, context);
            if (signal != null) return signal;
        }
        return null;
    }
}
