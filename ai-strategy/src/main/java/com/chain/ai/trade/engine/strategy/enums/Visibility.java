package com.chain.ai.trade.engine.strategy.enums;

import lombok.Getter;

/**
 * 可见性枚举
 */
@Getter
public enum Visibility {
    PRIVATE("私有"),
    TEAM("团队可见"),
    PUBLIC("公开");

    private final String description;

    Visibility(String description) {
        this.description = description;
    }
}

