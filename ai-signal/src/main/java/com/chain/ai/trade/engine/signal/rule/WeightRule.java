package com.chain.ai.trade.engine.signal.rule;

import lombok.Data;
import java.util.List;

@Data
public class WeightRule {
    private String name;
    private String type;
    private Double score;
    private Double vetoWeight;
    private List<RuleCondition> conditions;
    private String conditionOperator;
    private int order;
    private boolean enabled;
}
