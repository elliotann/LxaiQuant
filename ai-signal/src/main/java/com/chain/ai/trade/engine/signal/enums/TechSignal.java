package com.chain.ai.trade.engine.signal.enums;

/**
 * 技术信号类型枚举
 */
public enum TechSignal {

    LONG,          // 做多
    SHORT,         // 做空
    CLOSE_LONG,    // 平多
    CLOSE_SHORT;   // 平空

    /**
     * 获取枚举的字符串值
     */
    public String getValue() {
        return this.name();
    }

    /**
     * 根据字符串获取枚举值
     */
    public static TechSignal fromValue(String value) {
        for (TechSignal signal : TechSignal.values()) {
            if (signal.name().equalsIgnoreCase(value)) {
                return signal;
            }
        }
        throw new IllegalArgumentException("Unknown TechSignal value: " + value);
    }
}
