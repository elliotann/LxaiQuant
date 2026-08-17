package com.chain.ai.trade.extension.ta4j.core.rule;

/**
 * SMC 结构跟踪移动止损模式
 */
public enum TrailingMode {
    AUTO,   // 自动模式：根据 CompositeState 自动选择跟踪周期和点类型
    MANUAL  // 手动模式：用户预设挡位
}
