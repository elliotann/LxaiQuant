package com.chain.ai.trade.engine.strategy.enums;

import lombok.Getter;

/**
 * 策略状态枚举
 */
@Getter
public enum StrategyStatus {
    DRAFT("草稿"),
    TESTING("测试中"),
    ACTIVE("活跃"),
    DEPRECATED("已废弃"),
    ARCHIVED("已归档");

    private final String description;

    StrategyStatus(String description) {
        this.description = description;
    }
}

