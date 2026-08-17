package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.backtest.entity.dto.BacktestResultDTO;
import com.chain.ai.trade.backtest.service.BacktestTaskService;

import com.chain.ai.trade.engine.model.PerformanceMetrics;
import com.chain.ai.trade.common.entity.dto.BacktestRequest;
import com.chain.ai.trade.engine.entity.dto.BacktestResponse;
import com.chain.ai.trade.engine.exception.BacktestSaveException;
import com.chain.ai.trade.engine.util.PerformanceMetricsConverter;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;

import com.chain.ai.trade.engine2.persistence.PersistenceGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 回测结果保存服务
 * 负责保存回测的绩效指标
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestResultSaveService implements PersistenceGateway {

    private final BacktestTaskService backtestTaskService;
    private final PerformanceMetricsConverter metricsConverter;
    private final ITradingBotService tradingBotService;

    /**
     * 保存完整的回测结果（交易记录 + 绩效指标）
     *
     * @param taskId   任务ID
     * @param response 回测响应
     * @param request  回测请求
     */
    public void saveBacktestResult(String taskId, BacktestResponse response, BacktestRequest request) {
        try {
            // 输入验证
            validateInputs(taskId, response, request);

            // 获取第一个策略结果
            BacktestResponse.StrategyResult strategyResult = getFirstStrategyResult(response);

            // 保存绩效指标
            savePerformanceMetrics(taskId, strategyResult, request);

            log.info("回测结果保存完成: taskId={}", taskId);

        } catch (IllegalArgumentException e) {
            log.error("保存回测结果失败：参数无效 - taskId={}, error={}", taskId, e.getMessage(), e);
            throw new BacktestSaveException("参数无效: " + e.getMessage(), e);
        } catch (DataAccessException e) {
            log.error("保存回测结果失败：数据库错误 - taskId={}, error={}", taskId, e.getMessage(), e);
            throw new BacktestSaveException("数据库保存失败", e);
        } catch (Exception e) {
            log.error("保存回测结果失败：未知错误 - taskId={}, error={}", taskId, e.getMessage(), e);
            throw new BacktestSaveException("保存回测结果时发生未知错误", e);
        }
    }

    /**
     * 验证输入参数
     */
    private void validateInputs(String taskId, BacktestResponse response, BacktestRequest request) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (response == null) {
            throw new IllegalArgumentException("response 不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        if (response.getResults() == null || response.getResults().isEmpty()) {
            throw new IllegalArgumentException("response.getResults() 不能为空");
        }
    }

    /**
     * 获取第一个策略结果
     */
    private BacktestResponse.StrategyResult getFirstStrategyResult(BacktestResponse response) {
        return response.getResults().get(0);
    }

    /**
     * 保存绩效指标
     */
    private void savePerformanceMetrics(String taskId,
                                         BacktestResponse.StrategyResult strategyResult,
                                         BacktestRequest request) {
        // 构建 DTO
        BacktestResultDTO resultDTO = buildBacktestResultDTO(taskId, strategyResult, request);

        // 保存绩效指标
        boolean saved = backtestTaskService.saveBacktestResult(resultDTO);
        if (saved) {
            log.info("回测结果保存成功: taskId={}", taskId);
        } else {
            log.error("回测结果保存失败: taskId={}", taskId);
            throw new BacktestSaveException("回测结果保存失败");
        }
    }

    /**
     * 构建 BacktestResultDTO
     */
    private BacktestResultDTO buildBacktestResultDTO(String taskId,
                                                      BacktestResponse.StrategyResult strategyResult,
                                                      BacktestRequest request) {
        PerformanceMetrics metrics = strategyResult.getPerformanceMetrics();
        
        if (metrics == null) {
            log.warn("策略结果PerformanceMetrics为null: taskId={}, strategyName={}", taskId, strategyResult.getStrategyName());
        } else {
            log.debug("构建BacktestResultDTO: taskId={}, totalReturn={}, maxDrawdown={}", 
                    taskId, metrics.getTotalReturn(), metrics.getMaxDrawdown());
        }

        BigDecimal sharpeRatio = metricsConverter.getSharpeRatio(metrics);
        BigDecimal calmarRatio = metricsConverter.getCalmarRatio(metrics);
        
        log.info("计算得到的指标值 - taskId={}, sharpeRatio={}, calmarRatio={}", taskId, sharpeRatio, calmarRatio);

        double performanceBaseAmount = resolvePerformanceBaseAmount(request);

        return BacktestResultDTO.builder()
                .taskId(taskId)
                .strategyName(strategyResult.getStrategyName())
                .totalReturn(metricsConverter.getTotalReturn(metrics))
                .maxDrawdown(metricsConverter.getMaxDrawdown(metrics))
                .winRate(metricsConverter.getWinRate(metrics))
                .totalTrades(metricsConverter.getTotalTrades(metrics))
                .winningTrades(metricsConverter.getProfitableTrades(metrics))
                .profitFactor(metricsConverter.getProfitFactor(metrics))
                .finalValue(metricsConverter.calculateFinalValue(metrics, performanceBaseAmount))
                .sharpeRatio(sharpeRatio)
                .calmarRatio(calmarRatio)
                .totalCost(metricsConverter.getTotalCost(metrics))
                .annualReturn(metricsConverter.getAnnualReturn(metrics))
                .volatility(metricsConverter.getVolatility(metrics))
                .sortinoRatio(metricsConverter.getSortinoRatio(metrics))
                .averageWin(metricsConverter.getAverageWin(metrics))
                .averageLoss(metricsConverter.getAverageLoss(metrics))
                .largestWinTrade(metricsConverter.getLargestWinTrade(metrics))
                .largestLossTrade(metricsConverter.getLargestLossTrade(metrics))
                .maxConsecutiveWins(metrics != null ? metrics.getMaxConsecutiveWins() : 0)
                .maxConsecutiveLosses(metrics != null ? metrics.getMaxConsecutiveLosses() : 0)
                .avgTradeDuration(metrics != null ? metrics.getAverageHoldingPeriod() : 0.0)
                .equityCurve(strategyResult.getEquityCurve())
                .drawdownSeries(strategyResult.getDrawdownSeries())
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private double resolvePerformanceBaseAmount(BacktestRequest request) {
        double fallbackAmount = request.getInitialAmount() != null ? request.getInitialAmount() : 0.0;
        if (request.getRobotId() == null || request.getRobotId().isBlank()) {
            return fallbackAmount;
        }
        try {
            TradingBot bot = tradingBotService.getByBotId(request.getRobotId());
            if (bot == null || bot.getCurrentCapital() == null) {
                return fallbackAmount;
            }
            double currentCapital = bot.getCurrentCapital().doubleValue();
            if (Double.isNaN(currentCapital) || Double.isInfinite(currentCapital) || currentCapital <= 0) {
                return fallbackAmount;
            }
            return currentCapital;
        } catch (Exception e) {
            log.warn("读取机器人当前资金失败: {}", e.getMessage());
            return fallbackAmount;
        }
    }
}

