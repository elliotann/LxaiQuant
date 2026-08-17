package com.chain.ai.trade.engine2.core;

/**
 * 加仓原因枚举 — 标识加仓触发场景。
 */
public enum ScaleInReason {
    /** 趋势确认加仓（浮盈后趋势延续） */
    TREND_ADD,
    /** 亏损摊平加仓（浮亏后回调补仓） */
    DCA_ADD,
    /** 信号驱动加仓（策略入场信号再次触发） */
    SIGNAL,
    /** 手动加仓 */
    MANUAL
}
