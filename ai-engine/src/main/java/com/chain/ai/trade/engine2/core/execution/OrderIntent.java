package com.chain.ai.trade.engine2.core.execution;

import com.chain.ai.trade.common.entity.constants.SignalType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单意图值对象 — 策略决策后生成的订单描述，不关心执行细节。
 */
@Value
@Builder
public class OrderIntent {

    /** 唯一客户端订单ID（用于幂等去重） */
    String clientOrderId;

    /** 交易对 */
    String symbol;

    /** 方向：LONG / SHORT / CLOSE_LONG / CLOSE_SHORT */
    SignalType side;

    /** 订单类型：MARKET / LIMIT */
    OrderType orderType;

    /** 委托价格（市价单为 null） */
    BigDecimal price;

    /** 开仓/平仓数量 */
    BigDecimal quantity;

    /** 杠杆倍数 */
    int leverage;

    /** 止盈价（附加订单） */
    BigDecimal takeProfitPrice;

    /** 止损价（附加订单） */
    BigDecimal stopLossPrice;

    /** 仓位标识 — 开仓时生成，全程唯一；加仓时复用已有仓位ID */
    String positionId;

    /** 是否新开仓（用于 buildParams 区分新仓/加仓校验链） */
    @Builder.Default
    boolean isNewPosition = false;

    /** K 线时间戳 */
    LocalDateTime barTime;

    /** 触发入场的信号ID，关联到 TechnicalSignal */
    Long signalId;

    /** 退出类型（平仓时传入） */
    com.chain.ai.trade.extension.core.constants.ExitType exitType;

    /** 提交时间 */
    @Builder.Default
    LocalDateTime submitTime = LocalDateTime.now();
}
