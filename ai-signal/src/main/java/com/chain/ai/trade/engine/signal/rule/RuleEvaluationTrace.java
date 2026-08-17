package com.chain.ai.trade.engine.signal.rule;

import lombok.Data;
import java.util.List;

@Data
public class RuleEvaluationTrace {

    private String ruleName;
    private String ruleType;
    private boolean matched;
    private double contribution;
    private String reason;
    private String conditionOperator;
    private List<ConditionTrace> conditionResults;

    @Data
    public static class ConditionTrace {

        private String indicator;
        private String operator;
        private String direction;
        private String expectedValue;
        private Double actualValue;
        private boolean matched;
        private String reason;
    }
}
