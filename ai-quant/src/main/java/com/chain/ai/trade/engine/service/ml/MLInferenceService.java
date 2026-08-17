package com.chain.ai.trade.engine.service.ml;

import com.chain.ai.trade.engine.config.MlProperties;
import com.chain.ai.trade.engine.model.ml.PredictionResult;
import com.chain.ai.trade.engine.service.FeatureEngineeringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MLInferenceService {

    private static final String[] FEATURE_NAMES = {"RSI", "MACD", "MACD_Signal", "EMADiff"};

    private final FeatureEngineeringService featureEngineeringService;
    private final ModelStorageService modelStorageService;
    private final VolatilityModelTrainer volatilityModelTrainer;
    private final MarketStateClusterService marketStateClusterService;
    private final MlProperties mlProperties;

    public MLInferenceService(FeatureEngineeringService featureEngineeringService,
                               ModelStorageService modelStorageService,
                               VolatilityModelTrainer volatilityModelTrainer,
                               MarketStateClusterService marketStateClusterService,
                               MlProperties mlProperties) {
        this.featureEngineeringService = featureEngineeringService;
        this.modelStorageService = modelStorageService;
        this.volatilityModelTrainer = volatilityModelTrainer;
        this.marketStateClusterService = marketStateClusterService;
        this.mlProperties = mlProperties;
    }

    private final Map<String, RandomForest> modelCache = new ConcurrentHashMap<>();

    public PredictionResult predictDirection(BarSeries series, String symbol) {
        double[] features = featureEngineeringService.extractFeatureArray(series, series.getEndIndex());
        if (features == null) {
            return PredictionResult.insufficientData();
        }

        RandomForest forest = modelCache.computeIfAbsent(symbol + ":DIRECTION",
                k -> modelStorageService.loadModel(symbol, "DIRECTION"));
        if (forest == null) {
            return PredictionResult.noModel();
        }

        DataFrame singleRow = buildFeatureDataFrame(features);

        double[] posteriori = new double[forest.numClasses()];
        int prediction = forest.predict(singleRow.get(0), posteriori);

        double probUp = posteriori[1];
        double threshold = mlProperties.getInference().getProbabilityThreshold();
        String direction;
        double confidence;

        if (probUp > threshold) {
            direction = "BUY";
            confidence = (probUp - threshold) / (1 - threshold);
        } else if (probUp < 1 - threshold) {
            direction = "SELL";
            confidence = ((1 - threshold) - probUp) / (1 - threshold);
        } else {
            direction = "HOLD";
            confidence = 0;
        }

        PredictionResult result = new PredictionResult();
        result.setSuccess(true);
        result.setDirection(direction);
        result.setProbabilityUp(probUp);
        result.setProbabilityDown(posteriori[0]);
        result.setConfidence(confidence);

        log.info("预测完成: symbol={}, direction={}, confidence={}, probUp={}, threshold={}", symbol, direction, String.format("%.4f", confidence), String.format("%.4f", probUp), threshold);
        return result;
    }

    public VolatilityModelTrainer.VolatilityPrediction predictVolatility(BarSeries series) {
        VolatilityModelTrainer.VolatilityResult volResult = volatilityModelTrainer.calculate(series);
        return new VolatilityModelTrainer.VolatilityPrediction(
                volResult.getAnnualizedVolatility(),
                volResult.getRegime(),
                volResult.getAtr()
        );
    }

    public MarketStateClusterService.MarketStateCluster getMarketState(BarSeries series) {
        return marketStateClusterService.cluster(series);
    }

    public void evictCache(String symbol, String modelType) {
        modelCache.remove(symbol + ":" + modelType);
        log.info("模型缓存已清除: symbol={}, type={}", symbol, modelType);
    }

    private DataFrame buildFeatureDataFrame(double[] features) {
        DoubleVector[] vecs = new DoubleVector[FEATURE_NAMES.length];
        for (int j = 0; j < FEATURE_NAMES.length; j++) {
            vecs[j] = new DoubleVector(FEATURE_NAMES[j], new double[]{features[j]});
        }
        return new DataFrame(vecs).add(new IntVector("label", new int[]{0}));
    }
}
