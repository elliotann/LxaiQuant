package com.chain.ai.trade.common.entity.constants;

/**
 * 复合状态枚举（用于精细化交易决策）
 */
public enum CompositeState {
    // 上升趋势类
    STRONG_BULLISH_HEALTHY,              // 强上升·健康
    STRONG_BULLISH_SHALLOW_PULLBACK,     // 强上升·浅回调
    STRONG_BULLISH_WARNING_1H,           // 强上升·预警回调（1H）
    STRONG_BULLISH_WARNING_4H,           // 强上升·预警回调（4H内部）
    STRONG_BULLISH_CONFIRMED_PULLBACK,   // 强上升·确认回调
    BULLISH_PULLBACK_ONGOING,            // 上升回调·进行中
    BULLISH_PULLBACK_BOTTOMING,          // 上升回调·筑底
    BULLISH_PULLBACK_FAILURE,            // 上升回调·失败
    BULLISH_ENDING_CONTINUE_DOWN,        // 上升末端·延续下跌
    BULLISH_ENDING_CONFIRM,              // 上升末端·转势确认
    // 下降趋势类
    STRONG_BEARISH_HEALTHY,              // 强下降·健康
    STRONG_BEARISH_SHALLOW_BOUNCE,       // 强下降·浅反弹
    STRONG_BEARISH_WARNING_1H,           // 强下降·预警反弹（1H）
    STRONG_BEARISH_WARNING_4H,           // 强下降·预警反弹（4H内部）
    STRONG_BEARISH_CONFIRMED_BOUNCE,     // 强下降·确认反弹
    BEARISH_PULLBACK_ONGOING,            // 下降反弹·进行中
    BEARISH_PULLBACK_TOPPING,            // 下降反弹·筑顶
    BEARISH_PULLBACK_FAILURE,            // 下降反弹·失败
    BEARISH_ENDING_CONTINUE_UP,          // 下降末端·延续反弹
    BEARISH_ENDING_CONFIRM,              // 下降末端·转势确认
    // 震荡类
    RANGING_NO_DIRECTION,                // 震荡·无方向
    // 未知
    UNKNOWN;

    /**
     * 转换为宏观趋势类型（用于兼容旧逻辑）
     */
    public TrendType toMacroTrend() {
        switch (this) {
            case STRONG_BULLISH_HEALTHY:
            case STRONG_BULLISH_SHALLOW_PULLBACK:
            case STRONG_BULLISH_WARNING_1H:
            case STRONG_BULLISH_WARNING_4H:
            case STRONG_BULLISH_CONFIRMED_PULLBACK:
                return TrendType.STRONG_BULLISH;
            case BULLISH_PULLBACK_ONGOING:
            case BULLISH_PULLBACK_BOTTOMING:
                return TrendType.BULLISH_PULLBACK;
            case BULLISH_PULLBACK_FAILURE:
            case BULLISH_ENDING_CONTINUE_DOWN:
                return TrendType.BULLISH_ENDING;
            case BULLISH_ENDING_CONFIRM:
                return TrendType.POTENTIAL_BOTTOM;
            case STRONG_BEARISH_HEALTHY:
            case STRONG_BEARISH_SHALLOW_BOUNCE:
            case STRONG_BEARISH_WARNING_1H:
            case STRONG_BEARISH_WARNING_4H:
            case STRONG_BEARISH_CONFIRMED_BOUNCE:
                return TrendType.STRONG_BEARISH;
            case BEARISH_PULLBACK_ONGOING:
            case BEARISH_PULLBACK_TOPPING:
                return TrendType.BEARISH_PULLBACK;
            case BEARISH_PULLBACK_FAILURE:
            case BEARISH_ENDING_CONTINUE_UP:
                return TrendType.BEARISH_ENDING;
            case BEARISH_ENDING_CONFIRM:
                return TrendType.POTENTIAL_TOP;
            case RANGING_NO_DIRECTION:
                return TrendType.RANGING;
            default:
                return TrendType.CHAOTIC;
        }
    }
}