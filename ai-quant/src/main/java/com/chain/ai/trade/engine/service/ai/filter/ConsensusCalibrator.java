package com.chain.ai.trade.engine.service.ai.filter;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ConsensusCalibrator {

    public CalibratedResult calibrate(ObjectiveScorer.ScoreResult objectiveScore,
                                       LlmAnalyzerService.LlmResult llmResult,
                                       BigDecimal originalSignalStrength) {
        String finalDecision;
        BigDecimal finalStrength;

        if (objectiveScore.getDecision() == ObjectiveScorer.DIRECT_ALLOW) {
            finalDecision = "ALLOW";
            finalStrength = originalSignalStrength;
        } else if (objectiveScore.getDecision() == ObjectiveScorer.DIRECT_REJECT) {
            finalDecision = "REJECT";
            finalStrength = BigDecimal.ZERO;
        } else {
            boolean llmAllows = "ALLOW".equalsIgnoreCase(llmResult.getDecision());
            if (llmAllows) {
                finalDecision = "ALLOW";
                BigDecimal suggested = llmResult.getSuggestedStrength();
                finalStrength = suggested.compareTo(BigDecimal.ZERO) > 0 ? suggested : originalSignalStrength;
            } else {
                finalDecision = "REJECT";
                finalStrength = BigDecimal.ZERO;
            }
        }

        return new CalibratedResult(finalDecision, finalStrength,
            objectiveScore.getTotalScore(), llmResult.getDecision());
    }

    public static class CalibratedResult {
        private final String finalDecision;
        private final BigDecimal adjustedStrength;
        private final int objectiveScore;
        private final String llmDecision;

        public CalibratedResult(String finalDecision, BigDecimal adjustedStrength,
                                int objectiveScore, String llmDecision) {
            this.finalDecision = finalDecision;
            this.adjustedStrength = adjustedStrength;
            this.objectiveScore = objectiveScore;
            this.llmDecision = llmDecision;
        }

        public String getFinalDecision() { return finalDecision; }
        public BigDecimal getAdjustedStrength() { return adjustedStrength; }
        public int getObjectiveScore() { return objectiveScore; }
        public String getLlmDecision() { return llmDecision; }
    }
}
