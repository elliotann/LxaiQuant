package com.chain.ai.trade.engine.service.ml;

import com.chain.ai.trade.engine.config.MlProperties;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.entity.MlTrainingJob;
import com.chain.ai.trade.engine.mapper.MlTrainingJobMapper;
import com.chain.ai.trade.engine.service.FeatureEngineeringService;
import com.chain.ai.trade.engine.service.KLineV1Service;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryRequest;
import com.chain.ai.trade.engine.controller.dto.KLineDataDTO;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum.OKXMIN60;

@Slf4j
@Service
@RequiredArgsConstructor
public class MLTrainingService {

    private final KLineV1Service kLineV1Service;
    private final FeatureEngineeringService featureEngineeringService;
    private final DirectionModelTrainer directionModelTrainer;
    private final ModelStorageService modelStorageService;
    private final MlTrainingJobMapper trainingJobMapper;
    private final TrainingProgressService trainingProgressService;
    private final MlProperties mlProperties;

    public MlTrainingJob trainDirectionModel(String symbol) {
        return trainDirectionModel(symbol, null);
    }

    public MlTrainingJob trainDirectionModel(String symbol, String jobId) {
        MlTrainingJob job;
        if (jobId != null) {
            job = trainingJobMapper.selectById(jobId);
            if (job == null) {
                job = createJob(symbol, "DIRECTION");
            }
        } else {
            job = createJob(symbol, "DIRECTION");
        }

        String effectiveJobId = job.getId();
        try {
            updateJobStatus(job, "RUNNING");

            int lookbackBars = mlProperties.getTraining().getDefaultLookbackBars();
            KLineHistoryResponse klineData = fetchKLineData(symbol, lookbackBars);
            BarSeries series = IndicatorWrapHelper.buildSeries(toCandlestickList(klineData.getKlines()));

            FeatureEngineeringService.LabeledSample[] samples =
                    featureEngineeringService.extractTrainingData(series, directionModelTrainer.getLookaheadBars());

            if (samples.length < 100) {
                throw new IllegalStateException(
                        String.format("样本不足: symbol=%s, 样本数=%d (需要 ≥ 100)", symbol, samples.length));
            }

            int numTrees = directionModelTrainer.getNumTrees();
            trainingProgressService.initProgress(effectiveJobId, numTrees);

            DirectionModelTrainer.TrainingResult result = directionModelTrainer.train(samples,
                    (completedTrees, totalTrees, currentAccuracy) ->
                            trainingProgressService.updateProgress(effectiveJobId, completedTrees, currentAccuracy));

            Map<String, Object> dataRange = buildDataRange(klineData.getKlines());
            modelStorageService.saveModel(result.getModel(), symbol, "DIRECTION", result, dataRange);

            trainingProgressService.markSuccess(effectiveJobId, result.getMetrics().getAccuracy().doubleValue());

            job.setAccuracy(result.getMetrics().getAccuracy());
            job.setStatus("SUCCESS");
            trainingJobMapper.updateById(job);
            log.info("方向模型训练完成: symbol={}, accuracy={}", symbol, result.getMetrics().getAccuracy());
            return job;
        } catch (Exception e) {
            log.error("方向模型训练失败: symbol={}", symbol, e);
            trainingProgressService.markFailed(effectiveJobId, e.getMessage());
            job.setStatus("FAILED");
            job.setErrorMsg(e.getMessage());
            trainingJobMapper.updateById(job);
            return job;
        }
    }

    public CompletableFuture<MlTrainingJob> trainDirectionModelAsync(String symbol) {
        MlTrainingJob job = createJob(symbol, "DIRECTION");
        String jobId = job.getId();

        CompletableFuture<MlTrainingJob> future = CompletableFuture.supplyAsync(() ->
                trainDirectionModel(symbol, jobId));

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("异步训练异常: symbol={}", symbol, ex);
                trainingProgressService.markFailed(jobId, ex.getMessage());
            }
        });

        return future;
    }

    private MlTrainingJob createJob(String symbol, String modelType) {
        MlTrainingJob job = MlTrainingJob.builder()
                .symbol(symbol)
                .modelType(modelType)
                .status("PENDING")
                .startTime(new Date())
                .build();
        trainingJobMapper.insert(job);
        return trainingJobMapper.selectById(job.getId());
    }

    private void updateJobStatus(MlTrainingJob job, String status) {
        job.setStatus(status);
        trainingJobMapper.updateById(job);
    }

    KLineHistoryResponse fetchKLineData(String symbol, int limit) {
        KLineHistoryRequest request = new KLineHistoryRequest();
        request.setSymbol(symbol);
        request.setInterval(OKXMIN60.name());
        request.setLimit(limit);
        return kLineV1Service.getKLineHistory(request);
    }

    public BarSeries convertToBarSeries(List<KLineDataDTO> klines) {
        return IndicatorWrapHelper.buildSeries(toCandlestickList(klines));
    }

    private List<Candlestick> toCandlestickList(List<KLineDataDTO> klines) {
        if (klines == null || klines.isEmpty()) return Collections.emptyList();
        List<KLineDataDTO> sorted = new ArrayList<>(klines);
        sorted.sort(Comparator.comparing(KLineDataDTO::getTime));
        List<Candlestick> result = new ArrayList<>(sorted.size());
        for (KLineDataDTO k : sorted) {
            Candlestick c = new Candlestick();
            c.setId(k.getTime() * 1000);
            c.setOpenPrice(k.getOpen());
            c.setHighPrice(k.getHigh());
            c.setLowPrice(k.getLow());
            c.setClosePrice(k.getClose());
            c.setVolume(k.getVolume());
            c.setAmount(k.getQuoteVolume() != null ? k.getQuoteVolume() : BigDecimal.valueOf(0));
            c.setCount(k.getTradeCount() != null ? BigDecimal.valueOf(k.getTradeCount()) : BigDecimal.ZERO);
            result.add(c);
        }
        return result;
    }

    private Map<String, Object> buildDataRange(List<KLineDataDTO> klines) {
        if (klines == null || klines.isEmpty()) return Map.of();
        long minTime = klines.stream().mapToLong(KLineDataDTO::getTime).min().orElse(0);
        long maxTime = klines.stream().mapToLong(KLineDataDTO::getTime).max().orElse(0);
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("start", minTime);
        range.put("end", maxTime);
        return range;
    }
}
