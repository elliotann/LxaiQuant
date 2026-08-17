package com.chain.ai.trade.engine.strategy.enums;

import lombok.Getter;

/**
 * 策略类型枚举
 */
@Getter
public enum StrategyType {
    JAVA_CLASS("Java类"),
    GROOVY_SCRIPT("Groovy脚本"),
    PYTHON_SCRIPT("Python脚本"),
    JAVASCRIPT("JavaScript脚本");

    private final String description;

    StrategyType(String description) {
        this.description = description;
    }
}

