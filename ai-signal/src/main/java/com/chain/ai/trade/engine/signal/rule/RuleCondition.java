package com.chain.ai.trade.engine.signal.rule;

import lombok.Data;
import java.util.Map;

@Data
public class RuleCondition {
    private String indicator;
    private Map<String, String> params;
    private String operator;
    private String value;
    private String direction = "BOTH";
}
