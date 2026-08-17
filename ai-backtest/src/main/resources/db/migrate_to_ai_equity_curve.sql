-- =============================================================
-- 迁移脚本：backtest_equity_curve → ai_equity_curve
-- 执行前请先备份数据！
-- 变更内容：
--   1. 表重命名：backtest_equity_curve → ai_equity_curve
--   2. 字段更名：daily_return → return_rate
--   3. 字段更名：benchmark_daily_return → benchmark_return_rate
--   4. 添加唯一约束：uk_task_time (task_id, time)
-- =============================================================

-- 步骤1：RENAME TABLE
RENAME TABLE backtest_equity_curve TO ai_equity_curve;

-- 步骤2：ALTER 字段名（注意顺序：先 CHANGE column name，再 MODIFY）
ALTER TABLE ai_equity_curve
    CHANGE COLUMN daily_return return_rate DECIMAL(10,6) COMMENT '周期收益率（相对上一个采样点的环比收益率）',
    CHANGE COLUMN benchmark_daily_return benchmark_return_rate DECIMAL(10,6) COMMENT '基准周期收益率',
    MODIFY COLUMN time DATETIME NOT NULL COMMENT '时间（实盘：整点采样时间；回测：K线结束时间）';

-- 步骤3：添加唯一约束 uk_task_time（如果表已存在且无此约束）
-- 注意：先检查 backtest_result 表中的 task_id 是否唯一关联 robot_id，
-- 如果同一个 task_id 出现在多行且 time 重复，会报错
-- 如果有冲突需要先清理数据
ALTER TABLE ai_equity_curve ADD CONSTRAINT uk_task_time UNIQUE (task_id, time);

-- 步骤4：更新表注释
ALTER TABLE ai_equity_curve COMMENT = '净值曲线表（回测+实盘统一）';

-- =============================================================
-- 回滚脚本（如需恢复）
-- =============================================================
-- ALTER TABLE ai_equity_curve DROP INDEX uk_task_time;
-- ALTER TABLE ai_equity_curve CHANGE COLUMN return_rate daily_return DECIMAL(10,6) COMMENT '日收益率';
-- ALTER TABLE ai_equity_curve CHANGE COLUMN benchmark_return_rate benchmark_daily_return DECIMAL(10,6) COMMENT '基准日收益率';
-- RENAME TABLE ai_equity_curve TO backtest_equity_curve;
