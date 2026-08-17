-- 添加缺失的字段到technical_signal表
ALTER TABLE `technical_signal`
ADD COLUMN `signal_source` varchar(50) DEFAULT NULL COMMENT '信号来源（如 DEEPSEEK / OPENCLAW / SYSTEM）',
ADD COLUMN `source_advice_id` varchar(64) DEFAULT NULL COMMENT '关联的 DeepSeek 建议ID',
ADD COLUMN `open_price` decimal(20,8) DEFAULT NULL COMMENT '开盘价',
ADD COLUMN `close_price` decimal(20,8) DEFAULT NULL COMMENT '收盘价',
ADD COLUMN `high_price` decimal(20,8) DEFAULT NULL COMMENT '高点',
ADD COLUMN `low_price` decimal(20,8) DEFAULT NULL COMMENT '低点',
ADD COLUMN `volume` decimal(20,8) DEFAULT NULL COMMENT '成交量',
ADD COLUMN `threshold` decimal(20,8) DEFAULT NULL COMMENT '阈值触发值',
ADD COLUMN `extra_params` text DEFAULT NULL COMMENT '额外技术参数',
ADD COLUMN `market_trend` varchar(32) DEFAULT NULL COMMENT '市场趋势/状态';
