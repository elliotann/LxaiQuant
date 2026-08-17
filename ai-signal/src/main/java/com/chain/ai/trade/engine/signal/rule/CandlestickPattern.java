package com.chain.ai.trade.engine.signal.rule;

public enum CandlestickPattern {
    BULLISH_HARAMI("看涨孕线"),
    BEARISH_HARAMI("看跌孕线"),
    BULLISH_ENGULFING("看涨吞没"),
    BEARISH_ENGULFING("看跌吞没"),
    BULLISH_PIN_BAR("看涨锤子线"),
    BEARISH_PIN_BAR("看跌流星线"),
    DOJI("十字星"),
    MORNING_STAR("晨星"),
    EVENING_STAR("黄昏星"),
    THREE_WHITE_SOLDIERS("三白兵"),
    THREE_BLACK_CROWS("三只乌鸦"),
    BULLISH_ACCUMULATION("强阳吞没"),
    BEARISH_ACCUMULATION("强阴吞没");

    private final String displayName;

    CandlestickPattern(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
