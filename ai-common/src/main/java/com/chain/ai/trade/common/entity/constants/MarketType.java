package com.chain.ai.trade.common.entity.constants;

public enum MarketType {
    CRYPTO("加密货币"),
    STOCK_A("A股"),
    STOCK_HK("港股"),
    STOCK_US("美股"),
    COMMODITY("大宗商品"),
    FOREX("外汇");

    private final String description;

    MarketType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
