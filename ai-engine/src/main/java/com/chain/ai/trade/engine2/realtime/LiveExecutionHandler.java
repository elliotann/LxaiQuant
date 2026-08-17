package com.chain.ai.trade.engine2.realtime;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.service.TradeOrderServiceAdapter;
import com.chain.ai.trade.engine2.core.execution.*;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@Profile({"dev", "prod"})
public class LiveExecutionHandler implements ExecutionHandler {

    @Autowired(required = false)
    private TradeOrderServiceAdapter tradeOrderAdapter;

    @Autowired
    private RiskCheckHandler riskCheckHandler;

    @Override
    public OrderIntentResult submitOrder(OrderIntent intent, RealtimeContext context) {
        RiskCheckHandler.RiskResult riskResult = riskCheckHandler.checkBeforeEntry(intent, context);
        if (riskResult.isRejected()) {
            log.warn("风控拒绝: clientOrderId={}, reason={}", intent.getClientOrderId(), riskResult.getReason());
            return OrderIntentResult.rejected(intent.getClientOrderId(), riskResult.getReason());
        }

        if (tradeOrderAdapter == null) {
            log.error("TradeOrderServiceAdapter 未注入");
            return OrderIntentResult.rejected(intent.getClientOrderId(), "tradeOrderAdapter 不可用");
        }

        try {
            TradingStrategyParams params = buildParams(intent, context);
            String orderId = tradeOrderAdapter.createOrder(params);
            log.info("Live 下单成功: clientOrderId={}, orderSn={}, orderId={}",
                    intent.getClientOrderId(), params.getPositionId(), orderId);

            String orderSn = params.getPositionId();
            FillResult fill = checkFillByOrderSn(orderSn);
            if (fill != null && fill.isFilled()) {
                return OrderIntentResult.filled(intent.getClientOrderId(), fill.getFillPrice(), fill.getFilledQuantity());
            }
            return OrderIntentResult.pending(intent.getClientOrderId());

        } catch (Exception e) {
            log.error("Live 下单失败: clientOrderId={}", intent.getClientOrderId(), e);
            return OrderIntentResult.rejected(intent.getClientOrderId(), "下单异常: " + e.getMessage());
        }
    }

    @Override
    public CloseOrderResult closeOrder(OrderIntent intent, RealtimeContext context) {
        if (tradeOrderAdapter == null) return CloseOrderResult.noPosition();
        try {
            boolean ok = tradeOrderAdapter.closeOrderByVolume(intent.getPositionId(),
                    intent.getQuantity(), intent.getPrice(), new Date(),
                    intent.getExitType() != null ? intent.getExitType() : ExitType.TECHNICAL_INDICATOR);
            if (ok) {
                log.info("Live 平仓成功: positionId={}, qty={}", intent.getPositionId(), intent.getQuantity());
                return CloseOrderResult.success(intent.getPrice(), intent.getQuantity(), BigDecimal.ZERO);
            }
            return CloseOrderResult.noPosition();
        } catch (Exception e) {
            log.error("Live 平仓失败: positionId={}", intent.getPositionId(), e);
            return CloseOrderResult.noPosition();
        }
    }

    @Override
    public CancelResult cancelOrder(String clientOrderId) {
        log.warn("Live 撤单暂不支持: clientOrderId={}", clientOrderId);
        return CancelResult.fail("暂不支持");
    }

    @Override
    public FillResult checkFill(String clientOrderId) {
        if (tradeOrderAdapter == null) return null;
        try {
            TradePosition order = tradeOrderAdapter.getOrderByOrderSn(clientOrderId);
            return toFillResult(order);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<OrderIntent> getPendingOrders(String symbol) {
        return Collections.emptyList();
    }

    @Override
    public List<FillResult> processPendingOrdersOnBar(String symbol, double currentHigh, double currentLow) {
        return Collections.emptyList();
    }

    private TradingStrategyParams buildParams(OrderIntent intent, RealtimeContext context) {
        RealtimeConfig config = context.getConfig();
        // isNewPosition=true → 新开仓：positionId 不设，clientOrderId 传仓位ID给 DB 做 orderSn
        // isNewPosition=false → 加仓：positionId 传已有仓位ID，用于 isAddPosition 校验
        boolean isAddPosition = !intent.isNewPosition();
        String orderSn = intent.getPositionId(); // 仓位ID，全程唯一

        TradingStrategyParams.TradingStrategyParamsBuilder builder = TradingStrategyParams.builder()
                .symbol(intent.getSymbol())
                .side(intent.getSide() == SignalType.LONG ? "BUY" : "SELL")
                .amount(intent.getQuantity())
                .price(intent.getPrice())
                .leverage(context.getLeverage())
                .takeProfitPrice(intent.getTakeProfitPrice())
                .stopLossPrice(intent.getStopLossPrice())
                .orderTime(intent.getBarTime() != null
                        ? Date.from(intent.getBarTime().atZone(java.time.ZoneId.systemDefault()).toInstant())
                        : new Date())
                .memberPlatform(config.getExchange())
                .testMode(false)
                .accountId(config.getAccountId())
                .robotId(config.getRobotId());

        if (isAddPosition) {
            builder.positionId(orderSn);
        } else {
            builder.clientOrderId(orderSn);
        }
        return builder.build();
    }

    private FillResult checkFillByOrderSn(String orderSn) {
        if (orderSn == null || orderSn.isBlank()) return null;
        try {
            TradePosition order = tradeOrderAdapter.getOrderByOrderSn(orderSn);
            return toFillResult(order);
        } catch (Exception e) {
            return null;
        }
    }

    private FillResult toFillResult(TradePosition order) {
        if (order == null) return null;
        TradePosition.TradeOrderStatus status = order.getTradeOrderStatus();
        if (status == TradePosition.TradeOrderStatus.DEAL
                || status == TradePosition.TradeOrderStatus.GAIN
                || status == TradePosition.TradeOrderStatus.LOSS) {
            return FillResult.builder()
                    .clientOrderId(order.getPositionId())
                    .filled(true)
                    .fillPrice(order.getBuyPrice())
                    .filledQuantity(order.getVolume())
                    .build();
        }
        return null;
    }
}