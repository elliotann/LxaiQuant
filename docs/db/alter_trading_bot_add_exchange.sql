-- trading_bot 表新增 exchange 字段
-- 交易所标识（如 BINANCE, OKX, BYBIT 等），用于多交易所场景
ALTER TABLE `trading_bot`
ADD COLUMN `exchange` varchar(32) DEFAULT NULL COMMENT '交易所标识（BINANCE, OKX, BYBIT等）';
