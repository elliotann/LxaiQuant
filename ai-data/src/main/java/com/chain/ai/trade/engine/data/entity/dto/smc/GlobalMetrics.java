package com.chain.ai.trade.engine.data.entity.dto.smc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全局评估指标（独立于周期）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalMetrics {
    private double riskRewardRatio;      // 盈亏比
    private double riskPercent;          // 单笔风险占比
    private boolean chaosException;      // 混沌特例是否触发
    private double chaosForcedMultiplier; // 混沌特例强制乘数
    private double compositeScore;       // 综合评分
    private double suggestedMultiplier;  // 建议仓位乘数
    private String phaseDescription;     // 阶段说明
}