package com.chain.ai.trade.engine.signal.entity.constants;

// 新增交易信号类型
public enum TradeSignal {
    STRONG_LONG("🔥强势做多"),
    CAUTIOUS_LONG("🟢谨慎做多"),
    NEUTRAL("🟡持币观望"),
    CAUTIOUS_SHORT("🟠谨慎做空"),
    STRONG_SHORT("🔴强势做空"),
    INVALID("🚫无效信号");

    private final String display;


    TradeSignal(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}