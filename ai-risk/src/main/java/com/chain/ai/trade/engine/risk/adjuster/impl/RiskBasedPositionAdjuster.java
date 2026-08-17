package com.chain.ai.trade.engine.risk.adjuster.impl;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.common.utils.ContractSpecUtils;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.common.utils.TradingUtil;
import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
import com.chain.ai.trade.engine.risk.adjuster.AdjustmentContext;
import com.chain.ai.trade.engine.risk.adjuster.AdjustmentResult;
import com.chain.ai.trade.engine.risk.adjuster.PositionAdjuster;
import com.chain.ai.trade.engine.risk.adjuster.RiskAssessment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.util.*;

/**
 * 基于风险的仓位调节器 - 以损定量
 * <p>
 * 核心公式：
 * 固定风险 = 账户净值 × 单笔风险比例（由综合评分决定）
 * 仓位 = 固定风险 ÷ 止损距离（由 SMC 第一道止损决定）
 * </p>
 * <p>
 * Bean名称: risk-based (与 PositionAdjusterType.RISK 对应)
 * </p>
 *
 * @author system
 * @version 1.0
 * @since 2026-07-25
 */
@Slf4j
@Component("risk-based")
public class RiskBasedPositionAdjuster implements PositionAdjuster {

    // ==================== 配置（支持前端覆盖） ====================

    @Value("${risk.adjuster.risk-based.enabled:true}")
    private boolean enabled;

    @Value("${risk.adjuster.risk-based.default-risk-percent:1.0}")
    private double defaultRiskPercent;

    @Value("${risk.adjuster.risk-based.max-position-percent:20.0}")
    private double maxPositionPercent;

    @Value("${risk.adjuster.risk-based.min-position-size:0.01}")
    private double minPositionSize;

    @Value("${risk.adjuster.risk-based.max-stop-loss-distance:0.05}")
    private double maxStopLossDistance;

    @Autowired(required = false)
    private RedisCache redisCache;

    // ==================== PositionAdjuster 接口 ====================

    @Override
    public String getId() {
        return "risk-based";
    }

    @Override
    public AdjustmentResult adjust(
            TradingSignalDto signal,
            double qualityScore,
            double basePosition,
            AdjustmentContext context) {

        if (!enabled) {
            return createDefaultResult();
        }

        // 1. 提取入场价和止损价
        double entryPrice = signal.getTriggerPrice();
        Double stopLossPrice = signal.getStopLossPrice();

        // 从上下文获取预计算止损价
        if (stopLossPrice == null || stopLossPrice <= 0) {
            Object sl = context.getMetadata().get("stopLossPrice");
            if (sl instanceof Number) {
                stopLossPrice = ((Number) sl).doubleValue();
            }
        }

        if (entryPrice <= 0 || stopLossPrice == null || stopLossPrice <= 0) {
            log.warn("无法获取有效入场价或止损价: entry={}, stopLoss={}", entryPrice, stopLossPrice);
            return createConservativeResult();
        }

        // 2. 计算止损距离（百分比）
        double stopLossDistance = Math.abs(entryPrice - stopLossPrice) / entryPrice;
        if (stopLossDistance <= 0 || stopLossDistance > maxStopLossDistance) {
            log.warn("止损距离无效: {}%, 使用保守仓位", String.format("%.2f", stopLossDistance * 100));
            return createConservativeResult();
        }

        // 3. 风险比例：优先取上下文传递的，否则用默认值
        double riskPercent = defaultRiskPercent;
        Object rpObj = context.getMetadata().get("riskPercent");
        if (rpObj instanceof Number) {
            riskPercent = ((Number) rpObj).doubleValue();
        }

        // 4. 以损定量：固定风险 = 仓位金额 × 风险比例%，名义仓位 = 固定风险 / 止损距离
        double fixedRisk = basePosition * (riskPercent / 100.0);
        double nominalPosition = fixedRisk / stopLossDistance;

        // 5. 信号强度作为仓位乘数
        if (qualityScore > 0) {
            nominalPosition = nominalPosition * qualityScore;
        }

        // 6. 硬性约束：不超过账户余额的 maxPositionPercent%
        double accountEquity = context.getAccountBalance();
        double maxPosition = accountEquity * maxPositionPercent / 100.0;
        double finalPosition = Math.min(nominalPosition, maxPosition);
        finalPosition = Math.max(finalPosition, minPositionSize);

        // 7. 构建结果
        Map<String, Double> factors = new LinkedHashMap<>();
        factors.put("entryPrice", entryPrice);
        factors.put("stopLossPrice", stopLossPrice);
        factors.put("stopLossDistancePercent", stopLossDistance * 100);
        factors.put("riskPercent", riskPercent);
        factors.put("fixedRisk", fixedRisk);
        factors.put("qualityScore", qualityScore);
        factors.put("nominalPosition", nominalPosition);
        factors.put("finalPosition", finalPosition);

        List<String> adjustments = buildAdjustments(qualityScore, riskPercent, finalPosition, nominalPosition);

        log.info("以损定量: basePos=${}, 止损={}%, 风险={}%, 固定风险=${}, 信号={}, 名义=${}, 最终=${}",
                String.format("%.2f", basePosition),
                String.format("%.2f", stopLossDistance * 100),
                String.format("%.1f", riskPercent),
                String.format("%.2f", fixedRisk),
                String.format("%.2f", qualityScore),
                String.format("%.2f", nominalPosition),
                String.format("%.2f", finalPosition));

        return AdjustmentResult.builder()
                .adjustedWeight(finalPosition / Math.max(basePosition, 1.0))
                .positionSize(finalPosition)
                .factors(factors)
                .adjustments(adjustments)
                .riskAssessment(createRiskAssessment(signal, qualityScore, finalPosition, fixedRisk))
                .build();
    }

    @Override
    public BigDecimal adjust(Double initialCapital, String symbol, double leverage,
                             BigDecimal signalStrength, Num nowPrice) {
        // 简化版本：用于无法获取止损距离的场景（回测/兼容）
        if (initialCapital == null || initialCapital <= 0 || nowPrice == null) {
            return BigDecimal.ZERO;
        }

        double currentPrice = nowPrice.doubleValue();
        double defaultStopLossPercent = 0.008; // 0.8% 默认止损
        double stopLossDistance = currentPrice * defaultStopLossPercent;
        double riskPercent = defaultRiskPercent / 100.0;
        double fixedRisk = initialCapital * riskPercent;
        double nominalPosition = fixedRisk / stopLossDistance;

        if (signalStrength != null && signalStrength.compareTo(BigDecimal.ZERO) > 0) {
            nominalPosition = nominalPosition * signalStrength.doubleValue();
        }

        String normalizedSymbol = ContractSpecUtils.normalizeSymbol(Exchange.OKX, symbol.toUpperCase());
        ContractSpec contractSpec = ContractSpecUtils.getContractSpec(redisCache, Exchange.OKX, normalizedSymbol);
        double contractSize = contractSpec.getContractSize().doubleValue();

        double contractQuantity = TradingUtil.convertUsdtToContractSize(
                nominalPosition, currentPrice, leverage, contractSize);

        contractQuantity = Math.max(contractQuantity, minPositionSize);

        log.debug("以损定量(简化): initialCapital=${}, orderAmount={}张", initialCapital, contractQuantity);
        return BigDecimal.valueOf(contractQuantity);
    }

    // ==================== 辅助方法 ====================

    private List<String> buildAdjustments(double qualityScore, double riskPercent,
                                           double finalPosition, double nominalPosition) {
        List<String> adjustments = new ArrayList<>();
        if (finalPosition <= minPositionSize + 0.001) {
            adjustments.add("仓位触及最小限制");
        }
        if (nominalPosition > finalPosition) {
            adjustments.add("仓位被最大值限制裁剪");
        }
        return adjustments;
    }

    private RiskAssessment createRiskAssessment(TradingSignalDto signal,
                                                double qualityScore,
                                                double finalPosition,
                                                double fixedRisk) {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setMaxPositionSize(finalPosition * 1.2);
        assessment.setRiskLevel(qualityScore > 0.7 ? "LOW" : qualityScore > 0.5 ? "MEDIUM" : "HIGH");

        if (signal.getStopLossPrice() != null) {
            assessment.setStopLossPrice(signal.getStopLossPrice());
        }
        if (signal.getTakeProfitPrice() != null) {
            assessment.setTakeProfitPrice(signal.getTakeProfitPrice());
        }

        double entry = signal.getTriggerPrice();
        Double sl = signal.getStopLossPrice();
        Double tp = signal.getTakeProfitPrice();
        if (entry > 0 && sl != null && sl > 0 && tp != null && tp > 0) {
            double risk = Math.abs(entry - sl);
            double reward = Math.abs(tp - entry);
            assessment.setRiskRewardRatio(risk > 0 ? reward / risk : 0);
        }

        return assessment;
    }

    private AdjustmentResult createDefaultResult() {
        return AdjustmentResult.builder()
                .adjustedWeight(0.5)
                .positionSize(0)
                .adjustments(List.of("以损定量调节器已禁用"))
                .riskAssessment(new RiskAssessment())
                .build();
    }

    private AdjustmentResult createConservativeResult() {
        return AdjustmentResult.builder()
                .adjustedWeight(0.2)
                .positionSize(minPositionSize)
                .adjustments(List.of("无法获取止损距离，使用保守仓位"))
                .riskAssessment(new RiskAssessment())
                .build();
    }
}