-- ============================================================
-- 修复已存在的 symbols 表数据
-- 问题：旧版本的 init 脚本使用了 BTCUSDT 格式（无连字符），
--       但 candlestick 表存储的是 BTC-USDT-SWAP 格式，
--       导致 normalSymbol() 将其转成 BTCUSDT-USDT-SWAP ❌
-- 解决方法：将 Crypto 标的重设为正确的 OKX 格式
-- 适用场景：已跑过旧版 create_symbols_tables.sql 但 is_hot 未生效
-- ============================================================

-- 1) 重新标记 Crypto 热门标的（修复格式）
UPDATE symbols SET is_hot = 1, sort_order = 10
WHERE (market, symbol) IN (
  ('Crypto', 'BTC-USDT-SWAP'), ('Crypto', 'ETH-USDT-SWAP'), ('Crypto', 'SOL-USDT-SWAP'),
  ('Crypto', 'BNB-USDT-SWAP'), ('Crypto', 'XRP-USDT-SWAP'), ('Crypto', 'DOGE-USDT-SWAP'),
  ('Crypto', 'ADA-USDT-SWAP'), ('Crypto', 'AVAX-USDT-SWAP'), ('Crypto', 'DOT-USDT-SWAP'),
  ('Crypto', 'LINK-USDT-SWAP'), ('Crypto', 'LTC-USDT-SWAP'), ('Crypto', 'MATIC-USDT-SWAP'),
  ('Crypto', 'PEPE-USDT-SWAP'), ('Crypto', 'SUI-USDT-SWAP'), ('Crypto', 'APT-USDT-SWAP')
);

-- 2) 删除旧格式的错误数据（BTCUSDT 无连字符的）
DELETE FROM symbols WHERE market = 'Crypto' AND symbol NOT LIKE '%-USDT-SWAP';

-- 3) 重新补充名称
UPDATE symbols SET name = 'Bitcoin' WHERE symbol = 'BTC-USDT-SWAP' AND name IS NULL;
UPDATE symbols SET name = 'Ethereum' WHERE symbol = 'ETH-USDT-SWAP' AND name IS NULL;
UPDATE symbols SET name = 'Solana' WHERE symbol = 'SOL-USDT-SWAP' AND name IS NULL;
UPDATE symbols SET name = 'BNB' WHERE symbol = 'BNB-USDT-SWAP' AND name IS NULL;
UPDATE symbols SET name = 'XRP' WHERE symbol = 'XRP-USDT-SWAP' AND name IS NULL;
UPDATE symbols SET name = 'Dogecoin' WHERE symbol = 'DOGE-USDT-SWAP' AND name IS NULL;
UPDATE symbols SET name = 'Cardano' WHERE symbol = 'ADA-USDT-SWAP' AND name IS NULL;
UPDATE symbols SET name = 'Avalanche' WHERE symbol = 'AVAX-USDT-SWAP' AND name IS NULL;
UPDATE symbols SET name = 'Polkadot' WHERE symbol = 'DOT-USDT-SWAP' AND name IS NULL;
UPDATE symbols SET name = 'Chainlink' WHERE symbol = 'LINK-USDT-SWAP' AND name IS NULL;
UPDATE symbols SET name = 'Litecoin' WHERE symbol = 'LTC-USDT-SWAP' AND name IS NULL;
UPDATE symbols SET name = 'Polygon' WHERE symbol = 'MATIC-USDT-SWAP' AND name IS NULL;
