package com.chain.ai.trade.engine.signal.rule;

import lombok.Data;
import java.util.List;

@Data
public class WeightRuleConfig {
    private boolean enabled;
    /** 规则列表 */
    private List<WeightRule> rules;

    /** 评分配置 */
    private WeightScoringConfig scoringConfig;
    private Integer version;
    private String status;
}
