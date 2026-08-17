package com.chain.ai.trade.engine.signal.enums;

/**
 * 技术信号方向枚举
 */
public enum TechnicalDirection {
    LB("看多"),
    NEUTRAL("中性"),
    SB("看空");

    private final String description;

    TechnicalDirection(String description) {
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
    public static TechnicalDirection fromValue(String value) {
        for (TechnicalDirection direction : TechnicalDirection.values()) {
            if (direction.name().equalsIgnoreCase(value)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Unknown TechnicalDirection value: " + value);
    }
}
