package com.chain.ai.trade.engine.signal.entity.constants;

/**
 * 交易状态枚举
 */
public enum TradeStatus {

    PENDING("待处理"),
    VALIDATING("验证中"),
    APPROVED("已批准"),
    REJECTED("已拒绝"),
    EXECUTING("执行中"),
    PARTIALLY_FILLED("部分成交"),
    FILLED("完全成交"),
    CANCELLED("已取消"),
    FAILED("执行失败"),
    SETTLED("已结算");

    private final String description;

    TradeStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 获取枚举的字符串值
     */
    public String getValue() {
        return this.name();
    }

    /**
     * 根据字符串获取枚举值
     */
    public static TradeStatus fromValue(String value) {
        for (TradeStatus status : TradeStatus.values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TradeStatus value: " + value);
    }
}
