package com.chain.ai.trade.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ml")
public class MlProperties {

    private Model model = new Model();
    private Label label = new Label();
    private Training training = new Training();
    private Volatility volatility = new Volatility();
    private Inference inference = new Inference();
    private FactorMining factorMining = new FactorMining();

    @Data
    public static class Model {
        private String storageDir = "./models";
        private int lookaheadBars = 12;
        private int numTrees = 100;
        private int maxDepth = 10;
        private int minSamples = 5;
        private int maxVersionsPerSymbol = 5;
    }

    @Data
    public static class Label {
        private int horizon = 24;
        private double thresholdPct = 0.001;
    }

    @Data
    public static class Training {
        private int progressPollingIntervalMs = 2000;
        private int defaultLookbackBars = 2000;
    }

    @Data
    public static class Inference {
        private double probabilityThreshold = 0.65;
    }

    @Data
    public static class Volatility {
        private int atrPeriod = 14;
        private int lookbackDays = 30;
        private Clustering clustering = new Clustering();

        @Data
        public static class Clustering {
            private int nClusters = 3;
        }
    }

    @Data
    public static class FactorMining {
        private int defaultPopulationSize = 500;
        private int defaultGenerations = 20;
        private int defaultTournamentSize = 5;
        private double defaultCrossoverProb = 0.8;
        private double defaultMutationProb = 0.1;
        private double defaultParsimonyCoefficient = 0.001;
        private int defaultLookbackBars = 500;
        private int topK = 20;
        private int maxThreads = 4;
        private String defaultFitnessMetric = "RANK_IC";
    }
}
