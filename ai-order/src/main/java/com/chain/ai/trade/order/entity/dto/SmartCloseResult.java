package com.chain.ai.trade.order.entity.dto;


import java.math.BigDecimal;

/**
 * 智能平仓结果
 */
public class SmartCloseResult extends PartialCloseResult {
    private boolean autoFullClose;
    private String closeType;
    private BigDecimal minTradeAmount;

    public SmartCloseResult() {}

    public SmartCloseResult(String orderSn) {
        super(orderSn);
    }

    public boolean isAutoFullClose() { return autoFullClose; }
    public void setAutoFullClose(boolean autoFullClose) { this.autoFullClose = autoFullClose; }

    public String getCloseType() { return closeType; }
    public void setCloseType(String closeType) { this.closeType = closeType; }

    public BigDecimal getMinTradeAmount() { return minTradeAmount; }
    public void setMinTradeAmount(BigDecimal minTradeAmount) { this.minTradeAmount = minTradeAmount; }

    @Override
    public String toString() {
        return String.format("智能平仓%s: 类型%s, %s, 平仓%s, 价格%s",
                orderSn, closeType, success ? "成功" : "失败", closedAmount, closePrice);
    }
}