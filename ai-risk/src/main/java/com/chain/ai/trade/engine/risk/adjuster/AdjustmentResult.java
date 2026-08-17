package com.chain.ai.trade.engine.risk.adjuster;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调节结果
 */
@Data
@Builder
public class AdjustmentResult {
    private double adjustedWeight;        // 调整后的权重
    private double positionSize;          // 建议仓位大小
    @Builder.Default
    private Map<String, Double> factors = new HashMap<>();  // 调节因子
    @Builder.Default
    private List<String> adjustments = new ArrayList<>();     // 调整说明
    private RiskAssessment riskAssessment;// 风险评估
}

