package com.chain.ai.trade.engine.service.ml;

import com.chain.ai.trade.engine.config.MlProperties;
import com.chain.ai.trade.engine.model.ml.ModelMetrics;
import com.chain.ai.trade.engine.service.FeatureEngineeringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smile.model.cart.SplitRule;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class DirectionModelTrainer {

    private final MlProperties mlProperties;
    private static final String[] FEATURE_NAMES = {"RSI", "MACD", "MACD_Signal", "EMADiff"};

    public DirectionModelTrainer(MlProperties mlProperties) {
        this.mlProperties = mlProperties;
    }

    @PostConstruct
    public void logConfig() {
        log.info("ML训练配置 - horizon={}, thresholdPct={}%, numTrees={}, maxDepth={}, minSamples={}",
                mlProperties.getLabel().getHorizon(),
                mlProperties.getLabel().getThresholdPct() * 100,
                mlProperties.getModel().getNumTrees(),
                mlProperties.getModel().getMaxDepth(),
                mlProperties.getModel().getMinSamples());
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int completedTrees, int totalTrees, double currentAccuracy);
    }

    public TrainingResult train(FeatureEngineeringService.LabeledSample[] samples) {
        return train(samples, null);
    }

    public TrainingResult train(FeatureEngineeringService.LabeledSample[] samples, ProgressCallback callback) {
        if (samples.length < 100) {
            throw new IllegalArgumentException("训练样本不足，需要至少 100 个样本，当前: " + samples.length);
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
        log.info("训练集标签分布 - 正样本(涨): {} ({}%), 负样本(跌): {} ({}%)",
                trainPos, String.format("%.1f", 100.0 * trainPos / trainLabels.length),
                trainNeg, String.format("%.1f", 100.0 * trainNeg / trainLabels.length));

        long testPos = Arrays.stream(testLabels).filter(l -> l == 1).count();
        long testNeg = testLabels.length - testPos;
        log.info("测试集标签分布 - 正样本(涨): {} ({}%), 负样本(跌): {} ({}%)",
                testPos, String.format("%.1f", 100.0 * testPos / testLabels.length),
                testNeg, String.format("%.1f", 100.0 * testNeg / testLabels.length));

        log.info("训练集时间范围: {} ~ {}",
                Instant.ofEpochMilli(samples[0].getTimestamp()),
                Instant.ofEpochMilli(samples[trainSize - 1].getTimestamp()));
        log.info("测试集时间范围: {} ~ {}",
                Instant.ofEpochMilli(samples[trainSize].getTimestamp()),
                Instant.ofEpochMilli(samples[samples.length - 1].getTimestamp()));

        int numTrees = 500;
        int maxDepth = 4;
        int minSamples = 40;

        DataFrame trainDf = buildDataFrame(trainFeatures, trainLabels, true);
        DataFrame testDf = buildDataFrame(testFeatures, testLabels, true);

        long startTime = System.currentTimeMillis();

        RandomForest forest = RandomForest.fit(
                Formula.lhs("label"), trainDf,
                new RandomForest.Options(numTrees, 0, SplitRule.GINI, maxDepth, 0, minSamples, 1.0, null, null, null));

        long trainingDurationMs = System.currentTimeMillis() - startTime;

        int[] predictions = forest.predict(testDf);

        int correct = 0, tp = 0, fp = 0, tn = 0, fn = 0;
        for (int i = 0; i < predictions.length; i++) {
            if (predictions[i] == testLabels[i]) {
                correct++;
                if (predictions[i] == 1) tp++;
                else tn++;
            } else {
                if (predictions[i] == 1) fp++;
                else fn++;
            }
        }

        double accuracy = (double) correct / predictions.length;
        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
        double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;

        log.info("方向模型训练完成 - accuracy={}, precision={}, recall={}, f1={}, 训练样本={}, 测试样本={}",
                String.format("%.4f", accuracy), String.format("%.4f", precision),
                String.format("%.4f", recall), String.format("%.4f", f1),
                trainSize, testSize);

        Map<String, Integer> confusionMatrix = new LinkedHashMap<>();
        confusionMatrix.put("tp", tp);
        confusionMatrix.put("fp", fp);
        confusionMatrix.put("fn", fn);
        confusionMatrix.put("tn", tn);

        ModelMetrics metrics = ModelMetrics.builder()
                .accuracy(BigDecimal.valueOf(accuracy).setScale(4, RoundingMode.HALF_UP))
                .precision(BigDecimal.valueOf(precision).setScale(4, RoundingMode.HALF_UP))
                .recall(BigDecimal.valueOf(recall).setScale(4, RoundingMode.HALF_UP))
                .f1Score(BigDecimal.valueOf(f1).setScale(4, RoundingMode.HALF_UP))
                .totalSamples(testSize)
                .correctPredictions(correct)
                .featureImportance(extractFeatureImportance(forest))
                .confusionMatrix(confusionMatrix)
                .build();

        if (callback != null) {
            callback.onProgress(numTrees, numTrees, accuracy);
        }

        return new TrainingResult(forest, metrics, trainingDurationMs);
    }

    private DataFrame buildDataFrame(double[][] features, int[] labels, boolean includeLabel) {
        DoubleVector[] vecs = new DoubleVector[FEATURE_NAMES.length];
        for (int j = 0; j < FEATURE_NAMES.length; j++) {
            double[] col = new double[features.length];
            for (int i = 0; i < features.length; i++) {
                col[i] = features[i][j];
            }
            vecs[j] = new DoubleVector(FEATURE_NAMES[j], col);
        }
        DataFrame df = new DataFrame(vecs);
        if (includeLabel) {
            return df.add(new IntVector("label", labels));
        }
        return df;
    }

    private Map<String, Double> extractFeatureImportance(RandomForest forest) {
        Map<String, Double> importance = new LinkedHashMap<>();
        double[] raw = forest.importance();
        if (raw == null || raw.length == 0) {
            double equal = 1.0 / FEATURE_NAMES.length;
            for (String name : FEATURE_NAMES) {
                importance.put(name, equal);
            }
            return importance;
        }
        double total = 0;
        for (double v : raw) total += v;
        for (int i = 0; i < FEATURE_NAMES.length && i < raw.length; i++) {
            importance.put(FEATURE_NAMES[i], total > 0 ? raw[i] / total : 1.0 / FEATURE_NAMES.length);
        }
        return importance;
    }

    public int getLookaheadBars() {
        return mlProperties.getLabel().getHorizon();
    }

    public int getNumTrees() {
        return mlProperties.getModel().getNumTrees();
    }

    public static class TrainingResult {
        private final RandomForest model;
        private final ModelMetrics metrics;
        private final long trainingDurationMs;

        public TrainingResult(RandomForest model, ModelMetrics metrics, long trainingDurationMs) {
            this.model = model;
            this.metrics = metrics;
            this.trainingDurationMs = trainingDurationMs;
        }

        public RandomForest getModel() { return model; }
        public ModelMetrics getMetrics() { return metrics; }
        public long getTrainingDurationMs() { return trainingDurationMs; }
    }
}
