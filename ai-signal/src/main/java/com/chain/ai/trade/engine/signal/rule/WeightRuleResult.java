package com.chain.ai.trade.engine.signal.rule;

import com.chain.ai.trade.common.entity.constants.CompositeState;
import lombok.Data;

@Data
public class WeightRuleResult {
    private boolean vetoed;
    private double weight;
    private String reason;
    private int matchedRules;

    private CompositeState trendState;

    public static WeightRuleResult veto(String reason) {
        return veto(reason, 0.0);
    }

    public static WeightRuleResult veto(String reason, double weight) {
        WeightRuleResult r = new WeightRuleResult();
        r.setVetoed(true);
        r.setWeight(weight);
        r.setReason(reason);
        return r;
    }

    public static WeightRuleResult scored(double weight, String reason, int matchedRules) {
        WeightRuleResult r = new WeightRuleResult();
        r.setVetoed(false);
        r.setWeight(weight);
        r.setReason(reason);
        r.setMatchedRules(matchedRules);
        return r;
    }
}
