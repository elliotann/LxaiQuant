package com.chain.ai.trade.engine.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
public class ReviewMetrics {
    private BigDecimal totalPnL;
    private double winRate;
    private double profitLossRatio;
    private double maxDrawdown;
    private double stopLossRate;
    private double avgDailyTrades;
    private double concentrationRatio;
    private Map<String, BigDecimal> strategyPnL;
}
