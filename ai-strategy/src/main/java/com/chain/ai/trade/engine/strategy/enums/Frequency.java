package com.chain.ai.trade.engine.strategy.enums;

import lombok.Getter;

/**
 * 运行频率枚举
 */
@Getter
public enum Frequency {
    DAILY("日线"),
    HOURLY("小时线"),
    MINUTELY("分钟线"),
    REALTIME("实时"),
    CUSTOM("自定义");

    private final String description;

    Frequency(String description) {
        this.description = description;
    }
}

