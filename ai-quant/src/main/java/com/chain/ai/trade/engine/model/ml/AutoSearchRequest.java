package com.chain.ai.trade.engine.model.ml;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AutoSearchRequest {
    private String symbol;
    private int maxCombinations = 500;
    private int minFeatures = 2;
    private int maxFeatures = 6;
    private Map<String, List<?>> featurePool;
    private ModelHyperparams modelParams = new ModelHyperparams();
    private ScoringWeights weights = new ScoringWeights();
    private ThresholdScanConfig thresholdScan = new ThresholdScanConfig();

    @Data
    public static class ModelHyperparams {
        private int numTrees = 500;
        private int maxDepth = 4;
        private int minSamples = 40;
    }

    @Data
    public static class ScoringWeights {
        private double f1 = 0.5;
        private double precision = 0.3;
        private double signalCount = 0.2;
    }

    @Data
    public static class ThresholdScanConfig {
        private double start = 0.50;
        private double end = 0.90;
        private double step = 0.05;
    }
}
