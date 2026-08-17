package com.chain.ai.trade.engine2.core.execution;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * 平仓订单结果
 */
@Value
@Builder
public class CloseOrderResult {

    /** 是否成功 */
    boolean success;

    /** 平仓成交价 */
    BigDecimal fillPrice;

    /** 平仓成交量 */
    BigDecimal closedQuantity;

    /** 手续费 */
    BigDecimal fee;

    public static CloseOrderResult noPosition() {
        return CloseOrderResult.builder().success(false).build();
    }

    public static CloseOrderResult success(BigDecimal fillPrice, BigDecimal closedQuantity, BigDecimal fee) {
        return CloseOrderResult.builder()
                .success(true)
                .fillPrice(fillPrice)
                .closedQuantity(closedQuantity)
                .fee(fee)
                .build();
    }
}
