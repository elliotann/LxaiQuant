package com.chain.ai.trade.engine.signal.rule;

public enum IndicatorType {
    CLOSE,
    OPEN,
    HIGH,
    LOW,
    PRICE_MOVE,
    MACD_HISTOGRAM,
    MACD_SIGNAL,
    MACD_LINE,
    SMC_POSITION_SCORE,
    SMC_RR,
    TIME,
    TREND_DIRECTION,

    SWING_RANGING,
    SWING_BREAKOUT,
    SMC_OB_RANGING,
    WEEKDAY,

    EMA_TREND,

    SMC_POSITION_15M,
    SMC_RISK_PERCENT,
    SMC_IN_SUPPLY_ZONE,
    SMC_IN_DEMAND_ZONE
}
