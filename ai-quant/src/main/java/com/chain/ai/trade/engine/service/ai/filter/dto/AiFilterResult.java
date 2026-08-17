package com.chain.ai.trade.engine.service.ai.filter.dto;

import java.math.BigDecimal;

public class AiFilterResult {
    private final boolean enabled;
    private final String decision;
    private final BigDecimal adjustedStrength;
    private final int objectiveScore;
    private final String llmDecision;
    private final String aiFilterResultJson;

    public AiFilterResult(String decision, BigDecimal adjustedStrength, int objectiveScore,
                          String llmDecision, String aiFilterResultJson) {
        this.enabled = true;
        this.decision = decision;
        this.adjustedStrength = adjustedStrength;
        this.objectiveScore = objectiveScore;
        this.llmDecision = llmDecision;
        this.aiFilterResultJson = aiFilterResultJson;
    }

    private AiFilterResult(BigDecimal originalStrength) {
        this.enabled = false;
        this.decision = "ALLOW";
        this.adjustedStrength = originalStrength;
        this.objectiveScore = 0;
        this.llmDecision = "";
        this.aiFilterResultJson = "";
    }

    public static AiFilterResult notEnabled(BigDecimal originalStrength) {
        return new AiFilterResult(originalStrength);
    }

    public boolean isEnabled() { return enabled; }
    public String getDecision() { return decision; }
    public BigDecimal getAdjustedStrength() { return adjustedStrength; }
    public int getObjectiveScore() { return objectiveScore; }
    public String getLlmDecision() { return llmDecision; }
    public String getAiFilterResultJson() { return aiFilterResultJson; }
}
