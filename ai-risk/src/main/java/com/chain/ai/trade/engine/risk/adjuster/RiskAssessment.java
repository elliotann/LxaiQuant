package com.chain.ai.trade.engine.risk.adjuster;

import lombok.Data;

/**
 * 风险评估
 */
@Data
public class RiskAssessment {
    private double maxPositionSize;       // 最大仓位限制
    private double stopLossPrice;         // 建议止损价
    private double takeProfitPrice;       // 建议止盈价
    private double riskRewardRatio;       // 风险收益比
    private String riskLevel;             // 风险等级（LOW/MEDIUM/HIGH）
}

