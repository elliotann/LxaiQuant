package com.chain.ai.trade.extension.core.constants;

/**
 * 出场类型枚举
 * 用于标识不同的出场原因，防止重复出场
 */
public enum ExitType {
    STOP_LOSS("止损"),
    TAKE_PROFIT("止盈"),
    FIXED_PERCENT_TAKE_PROFIT("固定百分比止盈"),
    TIME_TAKE_PROFIT("日期止盈"),
    TIME_STOP_LOSS("日期止损"),
    TRAILING_STOP_LOSS("移动止损"),
    TRAILING_STOP_GAIN("移动止盈"),
    TECHNICAL_INDICATOR("技术指标"),
    SIGNAL_REVERSAL("信号反转"),
    TIME_LIMIT("时间限制"),
    MANUAL("手动平仓"),
    UNKNOWN("未知原因"),
    MACD_GOLDEN_CROSS("MACD金叉"),
    MACD_DEAD_CROSS("MACD死叉"),
    BATCH_TAKE_PROFIT("分批止盈"),

    // ==================== 新增 SMC 出场类型 ====================
    ORDER_BLOCK("订单块离场"),
    PROFIT_TARGET("目标位止盈"),              // 触及 SMC 动态目标位（订单块、等高点/等低点、波段极值）
    STRUCTURE_BREAK("结构破坏离场"),          // 市场结构被破坏（出现反向 BOS/CHOCH）
    PREMIUM_DISCOUNT_ZONE("溢价/折价区离场"), // 多头进入溢价区或空头进入折价区
    ORDER_ITEM_TAKE_PROFIT("订单项止盈"),    // 仅平掉单个补仓订单项
    BATCH_STOP_LOSS("分批止损"),             // 分批止损
    BATCH_TRAILING_GAIN("分批移动止盈"),     // 分批移动止盈
    BATCH_TRAILING_LOSS("分批移动止损"),     // 分批移动止损
    SMC_TRAILING_STOP("SMC结构跟踪止损"),   // SmcTrailingStopRule 触发的结构跟踪移动止损
    ACTIVE_TAKE_PROFIT_OB15M("主动止盈-15m订单块"),  // 主动止盈：15m对立订单块触发
    ACTIVE_TAKE_PROFIT_OB1H("主动止盈-1h订单块"),    // 主动止盈：1h对立订单块触发
    ACTIVE_TAKE_PROFIT_HIGHER("主动止盈-higher订单块"), // 主动止盈：更高周期对立订单块触发

    // ==================== SmcStructuredExitRule 出场类型 ====================
    STOP_LOSS_DAILY("结构止损-第一道防线"),           // 第一道动态止损触发
    STOP_LOSS_BUFFER("结构止损-第二道防线"),          // 第二道动态止损触发
    STOP_LOSS_ULTIMATE("结构止损-第三道防线"),        // 第三道动态止损触发
    PROFIT_TARGET_TP1("结构止盈-TP1"),               // TP1 摆动点止盈触发
    PROFIT_TARGET_TP2("结构止盈-TP2"),               // TP2 1H OB止盈触发
    STOP_LOSS_DAILY_TIGHT("结构止损-第一道收紧"),    // ★ 新增
    BREAKEVEN("结构保本止损"),                        // 结构保本止损触发
    TRAILING_STOP("结构移动止损");                    // 结构移动止损触发

    private final String description;

    ExitType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 该出场类型是否会清空仓位
     */
    public boolean shouldClearPosition() {
        return this != MANUAL && this != BATCH_TAKE_PROFIT && this != BATCH_STOP_LOSS
                && this != BATCH_TRAILING_GAIN && this != BATCH_TRAILING_LOSS
                && this != ORDER_ITEM_TAKE_PROFIT && !isActiveTakeProfitExit();
    }

    /** 是否为主动止盈系列（三级对立订单块分批出场） */
    public boolean isActiveTakeProfitExit() {
        return this == ACTIVE_TAKE_PROFIT_OB15M || this == ACTIVE_TAKE_PROFIT_OB1H
                || this == ACTIVE_TAKE_PROFIT_HIGHER;
    }
}
