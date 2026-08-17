-- backtest_result 表补充最大连续盈利/亏损次数、平均持仓时间列
-- 在已有数据库上执行此脚本
ALTER TABLE backtest_result
    ADD COLUMN max_consecutive_wins INT COMMENT '最大连续盈利次数' AFTER largest_loss_trade,
    ADD COLUMN max_consecutive_losses INT COMMENT '最大连续亏损次数' AFTER max_consecutive_wins,
    ADD COLUMN avg_trade_duration DOUBLE COMMENT '平均持仓时间（天）' AFTER max_consecutive_losses;
