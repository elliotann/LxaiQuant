package com.chain.ai.trade.engine.service.ml;

import com.chain.ai.trade.engine.config.MlProperties;
import com.chain.ai.trade.engine.service.FeatureEngineeringService;
import com.chain.ai.trade.engine.service.KLineV1Service;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryRequest;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import smile.model.cart.SplitRule;

import java.util.*;
import java.util.stream.Collectors;

import static com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum.OKXMIN60;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdBacktestService {

    private static final String[] FEATURE_NAMES = {"RSI", "MACD", "MACD_Signal", "EMADiff"};
    private static final double[] THRESHOLDS = {0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90};

    private final KLineV1Service kLineV1Service;
    private final FeatureEngineeringService featureEngineeringService;
    private final MLTrainingService mlTrainingService;
    private final MlProperties mlProperties;

    public ThresholdBacktestResult backtest(String symbol) {
        int lookaheadBars = mlProperties.getLabel().getHorizon();
        int fetchLimit = mlProperties.getTraining().getDefaultLookbackBars() + lookaheadBars + 60;

        KLineHistoryRequest request = new KLineHistoryRequest();
        request.setSymbol(symbol);
        request.setInterval(OKXMIN60.name());
        request.setLimit(fetchLimit);
        KLineHistoryResponse klineData = kLineV1Service.getKLineHistory(request);
        if (klineData == null || klineData.getKlines() == null || klineData.getKlines().isEmpty()) {
            return ThresholdBacktestResult.error("无法获取K线数据: symbol=" + symbol);
        }

        BarSeries series = mlTrainingService.convertToBarSeries(klineData.getKlines());
        if (series.getEndIndex() < FeatureEngineeringService.MIN_BARS + lookaheadBars) {
            return ThresholdBacktestResult.error("K线数据不足，需至少 " + (FeatureEngineeringService.MIN_BARS + lookaheadBars) + " 根");
        }

        FeatureEngineeringService.LabeledSample[] samples =
                featureEngineeringService.extractTrainingData(series, lookaheadBars);
        if (samples.length < 200) {
            return ThresholdBacktestResult.error("有效样本不足，需要至少200个，当前: " + samples.length);
        }

        Arrays.sort(samples, Comparator.comparingLong(FeatureEngineeringService.LabeledSample::getTimestamp));

        int trainSize = (int) (samples.length * 0.8);
        int testSize = samples.length - trainSize;

        double[][] trainFeatures = new double[trainSize][FeatureEngineeringService.FEATURE_DIMENSION];
        int[] trainLabels = new int[trainSize];
        double[][] testFeatures = new double[testSize][FeatureEngineeringService.FEATURE_DIMENSION];
        int[] testLabels = new int[testSize];

        for (int i = 0; i < trainSize; i++) {
            trainFeatures[i] = samples[i].getFeatures();
            trainLabels[i] = samples[i].getLabel();
        }
        for (int i = 0; i < testSize; i++) {
            testFeatures[i] = samples[trainSize + i].getFeatures();
            testLabels[i] = samples[trainSize + i].getLabel();
        }

        long trainPos = Arrays.stream(trainLabels).filter(l -> l == 1).count();
        long trainNeg = trainLabels.length - trainPos;
        long testPos = Arrays.stream(testLabels).filter(l -> l == 1).count();
        long testNeg = testLabels.length - testPos;

        log.info("阈值回测: symbol={}, 训练样本={}(涨={}/跌={}), 回测样本={}(涨={}/跌={})",
                symbol, trainSize, trainPos, trainNeg, testSize, testPos, testNeg);

        DataFrame trainDf = buildDataFrame(trainFeatures, trainLabels);
        int numTrees = 500;
        int maxDepth = 4;
        int minSamples = 40;

        RandomForest forest = RandomForest.fit(
                Formula.lhs("label"), trainDf,
                new RandomForest.Options(numTrees, 0, SplitRule.GINI, maxDepth, 0, minSamples, 1.0, null, null, null));

        List<SampleRecord> records = new ArrayList<>(testSize);
        for (int i = 0; i < testSize; i++) {
            DataFrame singleRow = buildFeatureDataFrame(testFeatures[i]);
            double[] posteriori = new double[forest.numClasses()];
            forest.predict(singleRow.get(0), posteriori);
            records.add(new SampleRecord(posteriori[1], testLabels[i]));
        }

        long totalPos = testPos;

        List<ThresholdResult> results = new ArrayList<>();
        for (double threshold : THRESHOLDS) {
            long tp = 0, fp = 0, fn = 0, tn = 0;
            for (SampleRecord r : records) {
                boolean predictedUp = r.probUp > threshold;
                if (predictedUp && r.actualLabel == 1) tp++;
                else if (predictedUp) fp++;
                else if (r.actualLabel == 1) fn++;
                else tn++;
            }

            long totalSignals = tp + fp;
            double precision = totalSignals > 0 ? (double) tp / totalSignals : 0;
            double recall = totalPos > 0 ? (double) tp / totalPos : 0;
            double accuracy = (double) (tp + tn) / records.size();
            double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;

            results.add(new ThresholdResult(threshold, totalSignals, tp, fp, fn, tn,
                    round4(precision), round4(recall), round4(accuracy), round4(f1)));
        }

        return ThresholdBacktestResult.success(symbol, samples.length, trainSize, testSize,
                totalPos, testNeg, results);
    }

    private DataFrame buildDataFrame(double[][] features, int[] labels) {
        DoubleVector[] vecs = new DoubleVector[FEATURE_NAMES.length];
        for (int j = 0; j < FEATURE_NAMES.length; j++) {
            double[] col = new double[features.length];
            for (int i = 0; i < features.length; i++) {
                col[i] = features[i][j];
            }
            vecs[j] = new DoubleVector(FEATURE_NAMES[j], col);
        }
        return new DataFrame(vecs).add(new IntVector("label", labels));
    }

    private DataFrame buildFeatureDataFrame(double[] features) {
        DoubleVector[] vecs = new DoubleVector[FEATURE_NAMES.length];
        for (int j = 0; j < FEATURE_NAMES.length; j++) {
            vecs[j] = new DoubleVector(FEATURE_NAMES[j], new double[]{features[j]});
        }
        return new DataFrame(vecs).add(new IntVector("label", new int[]{0}));
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    @Data
    public static class SampleRecord {
        private final double probUp;
        private final int actualLabel;
    }

    @Data
    public static class ThresholdResult {
        private final double threshold;
        private final long signalCount;
        private final long tp;
        private final long fp;
        private final long fn;
        private final long tn;
        private final double precision;
        private final double recall;
        private final double accuracy;
        private final double f1;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("threshold", threshold);
            m.put("signalCount", signalCount);
            m.put("tp", tp);
            m.put("fp", fp);
            m.put("fn", fn);
            m.put("tn", tn);
            m.put("precision", precision);
            m.put("recall", recall);
            m.put("accuracy", accuracy);
            m.put("f1", f1);
            return m;
        }
    }

    @Data
    public static class ThresholdBacktestResult {
        private final boolean success;
        private final String errorMsg;
        private final String symbol;
        private final long totalSamples;
        private final long trainSamples;
        private final long testSamples;
        private final long actualUp;
        private final long actualDown;
        private final List<Map<String, Object>> results;

        static ThresholdBacktestResult success(String symbol, long totalSamples, long trainSamples, long testSamples,
                                                long actualUp, long actualDown,
                                                List<ThresholdResult> results) {
            return new ThresholdBacktestResult(true, null, symbol, totalSamples, trainSamples, testSamples,
                    actualUp, actualDown,
                    results.stream().map(ThresholdResult::toMap).collect(Collectors.toList()));
        }

        static ThresholdBacktestResult error(String msg) {
            return new ThresholdBacktestResult(false, msg, null, 0, 0, 0, 0, 0, Collections.emptyList());
        }
    }
}
