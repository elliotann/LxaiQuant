package com.chain.ai.trade.engine.strategy.enums;

import lombok.Getter;

/**
 * 变更类型枚举
 */
@Getter
public enum ChangeType {
    CREATE("创建"),
    UPDATE("更新"),
    BUGFIX("修复"),
    ENHANCEMENT("增强");

    private final String description;

    ChangeType(String description) {
        this.description = description;
    }
}

