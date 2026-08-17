package com.chain.ai.trade.common.entity.constants;

public enum Exchange {
    // 加密货币
    BINANCE("Binance", "https://api.binance.com", MarketType.CRYPTO),
    HUOBI("Huobi", "https://api.huobi.pro", MarketType.CRYPTO),
    OKX("OKX", "https://www.okx.com", MarketType.CRYPTO),
    GATEIO("Gate.io", "https://api.gateio.ws", MarketType.CRYPTO),
    BYBIT("Bybit", "https://api.bybit.com", MarketType.CRYPTO),
    COINBASE("Coinbase", "https://api.coinbase.com", MarketType.CRYPTO),
    // 股票
    SSE("上交所", null, MarketType.STOCK_A),
    SZSE("深交所", null, MarketType.STOCK_A),
    HKEX("港交所", null, MarketType.STOCK_HK),
    NYSE("纽交所", null, MarketType.STOCK_US),
    NASDAQ("纳斯达克", null, MarketType.STOCK_US),
    // 商品
    SGE("上海黄金交易所", null, MarketType.COMMODITY),
    COMEX("纽约商品交易所", null, MarketType.COMMODITY);

    private final String name;
    private final String apiEndpoint;
    private final MarketType marketType;

    Exchange(String name, String apiEndpoint, MarketType marketType) {
        this.name = name;
        this.apiEndpoint = apiEndpoint;
        this.marketType = marketType;
    }

    public String getName() {
        return name;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public MarketType getMarketType() {
        return marketType;
    }
}