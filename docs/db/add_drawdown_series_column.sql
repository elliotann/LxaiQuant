-- backtest_result 表缺少 drawdown_series 列
-- 在已有数据库上执行此脚本
ALTER TABLE backtest_result ADD COLUMN drawdown_series TEXT COMMENT '回撤序列JSON' AFTER largest_loss_trade;
