ALTER TABLE `vdr_candlestick`
    ADD COLUMN `market_type` VARCHAR(32) DEFAULT NULL COMMENT '市场类型：CRYPTO-加密货币, STOCK_A-A股, STOCK_HK-港股, STOCK_US-美股, COMMODITY-大宗商品, FOREX-外汇' AFTER `symbol`,
    ADD COLUMN `exchange` VARCHAR(32) DEFAULT NULL COMMENT '交易所：BINANCE/OKX/SSE/SZSE/HKEX/NYSE/NASDAQ/SGE/COMEX 等' AFTER `market_type`;

-- 为常用查询条件添加索引
ALTER TABLE `vdr_candlestick`
    ADD INDEX `idx_market_type_exchange_symbol_interval` (`market_type`, `exchange`, `symbol`, `candlestick_interval_enum`);
