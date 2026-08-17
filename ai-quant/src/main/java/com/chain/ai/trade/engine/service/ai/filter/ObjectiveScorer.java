package com.chain.ai.trade.engine.service.ai.filter;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ObjectiveScorer {

    public static final int DIRECT_ALLOW = 1;
    public static final int DIRECT_NEUTRAL = 0;
    public static final int DIRECT_REJECT = -1;

    public ScoreResult score(ScoreInput input, AiFilterConfigLoader.AiFilterConfig config) {
        int trendScore = scoreTrendConsistency(input, config.getTrendWeight());
        int volatilityScore = scoreVolatility(input, config.getVolatilityWeight());
        int srScore = scoreSupportResistance(input, config.getSupportResistanceWeight());
        int vpScore = scoreVolumePrice(input, config.getVolumePriceWeight());

        int total = trendScore + volatilityScore + srScore + vpScore;

        int decision;
        if (total >= config.getDirectAllowThreshold()) {
            decision = DIRECT_ALLOW;
        } else if (total < config.getDirectRejectThreshold()) {
            decision = DIRECT_REJECT;
        } else {
            decision = DIRECT_NEUTRAL;
        }

        return new ScoreResult(total, decision);
    }

    private int scoreTrendConsistency(ScoreInput input, BigDecimal weight) {
        boolean consistent = input.signalDirection.equalsIgnoreCase(input.weeklyTrend)
            || input.signalDirection.equalsIgnoreCase(input.trend4h);
        int base = consistent ? 70 : 20;
        return BigDecimal.valueOf(base).multiply(weight).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private int scoreVolatility(ScoreInput input, BigDecimal weight) {
        boolean reasonableVolatility = input.atr15m != null && input.atr15m.compareTo(BigDecimal.ZERO) > 0;
        int base = reasonableVolatility ? 60 : 30;
        return BigDecimal.valueOf(base).multiply(weight).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private int scoreSupportResistance(ScoreInput input, BigDecimal weight) {
        boolean hasRoom = input.distanceToResistance != null && input.distanceToSupport != null
            && input.distanceToResistance.compareTo(BigDecimal.valueOf(0.5)) > 0
            && input.distanceToSupport.compareTo(BigDecimal.valueOf(0.5)) > 0;
        int base = hasRoom ? 65 : 25;
        return BigDecimal.valueOf(base).multiply(weight).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private int scoreVolumePrice(ScoreInput input, BigDecimal weight) {
        boolean volumeConfirmed = input.volumeRatio != null && input.volumeRatio.compareTo(BigDecimal.valueOf(1.2)) > 0;
        int base = volumeConfirmed ? 60 : 35;
        return BigDecimal.valueOf(base).multiply(weight).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    public static class ScoreInput {
        private String signalDirection;
        private String weeklyTrend;
        private String trend4h;
        private BigDecimal atr15m;
        private BigDecimal distanceToResistance;
        private BigDecimal distanceToSupport;
        private BigDecimal volumeRatio;

        public String getSignalDirection() { return signalDirection; }
        public void setSignalDirection(String signalDirection) { this.signalDirection = signalDirection; }
        public String getWeeklyTrend() { return weeklyTrend; }
        public void setWeeklyTrend(String weeklyTrend) { this.weeklyTrend = weeklyTrend; }
        public String getTrend4h() { return trend4h; }
        public void setTrend4h(String trend4h) { this.trend4h = trend4h; }
        public BigDecimal getAtr15m() { return atr15m; }
        public void setAtr15m(BigDecimal atr15m) { this.atr15m = atr15m; }
        public BigDecimal getDistanceToResistance() { return distanceToResistance; }
        public void setDistanceToResistance(BigDecimal distanceToResistance) { this.distanceToResistance = distanceToResistance; }
        public BigDecimal getDistanceToSupport() { return distanceToSupport; }
        public void setDistanceToSupport(BigDecimal distanceToSupport) { this.distanceToSupport = distanceToSupport; }
        public BigDecimal getVolumeRatio() { return volumeRatio; }
        public void setVolumeRatio(BigDecimal volumeRatio) { this.volumeRatio = volumeRatio; }
    }

    public static class ScoreResult {
        private final int totalScore;
        private final int decision;

        public ScoreResult(int totalScore, int decision) {
            this.totalScore = totalScore;
            this.decision = decision;
        }

        public int getTotalScore() { return totalScore; }
        public int getDecision() { return decision; }
    }
}
