-- 迁移净值曲线表：将date字段改为time字段（秒级精度）
-- 执行前请先备份数据！

-- 方案1：如果表中没有重要数据，直接删除表后重建（推荐）
DROP TABLE IF EXISTS ai_equity_curve;

-- 然后执行 create_equity_curve_table.sql 创建新表（使用新的表结构）

-- ========================================
-- 方案2：如果需要保留现有数据，使用以下步骤（谨慎操作）
-- ========================================

-- 步骤1：备份数据
-- CREATE TABLE backtest_equity_curve_backup AS SELECT * FROM backtest_equity_curve;

-- 步骤2：添加新字段（允许NULL，以便迁移数据）
-- ALTER TABLE backtest_equity_curve ADD COLUMN time DATETIME NULL COMMENT '时间（秒级精度）' AFTER task_id;

-- 步骤3：迁移数据（将date转为datetime，设置为当天0点）
-- UPDATE backtest_equity_curve SET time = CONCAT(date, ' 00:00:00') WHERE time IS NULL;

-- 步骤4：删除旧索引
-- ALTER TABLE backtest_equity_curve DROP INDEX uk_task_date;
-- ALTER TABLE backtest_equity_curve DROP INDEX idx_date;

-- 步骤5：删除旧字段
-- ALTER TABLE backtest_equity_curve DROP COLUMN date;

-- 步骤6：设置新字段为NOT NULL
-- ALTER TABLE backtest_equity_curve MODIFY COLUMN time DATETIME NOT NULL COMMENT '时间（秒级精度）';

-- 步骤7：添加新索引
-- ALTER TABLE backtest_equity_curve ADD UNIQUE KEY uk_task_time (task_id, time);
-- ALTER TABLE backtest_equity_curve ADD INDEX idx_time (time);
