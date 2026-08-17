package com.chain.ai.trade.engine.service.ml;

import com.chain.ai.trade.engine.config.MlProperties;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryRequest;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryResponse;
import com.chain.ai.trade.engine.mapper.AutoSearchResultMapper;
import com.chain.ai.trade.engine.model.ml.ApplyFeatureRequest;
import com.chain.ai.trade.engine.model.ml.AutoSearchRequest;
import com.chain.ai.trade.engine.model.ml.AutoSearchResult;
import com.chain.ai.trade.engine.service.FeatureEngineeringService;
import com.chain.ai.trade.engine.service.KLineV1Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import smile.model.cart.SplitRule;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum.OKXMIN60;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSearchService {

    private static final String[] FEATURE_TYPE_NAMES = {
            "RSI", "MACD", "MACD_Signal", "EMADiff", "ATR", "VolumeRatio",
            "PriceChange", "StochasticK", "BollingerB", "LinearRegSlope"
    };

    private static final Map<String, List<?>> DEFAULT_FEATURE_POOL = new LinkedHashMap<>();

    static {
        DEFAULT_FEATURE_POOL.put("RSI", Arrays.asList(7, 14, 21, 28));
        DEFAULT_FEATURE_POOL.put("MACD", Arrays.asList(
                Arrays.asList(5, 13, 5), Arrays.asList(12, 26, 9), Arrays.asList(19, 39, 13)));
        DEFAULT_FEATURE_POOL.put("MACD_Signal", Arrays.asList(
                Arrays.asList(5, 13, 5), Arrays.asList(12, 26, 9), Arrays.asList(19, 39, 13)));
        DEFAULT_FEATURE_POOL.put("EMADiff", Arrays.asList(
                Arrays.asList(5, 20), Arrays.asList(10, 30), Arrays.asList(20, 60),
                Arrays.asList(30, 90), Arrays.asList(50, 200)));
        DEFAULT_FEATURE_POOL.put("ATR", Arrays.asList(7, 14, 21));
        DEFAULT_FEATURE_POOL.put("VolumeRatio", Arrays.asList(
                Arrays.asList(5, 20), Arrays.asList(10, 30)));
        DEFAULT_FEATURE_POOL.put("PriceChange", Arrays.asList(1, 3, 5));
        DEFAULT_FEATURE_POOL.put("StochasticK", Arrays.asList(7, 14, 21));
        DEFAULT_FEATURE_POOL.put("BollingerB", Arrays.asList(14, 20, 26));
        DEFAULT_FEATURE_POOL.put("LinearRegSlope", Arrays.asList(5, 10, 20));
    }

    private final KLineV1Service kLineV1Service;
    private final FeatureEngineeringService featureEngineeringService;
    private final MLTrainingService mlTrainingService;
    private final MlProperties mlProperties;
    private final AutoSearchResultMapper autoSearchResultMapper;
    private final ObjectMapper objectMapper;

    private final Map<String, Boolean> stopFlags = new ConcurrentHashMap<>();

    public Map<String, Object> getFeaturePoolInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String type : FEATURE_TYPE_NAMES) {
            result.put(type, DEFAULT_FEATURE_POOL.get(type));
        }
        return result;
    }

    public AutoSearchResult startSearch(AutoSearchRequest request) {
        String searchId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        AutoSearchResult entity = new AutoSearchResult();
        entity.setSearchId(searchId);
        entity.setSymbol(request.getSymbol());
        entity.setStatus("RUNNING");
        entity.setTotalCombinations(request.getMaxCombinations());
        entity.setCompletedCombinations(0);
        entity.setStartTime(new Date());
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        try {
            entity.setRequestJson(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            entity.setRequestJson("{}");
        }
        autoSearchResultMapper.insert(entity);

        Map<String, List<?>> featurePool = request.getFeaturePool() != null
                ? request.getFeaturePool() : DEFAULT_FEATURE_POOL;

        stopFlags.put(searchId, false);

        CompletableFuture.runAsync(() -> runSearch(searchId, request, featurePool))
                .exceptionally(e -> {
                    log.error("自动搜索异常: searchId={}", searchId, e);
                    autoSearchResultMapper.updateResult(searchId, "FAILED", 0, null, null, new Date(),
                            e.getMessage() != null ? e.getMessage() : "未知错误");
                    return null;
                });

        return autoSearchResultMapper.findBySearchId(searchId);
    }

    private void runSearch(String searchId, AutoSearchRequest request, Map<String, List<?>> featurePool) {
        int lookaheadBars = mlProperties.getLabel().getHorizon();

        KLineHistoryRequest klineReq = new KLineHistoryRequest();
        klineReq.setSymbol(request.getSymbol());
        klineReq.setInterval(OKXMIN60.name());
        klineReq.setLimit(request.getMaxCombinations() > 1000 ? 3000 : 2000);
        KLineHistoryResponse klineData = kLineV1Service.getKLineHistory(klineReq);
        if (klineData == null || klineData.getKlines() == null || klineData.getKlines().isEmpty()) {
            autoSearchResultMapper.updateResult(searchId, "FAILED", 0, null, null, new Date(), "无法获取K线数据");
            return;
        }

        BarSeries series = mlTrainingService.convertToBarSeries(klineData.getKlines());
        int totalBars = series.getBarCount();
        if (totalBars < FeatureEngineeringService.MIN_BARS + lookaheadBars) {
            autoSearchResultMapper.updateResult(searchId, "FAILED", 0, null, null, new Date(), "K线数据不足");
            return;
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        int labelStart = FeatureEngineeringService.MIN_BARS;
        int labelEnd = totalBars - lookaheadBars;
        int validBars = labelEnd - labelStart;
        if (validBars < 200) {
            autoSearchResultMapper.updateResult(searchId, "FAILED", 0, null, null, new Date(),
                    "有效K线不足，需要至少200根，当前" + validBars);
            return;
        }

        int[] labels = new int[validBars];
        for (int i = 0; i < validBars; i++) {
            int idx = labelStart + i;
            double futureReturn = (closePrice.getValue(idx + lookaheadBars).doubleValue()
                    - closePrice.getValue(idx).doubleValue()) / closePrice.getValue(idx).doubleValue() * 100;
            double pastMeanReturn = computeMeanReturn(closePrice, idx - 20, idx);
            labels[i] = futureReturn > pastMeanReturn ? 1 : 0;
        }

        int trainSize = (int) (validBars * 0.8);
        int testSize = validBars - trainSize;

        List<DynamicFeature> allVariants = FeatureFactory.createAllVariants(series, featurePool);
        if (allVariants.isEmpty()) {
            autoSearchResultMapper.updateResult(searchId, "FAILED", 0, null, null, new Date(), "特征池为空");
            return;
        }

        log.info("特征池创建了 {} 个变体: {}", allVariants.size(),
                allVariants.stream().map(DynamicFeature::getVariantName).collect(Collectors.toList()));

        Map<String, double[]> variantData = new LinkedHashMap<>();
        for (DynamicFeature v : allVariants) {
            double[] values = new double[validBars];
            for (int i = 0; i < validBars; i++) {
                double val = v.extract(labelStart + i);
                values[i] = Double.isFinite(val) ? val : 0;
            }
            variantData.put(v.getVariantName(), values);
        }

        List<String> variantNames = new ArrayList<>(variantData.keySet());
        int totalVariants = variantNames.size();

        int maxCombinations = Math.min(request.getMaxCombinations(), 2000);

        int maxPossible = 0;
        int minF = request.getMinFeatures();
        int maxF = Math.min(request.getMaxFeatures(), totalVariants);
        for (int k = minF; k <= maxF; k++) {
            maxPossible += nCr(totalVariants, k);
        }
        int targetCombos = Math.min(maxCombinations, maxPossible);

        List<FeatureCombination> combos = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();
        int maxAttempts = targetCombos * 10;
        int attempts = 0;
        while (combos.size() < targetCombos && attempts < maxAttempts) {
            attempts++;
            int featureCount = ThreadLocalRandom.current().nextInt(minF, maxF + 1);
            List<String> selected = new ArrayList<>(variantNames);
            Collections.shuffle(selected, ThreadLocalRandom.current());
            List<String> chosen = selected.subList(0, featureCount);
            List<String> sorted = new ArrayList<>(chosen);
            Collections.sort(sorted);
            String signature = String.join(",", sorted);
            if (seenSignatures.add(signature)) {
                combos.add(new FeatureCombination(new ArrayList<>(chosen)));
            }
        }
        log.info("生成去重特征组合: 目标={}, 实际={}, 总变体数={}", targetCombos, combos.size(), totalVariants);

        List<EvaluatedCombination> evaluated = new ArrayList<>();
        EvaluatedCombination bestSoFar = null;

        int numTrees = request.getModelParams().getNumTrees();
        int maxDepth = request.getModelParams().getMaxDepth();
        int minSamples = request.getModelParams().getMinSamples();

        for (int c = 0; c < combos.size(); c++) {
            if (Boolean.TRUE.equals(stopFlags.get(searchId))) {
                log.info("搜索被终止: searchId={}", searchId);
                break;
            }

            FeatureCombination combo = combos.get(c);

            int dim = combo.getFeatureNames().size();
            double[][] trainFeatures = new double[dim][trainSize];
            double[][] testFeatures = new double[dim][testSize];
            int[] trainLabelsArr = new int[trainSize];
            int[] testLabelsArr = new int[testSize];

            System.arraycopy(labels, 0, trainLabelsArr, 0, trainSize);
            System.arraycopy(labels, trainSize, testLabelsArr, 0, testSize);

            for (int f = 0; f < dim; f++) {
                double[] vals = variantData.get(combo.getFeatureNames().get(f));
                System.arraycopy(vals, 0, trainFeatures[f], 0, trainSize);
                System.arraycopy(vals, trainSize, testFeatures[f], 0, testSize);
            }

            double[][] testData = transpose(testFeatures);

            DataFrame trainDf = buildDataFrame(trainFeatures, trainLabelsArr, combo.getFeatureNames());
            RandomForest forest = RandomForest.fit(
                    Formula.lhs("label"), trainDf,
                    new RandomForest.Options(numTrees, 0, SplitRule.GINI, maxDepth, 0, minSamples, 1.0, null, null, null));

            EvaluatedCombination eval = evaluate(forest, testData, testLabelsArr, request, combo.getFeatureNames());
            eval.getFeatureNames().addAll(combo.getFeatureNames());
            evaluated.add(eval);

            if (bestSoFar == null || eval.getScore() > bestSoFar.getScore()) {
                bestSoFar = eval;
                try {
                    autoSearchResultMapper.updateProgress(searchId, c + 1, objectMapper.writeValueAsString(bestSoFar.toMap()));
                } catch (JsonProcessingException ignored) {}
            }

            if ((c + 1) % 50 == 0) {
                autoSearchResultMapper.updateProgress(searchId, c + 1,
                        bestSoFar != null ? tryToJson(bestSoFar.toMap()) : null);
                log.info("自动搜索进度: searchId={}, {}/{}", searchId, c + 1, combos.size());
            }
        }

        evaluated.sort(Comparator.comparing(EvaluatedCombination::getScore).reversed());
        List<EvaluatedCombination> top20 = evaluated.subList(0, Math.min(20, evaluated.size()));

        try {
            String finalTop20 = objectMapper.writeValueAsString(top20.stream()
                    .map(EvaluatedCombination::toMap).collect(Collectors.toList()));
            autoSearchResultMapper.updateResult(searchId, "DONE", evaluated.size(),
                    bestSoFar != null ? tryToJson(bestSoFar.toMap()) : null,
                    finalTop20, new Date(), null);
        } catch (Exception e) {
            log.error("保存搜索结果失败", e);
        }

        stopFlags.remove(searchId);
        log.info("自动搜索完成: searchId={}, 评估了{}组合", searchId, evaluated.size());
    }

    private EvaluatedCombination evaluate(RandomForest forest, double[][] testData,
                                           int[] testLabels, AutoSearchRequest req,
                                           List<String> featureNames) {
        double[] probs = new double[testData.length];

        for (int i = 0; i < testData.length; i++) {
            DataFrame row = buildSingleRow(testData[i], featureNames);
            double[] posteriori = new double[forest.numClasses()];
            forest.predict(row.get(0), posteriori);
            probs[i] = posteriori[1];
        }

        double bestF1 = 0, bestPrecision = 0, bestRecall = 0, bestThreshold = req.getThresholdScan().getStart();
        long bestTp = 0, bestFp = 0, bestFn = 0, bestTn = 0;

        long totalPos = Arrays.stream(testLabels).filter(l -> l == 1).count();

        for (double th = req.getThresholdScan().getStart();
             th <= req.getThresholdScan().getEnd() + 1e-8;
             th += req.getThresholdScan().getStep()) {
            long tp = 0, fp = 0, fn = 0, tn = 0;
            for (int i = 0; i < testLabels.length; i++) {
                if (probs[i] > th) {
                    if (testLabels[i] == 1) tp++; else fp++;
                } else {
                    if (testLabels[i] == 1) fn++; else tn++;
                }
            }
            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
            double recall = totalPos > 0 ? (double) tp / totalPos : 0;
            double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;
            if (f1 > bestF1) {
                bestF1 = f1;
                bestPrecision = precision;
                bestRecall = recall;
                bestThreshold = th;
                bestTp = tp;
                bestFp = fp;
                bestFn = fn;
                bestTn = tn;
            }
        }

        long signalCount = bestTp + bestFp;
        double normSignal = Math.min(1.0, signalCount / (testLabels.length * 0.15));
        double score = bestF1 * req.getWeights().getF1()
                + bestPrecision * req.getWeights().getPrecision()
                + normSignal * req.getWeights().getSignalCount();

        EvaluatedCombination result = new EvaluatedCombination();
        result.setBestThreshold(round4(bestThreshold));
        result.setPrecision(round4(bestPrecision));
        result.setRecall(round4(bestRecall));
        result.setF1(round4(bestF1));
        result.setScore(round4(score));
        result.setSignalCount(signalCount);
        result.setTp(bestTp);
        result.setFp(bestFp);
        result.setFn(bestFn);
        result.setTn(bestTn);
        return result;
    }

    public void stopSearch(String searchId) {
        stopFlags.put(searchId, true);
    }

    public String applyFeatureCombination(String symbol, List<String> featureNames) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("symbol", symbol);
        config.put("activeFeatures", featureNames);
        config.put("appliedAt", new Date().toString());
        String configJson = tryToJson(config);

        AutoSearchResult entity = new AutoSearchResult();
        entity.setSearchId("APPLY_" + symbol + "_" + System.currentTimeMillis());
        entity.setSymbol(symbol);
        entity.setStatus("APPLIED");
        entity.setTotalCombinations(0);
        entity.setCompletedCombinations(0);
        entity.setRequestJson(configJson);
        entity.setFinalTop20(configJson);
        entity.setStartTime(new Date());
        entity.setEndTime(new Date());
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        autoSearchResultMapper.insert(entity);

        log.info("特征组合已应用: symbol={}, features={}", symbol, featureNames);
        return configJson;
    }

    private double computeMeanReturn(ClosePriceIndicator closePrice, int from, int to) {
        int start = Math.max(from, FeatureEngineeringService.MIN_BARS);
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

    private static long nCr(int n, int r) {
        if (r < 0 || r > n) return 0;
        if (r == 0 || r == n) return 1;
        r = Math.min(r, n - r);
        long result = 1;
        for (int i = 1; i <= r; i++) {
            result = result * (n - r + i) / i;
        }
        return result;
    }

    private static double[][] transpose(double[][] columns) {
        int rows = columns[0].length;
        int cols = columns.length;
        double[][] result = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                result[r][c] = columns[c][r];
            }
        }
        return result;
    }

    private static DataFrame buildDataFrame(double[][] data, int[] labels, List<String> featureNames) {
        DoubleVector[] vecs = new DoubleVector[featureNames.size()];
        for (int j = 0; j < featureNames.size(); j++) {
            vecs[j] = new DoubleVector(featureNames.get(j), data[j]);
        }
        return new DataFrame(vecs).add(new IntVector("label", labels));
    }

    private static DataFrame buildSingleRow(double[] features, List<String> featureNames) {
        DoubleVector[] vecs = new DoubleVector[features.length];
        for (int j = 0; j < features.length; j++) {
            vecs[j] = new DoubleVector(featureNames.get(j), new double[]{features[j]});
        }
        return new DataFrame(vecs).add(new IntVector("label", new int[]{0}));
    }

    private String tryToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    @Data
    public static class FeatureCombination {
        private final List<String> featureNames;
    }

    @Data
    public static class EvaluatedCombination {
        private final List<String> featureNames = new ArrayList<>();
        private double bestThreshold;
        private double precision;
        private double recall;
        private double f1;
        private double score;
        private long signalCount;
        private long tp, fp, fn, tn;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("tp", tp);
            cm.put("fp", fp);
            cm.put("fn", fn);
            cm.put("tn", tn);
            m.put("features", featureNames);
            m.put("featureCount", featureNames.size());
            m.put("bestThreshold", bestThreshold);
            m.put("precision", precision);
            m.put("recall", recall);
            m.put("f1", f1);
            m.put("score", score);
            m.put("signalCount", signalCount);
            m.put("confusionMatrix", cm);
            return m;
        }
    }
}
