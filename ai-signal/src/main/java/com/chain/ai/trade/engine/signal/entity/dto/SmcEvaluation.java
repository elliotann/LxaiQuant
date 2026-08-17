package com.chain.ai.trade.engine.signal.entity.dto;

import lombok.Data;

@Data
public class SmcEvaluation {
    boolean allowed;
    double weightMultiplier;
    Double stopLoss;
    Double takeProfit;
    String reason;
    double trendScore;
    double positionScore;
    double netRR;
}