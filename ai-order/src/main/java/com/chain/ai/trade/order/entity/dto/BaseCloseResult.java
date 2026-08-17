package com.chain.ai.trade.order.entity.dto;


import java.math.BigDecimal;
import java.util.Date;

/**
 * 平仓结果基类
 */
public class BaseCloseResult {
    protected boolean success;
    protected String message;
    protected String orderSn;
    protected BigDecimal closedAmount = BigDecimal.ZERO;
    protected BigDecimal remainingAmount = BigDecimal.ZERO;
    protected BigDecimal closePrice;
    protected Date closeTime;

    public BaseCloseResult() {}

    public BaseCloseResult(String orderSn) {
        this.orderSn = orderSn;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getOrderSn() { return orderSn; }
    public void setOrderSn(String orderSn) { this.orderSn = orderSn; }

    public BigDecimal getClosedAmount() { return closedAmount; }
    public void setClosedAmount(BigDecimal closedAmount) { this.closedAmount = closedAmount; }

    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }

    public BigDecimal getClosePrice() { return closePrice; }
    public void setClosePrice(BigDecimal closePrice) { this.closePrice = closePrice; }

    public Date getCloseTime() { return closeTime; }
    public void setCloseTime(Date closeTime) { this.closeTime = closeTime; }

    @Override
    public String toString() {
        return String.format("订单%s: %s, 平仓%s, 剩余%s, 价格%s, 时间%s",
                orderSn, success ? "成功" : "失败",
                closedAmount, remainingAmount, closePrice,
                closeTime != null ? closeTime : "当前时间");
    }
}