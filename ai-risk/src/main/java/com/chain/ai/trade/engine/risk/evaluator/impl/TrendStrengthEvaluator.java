package com.chain.ai.trade.engine.risk.evaluator.impl;

import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.entity.dto.AnalysisData;
import com.chain.ai.trade.engine.risk.evaluator.EvaluationContext;
import com.chain.ai.trade.engine.risk.evaluator.QualityEvaluationResult;
import com.chain.ai.trade.engine.risk.evaluator.QualityEvaluator;
import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
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
 * 趋势强度评估器
 * 评估趋势方向、力度、持续性
 */
@Slf4j
@Component
public class TrendStrengthEvaluator implements QualityEvaluator {

    @Value("${risk.evaluator.trend-strength.weight:1.2}")
    private double weight;

    @Value("${risk.evaluator.trend-strength.period:50}")
    private int period;

    @Value("${risk.evaluator.trend-strength.threshold:0.6}")
    private double threshold;

    @Override
    public String getId() {
        return "trend-strength";
    }

    @Override
    public String getName() {
        return "趋势强度评估器";
    }

    @Override
    public String getDescription() {
        return "评估趋势方向、力度和持续性";
    }

    @Override
    public double getWeight() {
        return weight;
    }

    @Override
    public QualityEvaluationResult evaluate(TradingSignalDto signal, EvaluationContext context) {
        AnalysisData data = context.getAnalysisData();
        if (data == null || data.getBars().size() < period) {
            return QualityEvaluationResult.builder()
                    .evaluatorId(getId())
                    .signalId(signal.getId())
                    .score(0.5)
                    .weight(getWeight())
                    .summary("数据不足，无法评估趋势强度")
                    .warnings(List.of("K线数据不足"))
                    .build();
        }

        List<Candlestick> bars = data.getBars();
        int size = bars.size();

        // 计算趋势方向
        double trendDirection = calculateTrendDirection(bars, size);
        
        // 计算趋势力度
        double trendStrength = calculateTrendStrength(bars, size);
        
        // 计算趋势持续性
        double trendConsistency = calculateTrendConsistency(bars, size);

        // 综合得分
        double score = (trendDirection * 0.4 + trendStrength * 0.4 + trendConsistency * 0.2);

        Map<String, Object> factors = new HashMap<>();
        factors.put("trendDirection", trendDirection);
        factors.put("trendStrength", trendStrength);
        factors.put("trendConsistency", trendConsistency);

        List<String> recommendations = new ArrayList<>();
        if (score > threshold) {
            recommendations.add("趋势强度较高，信号可靠性增强");
        } else {
            recommendations.add("趋势强度较低，建议谨慎操作");
        }

        return QualityEvaluationResult.builder()
                .evaluatorId(getId())
                .signalId(signal.getId())
                .score(Math.max(0.0, Math.min(1.0, score)))
                .weight(getWeight())
                .factors(factors)
                .summary(String.format("趋势强度: %.2f, 方向: %.2f, 力度: %.2f, 持续性: %.2f",
                        score, trendDirection, trendStrength, trendConsistency))
                .recommendations(recommendations)
                .build();
    }

    /**
     * 计算趋势方向（-1到1，正数表示上涨趋势）
     */
    private double calculateTrendDirection(List<Candlestick> bars, int size) {
        if (size < 2) return 0.0;

        BigDecimal startPrice = bars.get(size - period).getClosePrice();
        BigDecimal endPrice = bars.get(size - 1).getClosePrice();
        
        if (startPrice == null || endPrice == null || startPrice.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        
        double change = endPrice.subtract(startPrice).divide(startPrice, 4, RoundingMode.HALF_UP).doubleValue();
        return Math.tanh(change * 10); // 使用tanh归一化到-1到1
    }

    /**
     * 计算趋势力度（0-1）
     */
    private double calculateTrendStrength(List<Candlestick> bars, int size) {
        if (size < period) return 0.5;

        double totalStrength = 0.0;
        for (int i = size - period; i < size - 1; i++) {
            BigDecimal close1 = bars.get(i).getClosePrice();
            BigDecimal close2 = bars.get(i + 1).getClosePrice();
            
            if (close1 == null || close2 == null) continue;
            
            double priceChange = Math.abs(close2.subtract(close1).doubleValue());
            double avgPrice = close1.add(close2).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP).doubleValue();
            if (avgPrice > 0) {
                totalStrength += priceChange / avgPrice;
            }
        }

        double avgStrength = totalStrength / (period - 1);
        return Math.min(1.0, avgStrength * 100); // 归一化到0-1
    }

    /**
     * 计算趋势持续性（0-1）
     */
    private double calculateTrendConsistency(List<Candlestick> bars, int size) {
        if (size < period) return 0.5;

        int upCount = 0;
        int downCount = 0;
        
        for (int i = size - period; i < size - 1; i++) {
            BigDecimal close1 = bars.get(i).getClosePrice();
            BigDecimal close2 = bars.get(i + 1).getClosePrice();
            
            if (close1 == null || close2 == null) continue;
            
            if (close2.compareTo(close1) > 0) {
                upCount++;
            } else {
                downCount++;
            }
        }

        double consistency = Math.max(upCount, downCount) / (double) (period - 1);
        return consistency;
    }
}

