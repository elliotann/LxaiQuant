package com.chain.ai.trade.engine2.core;

import com.chain.ai.trade.common.entity.constants.SignalType;

import java.math.BigDecimal;

/**
 * 订单意图 — 策略发出 onOrderFilled() 回调时携带的订单信息。
 * <p>
 * 引擎执行完开/平仓后，回调通知策略本次成交结果。
 */
public class OrderIntent {

    /** 订单类型 */
    public enum IntentType { ENTRY, EXIT }

    private final String clientOrderId;
    private final IntentType intentType;
    private final SignalType direction;
    private final String symbol;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final BigDecimal filledQuantity;
    private final BigDecimal avgPrice;

    public OrderIntent(String clientOrderId, IntentType intentType, SignalType direction,
                       String symbol, BigDecimal quantity, BigDecimal price,
                       BigDecimal filledQuantity, BigDecimal avgPrice) {
        this.clientOrderId = clientOrderId;
        this.intentType = intentType;
        this.direction = direction;
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
        this.filledQuantity = filledQuantity;
        this.avgPrice = avgPrice;
    }

    public String getClientOrderId() { return clientOrderId; }
    public IntentType getIntentType() { return intentType; }
    public SignalType getDirection() { return direction; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getFilledQuantity() { return filledQuantity; }
    public BigDecimal getAvgPrice() { return avgPrice; }
}
