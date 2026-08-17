-- ai_equity_curve 表新增回测维度唯一约束
-- 确保每个回测任务每根K线只有一条权益记录
-- 对应设计文档：收益统计权益曲线重构 v1.3 §4.3

ALTER TABLE ai_equity_curve
    ADD CONSTRAINT uk_task_time UNIQUE (task_id, time);
