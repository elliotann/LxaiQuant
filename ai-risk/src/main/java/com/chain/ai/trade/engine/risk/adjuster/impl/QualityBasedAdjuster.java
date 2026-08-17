package com.chain.ai.trade.engine.risk.adjuster.impl;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.common.utils.ContractSpecUtils;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.common.utils.TradingUtil;
import com.chain.ai.trade.engine.risk.adjuster.AdjustmentContext;
import com.chain.ai.trade.engine.risk.adjuster.AdjustmentResult;
import com.chain.ai.trade.engine.risk.adjuster.PositionAdjuster;
import com.chain.ai.trade.engine.risk.adjuster.RiskAssessment;
import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.util.*;

/**
 * 基于质量得分的仓位调节器 - 输出权重倍数（0-3范围）
 */
@Slf4j
@Component("quality-weight-adjuster")
public class QualityBasedAdjuster implements PositionAdjuster {

    // 基础配置
    @Value("${risk.adjuster.quality-based.enabled:true}")
    private boolean enabled;

    @Value("${risk.adjuster.quality-based.weight:1.0}")
    private double weight;

    @Value("${risk.adjuster.quality-based.min-quality-score:0.3}")
    private double minQualityScore;

    @Value("${risk.adjuster.quality-based.max-quality-score:0.9}")
    private double maxQualityScore;

    @Value("${risk.adjuster.quality-based.quality-multiplier:2.0}") // 调整为2.0以支持更大范围
    private double qualityMultiplier;

    // 分桶配置
    @Value("${risk.adjuster.quality-based.buckets.low.range-start:0.3}")
    private double lowRangeStart;

    @Value("${risk.adjuster.quality-based.buckets.low.range-end:0.5}")
    private double lowRangeEnd;

    @Value("${risk.adjuster.quality-based.buckets.low.factor:0.0}") // 低质量信号权重为0
    private double lowFactor;

    @Value("${risk.adjuster.quality-based.buckets.medium.range-start:0.5}")
    private double mediumRangeStart;

    @Value("${risk.adjuster.quality-based.buckets.medium.range-end:0.7}")
    private double mediumRangeEnd;

    @Value("${risk.adjuster.quality-based.buckets.medium.factor:1.0}") // 中等质量信号权重为1
    private double mediumFactor;

    @Value("${risk.adjuster.quality-based.buckets.high.range-start:0.7}")
    private double highRangeStart;

    @Value("${risk.adjuster.quality-based.buckets.high.range-end:0.9}")
    private double highRangeEnd;

    @Value("${risk.adjuster.quality-based.buckets.high.factor:2.0}") // 高质量信号权重为2
    private double highFactor;

    // 最大权重限制
    @Value("${risk.adjuster.quality-based.max-weight:3.0}")
    private double maxWeight; // 权重上限

    // 基于止损距离的市场调整配置
    @Value("${risk.adjuster.quality-based.stop-loss-adjustment.enabled:true}")
    private boolean stopLossAdjustmentEnabled;

    @Value("${risk.adjuster.quality-based.stop-loss-adjustment.warning-threshold:0.03}")
    private double stopLossWarningThreshold;

    @Value("${risk.adjuster.quality-based.stop-loss-adjustment.reduction-threshold:0.05}")
    private double stopLossReductionThreshold;

    @Value("${risk.adjuster.quality-based.stop-loss-adjustment.reduction-factor:0.5}")
    private double stopLossReductionFactor;

    @Value("${risk.adjuster.quality-based.stop-loss-adjustment.severe-threshold:0.08}")
    private double stopLossSevereThreshold;

    @Value("${risk.adjuster.quality-based.stop-loss-adjustment.severe-reduction-factor:0.0}")
    private double stopLossSevereReductionFactor; // 严重情况权重设为0

    // 风险收益比配置
    @Value("${risk.adjuster.quality-based.risk-reward.min-ratio:1.0}")
    private double minRiskRewardRatio;

    @Value("${risk.adjuster.quality-based.risk-reward.adjustment-enabled:true}")
    private boolean riskRewardAdjustmentEnabled;

    @Autowired(required = false)
    private RedisCache redisCache;

    @Override
    public String getId() {
        return "quality-weight-adjuster";
    }

    @Override
    public AdjustmentResult adjust(
            TradingSignalDto signal,
            double qualityScore,
            double basePosition,
            AdjustmentContext context) {

        if (!enabled) {
            return AdjustmentResult.builder()
                    .adjustedWeight(1.0)
                    .positionSize(basePosition)
                    .adjustments(Collections.emptyList())
                    .build();
        }

        // 限制质量得分范围到 0-1
        qualityScore = Math.max(0.0, Math.min(1.0, qualityScore));

        // 简单模式：仓位 = 基础资金 × 质量权重（USDT）
        double positionSize = basePosition * qualityScore;

        Map<String, Double> factors = new HashMap<>();
        factors.put("qualityScore", qualityScore);
        factors.put("positionSize", positionSize);

        return AdjustmentResult.builder()
                .adjustedWeight(qualityScore)
                .positionSize(positionSize)
                .factors(factors)
                .adjustments(Collections.emptyList())
                .build();
    }

    @Override
    public BigDecimal adjust(Double initialCapital, String coinId,double leverage , BigDecimal signalStrength, Num nowPrice) {

        if (initialCapital != null && initialCapital > 0 && nowPrice != null) {
            Double riskCapital = initialCapital;
            if(signalStrength!=null&&signalStrength.compareTo(BigDecimal.ZERO)>0){
                riskCapital = initialCapital*signalStrength.doubleValue();
            }

            double currentPrice = nowPrice.doubleValue();


            String symbol = ContractSpecUtils.normalizeSymbol(Exchange.OKX, coinId.toUpperCase());
            ContractSpec contractSpec = ContractSpecUtils.getContractSpec(redisCache, Exchange.OKX, symbol);
            double contractSize = contractSpec.getContractSize().doubleValue();

            double contractQuantity = TradingUtil.convertUsdtToContractSize(

                    riskCapital, currentPrice, leverage, contractSize);
            log.info("根据初始资金计算下单数量: initialCapital={} USDT, currentPrice={}, leverage={}, contractSize={}, orderAmount={} 张",
                    initialCapital, currentPrice, leverage, contractSize, contractQuantity);
            return BigDecimal.valueOf(contractQuantity);
        } else {
            log.warn("无法根据初始资金计算下单数量，使用默认值: orderAmount=1");
            return BigDecimal.ZERO;
        }
    }

    /**
     * 使用S型曲线计算质量权重（输出范围 0-2.5）
     */
    private double calculateSigmoidQualityWeight(double qualityScore) {
        double normalized = (qualityScore - minQualityScore) / (maxQualityScore - minQualityScore);
        normalized = Math.max(0.0, Math.min(1.0, normalized));

        // S型函数映射到 0-2.5 范围
        double sigmoid = 1.0 / (1.0 + Math.exp(-10 * (normalized - 0.5)));

        // 质量得分越高，权重越高（0.3分对应0权重，0.9分对应2.5权重）
        return 2.5 * sigmoid;
    }

    /**
     * 计算基于止损距离的市场调整因子
     */
    private double calculateMarketAdjustmentFactor(TradingSignalDto signal) {
        double adjustmentFactor = 1.0;

        // 检查信号是否包含止损价
        if (signal.getStopLossPrice() == null || signal.getStopLossPrice() <= 0) {
            log.debug("信号未提供止损价，跳过市场状态调整");
            return adjustmentFactor;
        }

        if (!stopLossAdjustmentEnabled) {
            return adjustmentFactor;
        }

        double triggerPrice = signal.getTriggerPrice();
        double stopLossPrice = signal.getStopLossPrice();

        // 验证价格有效性
        if (triggerPrice <= 0) {
            log.error("触发价格无效，无法计算止损距离: triggerPrice={}", triggerPrice);
            return adjustmentFactor;
        }

        // 计算止损距离百分比
        double stopLossDistance = Math.abs(triggerPrice - stopLossPrice);
        double stopLossPercent = stopLossDistance / triggerPrice;

        // 检查是否是异常值
        if (Double.isNaN(stopLossPercent) || Double.isInfinite(stopLossPercent)) {
            log.error("止损距离百分比计算异常: triggerPrice={}, stopLossPrice={}, percent={}",
                    triggerPrice, stopLossPrice, stopLossPercent);
            return adjustmentFactor;
        }

        // 基于止损距离的调整逻辑
        if (stopLossPercent > stopLossSevereThreshold) {
            // 严重情况：止损距离非常大（>8%），权重设为0
            adjustmentFactor = stopLossSevereReductionFactor;
            log.warn("止损距离极大({}%)，权重设为0", stopLossPercent * 100);

        } else if (stopLossPercent > stopLossReductionThreshold) {
            // 中等情况：止损距离较大（5%-8%），降低权重
            double severity = (stopLossPercent - stopLossReductionThreshold) /
                    (stopLossSevereThreshold - stopLossReductionThreshold);
            adjustmentFactor = stopLossReductionFactor +
                    (stopLossSevereReductionFactor - stopLossReductionFactor) * severity;
            log.debug("止损距离较大({}%)，权重降低到{}",
                    stopLossPercent * 100, adjustmentFactor);

        } else if (stopLossPercent > stopLossWarningThreshold) {
            // 警告情况：止损距离中等（3%-5%），轻微调整
            adjustmentFactor = 0.8;
            log.debug("止损距离中等({}%)，轻微降低权重", stopLossPercent * 100);
        }

        return adjustmentFactor;
    }

    /**
     * 应用风险收益比调整
     */
    private double applyRiskRewardAdjustment(TradingSignalDto signal, double currentWeight) {
        double triggerPrice = signal.getTriggerPrice();
        Double stopLossPrice = signal.getStopLossPrice();
        Double takeProfitPrice = signal.getTakeProfitPrice();

        // 验证价格有效性
        if (triggerPrice <= 0 || stopLossPrice == null || stopLossPrice <= 0 ||
                takeProfitPrice == null || takeProfitPrice <= 0) {
            return currentWeight;
        }

        // 计算风险和收益
        double risk = Math.abs(triggerPrice - stopLossPrice);
        double reward = Math.abs(takeProfitPrice - triggerPrice);

        if (risk <= 0) {
            return currentWeight;
        }

        double riskRewardRatio = reward / risk;

        // 根据风险收益比调整权重
        if (riskRewardRatio < minRiskRewardRatio) {
            // 风险收益比过低，大幅降低权重
            double reduction = riskRewardRatio / minRiskRewardRatio;
            return currentWeight * Math.max(0.1, reduction);
        } else if (riskRewardRatio > 3.0) {
            // 风险收益比很高，适当增加权重
            double increase = Math.min(1.5, 1.0 + (riskRewardRatio - 3.0) * 0.1);
            return currentWeight * increase;
        }

        return currentWeight;
    }

    /**
     * 计算建议仓位（基于权重和账户余额）
     */
    private double calculateSuggestedPosition(double weight, AdjustmentContext context) {
        // 假设您的基础仓位是账户余额的10%
        double baseCapital = context.getAccountBalance() * 0.1;

        // 实际仓位 = 基础仓位 × 权重
        double position = baseCapital * weight;

        // 应用单笔最大风险限制
        double maxRiskPerTrade = context.getAccountBalance() * 0.02; // 2%
        double maxPositionByRisk = maxRiskPerTrade / getAverageRiskPerUnit();

        return Math.min(position, maxPositionByRisk);
    }

    /**
     * 获取平均每单位风险（简化实现）
     */
    private double getAverageRiskPerUnit() {
        return 0.02; // 假设每单位平均2%的风险
    }

    /**
     * 准备调整因子详情
     */
    private Map<String, Double> prepareAdjustmentFactors(
            TradingSignalDto signal, double qualityScore, double qualityWeight,
            double finalWeight, double marketAdjustmentFactor) {

        Map<String, Double> factors = new LinkedHashMap<>();
        factors.put("qualityScore", qualityScore);
        factors.put("qualityWeight", qualityWeight);
        factors.put("finalWeight", finalWeight);
        factors.put("marketAdjustmentFactor", marketAdjustmentFactor);

        // 添加止损信息
        if (signal.getStopLossPrice() != null && signal.getTriggerPrice() > 0) {
            double triggerPrice = signal.getTriggerPrice();
            double stopLossPercent = Math.abs(triggerPrice - signal.getStopLossPrice()) / triggerPrice;
            factors.put("stopLossDistancePercent", stopLossPercent * 100);
        }

        return factors;
    }

    /**
     * 生成调整消息
     */
    private List<String> generateAdjustmentMessages(
            double finalWeight, double marketAdjustmentFactor, TradingSignalDto signal) {

        List<String> adjustments = new ArrayList<>();

        // 权重调整说明
        if (finalWeight > 2.0) {
            adjustments.add("信号质量优秀，使用高权重");
        } else if (finalWeight > 1.0) {
            adjustments.add("信号质量良好，使用中高权重");
        } else if (finalWeight > 0.5) {
            adjustments.add("信号质量一般，使用中等权重");
        } else if (finalWeight > 0.1) {
            adjustments.add("信号质量较差，使用低权重");
        } else {
            adjustments.add("信号质量差，权重为0");
        }

        // 市场调整说明
        if (marketAdjustmentFactor < 0.3) {
            adjustments.add("止损距离过大，大幅降低权重");
        } else if (marketAdjustmentFactor < 0.8) {
            adjustments.add("止损距离较大，降低权重");
        }

        return adjustments;
    }

    /**
     * 使用给定的止盈止损价格创建风险评估
     */
    private RiskAssessment createRiskAssessmentWithGivenPrices(TradingSignalDto signal,
                                                               double qualityScore,
                                                               double positionSize,
                                                               AdjustmentContext context) {
        RiskAssessment assessment = new RiskAssessment();

        double triggerPrice = signal.getTriggerPrice();
        Double stopLossPrice = signal.getStopLossPrice();
        Double takeProfitPrice = signal.getTakeProfitPrice();

        // 使用给定的止盈止损价格
        if (stopLossPrice != null && stopLossPrice > 0) {
            assessment.setStopLossPrice(stopLossPrice);
        }

        if (takeProfitPrice != null && takeProfitPrice > 0) {
            assessment.setTakeProfitPrice(takeProfitPrice);
        }

        // 计算风险收益比
        if (stopLossPrice != null && takeProfitPrice != null && triggerPrice > 0) {
            double risk = Math.abs(triggerPrice - stopLossPrice);
            double reward = Math.abs(takeProfitPrice - triggerPrice);
            double riskRewardRatio = risk > 0 ? reward / risk : 0;
            assessment.setRiskRewardRatio(riskRewardRatio);
        }

        // 设置最大仓位
        assessment.setMaxPositionSize(positionSize * 1.2);

        // 设置风险等级
        assessment.setRiskLevel(determineRiskLevel(qualityScore));

        return assessment;
    }

    /**
     * 确定风险等级
     */
    private String determineRiskLevel(double qualityScore) {
        if (qualityScore > 0.7) return "LOW";
        if (qualityScore > 0.5) return "MEDIUM";
        return "HIGH";
    }

    /**
     * 创建默认结果
     */
    private AdjustmentResult createDefaultResult(TradingSignalDto signal, double defaultWeight) {
        return AdjustmentResult.builder()
                .adjustedWeight(defaultWeight)
                .positionSize(0.0)
                .factors(Map.of("adjusterEnabled", 0.0))
                .adjustments(List.of("质量调节器已禁用，使用默认权重"))
                .riskAssessment(new RiskAssessment())
                .build();
    }

    /**
     * 记录调整详情
     */
    private void logAdjustmentDetails(TradingSignalDto signal, double qualityScore,
                                      double finalWeight, double suggestedPosition,
                                      AdjustmentContext context) {

        log.info("权重调节完成: 信号ID={}, 质量得分={}, 最终权重={}, 建议仓位={}, 账户余额={}",
                signal.getId(),
                String.format("%.3f", qualityScore),
                String.format("%.3f", finalWeight),
                String.format("%.2f", suggestedPosition),
                String.format("%.2f", context.getAccountBalance()));
    }
}