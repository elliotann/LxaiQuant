package com.chain.ai.trade.engine.signal.enums;

/**
 * 业务信号状态枚举
 */
public enum TradeSignalStatus {

    PENDING,       // 待处理
    EXECUTING,     // 执行中
    EXECUTED,      // 已执行
    CANCELLED,     // 已取消
    FAILED;        // 失败

    /**
     * 获取枚举的字符串值
     */
    public String getValue() {
        return this.name();
    }

    /**
     * 根据字符串获取枚举值
     */
    public static TradeSignalStatus fromValue(String value) {
        for (TradeSignalStatus status : TradeSignalStatus.values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TradeSignalStatus value: " + value);
    }
}
