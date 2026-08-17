package com.chain.ai.trade.engine.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.engine.controller.dto.AiAnalysisTaskCreateRequest;
import com.chain.ai.trade.engine.controller.dto.AiAnalysisTaskUpdateRequest;
import com.chain.ai.trade.engine.controller.dto.MarketAnalysisDTO;
import com.chain.ai.trade.engine.entity.AiAnalysisTask;
import com.chain.ai.trade.engine.entity.AnalysisReport;
import com.chain.ai.trade.engine.mapper.AiAnalysisTaskMapper;
import com.chain.ai.trade.engine.mapper.AnalysisReportMapper;
import com.chain.ai.trade.engine.service.ai.filter.LlmAnalyzerService;
import com.chain.ai.trade.engine.service.ai.filter.MarketDataCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisTaskService {

    private final AiAnalysisTaskMapper taskMapper;
    private final AnalysisReportMapper reportMapper;
    private final MarketAnalysisService marketAnalysisService;
    private final LlmAnalyzerService llmAnalyzerService;
    private final MarketDataCollector marketDataCollector;

    public IPage<AiAnalysisTask> listTasks(String userId, int page, int size) {
        return taskMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AiAnalysisTask>()
                        .eq(AiAnalysisTask::getUserId, userId)
                        .orderByDesc(AiAnalysisTask::getCreateTime));
    }

    public AiAnalysisTask getTask(String id) {
        return taskMapper.selectById(id);
    }

    @Transactional
    public AiAnalysisTask createTask(String userId, AiAnalysisTaskCreateRequest req) {
        AiAnalysisTask task = AiAnalysisTask.builder()
                .userId(userId)
                .symbols(JSONUtil.toJsonStr(req.getSymbols()))
                .intervalMin(req.getIntervalMin())
                .notifyChannels(req.getNotifyChannels() != null ? JSONUtil.toJsonStr(req.getNotifyChannels()) : "[\"app\"]")
                .enabled(true)
                .build();
        taskMapper.insert(task);
        log.info("创建AI分析任务: taskId={}, symbols={}, interval={}min",
                task.getId(), req.getSymbols(), req.getIntervalMin());
        return task;
    }

    @Transactional
    public AiAnalysisTask updateTask(String id, AiAnalysisTaskUpdateRequest req) {
        AiAnalysisTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("Task not found: " + id);
        }
        if (req.getIntervalMin() != null) {
            task.setIntervalMin(req.getIntervalMin());
        }
        if (req.getNotifyChannels() != null) {
            task.setNotifyChannels(JSONUtil.toJsonStr(req.getNotifyChannels()));
        }
        if (req.getEnabled() != null) {
            task.setEnabled(req.getEnabled());
        }
        taskMapper.updateById(task);
        log.info("更新AI分析任务: taskId={}", id);
        return task;
    }

    @Transactional
    public void deleteTask(String id) {
        AiAnalysisTask task = taskMapper.selectById(id);
        if (task != null) {
            taskMapper.deleteById(id);
            log.info("删除AI分析任务: taskId={}", id);
        }
    }

    @Transactional
    public void executeTask(AiAnalysisTask task) {
        log.info("执行AI分析任务: taskId={}, symbols={}", task.getId(), task.getSymbols());
        List<String> symbols = JSONUtil.toList(task.getSymbols(), String.class);

        for (String symbol : symbols) {
            try {
                AnalysisReport report = executeSingleSymbol(task.getId(), symbol);
                reportMapper.insert(report);
                log.info("AI分析完成: taskId={}, symbol={}, decision={}",
                        task.getId(), symbol, report.getDecision());
            } catch (Exception e) {
                log.error("AI分析失败: taskId={}, symbol={}", task.getId(), symbol, e);
            }
        }

        Date now = new Date();
        task.setLastRunAt(now);
        task.setNextRunAt(new Date(now.getTime() + task.getIntervalMin() * 60_000L));
        taskMapper.updateById(task);
    }

    public AnalysisReport executeSingleSymbol(String taskId, String symbol) {
        var marketAnalysis = marketAnalysisService.analyze(symbol, "1h", 240);

        MarketDataCollector.MarketData marketData = marketDataCollector.collectPromptData(symbol, "SCHEDULED_ANALYSIS", null);
        if (marketAnalysis != null) {
            if (marketAnalysis.getPrice() != null) {
                marketData.setLatestPrice(marketAnalysis.getPrice().toPlainString());
            }
            if (marketAnalysis.getTrendLabel() != null) {
                marketData.setTrend1h(marketAnalysis.getTrendLabel());
            }
            if (marketAnalysis.getRsi14() != null) {
                marketData.setRsi1h(marketAnalysis.getRsi14().setScale(1, BigDecimal.ROUND_HALF_UP).toPlainString());
            }
            if (marketAnalysis.getBollingerWidthPercent() != null) {
                marketData.setBbPosition4h(marketAnalysis.getBollingerWidthPercent().setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "%");
            }
            if (marketAnalysis.getSupports() != null && !marketAnalysis.getSupports().isEmpty()) {
                marketData.setSupportLevels(marketAnalysis.getSupports().stream()
                        .map(bd -> bd.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString())
                        .collect(Collectors.joining(", ")));
            }
            if (marketAnalysis.getResistances() != null && !marketAnalysis.getResistances().isEmpty()) {
                marketData.setResistanceLevels(marketAnalysis.getResistances().stream()
                        .map(bd -> bd.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString())
                        .collect(Collectors.joining(", ")));
            }
        }

        LlmAnalyzerService.LlmResult llmResult = llmAnalyzerService.analyze(marketData);
        String decision = llmResult.getDecision() != null ? llmResult.getDecision().toLowerCase() : "hold";
        String confidence = llmResult.getConfidence() != null ? llmResult.getConfidence() : "MEDIUM";
        String risks = llmResult.getRisks() != null ? String.join("; ", llmResult.getRisks()) : "";
        String summary = llmResult.getSummary() != null ? llmResult.getSummary() : "";
        String analysisJson = JSONUtil.toJsonStr(llmResult);

        return AnalysisReport.builder()
                .taskId(taskId)
                .symbol(symbol)
                .decision(decision)
                .confidence(confidenceToScore(confidence))
                .summary(summary)
                .analysis(analysisJson)
                .risks(risks)
                .triggerType("SCHEDULED")
                .reportJson(analysisJson)
                .build();
    }

    private static int confidenceToScore(String confidence) {
        if (confidence == null) return 50;
        switch (confidence.toUpperCase()) {
            case "HIGH": return 85;
            case "MEDIUM": return 60;
            case "LOW": return 35;
            default: return 50;
        }
    }

    public IPage<AnalysisReport> listReports(String taskId, int page, int size) {
        return reportMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AnalysisReport>()
                        .eq(AnalysisReport::getTaskId, taskId)
                        .orderByDesc(AnalysisReport::getCreateTime));
    }

    public List<AiAnalysisTask> getDueTasks() {
        return taskMapper.selectList(new LambdaQueryWrapper<AiAnalysisTask>()
                .eq(AiAnalysisTask::getEnabled, true)
                .and(w -> w.isNull(AiAnalysisTask::getNextRunAt)
                        .or().le(AiAnalysisTask::getNextRunAt, new Date())));
    }

}
