package com.chain.ai.trade.engine.signal.enums;

public enum IndicatorSignal {
    //macd
    GOLDEN_CROSS("★金叉形成★",CommonTrend.BULLISH),
    BULLISH_ALIGNMENT("多头排列", CommonTrend.BULLISH),
    STRONG_GOLDEN_CROSS("水上金叉+柱状图放大",CommonTrend.BULLISH),    // 水上金叉+柱状图放大     // 普通金叉
    DIVERGENCE_GOLDEN_CROSS("底背离金叉",CommonTrend.BULLISH),// 底背离金叉
    BULLISH_STRENGTH("多头强势排列(连续红柱放大)",CommonTrend.BULLISH),       // 多头强势排列(连续红柱放大)
    STRONG_DEATH_CROSS("水下死叉+柱状图放大",CommonTrend.BEARISH),     // 水下死叉+柱状图放大
    DIVERGENCE_DEATH_CROSS("顶背离死叉",CommonTrend.BEARISH), // 顶背离死叉
    BEARISH_STRENGTH("空头强势排列(连续绿柱放大)",CommonTrend.BEARISH),       // 空头强势排列(连续绿柱放大)
    DEATH_CROSS("▲死叉形成▲", CommonTrend.BEARISH),
    BEARISH_ALIGNMENT("空头排列",CommonTrend.BEARISH),
    INSUFFICIENT_DATA("数据不足", CommonTrend.NEUTRAL),

    BULLISH_MOMENTUM("多头动能增强", CommonTrend.BULLISH),

    BULLISH_MOMENTUM_WEAK("多头动能减弱", CommonTrend.BULLISH),

    BEARISH_MOMENTUM("空头动能增强", CommonTrend.BEARISH),

    BEARISH_MOMENTUM_WEAK("空头动能减弱", CommonTrend.WEAK_BEARISH),

    MOMENTUM_NEUTRAL("动能中性", CommonTrend.NEUTRAL),
    /**
     * EMA
     */
    STRONG_EMA_BULLISH_ALIGNMENT("★EMA强多头★",CommonTrend.BULLISH),
    EMA_BULLISH_ALIGNMENT("★EMA多头排列★",CommonTrend.BULLISH),
    EMA_WEAK_BULLISH_ALIGNMENT("★EMA弱多头★",CommonTrend.BULLISH),
    EMA_BEARISH_ALIGNMENT("▲EMA空头排列▲", CommonTrend.BEARISH),
    EMA_WEAK_BEARISH_ALIGNMENT("▲EMA弱空头▲", CommonTrend.BEARISH),
    STRONG_EMA_BEARISH_ALIGNMENT("▲EMA强空头排列▲", CommonTrend.BEARISH),

    PRICE_UP_SMA("▲价格突破SMA（空头风险）▲", CommonTrend.BEARISH),

    PRICE_DOWN_SMA("价格跌破SMA(多头风险)", CommonTrend.BEARISH),
    DIRECTION_STRONG_BULLISH_ALIGNMENT("大方向强多头", CommonTrend.BULLISH),
    DIRECTION_BULLISH_ALIGNMENT("大方向多头", CommonTrend.BULLISH),
    DIRECTION_WEAK_BULLISH_ALIGNMENT("大方向弱多头", CommonTrend.BULLISH),
    DIRECTION_STRONG_BEARISH_ALIGNMENT("大方向强空头", CommonTrend.BEARISH),
    DIRECTION_BEARISH_ALIGNMENT("大方向空头", CommonTrend.BEARISH),
    DIRECTION_WEAK_BEARISH_ALIGNMENT("大方向弱空头", CommonTrend.BEARISH),;

    private String display;
    private CommonTrend trend;

    //所暂用权重
    private double weight;

    IndicatorSignal(String display, CommonTrend trend) {
        this.display = display;
        this.trend = trend;
    }


    public String getDisplay() { return display; }
    public CommonTrend getTrend() { return trend; }
}
