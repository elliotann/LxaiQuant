package com.chain.ai.trade.engine.risk.common;

import lombok.Getter;

/**
 * 时间框架枚举
 */
@Getter
public enum TimeFrame {
    M1("1分钟", 1),
    M5("5分钟", 5),
    M15("15分钟", 15),
    M30("30分钟", 30),
    H1("1小时", 60),
    H4("4小时", 240),
    D1("1天", 1440),
    W1("1周", 10080),
    MON1("1月", 43200),
    M3("3分钟",180 );

    private final String description;
    private final int minutes;

    TimeFrame(String description, int minutes) {
        this.description = description;
        this.minutes = minutes;
    }

    /**
     * 根据分钟数获取时间框架
     */
    public static TimeFrame fromMinutes(int minutes) {
        for (TimeFrame tf : values()) {
            if (tf.minutes == minutes) {
                return tf;
            }
        }
        return H1; // 默认返回1小时
    }
}

