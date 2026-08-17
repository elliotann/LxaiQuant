package com.chain.ai.trade.engine.controller.ml;

import com.chain.ai.trade.engine.entity.MlModel;
import com.chain.ai.trade.engine.entity.MlTrainingJob;
import com.chain.ai.trade.engine.mapper.MlModelMapper;
import com.chain.ai.trade.engine.model.ml.PredictionResult;
import com.chain.ai.trade.engine.service.FeatureEngineeringService;
import com.chain.ai.trade.engine.service.KLineV1Service;
import com.chain.ai.trade.engine.service.ml.*;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryRequest;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import static com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum.OKXMIN60;
import org.springframework.web.bind.annotation.*;
import org.ta4j.core.BarSeries;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
public class MlModelController {

    private final MLTrainingService mlTrainingService;
    private final ModelStorageService modelStorageService;
    private final MLInferenceService mlInferenceService;
    private final KLineV1Service kLineV1Service;
    private final FeatureEngineeringService featureEngineeringService;
    private final MlModelMapper mlModelMapper;
    private final ObjectMapper objectMapper;
    private final TrainingProgressService trainingProgressService;
    private final ThresholdBacktestService thresholdBacktestService;

    @PostMapping("/train/{symbol}")
    public ApiResponse<MlTrainingJob> trainModel(@PathVariable String symbol,
                                                   @RequestParam(defaultValue = "DIRECTION") String modelType) {
        MlTrainingJob job = mlTrainingService.trainDirectionModel(symbol);
        return ApiResponse.success(job);
    }

    @PostMapping("/train/{symbol}/async")
    public ApiResponse<Map<String, String>> trainModelAsync(@PathVariable String symbol) {
        CompletableFuture<MlTrainingJob> future = mlTrainingService.trainDirectionModelAsync(symbol);
        Map<String, String> response = new LinkedHashMap<>();
        response.put("symbol", symbol);
        response.put("status", "TRAINING_STARTED");
        response.put("message", "训练任务已异步启动");
        return ApiResponse.success(response);
    }

    @GetMapping("/training/progress/{jobId}")
    public ApiResponse<TrainingProgressService.TrainingProgress> getTrainingProgress(@PathVariable String jobId) {
        TrainingProgressService.TrainingProgress progress = trainingProgressService.getProgress(jobId);
        if (progress == null) {
            return ApiResponse.error(404, "未找到训练进度");
        }
        return ApiResponse.success(progress);
    }

    @GetMapping("/predict/{symbol}")
    public ApiResponse<PredictionResult> predict(@PathVariable String symbol) {
        KLineHistoryRequest request = new KLineHistoryRequest();
        request.setSymbol(symbol);
        request.setInterval(OKXMIN60.name());
        request.setLimit(100);
        KLineHistoryResponse klineData = kLineV1Service.getKLineHistory(request);
        if (klineData == null || klineData.getKlines() == null || klineData.getKlines().isEmpty()) {
            return ApiResponse.error(400, "无法获取K线数据");
        }

        BarSeries series = mlTrainingService.convertToBarSeries(klineData.getKlines());
        PredictionResult result = mlInferenceService.predictDirection(series, symbol);
        return ApiResponse.success(result);
    }

    @PostMapping("/predict/volatility")
    public ApiResponse<VolatilityModelTrainer.VolatilityPrediction> predictVolatility(
            @RequestParam String symbol) {
        KLineHistoryRequest request = new KLineHistoryRequest();
        request.setSymbol(symbol);
        request.setInterval(OKXMIN60.name());
        request.setLimit(100);
        KLineHistoryResponse klineData = kLineV1Service.getKLineHistory(request);
        if (klineData == null || klineData.getKlines() == null || klineData.getKlines().isEmpty()) {
            return ApiResponse.error(400, "无法获取K线数据");
        }

        BarSeries series = mlTrainingService.convertToBarSeries(klineData.getKlines());
        VolatilityModelTrainer.VolatilityPrediction result = mlInferenceService.predictVolatility(series);
        return ApiResponse.success(result);
    }

    @GetMapping("/market/state")
    public ApiResponse<MarketStateClusterService.MarketStateCluster> getMarketState(
            @RequestParam String symbol) {
        KLineHistoryRequest request = new KLineHistoryRequest();
        request.setSymbol(symbol);
        request.setInterval(OKXMIN60.name());
        request.setLimit(100);
        KLineHistoryResponse klineData = kLineV1Service.getKLineHistory(request);
        if (klineData == null || klineData.getKlines() == null || klineData.getKlines().isEmpty()) {
            return ApiResponse.error(400, "无法获取K线数据");
        }

        BarSeries series = mlTrainingService.convertToBarSeries(klineData.getKlines());
        MarketStateClusterService.MarketStateCluster result = mlInferenceService.getMarketState(series);
        return ApiResponse.success(result);
    }

    @GetMapping("/models/{symbol}")
    public ApiResponse<List<MlModel>> listModels(@PathVariable String symbol,
                                                   @RequestParam(defaultValue = "DIRECTION") String modelType) {
        List<MlModel> models = modelStorageService.listModels(symbol, modelType);
        return ApiResponse.success(models);
    }

    @GetMapping("/models/active/{symbol}")
    public ApiResponse<MlModel> getActiveModel(@PathVariable String symbol,
                                                 @RequestParam(defaultValue = "DIRECTION") String modelType) {
        MlModel model = modelStorageService.getActiveModelMeta(symbol, modelType);
        if (model == null) return ApiResponse.error(404, "模型不存在");
        return ApiResponse.success(model);
    }

    @PostMapping("/models/{modelId}/activate")
    public ApiResponse<Void> activateModel(@PathVariable String modelId) {
        MlModel model = mlModelMapper.selectById(modelId);
        if (model == null) return ApiResponse.error(404, "模型不存在");
        mlModelMapper.deactivateAll(model.getSymbol(), model.getModelType());
        model.setIsActive(true);
        mlModelMapper.updateById(model);
        mlInferenceService.evictCache(model.getSymbol(), model.getModelType());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/models/{modelId}")
    public ApiResponse<Void> deleteModel(@PathVariable String modelId) {
        MlModel model = mlModelMapper.selectById(modelId);
        if (model == null) return ApiResponse.error(404, "模型不存在");
        mlInferenceService.evictCache(model.getSymbol(), model.getModelType());
        mlModelMapper.deleteById(modelId);
        return ApiResponse.success(null);
    }

    @GetMapping("/features/{symbol}")
    public ApiResponse<Map<String, Object>> getCurrentFeatures(@PathVariable String symbol) {
        KLineHistoryRequest request = new KLineHistoryRequest();
        request.setSymbol(symbol);
        request.setInterval(OKXMIN60.name());
        request.setLimit(50);
        KLineHistoryResponse klineData = kLineV1Service.getKLineHistory(request);
        if (klineData == null || klineData.getKlines() == null || klineData.getKlines().isEmpty()) {
            return ApiResponse.error(400, "无法获取K线数据");
        }

        BarSeries series = mlTrainingService.convertToBarSeries(klineData.getKlines());
        double[] features = featureEngineeringService.extractFeatureArray(series, series.getEndIndex());
        if (features == null) {
            return ApiResponse.error(400, "数据不足");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("RSI", features[0]);
        result.put("MACD", features[1]);
        result.put("MACD_Signal", features[2]);
        result.put("EMADiff", features[3]);
        return ApiResponse.success(result);
    }

    @GetMapping("/features/timeseries")
    public ApiResponse<List<Map<String, Object>>> getFeatureTimeSeries(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        KLineHistoryRequest request = new KLineHistoryRequest();
        request.setSymbol(symbol);
        request.setInterval(OKXMIN60.name());
        request.setLimit(limit + 60);
        KLineHistoryResponse klineData = kLineV1Service.getKLineHistory(request);
        if (klineData == null || klineData.getKlines() == null || klineData.getKlines().isEmpty()) {
            return ApiResponse.error(400, "无法获取K线数据");
        }

        BarSeries series = mlTrainingService.convertToBarSeries(klineData.getKlines());
        List<Map<String, Object>> timeseries = new ArrayList<>();
        for (int i = Math.max(0, series.getEndIndex() - limit); i <= series.getEndIndex(); i++) {
            double[] features = featureEngineeringService.extractFeatureArray(series, i);
            if (features == null) continue;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("timestamp", series.getBar(i).getEndTime().toEpochMilli());
            point.put("RSI", features[0]);
            point.put("MACD", features[1]);
            point.put("MACD_Signal", features[2]);
            point.put("EMADiff", features[3]);
            timeseries.add(point);
        }
        return ApiResponse.success(timeseries);
    }

    @GetMapping("/models/{modelId}/feature-importance")
    public ApiResponse<Map<String, Double>> getFeatureImportance(@PathVariable String modelId) {
        MlModel model = mlModelMapper.selectById(modelId);
        if (model == null) return ApiResponse.error(404, "模型不存在");
        if (model.getFeatureImportance() == null || model.getFeatureImportance().isEmpty()) {
            return ApiResponse.error(404, "特征重要性数据不存在");
        }
        try {
            Map<String, Double> importance = objectMapper.readValue(model.getFeatureImportance(),
                    new TypeReference<Map<String, Double>>() {});
            return ApiResponse.success(importance);
        } catch (Exception e) {
            return ApiResponse.error(500, "解析特征重要性失败");
        }
    }

    @GetMapping("/models/{modelId}/confusion-matrix")
    public ApiResponse<Map<String, Integer>> getConfusionMatrix(@PathVariable String modelId) {
        MlModel model = mlModelMapper.selectById(modelId);
        if (model == null) return ApiResponse.error(404, "模型不存在");
        if (model.getConfusionMatrix() == null || model.getConfusionMatrix().isEmpty()) {
            return ApiResponse.error(404, "混淆矩阵数据不存在");
        }
        try {
            Map<String, Integer> matrix = objectMapper.readValue(model.getConfusionMatrix(),
                    new TypeReference<Map<String, Integer>>() {});
            return ApiResponse.success(matrix);
        } catch (Exception e) {
            return ApiResponse.error(500, "解析混淆矩阵失败");
        }
    }

    @GetMapping("/models/{modelId}/accuracy-trend")
    public ApiResponse<List<Map<String, Object>>> getAccuracyTrend(@PathVariable String modelId) {
        MlModel model = mlModelMapper.selectById(modelId);
        if (model == null) return ApiResponse.error(404, "模型不存在");
        if (model.getAccuracyTrend() == null || model.getAccuracyTrend().isEmpty()) {
            return ApiResponse.error(404, "准确率趋势数据不存在");
        }
        try {
            List<Map<String, Object>> trend = objectMapper.readValue(model.getAccuracyTrend(),
                    new TypeReference<List<Map<String, Object>>>() {});
            return ApiResponse.success(trend);
        } catch (Exception e) {
            return ApiResponse.error(500, "解析准确率趋势失败");
        }
    }

    @GetMapping("/threshold-backtest/{symbol}")
    public ApiResponse<ThresholdBacktestService.ThresholdBacktestResult> thresholdBacktest(
            @PathVariable String symbol) {
        ThresholdBacktestService.ThresholdBacktestResult result = thresholdBacktestService.backtest(symbol);
        if (!result.isSuccess()) {
            return ApiResponse.error(400, result.getErrorMsg());
        }
        return ApiResponse.success(result);
    }
}
