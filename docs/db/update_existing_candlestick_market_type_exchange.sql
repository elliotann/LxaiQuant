-- 历史数据一次性更新：所有已存在的K线数据默认为加密货币-OKX
UPDATE `vdr_candlestick`
SET `market_type` = 'CRYPTO',
    `exchange` = 'OKX'
WHERE `market_type` IS NULL
   OR `exchange` IS NULL;
