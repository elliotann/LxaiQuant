package com.chain.ai.trade.engine.strategy.enums;

/**
 * 出场规则类型枚举
 * 用于标识规则的技术分类，用于规则管理和配置
 *
 * 与ExitType的区别：
 * - ExitType: 业务视角的"为什么出场"，用于防重、日志、操作
 * - ExitRuleType: 技术视角的"基于什么规则"，用于规则管理、配置、扩展
 */
public enum ExitRuleType {
    /**
     * 技术指标规则 - 基于技术指标（如RSI、MACD、布林带等）
     */
    TECHNICAL_INDICATOR("技术指标规则", "基于技术指标的出场规则"),

    /**
     * 信号基础规则 - 基于交易信号（如信号反转、信号强度等）
     */
    SIGNAL_BASED("信号基础规则", "基于交易信号的出场规则"),

    /**
     * 风险管理规则 - 基于风险管理（如止损、止盈、仓位限制等）
     */
    RISK_MANAGEMENT("风险管理规则", "基于风险管理的出场规则"),

    /**
     * 时间基础规则 - 基于时间条件（如持仓时间限制、定时出场等）
     */
    TIME_BASED("时间基础规则", "基于时间条件的出场规则"),

    /**
     * 仓位感知规则 - 基于仓位信息（如仓位大小、盈亏比例等）
     */
    POSITION_AWARE("仓位感知规则", "基于仓位信息的出场规则"),

    /**
     * 自定义规则 - 用户自定义的特殊规则
     */
    CUSTOM("自定义规则", "用户自定义的出场规则");

    private final String displayName;
    private final String description;

    ExitRuleType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为风险管理规则
     */
    public boolean isRiskManagement() {
        return this == RISK_MANAGEMENT;
    }

    /**
     * 判断是否为信号基础规则
     */
    public boolean isSignalBased() {
        return this == SIGNAL_BASED;
    }

    /**
     * 判断是否为技术指标规则
     */
    public boolean isTechnicalIndicator() {
        return this == TECHNICAL_INDICATOR;
    }

    /**
     * 判断是否为时间基础规则
     */
    public boolean isTimeBased() {
        return this == TIME_BASED;
    }

    /**
     * 判断是否为仓位感知规则
     */
    public boolean isPositionAware() {
        return this == POSITION_AWARE;
    }

    /**
     * 判断是否为自定义规则
     */
    public boolean isCustom() {
        return this == CUSTOM;
    }
}