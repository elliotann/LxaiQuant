-- 迁移脚本：从backtest_result表的equity_curve字段迁移到backtest_equity_curve表
-- 注意：执行此脚本前，请先备份数据

-- 1. 创建新表（如果尚未创建）
-- 执行 create_equity_curve_table.sql

-- 2. 迁移数据（可选，如果现有数据需要迁移）
-- 此脚本需要根据实际的equity_curve JSON格式来编写
-- 建议通过Java代码进行数据迁移，因为需要解析JSON

-- 3. 删除backtest_result表中的equity_curve字段（谨慎操作，建议先备份）
-- ALTER TABLE backtest_result DROP COLUMN equity_curve;

-- 注意：
-- 1. 执行前请先备份backtest_result表
-- 2. 数据迁移建议通过Java代码完成，确保数据格式正确
-- 3. 删除字段前确认所有数据已迁移完成

