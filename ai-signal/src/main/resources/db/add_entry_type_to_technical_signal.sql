ALTER TABLE technical_signal
ADD COLUMN entry_type VARCHAR(16) DEFAULT 'MARKET' COMMENT '入场类型: MARKET(市价) / LIMIT(限价)';

ALTER TABLE technical_signal
ADD COLUMN market_trend VARCHAR(32) DEFAULT NULL COMMENT '市场趋势/状态';

