-- 修改净值曲线表：将date字段改为time字段（秒级精度）
-- 执行前请先备份数据

-- 方案1：如果表是空的或者可以接受数据丢失，直接重建表
-- DROP TABLE IF EXISTS backtest_equity_curve;

-- 方案2：如果表中有数据需要保留，执行以下迁移（推荐先备份数据）
-- 步骤1：添加新字段
ALTER TABLE ai_equity_curve ADD COLUMN time DATETIME NULL COMMENT '时间（秒级精度）' AFTER task_id;

-- 步骤2：如果有数据，将date字段的数据迁移到time字段（假设date是当天0点）
-- UPDATE backtest_equity_curve SET time = CONCAT(date, ' 00:00:00') WHERE time IS NULL;

-- 步骤3：删除旧字段和相关索引
ALTER TABLE ai_equity_curve DROP INDEX uk_task_date;
ALTER TABLE ai_equity_curve DROP INDEX idx_date;
ALTER TABLE ai_equity_curve DROP COLUMN date;

-- 步骤4：设置新字段为NOT NULL（如果之前有数据迁移）
-- ALTER TABLE ai_equity_curve MODIFY COLUMN time DATETIME NOT NULL COMMENT '时间（秒级精度）';

-- 步骤5：添加新索引
ALTER TABLE ai_equity_curve ADD UNIQUE KEY uk_task_time (task_id, time);
ALTER TABLE ai_equity_curve ADD INDEX idx_time (time);

-- 注意：
-- 1. 如果表是新创建的，直接使用 create_equity_curve_table.sql 即可
-- 2. 如果表已存在且有数据，建议先备份数据，然后执行上述步骤
-- 3. 如果表已存在但没有重要数据，可以直接删除表后使用 create_equity_curve_table.sql 重建
