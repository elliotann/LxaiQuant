package com.chain.ai.trade.engine.signal.rule;

public enum OperatorType {
    GT,
    GTE,
    LT,
    LTE,
    EQ,
    NEQ,
    CROSS_ABOVE,
    CROSS_BELOW,
    BETWEEN,
    IN,
    NOT_IN,
    IS,

    // ===== abs_space 分位数运算符（4 个） =====
    LT_ABS_PERCENTILE,
    GT_ABS_PERCENTILE,
    LTE_ABS_PERCENTILE,
    GTE_ABS_PERCENTILE,

    // ===== cumRatio 分位数运算符（4 个） =====
    LT_RATIO_PERCENTILE,
    GT_RATIO_PERCENTILE,
    LTE_RATIO_PERCENTILE,
    GTE_RATIO_PERCENTILE,

    // ===== 方向比较运算符（2 个） =====
    EQ_CURRENT_SAME,
    EQ_CURRENT_OPPOSITE
}
