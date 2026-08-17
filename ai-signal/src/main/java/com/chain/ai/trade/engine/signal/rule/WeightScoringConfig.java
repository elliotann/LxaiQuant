package com.chain.ai.trade.engine.signal.rule;

import lombok.Data;

/**
 * 评分配置（v5.2 §4.1.2）
 * 控制分数到权重的映射行为和 VETO 规则权重开关
 */
@Data
public class WeightScoringConfig {
    /** VETO 规则通过时是否贡献权重，默认 true */
    private Boolean vetoContributeScore = true;
    /** 映射模式：STEP（阶梯）/ LINEAR（线性） */
    private String mappingMode = "STEP";
    /** 线性模式斜率 */
    private Double linearSlope = 0.6;
    /** 线性模式最小权重 */
    private Double linearMinWeight = 0.0;
    /** 线性模式最大权重 */
    private Double linearMaxWeight = 2.0;
}
