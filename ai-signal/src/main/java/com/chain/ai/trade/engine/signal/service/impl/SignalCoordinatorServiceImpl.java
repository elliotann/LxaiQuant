package com.chain.ai.trade.engine.signal.service.impl;

import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import com.chain.ai.trade.engine.signal.entity.dto.GenerateTradeSignalRequest;
import com.chain.ai.trade.engine.signal.entity.dto.GenerateTradeSignalResponse;
import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.engine.signal.entity.constants.TradeStatus;
import com.chain.ai.trade.engine.signal.enums.TechSignal;
import com.chain.ai.trade.engine.signal.service.ISignalCoordinatorService;
import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.engine.signal.service.ITradeSignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 信号协调服务实现类
 * 负责技术信号到业务信号的转换和协调
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalCoordinatorServiceImpl implements ISignalCoordinatorService {

    private final ITechnicalSignalService technicalSignalService;
    private final ITradeSignalService tradeSignalService;

    @Override
    public GenerateTradeSignalResponse generateTradeSignal(GenerateTradeSignalRequest request) {
        try {
            log.info("开始生成交易信号: technicalSignalId={}, symbol={}",
                    request.getTechnicalSignalId(), request.getSymbol());

            // 1. 获取技术信号
            TechnicalSignal technicalSignal = technicalSignalService.getById(request.getTechnicalSignalId());
            if (technicalSignal == null) {
                log.warn("技术信号不存在: id={}", request.getTechnicalSignalId());
                return GenerateTradeSignalResponse.failure("技术信号不存在");
            }

            // 2. 检查是否可以生成交易信号
            if (!canGenerateTradeSignal(technicalSignal)) {
                log.warn("技术信号不符合生成条件: id={}", request.getTechnicalSignalId());
                return GenerateTradeSignalResponse.rejected("技术信号不符合生成条件");
            }

            // 3. 创建交易信号
            TradeSignal tradeSignal = new TradeSignal();

            // 设置关联关系
            tradeSignal.setTechnicalSignalId(technicalSignal.getId());
            tradeSignal.setTechnicalSignalHash(technicalSignal.getSignalHash());
            tradeSignal.setTechnicalSignalBrief(getSignalBrief(technicalSignal.getId()));

            // 设置基本信息
            tradeSignal.setSymbol(request.getSymbol() != null ? request.getSymbol() : technicalSignal.getSymbol());
            tradeSignal.setTimeframe(technicalSignal.getTimeframe());
            tradeSignal.setKlineTime(technicalSignal.getKlineTime());

            // 设置决策信息
            tradeSignal.setDecisionReason("基于技术信号自动生成");
            tradeSignal.setRiskLevel(request.getRiskLevel() != null ? request.getRiskLevel() : "MEDIUM");
            tradeSignal.setPositionRatio(request.getMaxPositionRatio());
            tradeSignal.setPriority(request.getPriority() != null ? request.getPriority() : 5);

            // 设置订单操作
            OrderAction orderAction = determineOrderAction(technicalSignal);
            tradeSignal.setOrderAction(orderAction);

            // 设置订单状态
            tradeSignal.setStatus(TradeStatus.PENDING);

            // 从技术信号复制入场类型和限价单价格
            if (technicalSignal.getEntryType() != null) {
                tradeSignal.setEntryType(technicalSignal.getEntryType().name());
            }
            tradeSignal.setLimitPrice(technicalSignal.getLimitPrice());

            // 计算价格和数量
            BigDecimal expectedPrice = calculateExpectedPrice(technicalSignal, orderAction);
            BigDecimal expectedAmount = calculateExpectedAmount(request, expectedPrice);

            tradeSignal.setExpectedPrice(expectedPrice);
            tradeSignal.setExpectedAmount(expectedAmount);

            // 设置风控参数
            if (request.getStopLossRatio() != null) {
                BigDecimal stopLossPrice = calculateStopLossPrice(expectedPrice, request.getStopLossRatio(), orderAction);
                tradeSignal.setStopLossPrice(stopLossPrice);
            }

            if (request.getTakeProfitRatio() != null) {
                BigDecimal takeProfitPrice = calculateTakeProfitPrice(expectedPrice, request.getTakeProfitRatio(), orderAction);
                tradeSignal.setTakeProfitPrice(takeProfitPrice);
            }

            tradeSignal.setLeverage(request.getLeverage());
            tradeSignal.setFeeRate(BigDecimal.valueOf(0.00045)); // 默认万4.5手续费

            // 设置时间戳
            tradeSignal.setCreateTime(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));

            // 4. 应用风控规则
            tradeSignal = applyRiskControl(tradeSignal);
            if (tradeSignal == null) {
                return GenerateTradeSignalResponse.failure("风控检查失败");
            }

            // 5. 应用仓位管理
            tradeSignal = applyPositionManagement(tradeSignal);
            if (tradeSignal == null) {
                return GenerateTradeSignalResponse.failure("仓位管理检查失败");
            }

            // 6. 生成订单号
            String orderSn = generateOrderSn(tradeSignal.getSymbol(), orderAction);
            tradeSignal.setOrderSn(orderSn);

            // 7. 保存交易信号
            Long signalId = tradeSignalService.createTradeSignal(tradeSignal);

            log.info("交易信号生成成功: id={}, orderSn={}", signalId, orderSn);

            // 8. 构建响应
            GenerateTradeSignalResponse response = GenerateTradeSignalResponse.success(signalId, orderSn);
            response.setCalculatedPrice(expectedPrice);
            response.setCalculatedAmount(expectedAmount);

            if (tradeSignal.getStopLossPrice() != null) {
                response.setSuggestedStopLoss(tradeSignal.getStopLossPrice());
            }

            if (tradeSignal.getTakeProfitPrice() != null) {
                response.setSuggestedTakeProfit(tradeSignal.getTakeProfitPrice());
            }

            response.setRiskAssessment(tradeSignal.getRiskLevel());
            response.setDecisionReason(tradeSignal.getDecisionReason());

            return response;

        } catch (Exception e) {
            log.error("生成交易信号异常", e);
            return GenerateTradeSignalResponse.failure("生成交易信号失败: " + e.getMessage());
        }
    }

    @Override
    public TradeSignal processTechnicalSignal(TechnicalSignal technicalSignal) {
        // 简化实现，实际应该包含完整的业务逻辑
        try {
            GenerateTradeSignalRequest request = new GenerateTradeSignalRequest();
            request.setTechnicalSignalId(technicalSignal.getId());
            request.setSymbol(technicalSignal.getSymbol());
            request.setMaxPositionRatio(BigDecimal.valueOf(0.05));

            GenerateTradeSignalResponse response = generateTradeSignal(request);
            if (response.isSuccess()) {
                return tradeSignalService.getById(response.getTradeSignalId());
            }
        } catch (Exception e) {
            log.error("处理技术信号异常", e);
        }
        return null;
    }

    @Override
    public boolean shouldGenerateTradeSignal(TechSignal techSignal, String symbol, Double currentPrice) {
        // 简化实现，实际应该包含复杂的判断逻辑
        return techSignal != null && symbol != null && currentPrice != null;
    }

    @Override
    public Double calculateOrderAmount(TechnicalSignal technicalSignal, Double availableBalance, Double currentPrice) {
        // 简化实现
        return availableBalance * 0.1 / currentPrice; // 10%仓位
    }

    @Override
    public TradeSignal applyRiskControl(TradeSignal tradeSignal) {
        // 简化实现，实际应该包含风控逻辑
        if (tradeSignal.getPositionRatio() != null &&
            tradeSignal.getPositionRatio().compareTo(BigDecimal.valueOf(0.2)) > 0) {
            log.warn("仓位比例过高: {}", tradeSignal.getPositionRatio());
            return null; // 拒绝高风险交易
        }
        return tradeSignal;
    }

    @Override
    public TradeSignal applyPositionManagement(TradeSignal tradeSignal) {
        // 简化实现，实际应该包含仓位管理逻辑
        return tradeSignal;
    }

    @Override
    public String generateOrderSn(String symbol, TechSignal techSignal) {
        // 简化实现
        return symbol + "_" + techSignal.name() + "_" + System.currentTimeMillis();
    }

    @Override
    public String generateOrderSn(String symbol, OrderAction orderAction) {
        // 简化实现
        return symbol + "_" + orderAction.name() + "_" + System.currentTimeMillis();
    }

    @Override
    public Double calculateExpectedIncome(TradeSignal tradeSignal) {
        // 简化实现
        return 0.0;
    }

    // ==================== 私有辅助方法 ====================

    private boolean canGenerateTradeSignal(TechnicalSignal technicalSignal) {
        if (technicalSignal == null) {
            return false;
        }
        BigDecimal strength = technicalSignal.getSignalStrength();
        if (strength == null) {
            return false;
        }
        if ("DEEPSEEK".equalsIgnoreCase(technicalSignal.getSignalSource())) {
            return strength.compareTo(BigDecimal.ZERO) > 0;
        }
        if (strength.compareTo(BigDecimal.valueOf(0.5)) <= 0) {
            return false;
        }
        return true;
    }

    private String getSignalBrief(Long technicalSignalId) {
        return technicalSignalService.getSignalBrief(technicalSignalId);
    }

    private OrderAction determineOrderAction(TechnicalSignal technicalSignal) {
        if (technicalSignal.getTechnicalDirection() == null) {
            return null;
        }

        String direction = technicalSignal.getTechnicalDirection().toUpperCase();
        switch (direction) {
            case "LONG":
                return OrderAction.OPEN_LONG;
            case "SHORT":
                return OrderAction.OPEN_SHORT;
            case "CLOSE_LONG":
                return OrderAction.CLOSE_LONG;
            case "CLOSE_SHORT":
                return OrderAction.CLOSE_SHORT;
            case "BULLISH":
            case "STRONG_BULLISH":
                return OrderAction.OPEN_LONG;
            case "BEARISH":
            case "STRONG_BEARISH":
                return OrderAction.OPEN_SHORT;
            default:
                return null;
        }
    }

    private BigDecimal calculateExpectedPrice(TechnicalSignal technicalSignal, OrderAction orderAction) {
        // 使用当前价格作为预期价格
        return technicalSignal.getClosePrice();
    }

    private BigDecimal calculateExpectedAmount(GenerateTradeSignalRequest request, BigDecimal price) {
        if (request.getMaxPositionRatio() == null || price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // 简化计算：假设账户余额为10000，计算可开仓位
        BigDecimal accountBalance = BigDecimal.valueOf(10000); // 应该从账户服务获取
        BigDecimal positionValue = accountBalance.multiply(request.getMaxPositionRatio());

        return positionValue.divide(price, 8, BigDecimal.ROUND_DOWN);
    }

    private BigDecimal calculateStopLossPrice(BigDecimal currentPrice, BigDecimal stopLossRatio, OrderAction orderAction) {
        if (currentPrice == null || stopLossRatio == null) {
            return null;
        }

        if (OrderAction.OPEN_LONG.equals(orderAction)) {
            // 多头止损：当前价格 * (1 - 止损比例)
            return currentPrice.multiply(BigDecimal.ONE.subtract(stopLossRatio));
        } else if (OrderAction.OPEN_SHORT.equals(orderAction)) {
            // 空头止损：当前价格 * (1 + 止损比例)
            return currentPrice.multiply(BigDecimal.ONE.add(stopLossRatio));
        }

        return null;
    }

    private BigDecimal calculateTakeProfitPrice(BigDecimal currentPrice, BigDecimal takeProfitRatio, OrderAction orderAction) {
        if (currentPrice == null || takeProfitRatio == null) {
            return null;
        }

        if (OrderAction.OPEN_LONG.equals(orderAction)) {
            // 多头止盈：当前价格 * (1 + 止盈比例)
            return currentPrice.multiply(BigDecimal.ONE.add(takeProfitRatio));
        } else if (OrderAction.OPEN_SHORT.equals(orderAction)) {
            // 空头止盈：当前价格 * (1 - 止盈比例)
            return currentPrice.multiply(BigDecimal.ONE.subtract(takeProfitRatio));
        }

        return null;
    }
}
