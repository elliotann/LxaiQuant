-- ============================================================
-- 初始化 symbols 表核心标的（ETH / BTC 等热门交易对）
-- 注意：symbol 字段格式必须与 candlestick 表一致（OKX 格式）
-- 适用场景：全新安装时无需从 candlestick 表导入即可使用
-- 使用方法：在 create_symbols_tables.sql 之后执行
-- ============================================================

-- 如果已有数据则跳过
INSERT IGNORE INTO symbols (market, symbol, name, exchange, is_hot, sort_order) VALUES
-- Crypto 热门（BINANCE）
('Crypto', 'BTC-USDT-SWAP',  'Bitcoin',    'BINANCE', 1, 100),
('Crypto', 'ETH-USDT-SWAP',  'Ethereum',   'BINANCE', 1, 99),
('Crypto', 'SOL-USDT-SWAP',  'Solana',     'BINANCE', 1, 98),
('Crypto', 'BNB-USDT-SWAP',  'BNB',        'BINANCE', 1, 97),
('Crypto', 'XRP-USDT-SWAP',  'XRP',        'BINANCE', 1, 96),
('Crypto', 'DOGE-USDT-SWAP', 'Dogecoin',   'BINANCE', 1, 95),
('Crypto', 'ADA-USDT-SWAP',  'Cardano',    'BINANCE', 1, 94),
('Crypto', 'AVAX-USDT-SWAP', 'Avalanche',  'BINANCE', 1, 93),
('Crypto', 'DOT-USDT-SWAP',  'Polkadot',   'BINANCE', 1, 92),
('Crypto', 'LINK-USDT-SWAP', 'Chainlink',  'BINANCE', 1, 91),
('Crypto', 'LTC-USDT-SWAP',  'Litecoin',   'BINANCE', 1, 90),
('Crypto', 'MATIC-USDT-SWAP','Polygon',    'BINANCE', 1, 89),
('Crypto', 'PEPE-USDT-SWAP', 'Pepe',       'BINANCE', 1, 88),
('Crypto', 'SUI-USDT-SWAP',  'Sui',        'BINANCE', 1, 87),
('Crypto', 'APT-USDT-SWAP',  'Aptos',      'BINANCE', 1, 86),
-- Crypto 热门（OKX）
('Crypto', 'BTC-USDT-SWAP',  'Bitcoin',    'OKX',    1, 80),
('Crypto', 'ETH-USDT-SWAP',  'Ethereum',   'OKX',    1, 79),
('Crypto', 'SOL-USDT-SWAP',  'Solana',     'OKX',    1, 78);

-- 美股热门（可选）
INSERT IGNORE INTO symbols (market, symbol, name, exchange, is_hot, sort_order) VALUES
('USStock', 'AAPL',  'Apple',           'NASDAQ', 1, 50),
('USStock', 'MSFT',  'Microsoft',       'NASDAQ', 1, 49),
('USStock', 'GOOGL', 'Alphabet',        'NASDAQ', 1, 48),
('USStock', 'AMZN',  'Amazon',          'NASDAQ', 1, 47),
('USStock', 'TSLA',  'Tesla',           'NASDAQ', 1, 46),
('USStock', 'NVDA',  'NVIDIA',          'NASDAQ', 1, 45),
('USStock', 'META',  'Meta Platforms',  'NASDAQ', 1, 44);
-- ============================================================
-- 初始化 symbols 表核心标的（ETH / BTC 等热门交易对）
-- 注意：symbol 字段格式必须与 candlestick 表一致（OKX 格式）
-- 适用场景：全新安装时无需从 candlestick 表导入即可使用
-- 使用方法：在 create_symbols_tables.sql 之后执行
-- ============================================================

-- 如果已有数据则跳过
INSERT IGNORE INTO symbols (market, symbol, name, exchange, is_hot, sort_order) VALUES
-- Crypto 热门（BINANCE）
('Crypto', 'BTC-USDT-SWAP',  'Bitcoin',    'BINANCE', 1, 100),
('Crypto', 'ETH-USDT-SWAP',  'Ethereum',   'BINANCE', 1, 99),
('Crypto', 'SOL-USDT-SWAP',  'Solana',     'BINANCE', 1, 98),
('Crypto', 'BNB-USDT-SWAP',  'BNB',        'BINANCE', 1, 97),
('Crypto', 'XRP-USDT-SWAP',  'XRP',        'BINANCE', 1, 96),
('Crypto', 'DOGE-USDT-SWAP', 'Dogecoin',   'BINANCE', 1, 95),
('Crypto', 'ADA-USDT-SWAP',  'Cardano',    'BINANCE', 1, 94),
('Crypto', 'AVAX-USDT-SWAP', 'Avalanche',  'BINANCE', 1, 93),
('Crypto', 'DOT-USDT-SWAP',  'Polkadot',   'BINANCE', 1, 92),
('Crypto', 'LINK-USDT-SWAP', 'Chainlink',  'BINANCE', 1, 91),
('Crypto', 'LTC-USDT-SWAP',  'Litecoin',   'BINANCE', 1, 90),
('Crypto', 'MATIC-USDT-SWAP','Polygon',    'BINANCE', 1, 89),
('Crypto', 'PEPE-USDT-SWAP', 'Pepe',       'BINANCE', 1, 88),
('Crypto', 'SUI-USDT-SWAP',  'Sui',        'BINANCE', 1, 87),
('Crypto', 'APT-USDT-SWAP',  'Aptos',      'BINANCE', 1, 86),
-- Crypto 热门（OKX）
('Crypto', 'BTC-USDT-SWAP',  'Bitcoin',    'OKX',    1, 80),
('Crypto', 'ETH-USDT-SWAP',  'Ethereum',   'OKX',    1, 79),
('Crypto', 'SOL-USDT-SWAP',  'Solana',     'OKX',    1, 78);

-- 美股热门（可选）
INSERT IGNORE INTO symbols (market, symbol, name, exchange, is_hot, sort_order) VALUES
('USStock', 'AAPL',  'Apple',           'NASDAQ', 1, 50),
('USStock', 'MSFT',  'Microsoft',       'NASDAQ', 1, 49),
('USStock', 'GOOGL', 'Alphabet',        'NASDAQ', 1, 48),
('USStock', 'AMZN',  'Amazon',          'NASDAQ', 1, 47),
('USStock', 'TSLA',  'Tesla',           'NASDAQ', 1, 46),
('USStock', 'NVDA',  'NVIDIA',          'NASDAQ', 1, 45),
('USStock', 'META',  'Meta Platforms',  'NASDAQ', 1, 44);
