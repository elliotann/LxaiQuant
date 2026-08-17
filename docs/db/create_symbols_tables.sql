-- ============================================================
-- 统一标的字典表 & 用户自选股表
-- 创建日期：2026-06-03
-- 说明：
--   1. symbols 表作为全系统统一标的字典，替代各处硬编码标的列表
--   2. user_favorites 表存储用户自选股，关联 symbols.id
--   3. is_hot + sort_order 用于 AI 雷达热门标的筛选
-- ============================================================

-- 统一标的字典表
CREATE TABLE IF NOT EXISTS `symbols` (
  `id`          INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `market`      VARCHAR(20)  NOT NULL COMMENT '市场：Crypto/USStock/CNStock/HKStock/Forex',
  `symbol`      VARCHAR(50)  NOT NULL COMMENT '标的代码，如 BTCUSDT、AAPL',
  `name`        VARCHAR(100) DEFAULT NULL COMMENT '中文名称，如 比特币、苹果',
  `exchange`    VARCHAR(20)  DEFAULT NULL COMMENT '交易所，如 OKX、BINANCE、NYSE',
  `is_hot`      TINYINT(1)   DEFAULT 0 COMMENT '是否热门标的，供雷达/推荐使用',
  `sort_order`  INT          DEFAULT 0 COMMENT '排序优先级，同 market 内排序',
  `active`      TINYINT(1)   DEFAULT 1 COMMENT '是否启用',
  `created_at`  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_market_symbol` (`market`, `symbol`),
  KEY `idx_market_hot` (`market`, `is_hot`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一标的字典表';

-- 用户自选股表
CREATE TABLE IF NOT EXISTS `user_favorites` (
  `id`         INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `user_id`    VARCHAR(32) NOT NULL COMMENT '用户ID',
  `symbol_id`  INT NOT NULL COMMENT '关联 symbols.id',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_user_symbol` (`user_id`, `symbol_id`),
  KEY `idx_user_id` (`user_id`),
  FOREIGN KEY (`symbol_id`) REFERENCES `symbols`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自选股表';

-- ============================================================
-- 初始化数据
-- ============================================================

-- 1) 从现有 candlestick 表导入所有已知交易对
INSERT INTO symbols (market, symbol, exchange)
SELECT DISTINCT
  COALESCE(market_type,
    CASE
      WHEN symbol LIKE '%USDT' THEN 'Crypto'
      WHEN symbol REGEXP '^[A-Z]{1,4}$' THEN 'USStock'
      ELSE 'Other'
    END
  ) AS market,
  symbol,
  NULL AS exchange
FROM candlestick
WHERE symbol IS NOT NULL;

-- 标记热门标的（symbol 格式须与 candlestick 表一致：ETH-USDT-SWAP）
UPDATE symbols SET is_hot = 1, sort_order = 10
WHERE (market, symbol) IN (
  ('Crypto', 'BTC-USDT-SWAP'), ('Crypto', 'ETH-USDT-SWAP'), ('Crypto', 'SOL-USDT-SWAP'),
  ('Crypto', 'BNB-USDT-SWAP'), ('Crypto', 'XRP-USDT-SWAP'), ('Crypto', 'DOGE-USDT-SWAP'),
  ('Crypto', 'ADA-USDT-SWAP'), ('Crypto', 'AVAX-USDT-SWAP'), ('Crypto', 'DOT-USDT-SWAP'),
  ('Crypto', 'LINK-USDT-SWAP'), ('Crypto', 'LTC-USDT-SWAP'), ('Crypto', 'MATIC-USDT-SWAP'),
  ('USStock', 'AAPL'), ('USStock', 'MSFT'), ('USStock', 'GOOGL'),
  ('USStock', 'AMZN'), ('USStock', 'TSLA'), ('USStock', 'NVDA'),
  ('USStock', 'META'), ('USStock', 'NFLX'), ('USStock', 'AMD'),
  ('USStock', 'JPM'), ('USStock', 'V'), ('USStock', 'JNJ')
);

-- 补充常见名称（symbol 格式须与 candlestick 表一致）
UPDATE symbols SET name = 'Bitcoin' WHERE symbol = 'BTC-USDT-SWAP';
UPDATE symbols SET name = 'Ethereum' WHERE symbol = 'ETH-USDT-SWAP';
UPDATE symbols SET name = 'Solana' WHERE symbol = 'SOL-USDT-SWAP';
UPDATE symbols SET name = 'Apple' WHERE symbol = 'AAPL';
UPDATE symbols SET name = 'Microsoft' WHERE symbol = 'MSFT';
UPDATE symbols SET name = 'Google' WHERE symbol = 'GOOGL';
UPDATE symbols SET name = 'Amazon' WHERE symbol = 'AMZN';
UPDATE symbols SET name = 'Tesla' WHERE symbol = 'TSLA';
UPDATE symbols SET name = 'NVIDIA' WHERE symbol = 'NVDA';
UPDATE symbols SET name = 'Meta' WHERE symbol = 'META';
