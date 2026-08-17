package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.engine.config.MlProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import com.chain.ai.trade.engine.model.FeatureVector;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FeatureEngineeringService {

    public static final int FEATURE_DIMENSION = 4;
    public static final int MIN_BARS = 60;

    private final MlProperties mlProperties;

    public FeatureEngineeringService(MlProperties mlProperties) {
        this.mlProperties = mlProperties;
    }

    public FeatureVector extractFeatures(BarSeries series) {
        int endIndex = series.getEndIndex();
        if (endIndex < MIN_BARS) return null;

        double[] features = extractFeatureArray(series, endIndex);
        if (features == null) return null;

        FeatureVector vector = new FeatureVector();
        vector.setRsi(features[0]);
        vector.setMacd(features[1]);
        vector.setMacdSignal(features[2]);
        vector.setEmaDiff(features[3]);
        return vector;
    }

    public double[] extractFeatureArray(BarSeries series, int index) {
        if (index < MIN_BARS) return null;

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        double rsiValue = rsi.getValue(index).doubleValue();

        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        EMAIndicator macdSignal = new EMAIndicator(macd, 9);
        double macdValue = macd.getValue(index).doubleValue();
        double macdSignalValue = macdSignal.getValue(index).doubleValue();

        EMAIndicator ema20 = new EMAIndicator(closePrice, 20);
        EMAIndicator ema60 = new EMAIndicator(closePrice, 60);
        double ema20Value = ema20.getValue(index).doubleValue();
        double ema60Value = ema60.getValue(index).doubleValue();
        double emaDiff = ema60Value > 0 ? (ema20Value - ema60Value) / ema60Value * 100 : 0;

        double[] result = new double[]{rsiValue, macdValue, macdSignalValue, emaDiff};
        for (int i = 0; i < result.length; i++) {
            if (Double.isNaN(result[i]) || Double.isInfinite(result[i])) {
                result[i] = 0;
            }
        }
        return result;
    }

    public LabeledSample[] extractTrainingData(BarSeries series, int lookaheadBars) {
        int endIndex = series.getEndIndex();
        if (endIndex < MIN_BARS + lookaheadBars) return new LabeledSample[0];

        List<LabeledSample> samples = new ArrayList<>();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        for (int i = MIN_BARS; i <= endIndex - lookaheadBars; i++) {
            double[] features = extractFeatureArray(series, i);
            if (features == null) continue;

            double currentClose = closePrice.getValue(i).doubleValue();
            double futureClose = closePrice.getValue(i + lookaheadBars).doubleValue();
            double futureReturnPct = ((futureClose - currentClose) / currentClose) * 100;

            double pastMeanReturn = computeMeanReturn(closePrice, i - 20, i);
            int label = futureReturnPct > pastMeanReturn ? 1 : 0;

            long timestamp = series.getBar(i).getEndTime().toEpochMilli();
            samples.add(new LabeledSample(features, label, futureReturnPct, timestamp));
        }

        return samples.toArray(new LabeledSample[0]);
    }

    public List<FeatureVector> extractBatch(BarSeries series) {
        List<FeatureVector> result = new ArrayList<>();
        for (int i = MIN_BARS; i <= series.getEndIndex(); i++) {
            double[] features = extractFeatureArray(series, i);
            if (features == null) continue;

            FeatureVector fv = new FeatureVector();
            fv.setRsi(features[0]);
            fv.setMacd(features[1]);
            fv.setMacdSignal(features[2]);
            fv.setEmaDiff(features[3]);
            result.add(fv);
        }
        return result;
    }

    private double computeMeanReturn(ClosePriceIndicator closePrice, int from, int to) {
        int start = Math.max(from, MIN_BARS);
        if (start >= to) return 0;
        double sum = 0;
        int count = 0;
        for (int i = start; i < to; i++) {
            double prev = closePrice.getValue(i - 1).doubleValue();
            double curr = closePrice.getValue(i).doubleValue();
            if (prev != 0) {
                sum += (curr - prev) / prev * 100;
                count++;
            }
        }
        return count > 0 ? sum / count : 0;
    }

    public static class LabeledSample {
        private final double[] features;
        private final int label;
        private final double futureReturnPct;
        private final long timestamp;

        public LabeledSample(double[] features, int label, double futureReturnPct, long timestamp) {
            this.features = features;
            this.label = label;
            this.futureReturnPct = futureReturnPct;
            this.timestamp = timestamp;
        }

        public double[] getFeatures() { return features; }
        public int getLabel() { return label; }
        public double getFutureReturnPct() { return futureReturnPct; }
        public long getTimestamp() { return timestamp; }
    }
}
