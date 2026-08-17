package com.chain.ai.trade.engine.service.ml.factor;

import com.chain.ai.trade.engine.config.MlProperties;
import com.chain.ai.trade.engine.controller.dto.KLineDataDTO;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryRequest;
import com.chain.ai.trade.engine.controller.dto.KLineHistoryResponse;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.mapper.FactorCandidateMapper;
import com.chain.ai.trade.engine.mapper.FactorMiningTaskMapper;
import com.chain.ai.trade.engine.model.ml.FactorCandidate;
import com.chain.ai.trade.engine.model.ml.FactorCandidateVO;
import com.chain.ai.trade.engine.model.ml.FactorMiningRequest;
import com.chain.ai.trade.engine.model.ml.FactorMiningTask;
import com.chain.ai.trade.engine.service.KLineV1Service;
import com.chain.ai.trade.engine.service.ml.DynamicFeature;
import com.chain.ai.trade.engine.service.ml.FeatureFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FactorMiningTaskService {

    private final KLineV1Service kLineV1Service;
    private final FactorMiningTaskMapper taskMapper;
    private final FactorCandidateMapper candidateMapper;
    private final GeneticProgrammingEngine gpEngine;
    private final FactorEvaluator evaluator;
    private final FactorExpressionParser parser;
    private final MlProperties mlProperties;
    private final ObjectMapper objectMapper;

    private final Map<String, Boolean> cancelFlags = new ConcurrentHashMap<>();
    private final Map<String, GeneticProgrammingEngine.EvolutionResult> lastResults = new ConcurrentHashMap<>();

    public Map<String, Object> getTerminalPool(String symbol, String interval) {
        KLineHistoryResponse klineData = fetchKLineData(symbol, mlProperties.getFactorMining().getDefaultLookbackBars(), interval);
        BarSeries series = IndicatorWrapHelper.buildSeries(toCandlestickList(klineData.getKlines(), interval));

        Map<String, List<?>> fullPool = new LinkedHashMap<>();
        fullPool.put("RSI", Arrays.asList(7, 14, 21));
        fullPool.put("MACD", Arrays.asList(
                Arrays.asList(12, 26, 9), Arrays.asList(5, 13, 5)));
        fullPool.put("MACD_Signal", Arrays.asList(
                Arrays.asList(12, 26, 9), Arrays.asList(5, 13, 5)));
        fullPool.put("EMADiff", Arrays.asList(
                Arrays.asList(5, 20), Arrays.asList(10, 30), Arrays.asList(20, 60)));
        fullPool.put("ATR", Arrays.asList(7, 14, 21));
        fullPool.put("VolumeRatio", Arrays.asList(
                Arrays.asList(5, 20), Arrays.asList(10, 30)));
        fullPool.put("PriceChange", Arrays.asList(1, 3, 5));
        fullPool.put("StochasticK", Arrays.asList(7, 14, 21));
        fullPool.put("BollingerB", Arrays.asList(14, 20, 26));
        fullPool.put("LinearRegSlope", Arrays.asList(5, 10, 20));

        List<DynamicFeature> allVariants = FeatureFactory.createAllVariants(series, fullPool);
        List<Map<String, Object>> variants = allVariants.stream()
                .map(v -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", v.getVariantName());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("variants", variants);
        result.put("total", variants.size());
        return result;
    }

    @Transactional
    public FactorMiningTask createTask(FactorMiningRequest request) {
        FactorMiningTask task = new FactorMiningTask();
        task.setTaskName(request.getTaskName());
        task.setSymbol(request.getSymbol());
        task.setInterval(request.getInterval());
        try {
            task.setOperatorSet(objectMapper.writeValueAsString(request.getOperatorSet()));
            task.setTerminalSet(objectMapper.writeValueAsString(request.getTerminalSet()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize sets", e);
        }
        task.setPopulationSize(request.getPopulationSize() != null ? request.getPopulationSize()
                : mlProperties.getFactorMining().getDefaultPopulationSize());
        task.setGenerations(request.getGenerations() != null ? request.getGenerations()
                : mlProperties.getFactorMining().getDefaultGenerations());
        task.setTournamentSize(request.getTournamentSize() != null ? request.getTournamentSize()
                : mlProperties.getFactorMining().getDefaultTournamentSize());
        task.setCrossoverProb(request.getCrossoverProb() != null ? request.getCrossoverProb()
                : mlProperties.getFactorMining().getDefaultCrossoverProb());
        task.setMutationProb(request.getMutationProb() != null ? request.getMutationProb()
                : mlProperties.getFactorMining().getDefaultMutationProb());
        task.setParsimonyCoefficient(request.getParsimonyCoefficient() != null ? request.getParsimonyCoefficient()
                : mlProperties.getFactorMining().getDefaultParsimonyCoefficient());
        task.setFitnessMetric(request.getFitnessMetric() != null ? request.getFitnessMetric()
                : mlProperties.getFactorMining().getDefaultFitnessMetric());
        task.setLookbackBars(request.getLookbackBars() != null ? request.getLookbackBars()
                : mlProperties.getFactorMining().getDefaultLookbackBars());
        task.setStatus("PENDING");
        task.setProgress(0.0);
        task.setDeleteFlag(false);
        taskMapper.insert(task);
        return task;
    }

    public CompletableFuture<Void> startTaskAsync(String taskId) {
        cancelFlags.put(taskId, false);
        return CompletableFuture.runAsync(() -> startTask(taskId));
    }

    public void startTask(String taskId) {
        FactorMiningTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("Task {} not found", taskId);
            return;
        }
        try {
            task.setStatus("RUNNING");
            task.setStartTime(new Date());
            taskMapper.updateById(task);

            KLineHistoryResponse klineData = fetchKLineData(task.getSymbol(), task.getLookbackBars(), task.getInterval());
            BarSeries series = IndicatorWrapHelper.buildSeries(toCandlestickList(klineData.getKlines(), task.getInterval()));

            Map<String, double[]> data = new HashMap<>();
            List<String> terminalNames = objectMapper.readValue(task.getTerminalSet(), List.class);
            Map<String, List<?>> terminalPool = buildTerminalPool(terminalNames);
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            List<DynamicFeature> features = FeatureFactory.createAllVariants(series, terminalPool);

            for (DynamicFeature f : features) {
                double[] values = new double[series.getEndIndex() + 1];
                for (int i = 0; i <= series.getEndIndex(); i++) {
                    values[i] = f.extract(i);
                }
                data.put(f.getVariantName(), values);
            }

            int horizon = mlProperties.getLabel().getHorizon();
            int startBar = mlProperties.getModel().getLookaheadBars() + horizon;
            int totalBars = series.getEndIndex() + 1;
            double[] labels = new double[totalBars];
            for (int i = startBar; i < totalBars - horizon; i++) {
                double currentClose = closePrice.getValue(i).doubleValue();
                double futureClose = closePrice.getValue(i + horizon).doubleValue();
                labels[i] = (futureClose - currentClose) / currentClose;
            }

            List<String> operatorSymbols = objectMapper.readValue(task.getOperatorSet(), List.class);
            List<Operator> operators = operatorSymbols.stream()
                    .map(Operator::fromSymbol)
                    .collect(Collectors.toList());

            GeneticProgrammingEngine.EvolutionCallback callback = new GeneticProgrammingEngine.EvolutionCallback() {
                @Override
                public void onGeneration(int gen, int totalGen, Node best, double bestFitness, int popSize) {
                    double progress = (double) gen / totalGen;
                    task.setProgress(progress);
                    task.setBestFitness(bestFitness);
                    task.setBestExpression(best.toExpression());
                    try {
                        task.setBestExpressionLatex(best.toLatex());
                    } catch (Exception ignored) {}
                    taskMapper.updateProgress(task.getId(), progress, bestFitness, best.toExpression());
                }
                @Override
                public boolean isCancelled() {
                    return cancelFlags.getOrDefault(taskId, false);
                }
            };

            GeneticProgrammingEngine.EvolutionResult result = gpEngine.evolve(
                    terminalNames, operators, data, labels,
                    task.getPopulationSize(), task.getGenerations(),
                    task.getTournamentSize(), task.getCrossoverProb(),
                    task.getMutationProb(), task.getParsimonyCoefficient(),
                    FactorEvaluator.FitnessMetric.valueOf(task.getFitnessMetric()),
                    startBar, callback);

            lastResults.put(taskId, result);
            task.setProgress(1.0);
            task.setStatus("DONE");
            task.setBestFitness(result.getBestFitness());
            task.setBestExpression(result.getBestIndividual().toExpression());
            try {
                task.setBestExpressionLatex(result.getBestIndividual().toLatex());
            } catch (Exception ignored) {}
            task.setEndTime(new Date());
            taskMapper.updateById(task);

            saveCandidates(task.getId(), terminalNames, operators, data, labels, startBar);

        } catch (Exception e) {
            log.error("Factor mining task {} failed", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMsg(e.getMessage());
            task.setEndTime(new Date());
            taskMapper.updateById(task);
        }
    }

    private void saveCandidates(String taskId, List<String> terminalNames, List<Operator> operators,
                                 Map<String, double[]> data, double[] labels, int startBar) {
        GeneticProgrammingEngine.EvolutionResult result = lastResults.get(taskId);
        if (result == null) return;

        FactorCandidate best = buildCandidate(taskId, result.getBestIndividual(), terminalNames, operators, data, labels, startBar);
        if (best != null) {
            candidateMapper.insert(best);
        }
    }

    private FactorCandidate buildCandidate(String taskId, Node tree, List<String> terminalNames,
                                            List<Operator> operators, Map<String, double[]> data,
                                            double[] labels, int startBar) {
        FactorEvaluator.FactorEvalResult eval = gpEngine.evaluateFull(tree, data, labels,
                FactorEvaluator.FitnessMetric.valueOf(
                        taskMapper.selectById(taskId).getFitnessMetric()),
                mlProperties.getFactorMining().getDefaultParsimonyCoefficient(), startBar);
        FactorCandidate c = new FactorCandidate();
        c.setTaskId(taskId);
        c.setExpression(tree.toExpression());
        try {
            c.setExpressionLatex(tree.toLatex());
        } catch (Exception ignored) {}
        c.setFitness(eval.getFitness());
        c.setRankIc(eval.getRankIc());
        c.setSharpe(eval.getSharpe());
        c.setTurnover(eval.getTurnover());
        c.setTreeDepth(eval.getDepth());
        c.setNodeCount(eval.getNodeCount());
        c.setCorrWithLabel(eval.getCorrWithLabel());
        c.setTopRet(eval.getTopRet());
        c.setSelected(false);
        return c;
    }

    @Transactional
    public void selectCandidate(String candidateId, String customFeatureName) {
        FactorCandidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) return;
        candidate.setSelected(true);
        candidate.setCustomFeatureName(customFeatureName);
        candidateMapper.updateById(candidate);
    }

    @Transactional
    public void deselectCandidate(String candidateId) {
        FactorCandidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) return;
        candidate.setSelected(false);
        candidate.setCustomFeatureName(null);
        candidateMapper.updateById(candidate);
    }

    public List<FactorCandidateVO> getCandidatesByTaskId(String taskId) {
        return candidateMapper.findByTaskId(taskId).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public List<FactorCandidateVO> getSelectedCandidates() {
        return candidateMapper.findSelected().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public FactorMiningTask getTaskById(String taskId) {
        return taskMapper.selectById(taskId);
    }

    public List<FactorMiningTask> getRecentTasks(int limit) {
        return taskMapper.findRecent(limit);
    }

    public void cancelTask(String taskId) {
        cancelFlags.put(taskId, true);
    }

    public List<DynamicFeature> toDynamicFeatures(Map<String, double[]> data,
                                                   List<String> terminalNames,
                                                   List<Operator> operators) {
        List<FactorCandidate> selected = candidateMapper.findSelected();
        if (selected.isEmpty()) {
            return Collections.emptyList();
        }
        List<DynamicFeature> features = new ArrayList<>(selected.size());
        for (FactorCandidate c : selected) {
            try {
                Node node = parser.parse(c.getExpression(), terminalNames, operators);
                String name = c.getCustomFeatureName() != null ? c.getCustomFeatureName() : "GP_" + c.getId().substring(0, 8);
                features.add(FeatureFactory.createFromExpression(name, node, data));
            } catch (Exception e) {
                log.warn("Failed to parse candidate expression: id={}, expr={}", c.getId(), c.getExpression(), e);
            }
        }
        return features;
    }

    private FactorCandidateVO toVO(FactorCandidate c) {
        FactorCandidateVO vo = new FactorCandidateVO();
        vo.setId(c.getId());
        vo.setTaskId(c.getTaskId());
        vo.setExpression(c.getExpression());
        vo.setExpressionLatex(c.getExpressionLatex());
        vo.setFitness(c.getFitness());
        vo.setRankIc(c.getRankIc());
        vo.setSharpe(c.getSharpe());
        vo.setTurnover(c.getTurnover());
        vo.setTreeDepth(c.getTreeDepth());
        vo.setNodeCount(c.getNodeCount());
        vo.setCorrWithLabel(c.getCorrWithLabel());
        vo.setTopRet(c.getTopRet());
        vo.setSelected(c.getSelected());
        vo.setCustomFeatureName(c.getCustomFeatureName());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }

    private KLineHistoryResponse fetchKLineData(String symbol, int limit, String interval) {
        KLineHistoryRequest req = new KLineHistoryRequest();
        req.setSymbol(symbol);
        req.setLimit(limit);
        req.setInterval(interval != null ? interval : "1H");
        return kLineV1Service.getKLineHistory(req);
    }

    private List<Candlestick> toCandlestickList(List<KLineDataDTO> klines, String interval) {
        CandlestickIntervalEnum intervalEnum = resolveIntervalEnum(interval);
        return klines.stream()
                .map(k -> Candlestick.builder()
                        .id(k.getTime() != null ? k.getTime() * 1000 : null)
                        .openPrice(k.getOpen())
                        .highPrice(k.getHigh())
                        .lowPrice(k.getLow())
                        .closePrice(k.getClose())
                        .volume(k.getVolume())
                        .candlestickIntervalEnum(intervalEnum)
                        .build())
                .collect(Collectors.toList());
    }

    private static CandlestickIntervalEnum resolveIntervalEnum(String interval) {
        if (interval == null) return null;
        for (CandlestickIntervalEnum e : CandlestickIntervalEnum.values()) {
            if (e.getCode().equals(interval)) {
                return e;
            }
        }
        return null;
    }

    private static final List<String> KNOWN_TYPES = Arrays.asList(
            "MACD_Signal", "MACDSIGNAL",
            "LinearRegSlope",
            "EMADiff", "VolumeRatio", "PriceChange",
            "StochasticK", "BollingerB",
            "RSI", "ATR", "MACD"
    );

    private Map<String, List<?>> buildTerminalPool(List<String> terminalNames) {
        Map<String, List<?>> pool = new LinkedHashMap<>();
        for (String name : terminalNames) {
            String type = null;
            String paramStr = null;
            for (String knownType : KNOWN_TYPES) {
                String prefix = knownType + "_";
                if (name.startsWith(prefix)) {
                    type = knownType;
                    paramStr = name.substring(prefix.length());
                    break;
                }
            }
            if (type == null) continue;

            String[] paramStrs = paramStr.split("_");
            List<Integer> params = new ArrayList<>();
            for (String p : paramStrs) {
                params.add(Integer.parseInt(p));
            }
            if (params.size() == 1) {
                pool.merge(type, new ArrayList<>(Collections.singletonList(params.get(0))),
                        (old, val) -> { ((List) old).addAll((List) val); return old; });
            } else {
                pool.merge(type, new ArrayList<>(Collections.singletonList(params)),
                        (old, val) -> { ((List) old).addAll((List) val); return old; });
            }
        }
        return pool;
    }
}
