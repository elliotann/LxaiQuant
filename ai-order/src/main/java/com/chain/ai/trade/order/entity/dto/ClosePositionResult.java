package com.chain.ai.trade.order.entity.dto;


import java.math.BigDecimal;

/**
 * 全仓平仓结果
 */
public class ClosePositionResult extends BaseCloseResult {
    private boolean isFullClose;
    private BigDecimal originalPosition;

    public ClosePositionResult() {}

    public ClosePositionResult(String orderSn) {
        super(orderSn);
    }

    public boolean isFullClose() { return isFullClose; }
    public void setFullClose(boolean fullClose) { isFullClose = fullClose; }

    public BigDecimal getOriginalPosition() { return originalPosition; }
    public void setOriginalPosition(BigDecimal originalPosition) { this.originalPosition = originalPosition; }

    @Override
    public String toString() {
        return String.format("全平%s: %s, 原仓位%s, 平仓%s, 剩余%s, 价格%s",
                orderSn, success ? "成功" : "失败",
                originalPosition, closedAmount, remainingAmount, closePrice);
    }
}