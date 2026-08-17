package com.chain.ai.trade.engine2.core.execution;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单成交查询结果（PendingOrderWorker 轮询用）
 */
@Value
@Builder
public class FillResult {

    /** 客户端订单ID */
    String clientOrderId;

    /** 是否已成交 */
    boolean filled;

    /** 成交价 */
    BigDecimal fillPrice;

    /** 成交量 */
    BigDecimal filledQuantity;

    /** 成交时间 */
    LocalDateTime fillTime;

    /** 是否被拒绝 */
    boolean rejected;

    /** 拒绝原因 */
    String rejectReason;
}
