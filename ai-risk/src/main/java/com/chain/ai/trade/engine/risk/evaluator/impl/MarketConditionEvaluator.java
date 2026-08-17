package com.chain.ai.trade.engine.risk.evaluator.impl;

import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.entity.dto.AnalysisData;
import com.chain.ai.trade.engine.risk.evaluator.EvaluationContext;
import com.chain.ai.trade.engine.risk.evaluator.QualityEvaluationResult;
import com.chain.ai.trade.engine.risk.evaluator.QualityEvaluator;
import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
import com.chain.ai.trade.common.entity.constants.SignalType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 市场环境评估器
 * 评估波动率、趋势类型、市场阶段
 */
@Slf4j
@Component
public class MarketConditionEvaluator implements QualityEvaluator {

    @Value("${risk.evaluator.market-condition.weight:1.0}")
    private double weight;

    @Value("${risk.evaluator.market-condition.volatility-period:20}")
    private int volatilityPeriod;

    @Value("${risk.evaluator.market-condition.high-volatility-threshold:0.05}")
    private double highVolatilityThreshold; // 高波动率阈值（5%）

    @Value("${risk.evaluator.market-condition.low-volatility-threshold:0.01}")
    private double lowVolatilityThreshold; // 低波动率阈值（1%）

    @Override
    public String getId() {
        return "market-condition";
    }

    @Override
    public String getName() {
        return "市场环境评估器";
    }

    @Override
    public String getDescription() {
        return "评估波动率、趋势类型、市场阶段";
    }

    @Override
    public double getWeight() {
        return weight;
    }

    @Override
    public QualityEvaluationResult evaluate(TradingSignalDto signal, EvaluationContext context) {
        AnalysisData data = context.getAnalysisData();
        if (data == null || data.getBars().size() < volatilityPeriod) {
            return QualityEvaluationResult.builder()
                    .evaluatorId(getId())
                    .signalId(signal.getId())
                    .score(0.5)
                    .weight(getWeight())
                    .summary("数据不足，无法评估市场环境")
                    .warnings(List.of("K线数据不足"))
                    .build();
        }

        List<Candlestick> bars = data.getBars();
        int size = bars.size();

        // 计算波动率
        double volatility = calculateVolatility(bars, size);
        
        // 判断市场趋势类型
        String trendType = determineTrendType(bars, size);
        
        // 判断市场阶段
        String marketStage = determineMarketStage(bars, size, volatility);

        // 评估市场环境得分
        double score = evaluateMarketCondition(volatility, trendType, marketStage, signal.getType());

        Map<String, Object> factors = new HashMap<>();
        factors.put("volatility", volatility);
        factors.put("trendType", trendType);
        factors.put("marketStage", marketStage);

        List<String> warnings = new ArrayList<>();
        if (volatility > highVolatilityThreshold) {
            warnings.add(String.format("市场波动率较高 (%.2f%%)，请注意风险控制", volatility * 100));
        } else if (volatility < lowVolatilityThreshold) {
            warnings.add(String.format("市场波动率较低 (%.2f%%)，可能处于盘整阶段", volatility * 100));
        }

        List<String> recommendations = new ArrayList<>();
        if (score > 0.7) {
            recommendations.add("市场环境良好，适合执行交易");
        } else if (score > 0.5) {
            recommendations.add("市场环境一般，建议谨慎操作");
        } else {
            recommendations.add("市场环境不佳，建议等待更好机会");
        }

        return QualityEvaluationResult.builder()
                .evaluatorId(getId())
                .signalId(signal.getId())
                .score(Math.max(0.0, Math.min(1.0, score)))
                .weight(getWeight())
                .factors(factors)
                .summary(String.format("波动率: %.2f%%, 趋势类型: %s, 市场阶段: %s", 
                        volatility * 100, trendType, marketStage))
                .warnings(warnings)
                .recommendations(recommendations)
                .build();
    }

    /**
     * 计算波动率（标准差/均值）
     */
    private double calculateVolatility(List<Candlestick> bars, int size) {
        if (size < volatilityPeriod) return 0.0;

        int start = size - volatilityPeriod;
        List<Double> returns = new ArrayList<>();

        for (int i = start; i < size - 1; i++) {
            BigDecimal close1 = bars.get(i).getClosePrice();
            BigDecimal close2 = bars.get(i + 1).getClosePrice();
            
            if (close1 == null || close2 == null || close1.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            double returnValue = close2.subtract(close1).divide(close1, 4, RoundingMode.HALF_UP).doubleValue();
            returns.add(returnValue);
        }

        if (returns.isEmpty()) return 0.0;

        // 计算平均收益率
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        // 计算标准差
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        return Math.abs(stdDev);
    }

    /**
     * 判断趋势类型
     */
    private String determineTrendType(List<Candlestick> bars, int size) {
        if (size < 20) return "UNKNOWN";

        int start = size - 20;
        BigDecimal startPrice = bars.get(start).getClosePrice();
        BigDecimal endPrice = bars.get(size - 1).getClosePrice();

        if (startPrice == null || endPrice == null) return "UNKNOWN";

        double change = endPrice.subtract(startPrice).divide(startPrice, 4, RoundingMode.HALF_UP).doubleValue();

        if (change > 0.05) return "UPTREND";      // 上涨超过5%
        else if (change < -0.05) return "DOWNTREND"; // 下跌超过5%
        else return "SIDEWAYS";                    // 横盘
    }

    /**
     * 判断市场阶段
     */
    private String determineMarketStage(List<Candlestick> bars, int size, double volatility) {
        String trendType = determineTrendType(bars, size);
        
        if (volatility > highVolatilityThreshold) {
            return "HIGH_VOLATILITY";
        } else if (volatility < lowVolatilityThreshold) {
            return "LOW_VOLATILITY";
        } else if (trendType.equals("UPTREND")) {
            return "TRENDING_UP";
        } else if (trendType.equals("DOWNTREND")) {
            return "TRENDING_DOWN";
        } else {
            return "CONSOLIDATION";
        }
    }

    /**
     * 评估市场环境得分
     */
    private double evaluateMarketCondition(double volatility, String trendType, 
                                          String marketStage,
                                          SignalType signalType) {
        double score = 0.5; // 基础得分

        // 波动率评估（中等波动率最佳）
        if (volatility >= lowVolatilityThreshold && volatility <= highVolatilityThreshold) {
            score += 0.2; // 波动率适中，加分
        } else if (volatility > highVolatilityThreshold) {
            score -= 0.1; // 波动率过高，减分
        } else {
            score -= 0.1; // 波动率过低，减分
        }

        // 趋势类型评估
        if (signalType.name().equals("BUY") && trendType.equals("UPTREND")) {
            score += 0.2; // 买入信号 + 上涨趋势，加分
        } else if (signalType.name().equals("SELL") && trendType.equals("DOWNTREND")) {
            score += 0.2; // 卖出信号 + 下跌趋势，加分
        } else if (trendType.equals("SIDEWAYS")) {
            score -= 0.1; // 横盘，减分
        }

        // 市场阶段评估
        if (marketStage.equals("TRENDING_UP") || marketStage.equals("TRENDING_DOWN")) {
            score += 0.1; // 趋势市场，加分
        } else if (marketStage.equals("HIGH_VOLATILITY")) {
            score -= 0.2; // 高波动，减分
        }

        return Math.max(0.0, Math.min(1.0, score));
    }
}

