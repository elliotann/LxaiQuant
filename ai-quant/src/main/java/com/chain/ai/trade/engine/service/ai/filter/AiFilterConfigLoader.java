package com.chain.ai.trade.engine.service.ai.filter;

import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public class AiFilterConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AiFilterConfig load(Strategy strategy) {
        AiFilterConfig config = new AiFilterConfig();
        if (strategy == null || strategy.getAutoSignal() == null || strategy.getAutoSignal().isBlank()) {
            return config;
        }
        try {
            JsonNode root = MAPPER.readTree(strategy.getAutoSignal());
            config.enabled = root.path("enabled").asBoolean(false);
            config.llmConfigId = root.path("llmConfigId").asText(null);
            JsonNode thresholds = root.path("thresholds");
            if (!thresholds.isMissingNode()) {
                config.directAllowThreshold = thresholds.path("directAllowThreshold").asInt(50);
                config.directRejectThreshold = thresholds.path("directRejectThreshold").asInt(15);
                config.lowConfidenceThreshold = thresholds.path("lowConfidenceThreshold").asInt(30);
            }
            JsonNode weights = root.path("scoringWeights");
            if (!weights.isMissingNode()) {
                config.trendWeight = new BigDecimal(weights.path("trendWeight").asDouble(0.35));
                config.volatilityWeight = new BigDecimal(weights.path("volatilityWeight").asDouble(0.20));
                config.supportResistanceWeight = new BigDecimal(weights.path("supportResistanceWeight").asDouble(0.25));
                config.volumePriceWeight = new BigDecimal(weights.path("volumePriceWeight").asDouble(0.20));
            }
        } catch (JsonProcessingException e) {
            config.enabled = false;
        }
        return config;
    }

    public static class AiFilterConfig {
        private boolean enabled;
        private String llmConfigId;
        private int directAllowThreshold = 50;
        private int directRejectThreshold = 15;
        private int lowConfidenceThreshold = 30;
        private BigDecimal trendWeight = new BigDecimal("0.35");
        private BigDecimal volatilityWeight = new BigDecimal("0.20");
        private BigDecimal supportResistanceWeight = new BigDecimal("0.25");
        private BigDecimal volumePriceWeight = new BigDecimal("0.20");

        public boolean isEnabled() { return enabled; }
        public String getLlmConfigId() { return llmConfigId; }
        public int getDirectAllowThreshold() { return directAllowThreshold; }
        public int getDirectRejectThreshold() { return directRejectThreshold; }
        public int getLowConfidenceThreshold() { return lowConfidenceThreshold; }
        public BigDecimal getTrendWeight() { return trendWeight; }
        public BigDecimal getVolatilityWeight() { return volatilityWeight; }
        public BigDecimal getSupportResistanceWeight() { return supportResistanceWeight; }
        public BigDecimal getVolumePriceWeight() { return volumePriceWeight; }
    }
}
