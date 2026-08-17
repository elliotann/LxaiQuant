-- 删除 backtest_report 表中的 charts 字段
-- 执行前请先备份数据

ALTER TABLE backtest_report DROP COLUMN charts;

