package com.chain.ai.trade.order.entity.dto;


import java.math.BigDecimal;

/**
 * 部分平仓结果
 */
public class PartialCloseResult extends BaseCloseResult {
    private BigDecimal targetAmount;
    private BigDecimal actualRatio;
    private boolean hasTail;
    private BigDecimal tailAmount = BigDecimal.ZERO;

    public PartialCloseResult() {}

    public PartialCloseResult(String orderSn) {
        super(orderSn);
    }

    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }

    public BigDecimal getActualRatio() { return actualRatio; }
    public void setActualRatio(BigDecimal actualRatio) { this.actualRatio = actualRatio; }

    public boolean isHasTail() { return hasTail; }
    public void setHasTail(boolean hasTail) { this.hasTail = hasTail; }

    public BigDecimal getTailAmount() { return tailAmount; }
    public void setTailAmount(BigDecimal tailAmount) { this.tailAmount = tailAmount; }

    @Override
    public String toString() {
        return String.format("部分平仓%s: %s, 目标%s, 实际%s, 比例%s%%, 尾数%s, 价格%s",
                orderSn, success ? "成功" : "失败",
                targetAmount, closedAmount,
                actualRatio != null ? actualRatio.stripTrailingZeros() : "0",
                hasTail ? tailAmount : "无", closePrice);
    }
}