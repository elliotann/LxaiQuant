package com.chain.ai.trade.engine2.core.execution;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * 订单提交结果
 */
@Value
@Builder
public class OrderIntentResult {

    /** 客户端订单ID */
    String clientOrderId;

    /** 订单状态 */
    Status status;

    /** 成交价（FILLED 时有值） */
    BigDecimal fillPrice;

    /** 成交量（FILLED 时有值） */
    BigDecimal filledQuantity;

    /** 拒绝原因（REJECTED 时有值） */
    String rejectReason;

    public enum Status {
        /** 已成交（市价单通常立即成交） */
        FILLED,
        /** 挂单中（限价单等待成交） */
        PENDING,
        /** 已拒绝（风控拦截或交易所拒绝） */
        REJECTED
    }

    public boolean isFilled() { return status == Status.FILLED; }
    public boolean isPending() { return status == Status.PENDING; }
    public boolean isRejected() { return status == Status.REJECTED; }

    public static OrderIntentResult filled(String clientOrderId, BigDecimal fillPrice, BigDecimal filledQuantity) {
        return OrderIntentResult.builder()
                .clientOrderId(clientOrderId)
                .status(Status.FILLED)
                .fillPrice(fillPrice)
                .filledQuantity(filledQuantity)
                .build();
    }

    public static OrderIntentResult pending(String clientOrderId) {
        return OrderIntentResult.builder()
                .clientOrderId(clientOrderId)
                .status(Status.PENDING)
                .build();
    }

    public static OrderIntentResult rejected(String clientOrderId, String reason) {
        return OrderIntentResult.builder()
                .clientOrderId(clientOrderId)
                .status(Status.REJECTED)
                .rejectReason(reason)
                .build();
    }
}
