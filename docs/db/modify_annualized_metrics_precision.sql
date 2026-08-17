-- 扩大年化收益率、波动率、索提诺比率字段精度，防止短周期回测值溢出
-- 执行时间：2026年

ALTER TABLE backtest_result
MODIFY COLUMN annual_return DECIMAL(14,6) DEFAULT 0.0 COMMENT '年化收益率',
MODIFY COLUMN volatility DECIMAL(14,6) DEFAULT 0.0 COMMENT '年化波动率',
MODIFY COLUMN sortino_ratio DECIMAL(14,4) DEFAULT 0.0 COMMENT '索提诺比率';
