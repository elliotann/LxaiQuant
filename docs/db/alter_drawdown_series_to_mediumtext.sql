-- backtest_result 表 drawdown_series 列长度不足
-- TEXT(65KB) -> MEDIUMTEXT(16MB)
-- 适用于长周期回测（如分钟级数月数据）
ALTER TABLE backtest_result MODIFY COLUMN drawdown_series MEDIUMTEXT COMMENT '回撤序列JSON';
