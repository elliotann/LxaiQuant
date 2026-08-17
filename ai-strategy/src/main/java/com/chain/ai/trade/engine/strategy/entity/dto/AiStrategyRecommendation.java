package com.chain.ai.trade.engine.strategy.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class AiStrategyRecommendation {

    private String botType;

    private String reason;

    private BaseConfig baseConfig;

    private Map<String, Object> strategyParams;

    private RiskConfig riskConfig;

    @Data
    public static class BaseConfig {
        private String symbol;
        private String timeframe;
        private String marketType;
        private Integer leverage;
        private BigDecimal initialCapital;
    }

    @Data
    public static class RiskConfig {
        private BigDecimal maxDrawdownPct;
        private BigDecimal maxPositionPct;
        private BigDecimal dailyLossLimitPct;
    }
}
