package com.chain.ai.trade.backtest.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.backtest.entity.dos.BacktestReport;
import com.chain.ai.trade.backtest.entity.dto.BacktestReportDTO;
import com.chain.ai.trade.backtest.entity.dto.BacktestResultDTO;
import com.chain.ai.trade.backtest.entity.dto.BacktestTaskDTO;
import com.chain.ai.trade.backtest.mapper.BacktestReportMapper;
import com.chain.ai.trade.backtest.service.BacktestReportService;
import com.chain.ai.trade.backtest.service.BacktestResultService;
import com.chain.ai.trade.backtest.service.BacktestTaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 回测报告服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestReportServiceImpl extends ServiceImpl<BacktestReportMapper, BacktestReport>
        implements BacktestReportService {

    private final BacktestReportMapper backtestReportMapper;
    private final BacktestTaskService backtestTaskService;
    private final BacktestResultService backtestResultService;
    private final ObjectMapper objectMapper;

    @Override
    public BacktestReportDTO generateReport(String taskId) {
        log.info("开始生成回测报告，taskId: {}", taskId);

        try {
            // 获取任务信息
            BacktestTaskDTO task = backtestTaskService.getTaskDetail(taskId);
            if (task == null) {
                log.error("任务不存在: {}", taskId);
                return null;
            }

            // 获取回测结果
            BacktestResultDTO result = backtestResultService.getResult(taskId);
            if (result == null) {
                log.error("回测结果不存在: {}", taskId);
                return null;
            }

            // 生成报告标题
            String title = String.format("%s回测报告(%s - %s)",
                task.getStrategyName(),
                task.getStartDate(),
                task.getEndDate());

            // 生成详细总结
            String summary = generateDetailedSummary(task, result);

            // 生成深度分析
            Map<String, Object> analysis = generateAnalysis(result);

            // 生成关键指标
            Map<String, Object> metrics = generateKeyMetrics(result);

            // 构建报告DTO
            BacktestReportDTO reportDTO = BacktestReportDTO.builder()
                .taskId(taskId)
                .title(title)
                .version(1)
                .reportType("AUTO")
                .summary(summary)
                .analysis(analysis)
                .metrics(metrics)
                .tags(Arrays.asList("自动生成"))
                .isFavorite(false)
                .isArchived(false)
                .rating(0)
                .notes("")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("SYSTEM")
                .build();

            // 保存报告
            if (saveReport(reportDTO)) {
                log.info("回测报告生成并保存成功，taskId: {}", taskId);
                return reportDTO;
            } else {
                log.error("回测报告保存失败，taskId: {}", taskId);
                return null;
            }

        } catch (Exception e) {
            log.error("生成回测报告失败，taskId: {}", taskId, e);
            return null;
        }
    }

    @Override
    public BacktestReportDTO getReport(String taskId) {
        log.info("获取回测报告，taskId: {}", taskId);

        BacktestReport report = getById(taskId);
        if (report == null) {
            return null;
        }

        try {
            return convertToDTO(report);
        } catch (Exception e) {
            log.error("转换报告DTO失败，taskId: {}", taskId, e);
            return null;
        }
    }

    @Override
    public boolean saveReport(BacktestReportDTO reportDTO) {
        try {
            log.info("保存回测报告，taskId: {}", reportDTO.getTaskId());

            BacktestReport report = convertToEntity(reportDTO);
            return saveOrUpdate(report);

        } catch (Exception e) {
            log.error("保存回测报告失败，taskId: {}", reportDTO.getTaskId(), e);
            return false;
        }
    }

    @Override
    public boolean updateNotes(String taskId, String notes) {
        try {
            log.info("更新报告笔记，taskId: {}", taskId);

            BacktestReport report = getById(taskId);
            if (report == null) {
                return false;
            }

            report.setNotes(notes);
            report.setUpdatedAt(LocalDateTime.now());
            return updateById(report);

        } catch (Exception e) {
            log.error("更新报告笔记失败，taskId: {}", taskId, e);
            return false;
        }
    }

    @Override
    public boolean deleteReport(String taskId) {
        try {
            log.info("删除回测报告，taskId: {}", taskId);
            return removeById(taskId);
        } catch (Exception e) {
            log.error("删除回测报告失败，taskId: {}", taskId, e);
            return false;
        }
    }

    /**
     * 生成详细总结
     */
    private String generateDetailedSummary(BacktestTaskDTO task, BacktestResultDTO result) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 策略回测报告\n\n");
        sb.append("### 基本信息\n");
        sb.append(String.format("- 策略名称：%s\n", task.getStrategyName()));
        sb.append(String.format("- 回测期间：%s 至 %s\n",
            task.getStartDate(), task.getEndDate()));
        sb.append(String.format("- 初始资金：%s\n",
            task.getInitialCapital().toPlainString()));

        sb.append("\n### 绩效表现\n");
        if (result.getTotalReturn() != null) {
            sb.append(String.format("- 总收益率：%.2f%%\n", result.getTotalReturn().doubleValue() * 100));
        }
        if (result.getMaxDrawdown() != null) {
            sb.append(String.format("- 最大回撤：%.2f%%\n", result.getMaxDrawdown().doubleValue() * 100));
        }
        if (result.getWinRate() != null) {
            sb.append(String.format("- 胜率：%.2f%%\n", result.getWinRate().doubleValue() * 100));
        }
        if (result.getTotalTrades() != null) {
            sb.append(String.format("- 总交易次数：%d次\n", result.getTotalTrades()));
        }
        if (result.getWinningTrades() != null) {
            sb.append(String.format("- 盈利交易数：%d次\n", result.getWinningTrades()));
        }
        if (result.getProfitFactor() != null) {
            sb.append(String.format("- 盈亏比：%.2f\n", result.getProfitFactor().doubleValue()));
        }
        if (result.getFinalValue() != null) {
            sb.append(String.format("- 最终价值：%.2f\n", result.getFinalValue().doubleValue()));
        }

        sb.append("\n### 综合评价\n");
        sb.append(generateEvaluation(result));

        return sb.toString();
    }

    /**
     * 生成深度分析
     */
    private Map<String, Object> generateAnalysis(BacktestResultDTO result) {
        Map<String, Object> analysis = new HashMap<>();

        // 优势
        List<String> strengths = new ArrayList<>();
        if (result.getWinRate() != null && result.getWinRate().doubleValue() > 0.6) {
            strengths.add("胜率较高");
        }
        if (result.getProfitFactor() != null && result.getProfitFactor().doubleValue() > 1.5) {
            strengths.add("盈亏比较高");
        }
        if (result.getMaxDrawdown() != null && result.getMaxDrawdown().doubleValue() > -0.2) {
            strengths.add("回撤控制良好");
        }

        // 劣势
        List<String> weaknesses = new ArrayList<>();
        if (result.getWinRate() != null && result.getWinRate().doubleValue() < 0.4) {
            weaknesses.add("胜率较低");
        }
        if (result.getMaxDrawdown() != null && result.getMaxDrawdown().doubleValue() < -0.3) {
            weaknesses.add("最大回撤较大");
        }
        if (result.getTotalTrades() != null && result.getTotalTrades() < 10) {
            weaknesses.add("交易次数过少");
        }

        // 市场条件分析
        Map<String, Object> marketConditions = new HashMap<>();
        if (result.getTotalReturn() != null) {
            if (result.getTotalReturn().doubleValue() > 0.1) {
                marketConditions.put("overall_performance", "良好");
            } else if (result.getTotalReturn().doubleValue() > 0) {
                marketConditions.put("overall_performance", "一般");
            } else {
                marketConditions.put("overall_performance", "较差");
            }
        }

        analysis.put("strengths", strengths);
        analysis.put("weaknesses", weaknesses);
        analysis.put("market_conditions", marketConditions);

        return analysis;
    }

    /**
     * 生成关键指标
     */
    private Map<String, Object> generateKeyMetrics(BacktestResultDTO result) {
        Map<String, Object> metrics = new HashMap<>();
        List<Map<String, Object>> keyMetrics = new ArrayList<>();

        // 总收益率
        if (result.getTotalReturn() != null) {
            Map<String, Object> totalReturnMetric = new HashMap<>();
            totalReturnMetric.put("label", "总收益率");
            totalReturnMetric.put("value", String.format("%.2f%%", result.getTotalReturn().doubleValue() * 100));
            totalReturnMetric.put("trend", result.getTotalReturn().doubleValue() > 0 ? "up" : "down");
            keyMetrics.add(totalReturnMetric);
        }

        // 最大回撤
        if (result.getMaxDrawdown() != null) {
            Map<String, Object> maxDrawdownMetric = new HashMap<>();
            maxDrawdownMetric.put("label", "最大回撤");
            maxDrawdownMetric.put("value", String.format("%.2f%%", result.getMaxDrawdown().doubleValue() * 100));
            maxDrawdownMetric.put("trend", "down"); // 回撤越小越好
            keyMetrics.add(maxDrawdownMetric);
        }

        // 胜率
        if (result.getWinRate() != null) {
            Map<String, Object> winRateMetric = new HashMap<>();
            winRateMetric.put("label", "胜率");
            winRateMetric.put("value", String.format("%.1f%%", result.getWinRate().doubleValue() * 100));
            winRateMetric.put("trend", result.getWinRate().doubleValue() > 0.5 ? "up" : "neutral");
            keyMetrics.add(winRateMetric);
        }

        // 总交易次数
        if (result.getTotalTrades() != null) {
            Map<String, Object> totalTradesMetric = new HashMap<>();
            totalTradesMetric.put("label", "总交易次数");
            totalTradesMetric.put("value", String.valueOf(result.getTotalTrades()));
            totalTradesMetric.put("trend", "neutral");
            keyMetrics.add(totalTradesMetric);
        }

        metrics.put("key_metrics", keyMetrics);
        return metrics;
    }

    /**
     * 生成综合评价
     */
    private String generateEvaluation(BacktestResultDTO result) {
        double score = 0;

        if (result.getWinRate() != null && result.getWinRate().doubleValue() > 0.6) score += 2;
        else if (result.getWinRate() != null && result.getWinRate().doubleValue() > 0.5) score += 1;

        if (result.getProfitFactor() != null && result.getProfitFactor().doubleValue() > 1.5) score += 2;
        else if (result.getProfitFactor() != null && result.getProfitFactor().doubleValue() > 1.2) score += 1;

        if (result.getMaxDrawdown() != null && result.getMaxDrawdown().doubleValue() > -0.15) score += 2;
        else if (result.getMaxDrawdown() != null && result.getMaxDrawdown().doubleValue() > -0.25) score += 1;

        if (result.getTotalTrades() != null && result.getTotalTrades() > 50) score += 1;

        if (score >= 5) {
            return "策略表现优秀，风险收益比较高，建议实盘测试。";
        } else if (score >= 3) {
            return "策略表现良好，可以考虑进一步优化参数。";
        } else if (score >= 1) {
            return "策略表现一般，需要进一步优化或调整。";
        } else {
            return "策略表现较差，不建议实盘使用。";
        }
    }

    /**
     * 转换DTO为实体
     */
    private BacktestReport convertToEntity(BacktestReportDTO dto) throws JsonProcessingException {
        return BacktestReport.builder()
            .taskId(dto.getTaskId())
            .title(dto.getTitle())
            .version(dto.getVersion())
            .reportType(dto.getReportType())
            .summary(dto.getSummary())
            .analysis(dto.getAnalysis() != null ? objectMapper.writeValueAsString(dto.getAnalysis()) : null)
            .metrics(dto.getMetrics() != null ? objectMapper.writeValueAsString(dto.getMetrics()) : null)
            .tags(dto.getTags() != null ? objectMapper.writeValueAsString(dto.getTags()) : null)
            .isFavorite(dto.getIsFavorite())
            .isArchived(dto.getIsArchived())
            .rating(dto.getRating())
            .notes(dto.getNotes())
            .createdAt(dto.getCreatedAt())
            .updatedAt(dto.getUpdatedAt())
            .createdBy(dto.getCreatedBy())
            .updatedBy(dto.getUpdatedBy())
            .build();
    }

    /**
     * 转换实体为DTO
     */
    private BacktestReportDTO convertToDTO(BacktestReport entity) throws JsonProcessingException {
        return BacktestReportDTO.builder()
            .taskId(entity.getTaskId())
            .title(entity.getTitle())
            .version(entity.getVersion())
            .reportType(entity.getReportType())
            .summary(entity.getSummary())
            .analysis(entity.getAnalysis() != null ? objectMapper.readValue(entity.getAnalysis(), Map.class) : null)
            .metrics(entity.getMetrics() != null ? objectMapper.readValue(entity.getMetrics(), Map.class) : null)
            .tags(entity.getTags() != null ? objectMapper.readValue(entity.getTags(), List.class) : null)
            .isFavorite(entity.getIsFavorite())
            .isArchived(entity.getIsArchived())
            .rating(entity.getRating())
            .notes(entity.getNotes())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .createdBy(entity.getCreatedBy())
            .updatedBy(entity.getUpdatedBy())
            .build();
    }
}