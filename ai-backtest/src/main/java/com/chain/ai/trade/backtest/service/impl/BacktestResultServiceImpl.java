package com.chain.ai.trade.backtest.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.backtest.entity.dos.BacktestEquityCurve;
import com.chain.ai.trade.backtest.entity.dos.BacktestResult;
import com.chain.ai.trade.backtest.entity.dto.BacktestResultDTO;
import com.chain.ai.trade.backtest.mapper.BacktestResultMapper;
import com.chain.ai.trade.backtest.service.BacktestEquityCurveService;
import com.chain.ai.trade.backtest.service.BacktestResultService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 回测结果服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestResultServiceImpl extends ServiceImpl<BacktestResultMapper, BacktestResult>
        implements BacktestResultService {

    private final BacktestEquityCurveService equityCurveService;
    private final com.chain.ai.trade.backtest.mapper.BacktestTaskMapper backtestTaskMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean saveResult(BacktestResultDTO resultDTO) {
        try {
            // 验证任务是否存在
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.chain.ai.trade.backtest.entity.dos.BacktestTask> taskWrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            taskWrapper.eq(com.chain.ai.trade.backtest.entity.dos.BacktestTask::getTaskId, resultDTO.getTaskId());
            Long taskCount = backtestTaskMapper.selectCount(taskWrapper);
            
            if (taskCount == null || taskCount == 0L) {
                log.error("任务不存在，无法保存回测结果: taskId={}", resultDTO.getTaskId());
                return false;
            }
            
            BacktestResult result = BacktestResult.builder()
                    .taskId(resultDTO.getTaskId())
                    .strategyName(resultDTO.getStrategyName())
                    .totalReturn(resultDTO.getTotalReturn() != null ? resultDTO.getTotalReturn() : java.math.BigDecimal.ZERO)
                    .maxDrawdown(resultDTO.getMaxDrawdown() != null ? resultDTO.getMaxDrawdown() : java.math.BigDecimal.ZERO)
                    .winRate(resultDTO.getWinRate() != null ? resultDTO.getWinRate() : java.math.BigDecimal.valueOf(0.5))
                    .totalTrades(resultDTO.getTotalTrades() != null ? resultDTO.getTotalTrades() : 0)
                    .winningTrades(resultDTO.getWinningTrades() != null ? resultDTO.getWinningTrades() : 0)
                    .profitFactor(resultDTO.getProfitFactor() != null ? resultDTO.getProfitFactor() : java.math.BigDecimal.ONE)
                    .finalValue(resultDTO.getFinalValue())
                    .sharpeRatio(resultDTO.getSharpeRatio())
                    .calmarRatio(resultDTO.getCalmarRatio())
                    .totalCost(resultDTO.getTotalCost())
                    .annualReturn(resultDTO.getAnnualReturn())
                    .volatility(resultDTO.getVolatility())
                    .sortinoRatio(resultDTO.getSortinoRatio())
                    .averageWin(resultDTO.getAverageWin())
                    .averageLoss(resultDTO.getAverageLoss())
                    .largestWinTrade(resultDTO.getLargestWinTrade())
                    .largestLossTrade(resultDTO.getLargestLossTrade())
                    .maxConsecutiveWins(resultDTO.getMaxConsecutiveWins())
                    .maxConsecutiveLosses(resultDTO.getMaxConsecutiveLosses())
                    .avgTradeDuration(resultDTO.getAvgTradeDuration())
                    .drawdownSeries(resultDTO.getDrawdownSeries())
                    .calculatedAt(resultDTO.getCalculatedAt() != null ? resultDTO.getCalculatedAt() : LocalDateTime.now())
                    .build();

            boolean saved = saveOrUpdate(result);
            
            // 将权益曲线数据保存到新表
            if (saved && resultDTO.getEquityCurve() != null && !resultDTO.getEquityCurve().trim().isEmpty()) {
                List<BacktestEquityCurve> equityCurveList = parseEquityCurveToTable(
                    resultDTO.getEquityCurve(), 
                    resultDTO.getDrawdownSeries()
                );
                equityCurveService.saveEquityCurve(resultDTO.getTaskId(), equityCurveList);
            }
            
            return saved;
        } catch (Exception e) {
            log.error("保存回测结果失败", e);
            return false;
        }
    }

    @Override
    public BacktestResultDTO getResult(String taskId) {
        BacktestResult result = getById(taskId);
        return result != null ? convertToDTO(result) : null;
    }

    @Override
    public boolean deleteResult(String taskId) {
        // 删除权益曲线数据
        equityCurveService.deleteByTaskId(taskId);
        // 删除回测结果
        return removeById(taskId);
    }

    private BacktestResultDTO convertToDTO(BacktestResult result) {
        
        BacktestResultDTO dto = BacktestResultDTO.builder()
                .taskId(result.getTaskId())
                .strategyName(result.getStrategyName())
                .totalReturn(result.getTotalReturn() != null ? result.getTotalReturn() : java.math.BigDecimal.ZERO)
                .maxDrawdown(result.getMaxDrawdown() != null ? result.getMaxDrawdown() : java.math.BigDecimal.ZERO)
                .winRate(result.getWinRate() != null ? result.getWinRate() : java.math.BigDecimal.valueOf(0.5))
                .totalTrades(result.getTotalTrades() != null ? result.getTotalTrades() : 0)
                .winningTrades(result.getWinningTrades() != null ? result.getWinningTrades() : 0)
                .profitFactor(result.getProfitFactor() != null ? result.getProfitFactor() : java.math.BigDecimal.ONE)
                .finalValue(result.getFinalValue())
                .sharpeRatio(result.getSharpeRatio())
                .calmarRatio(result.getCalmarRatio())
                .totalCost(result.getTotalCost())
                .annualReturn(result.getAnnualReturn())
                .volatility(result.getVolatility())
                .sortinoRatio(result.getSortinoRatio())
                .averageWin(result.getAverageWin())
                .averageLoss(result.getAverageLoss())
                .largestWinTrade(result.getLargestWinTrade())
                .largestLossTrade(result.getLargestLossTrade())
                .maxConsecutiveWins(result.getMaxConsecutiveWins())
                .maxConsecutiveLosses(result.getMaxConsecutiveLosses())
                .avgTradeDuration(result.getAvgTradeDuration())
                .tradeRecords(new ArrayList<>()) // backtest_trade_record 表已废弃，交易记录已迁移至订单系统
                .equityCurve(convertEquityCurveToJson(result.getTaskId())) // 从新表查询并转换为JSON
                .drawdownSeries(result.getDrawdownSeries())
                .calculatedAt(result.getCalculatedAt())
                .build();

        return dto;
    }

    /**
     * 将权益曲线JSON数据解析并转换为BacktestEquityCurve列表
     */
    private List<BacktestEquityCurve> parseEquityCurveToTable(String equityCurveJson, String drawdownSeriesJson) {
        List<BacktestEquityCurve> result = new ArrayList<>();

        try {
            JsonNode equityArray = objectMapper.readTree(equityCurveJson);
            if (!equityArray.isArray()) {
                log.warn("权益曲线数据格式错误，不是数组格式");
                return result;
            }

            Map<LocalDateTime, BigDecimal> drawdownMap = new HashMap<>();
            if (drawdownSeriesJson != null && !drawdownSeriesJson.trim().isEmpty()) {
                try {
                    JsonNode drawdownArray = objectMapper.readTree(drawdownSeriesJson);
                    if (drawdownArray.isArray()) {
                        for (JsonNode node : drawdownArray) {
                            if (node.has("time") && node.has("drawdown")) {
                                long timestamp = node.get("time").asLong();
                                LocalDateTime time = Instant.ofEpochMilli(timestamp)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDateTime();
                                BigDecimal drawdown = BigDecimal.valueOf(node.get("drawdown").asDouble());
                                drawdownMap.put(time, drawdown);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析回撤数据失败", e);
                }
            }

            BigDecimal previousEquity = null;
            LocalDateTime previousTime = null;

            for (JsonNode node : equityArray) {
                if (!node.has("time") || !node.has("equity")) {
                    continue;
                }

                long timestamp = node.get("time").asLong();
                LocalDateTime currentTime = Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDateTime();
                BigDecimal equity = BigDecimal.valueOf(node.get("equity").asDouble());

                BigDecimal returnRate = null;
                if (previousEquity != null && previousEquity.compareTo(BigDecimal.ZERO) > 0) {
                    returnRate = equity.subtract(previousEquity)
                        .divide(previousEquity, 6, RoundingMode.HALF_UP);
                }

                BigDecimal drawdown = drawdownMap.get(currentTime);

                BacktestEquityCurve curve = BacktestEquityCurve.builder()
                    .time(currentTime)
                    .equity(equity)
                    .returnRate(returnRate)
                    .drawdown(drawdown)
                    .benchmarkValue(null)
                    .benchmarkReturnRate(null)
                    .build();

                result.add(curve);
                previousEquity = equity;
                previousTime = currentTime;
            }

            result.sort((a, b) -> a.getTime().compareTo(b.getTime()));

            log.info("解析权益曲线数据成功，共{}条记录", result.size());
        } catch (Exception e) {
            log.error("解析权益曲线数据失败", e);
        }

        return result;
    }

    /**
     * 将BacktestEquityCurve列表转换为JSON字符串（用于向后兼容）
     */
    private String convertEquityCurveToJson(String taskId) {
        try {
            List<BacktestEquityCurve> curves = equityCurveService.getEquityCurveByTaskId(taskId);
            if (curves == null || curves.isEmpty()) {
                return null;
            }

            List<Map<String, Object>> jsonList = new ArrayList<>();
            for (BacktestEquityCurve curve : curves) {
                Map<String, Object> item = new HashMap<>();
                // 将LocalDateTime转换为时间戳（毫秒），使用UTC时区
                long timestamp = curve.getTime().toInstant(ZoneOffset.UTC).toEpochMilli();
                item.put("time", timestamp);
                item.put("equity", curve.getEquity());
                jsonList.add(item);
            }
            
            return objectMapper.writeValueAsString(jsonList);
        } catch (Exception e) {
            log.error("转换权益曲线数据为JSON失败: taskId={}", taskId, e);
            return null;
        }
    }
}
