package com.chain.ai.trade.engine.controller.test;

import com.chain.ai.trade.backtest.service.BacktestTaskService;
import com.chain.ai.trade.backtest.entity.dto.BacktestResultDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 回测报表控制器
 * 提供回测相关的报表和统计功能
 */
@Slf4j
@RestController
@RequestMapping("/api/backtest")
@ConditionalOnClass(name = "com.chain.ai.trade.backtest.service.BacktestTaskService")
@RequiredArgsConstructor
public class BacktestReportController {

    private final BacktestTaskService backtestTaskService;

    /**
     * 获取收益统计数据
     */
    @GetMapping("/performance/{taskId}")
    public ResponseEntity<Map<String, Object>> getBacktestPerformance(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "daily") String period) {

        log.info("获取收益统计数据: taskId={}, period={}", taskId, period);

        try {
            BacktestResultDTO result = backtestTaskService.getBacktestResult(taskId);
            log.debug("获取回测结果: {}", result != null ? "成功" : "失败");

            if (result == null) {
                log.warn("回测结果不存在: taskId={}", taskId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("errorMessage", "回测结果不存在");
                return ResponseEntity.notFound().build();
            }

            if (result.getEquityCurve() == null) {
                log.warn("回测结果无权益曲线数据: taskId={}", taskId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("errorMessage", "无权益曲线数据");
                return ResponseEntity.notFound().build();
            }

            // 解析权益曲线数据
            List<Map<String, Object>> equityData = parseEquityCurve(result.getEquityCurve());
            if (equityData.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("errorMessage", "权益曲线数据解析失败");
                return ResponseEntity.badRequest().body(response);
            }

            // 根据周期分组计算收益
            List<Map<String, Object>> performanceData;
            if ("monthly".equals(period)) {
                performanceData = calculateMonthlyPerformance(equityData);
            } else {
                performanceData = calculateDailyPerformance(equityData);
            }

            // 计算统计指标
            Map<String, Object> statistics = calculatePerformanceStatistics(performanceData);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("taskId", taskId);
            response.put("period", period);
            response.put("data", performanceData);
            response.put("statistics", statistics);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取收益统计数据失败: taskId={}, period={}", taskId, period, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("errorMessage", "获取收益统计数据失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 解析权益曲线JSON字符串
     */
    private List<Map<String, Object>> parseEquityCurve(String equityCurveJson) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            if (equityCurveJson == null || equityCurveJson.trim().isEmpty()) {
                log.warn("权益曲线JSON字符串为空");
                return result;
            }

            log.debug("解析权益曲线数据: {}", equityCurveJson.substring(0, Math.min(200, equityCurveJson.length())));

            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> data = objectMapper.readValue(equityCurveJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            log.info("成功解析权益曲线数据，共 {} 个数据点", data != null ? data.size() : 0);

            return data != null ? data : result;

        } catch (Exception e) {
            log.error("解析权益曲线数据失败: {}", e.getMessage(), e);
            return result;
        }
    }

    /**
     * 计算日收益统计
     */
    private List<Map<String, Object>> calculateDailyPerformance(List<Map<String, Object>> equityData) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (equityData.isEmpty()) return result;

        // 按日期分组
        Map<String, List<Map<String, Object>>> dailyGroups = new LinkedHashMap<>();

        for (Map<String, Object> point : equityData) {
            Object timeObj = point.get("time");
            Object equityObj = point.get("equity");

            if (timeObj == null || equityObj == null) continue;

            long timestamp = ((Number) timeObj).longValue();
            java.time.LocalDate date = java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();

            String dateStr = date.toString();
            dailyGroups.computeIfAbsent(dateStr, k -> new ArrayList<>()).add(point);
        }

        // 计算每日收益
        List<String> sortedDates = new ArrayList<>(dailyGroups.keySet());
        Collections.sort(sortedDates);

        for (int i = 0; i < sortedDates.size(); i++) {
            String dateStr = sortedDates.get(i);
            List<Map<String, Object>> dayData = dailyGroups.get(dateStr);

            if (dayData.isEmpty()) continue;

            // 使用当日最后的值
            Map<String, Object> lastPoint = dayData.get(dayData.size() - 1);
            double endEquity = ((Number) lastPoint.get("equity")).doubleValue();

            double startEquity = endEquity;
            if (i > 0) {
                // 使用前一日的结束权益作为基准
                String prevDate = sortedDates.get(i - 1);
                List<Map<String, Object>> prevDayData = dailyGroups.get(prevDate);
                if (!prevDayData.isEmpty()) {
                    Map<String, Object> prevLastPoint = prevDayData.get(prevDayData.size() - 1);
                    startEquity = ((Number) prevLastPoint.get("equity")).doubleValue();
                }
            }

            // 计算收益率
            double returnRate = 0.0;
            if (startEquity > 0) {
                returnRate = (endEquity - startEquity) / startEquity * 100.0;
            }

            Map<String, Object> dailyResult = new HashMap<>();
            dailyResult.put("date", dateStr);
            dailyResult.put("return", Math.round(returnRate * 10000.0) / 10000.0); // 保留4位小数

            result.add(dailyResult);
        }

        return result;
    }

    /**
     * 计算月收益统计
     */
    private List<Map<String, Object>> calculateMonthlyPerformance(List<Map<String, Object>> equityData) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (equityData.isEmpty()) return result;

        // 按月份分组
        Map<String, List<Map<String, Object>>> monthlyGroups = new LinkedHashMap<>();

        for (Map<String, Object> point : equityData) {
            Object timeObj = point.get("time");
            Object equityObj = point.get("equity");

            if (timeObj == null || equityObj == null) continue;

            long timestamp = ((Number) timeObj).longValue();
            java.time.LocalDate date = java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();

            String monthStr = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            monthlyGroups.computeIfAbsent(monthStr, k -> new ArrayList<>()).add(point);
        }

        // 计算每月收益
        List<String> sortedMonths = new ArrayList<>(monthlyGroups.keySet());
        Collections.sort(sortedMonths);

        for (int i = 0; i < sortedMonths.size(); i++) {
            String monthStr = sortedMonths.get(i);
            List<Map<String, Object>> monthData = monthlyGroups.get(monthStr);

            if (monthData.isEmpty()) continue;

            // 使用月最后的值
            Map<String, Object> lastPoint = monthData.get(monthData.size() - 1);
            double endEquity = ((Number) lastPoint.get("equity")).doubleValue();

            double startEquity = endEquity;
            if (i > 0) {
                // 使用前一月的结束权益作为基准
                String prevMonth = sortedMonths.get(i - 1);
                List<Map<String, Object>> prevMonthData = monthlyGroups.get(prevMonth);
                if (!prevMonthData.isEmpty()) {
                    Map<String, Object> prevLastPoint = prevMonthData.get(prevMonthData.size() - 1);
                    startEquity = ((Number) prevLastPoint.get("equity")).doubleValue();
                }
            }

            // 计算收益率
            double returnRate = 0.0;
            if (startEquity > 0) {
                returnRate = (endEquity - startEquity) / startEquity * 100.0;
            }

            Map<String, Object> monthlyResult = new HashMap<>();
            monthlyResult.put("date", monthStr);
            monthlyResult.put("return", Math.round(returnRate * 10000.0) / 10000.0); // 保留4位小数

            result.add(monthlyResult);
        }

        return result;
    }

    /**
     * 计算收益统计指标
     */
    private Map<String, Object> calculatePerformanceStatistics(List<Map<String, Object>> performanceData) {
        Map<String, Object> stats = new HashMap<>();

        if (performanceData.isEmpty()) {
            stats.put("totalReturn", 0.0);
            stats.put("winRate", 0.0);
            stats.put("avgDailyReturn", 0.0);
            stats.put("maxDailyLoss", 0.0);
            return stats;
        }

        double totalReturn = 0.0;
        int winCount = 0;
        double maxLoss = 0.0;
        double sumReturn = 0.0;

        for (Map<String, Object> item : performanceData) {
            double returnRate = ((Number) item.get("return")).doubleValue();
            sumReturn += returnRate;

            if (returnRate > 0) {
                winCount++;
            }

            if (returnRate < maxLoss) {
                maxLoss = returnRate;
            }
        }

        // 计算总收益率（累乘）
        double cumulativeReturn = 1.0;
        for (Map<String, Object> item : performanceData) {
            double returnRate = ((Number) item.get("return")).doubleValue() / 100.0; // 转换为小数
            cumulativeReturn *= (1.0 + returnRate);
        }
        totalReturn = (cumulativeReturn - 1.0) * 100.0;

        double winRate = (double) winCount / performanceData.size() * 100.0;
        double avgDailyReturn = sumReturn / performanceData.size();

        stats.put("totalReturn", Math.round(totalReturn * 100.0) / 100.0);
        stats.put("winRate", Math.round(winRate * 100.0) / 100.0);
        stats.put("avgDailyReturn", Math.round(avgDailyReturn * 100.0) / 100.0);
        stats.put("maxDailyLoss", Math.round(maxLoss * 100.0) / 100.0);

        return stats;
    }
}
