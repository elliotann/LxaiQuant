package com.chain.ai.trade.engine.risk.evaluator.impl;

import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
import com.chain.ai.trade.engine.entity.dto.AnalysisData;
import com.chain.ai.trade.engine.risk.evaluator.EvaluationContext;
import com.chain.ai.trade.engine.risk.evaluator.QualityEvaluationResult;
import com.chain.ai.trade.engine.risk.evaluator.QualityEvaluator;
import com.chain.ai.trade.common.entity.constants.SignalType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险收益比评估器
 * 评估止损距离和目标距离，计算风险收益比
 */
@Slf4j
@Component
public class RiskRewardEvaluator implements QualityEvaluator {

    @Value("${risk.evaluator.risk-reward.weight:1.0}")
    private double weight;

    @Value("${risk.evaluator.risk-reward.min-risk-reward-ratio:1.5}")
    private double minRiskRewardRatio; // 最小风险收益比

    @Value("${risk.evaluator.risk-reward.max-risk-reward-ratio:5.0}")
    private double maxRiskRewardRatio; // 最大风险收益比（超过此值可能不现实）

    @Value("${risk.evaluator.risk-reward.stop-loss-percent:0.02}")
    private double defaultStopLossPercent; // 默认止损百分比（2%）

    @Value("${risk.evaluator.risk-reward.take-profit-percent:0.04}")
    private double defaultTakeProfitPercent; // 默认止盈百分比（4%）

    @Override
    public String getId() {
        return "risk-reward";
    }

    @Override
    public String getName() {
        return "风险收益比评估器";
    }

    @Override
    public String getDescription() {
        return "评估止损距离和目标距离，计算风险收益比";
    }

    @Override
    public double getWeight() {
        return weight;
    }

    @Override
    public QualityEvaluationResult evaluate(TradingSignalDto signal, EvaluationContext context) {
        AnalysisData data = context.getAnalysisData();
        if (data == null || data.getBars().isEmpty()) {
            return QualityEvaluationResult.builder()
                    .evaluatorId(getId())
                    .signalId(signal.getId())
                    .score(0.5)
                    .weight(getWeight())
                    .summary("数据不足，无法评估风险收益比")
                    .warnings(List.of("K线数据不足"))
                    .build();
        }

        List<Candlestick> bars = data.getBars();
        BigDecimal currentPrice = bars.get(bars.size() - 1).getClosePrice();
        BigDecimal triggerPrice = BigDecimal.valueOf(signal.getTriggerPrice());

        if (currentPrice == null || triggerPrice == null) {
            return QualityEvaluationResult.builder()
                    .evaluatorId(getId())
                    .signalId(signal.getId())
                    .score(0.5)
                    .weight(getWeight())
                    .summary("价格数据无效")
                    .build();
        }

        // 计算止损价和止盈价（简化实现，实际应从策略或元数据获取）
        BigDecimal stopLossPrice = calculateStopLossPrice(triggerPrice, signal.getType());
        BigDecimal takeProfitPrice = calculateTakeProfitPrice(triggerPrice, signal.getType());

        // 计算风险收益比
        double riskRewardRatio = calculateRiskRewardRatio(triggerPrice, stopLossPrice, takeProfitPrice, signal.getType());

        // 评估风险收益比
        double score = evaluateRiskRewardRatio(riskRewardRatio);

        Map<String, Object> factors = new HashMap<>();
        factors.put("triggerPrice", triggerPrice.doubleValue());
        factors.put("stopLossPrice", stopLossPrice.doubleValue());
        factors.put("takeProfitPrice", takeProfitPrice.doubleValue());
        factors.put("riskRewardRatio", riskRewardRatio);

        List<String> warnings = new ArrayList<>();
        if (riskRewardRatio < minRiskRewardRatio) {
            warnings.add(String.format("风险收益比 %.2f 低于最低要求 %.2f", riskRewardRatio, minRiskRewardRatio));
        }
        if (riskRewardRatio > maxRiskRewardRatio) {
            warnings.add(String.format("风险收益比 %.2f 过高，可能不现实", riskRewardRatio));
        }

        List<String> recommendations = new ArrayList<>();
        if (score > 0.7) {
            recommendations.add("风险收益比较优，建议执行");
        } else if (score > 0.5) {
            recommendations.add("风险收益比一般，建议谨慎操作");
        } else {
            recommendations.add("风险收益比不佳，建议等待更好机会");
        }

        return QualityEvaluationResult.builder()
                .evaluatorId(getId())
                .signalId(signal.getId())
                .score(Math.max(0.0, Math.min(1.0, score)))
                .weight(getWeight())
                .factors(factors)
                .summary(String.format("风险收益比: %.2f, 止损: %.4f, 止盈: %.4f", 
                        riskRewardRatio, stopLossPrice.doubleValue(), takeProfitPrice.doubleValue()))
                .warnings(warnings)
                .recommendations(recommendations)
                .build();
    }

    /**
     * 计算止损价
     */
    private BigDecimal calculateStopLossPrice(BigDecimal triggerPrice, SignalType signalType) {
        if (signalType.name().equals("BUY")) {
            // 买入信号：止损价低于触发价
            return triggerPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(defaultStopLossPercent)));
        } else {
            // 卖出信号：止损价高于触发价
            return triggerPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(defaultStopLossPercent)));
        }
    }

    /**
     * 计算止盈价
     */
    private BigDecimal calculateTakeProfitPrice(BigDecimal triggerPrice, SignalType signalType) {
        if (signalType.name().equals("BUY")) {
            // 买入信号：止盈价高于触发价
            return triggerPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(defaultTakeProfitPercent)));
        } else {
            // 卖出信号：止盈价低于触发价
            return triggerPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(defaultTakeProfitPercent)));
        }
    }

    /**
     * 计算风险收益比
     */
    private double calculateRiskRewardRatio(BigDecimal triggerPrice, BigDecimal stopLossPrice, 
                                           BigDecimal takeProfitPrice, 
                                           SignalType signalType) {
        BigDecimal risk = triggerPrice.subtract(stopLossPrice).abs();
        BigDecimal reward = takeProfitPrice.subtract(triggerPrice).abs();

        if (risk.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }

        return reward.divide(risk, 4, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 评估风险收益比得分（0-1）
     */
    private double evaluateRiskRewardRatio(double ratio) {
        if (ratio < minRiskRewardRatio) {
            // 低于最低要求，得分较低
            return 0.3 + (ratio / minRiskRewardRatio) * 0.2;
        } else if (ratio <= maxRiskRewardRatio) {
            // 在合理范围内，得分较高
            double normalizedRatio = (ratio - minRiskRewardRatio) / (maxRiskRewardRatio - minRiskRewardRatio);
            return 0.5 + normalizedRatio * 0.5;
        } else {
            // 超过最大值，可能不现实，得分下降
            return 0.8 - (ratio - maxRiskRewardRatio) / maxRiskRewardRatio * 0.3;
        }
    }
}

