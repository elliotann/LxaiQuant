package com.chain.ai.trade.common.entity.constants;

/**
 * 趋势方向
 */
public enum TrendType {
    STRONG_BULLISH,      // 强上涨趋势
    STRONG_BEARISH,      // 强下跌趋势
    BULLISH_PULLBACK,    // 上涨回调
    BEARISH_PULLBACK,    // 下跌反弹
    BULLISH_ENDING,      // 上升末端（4H 确认的CHoCH）
    BEARISH_ENDING,      // 下降末端（4H 确认的CHoCH）
    POTENTIAL_BOTTOM,    // 潜在底部反转
    POTENTIAL_TOP,       // 潜在顶部反转
    RANGING,             // 横盘震荡
    CHAOTIC              // 矛盾混乱
}
