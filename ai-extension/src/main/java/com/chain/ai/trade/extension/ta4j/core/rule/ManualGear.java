package com.chain.ai.trade.extension.ta4j.core.rule;

/**
 * SMC 结构跟踪移动止损手动模式挡位
 */
public enum ManualGear {
    CONSERVATIVE,   // 保守：4H 摆动点，偏移 0.3%
    MODERATE,       // 中等：1H 内部点，偏移 0.2%
    AGGRESSIVE      // 激进：15M 内部点，偏移 0.1%
}
