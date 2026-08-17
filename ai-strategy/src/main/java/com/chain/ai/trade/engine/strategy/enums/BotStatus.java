package com.chain.ai.trade.engine.strategy.enums;

/**
 * 交易机器人状态枚举
 */
public enum BotStatus {

    /**
     * 已创建
     */
    CREATED("CREATED", "已创建"),

    /**
     * 运行中
     */
    RUNNING("RUNNING", "运行中"),

    /**
     * 已暂停
     */
    PAUSED("PAUSED", "已暂停"),

    /**
     * 已停止
     */
    STOPPED("STOPPED", "已停止"),

    /**
     * 错误状态
     */
    ERROR("ERROR", "错误状态");

    private final String code;
    private final String description;

    BotStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取枚举
     */
    public static BotStatus fromCode(String code) {
        for (BotStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown bot status code: " + code);
    }

    /**
     * 根据描述获取枚举
     */
    public static BotStatus fromDescription(String description) {
        for (BotStatus status : values()) {
            if (status.description.equals(description)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown bot status description: " + description);
    }
}
