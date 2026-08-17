package com.chain.ai.trade.engine.service.ml;

import com.chain.ai.trade.engine.config.MlProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

@Slf4j
@Component
public class VolatilityModelTrainer {

    private final MlProperties mlProperties;

    public VolatilityModelTrainer(MlProperties mlProperties) {
        this.mlProperties = mlProperties;
    }

    public VolatilityResult calculate(BarSeries series) {
        int atrPeriod = mlProperties.getVolatility().getAtrPeriod();
        if (series.getBarCount() < atrPeriod + 1) {
            return new VolatilityResult(0, 0, 0, "LOW");
        }

        int endIndex = series.getEndIndex();
        int startIndex = Math.max(0, endIndex - mlProperties.getVolatility().getLookbackDays());

        double[] dailyReturns = new double[endIndex - startIndex];
        double[] trueRanges = new double[endIndex - startIndex - atrPeriod];
        double sumTrueRange = 0;

        for (int i = startIndex, j = 0; i < endIndex; i++, j++) {
            double close = series.getBar(i).getClosePrice().doubleValue();
            double prevClose = i > startIndex ? series.getBar(i - 1).getClosePrice().doubleValue() : close;
            dailyReturns[j] = (close - prevClose) / prevClose;

            if (j >= atrPeriod) {
                double high = series.getBar(i).getHighPrice().doubleValue();
                double low = series.getBar(i).getLowPrice().doubleValue();
                double prevCloseVal = series.getBar(i - 1).getClosePrice().doubleValue();
                double tr = Math.max(high - low, Math.max(Math.abs(high - prevCloseVal), Math.abs(low - prevCloseVal)));
                trueRanges[j - atrPeriod] = tr;
                sumTrueRange += tr;
            }
        }

        double atr = sumTrueRange / Math.max(trueRanges.length, 1);

        double mean = 0;
        for (double r : dailyReturns) mean += r;
        mean /= dailyReturns.length;

        double variance = 0;
        for (double r : dailyReturns) variance += Math.pow(r - mean, 2);
        variance /= dailyReturns.length;
        double stdDev = Math.sqrt(variance);

        double annualizedVol = stdDev * Math.sqrt(365);

        double atrPercent = atr / series.getBar(endIndex - 1).getClosePrice().doubleValue();
        String regime;
        if (atrPercent > 0.02) regime = "HIGH";
        else if (atrPercent > 0.008) regime = "MEDIUM";
        else regime = "LOW";

        return new VolatilityResult(annualizedVol, atr, stdDev, regime);
    }

    @Data
    public static class VolatilityResult {
        private final double annualizedVolatility;
        private final double atr;
        private final double dailyStdDev;
        private final String regime;

        public String getRegimeLabel() {
            switch (regime) {
                case "HIGH": return "高波动";
                case "MEDIUM": return "中波动";
                case "LOW": return "低波动";
                default: return regime;
            }
        }
    }

    @Data
    public static class VolatilityPrediction {
        private final double predictedVolatility;
        private final String regime;
        private final double atr;
        private final String suggestion;

        public VolatilityPrediction(double predictedVolatility, String regime, double atr) {
            this.predictedVolatility = predictedVolatility;
            this.regime = regime;
            this.atr = atr;
            if ("HIGH".equals(regime)) {
                this.suggestion = "建议降低仓位或使用止损";
            } else if ("MEDIUM".equals(regime)) {
                this.suggestion = "正常交易，注意风险";
            } else {
                this.suggestion = "适合正常交易";
            }
        }
    }
}
