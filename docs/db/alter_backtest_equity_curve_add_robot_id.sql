-- backtest_equity_curve 表新增实盘机器人相关字段
-- 用于兼容实盘机器人的日终权益数据存储
ALTER TABLE ai_equity_curve
    ADD COLUMN robot_id VARCHAR(32) NULL COMMENT '机器人ID（实盘数据）' AFTER task_id,
    ADD COLUMN robot_name VARCHAR(64) NULL COMMENT '机器人名称' AFTER robot_id,
    ADD UNIQUE KEY uk_robot_time (robot_id, time),
    ADD INDEX idx_robot_id (robot_id);
