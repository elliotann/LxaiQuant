-- 统一净值曲线表（兼容回测与实盘机器人）
-- 回测数据：task_id 非空，robot_id 为空
-- 实盘机器人数据：robot_id 非空，task_id 为空
CREATE TABLE IF NOT EXISTS ai_equity_curve (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(64) NULL COMMENT '回测任务ID（回测数据）',
    robot_id VARCHAR(32) NULL COMMENT '机器人ID（实盘数据）',
    robot_name VARCHAR(64) NULL COMMENT '机器人名称',
    time DATETIME NOT NULL COMMENT '时间（秒级精度）',
    
    -- 策略净值
    equity DECIMAL(18,4) NOT NULL COMMENT '净值',
    return_rate DECIMAL(10,6) COMMENT '周期收益率（相对上一个采样点的环比收益率）',
    
    -- 回撤
    drawdown DECIMAL(10,6) COMMENT '回撤',
    
    -- 基准曲线
    benchmark_value DECIMAL(12,4) COMMENT '基准指数净值',
    benchmark_return_rate DECIMAL(10,6) COMMENT '基准周期收益率',
    
    -- 索引
    UNIQUE KEY uk_task_time (task_id, time),
    UNIQUE KEY uk_robot_time (robot_id, time),
    INDEX idx_task_id (task_id),
    INDEX idx_robot_id (robot_id),
    INDEX idx_time (time),
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='净值曲线表（回测+实盘统一）';
