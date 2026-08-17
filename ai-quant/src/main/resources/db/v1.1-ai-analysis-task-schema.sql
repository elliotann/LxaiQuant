-- ============================================================
-- AI 定时分析任务功能 - 数据库迁移脚本 v1.1
-- 适用数据库：MySQL 5.7+
-- 创建表：ai_analysis_tasks（分析任务）、analysis_reports（分析报告）
-- ============================================================

-- 分析任务表
CREATE TABLE IF NOT EXISTS ai_analysis_tasks (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID（雪花算法）',
    user_id VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '用户ID',
    symbols TEXT COMMENT '标的列表（JSON数组字符串）',
    interval_min INT NOT NULL DEFAULT 60 COMMENT '分析间隔（分钟）',
    notify_channels VARCHAR(255) DEFAULT '["app"]' COMMENT '通知渠道（JSON数组字符串）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    last_run_at DATETIME COMMENT '上次执行时间',
    next_run_at DATETIME COMMENT '下次执行时间',
    xxl_job_id INT COMMENT 'XXL-JOB任务ID',
    xxl_job_group_id VARCHAR(64) COMMENT 'XXL-JOB执行器分组ID',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT(1) DEFAULT 0 COMMENT '删除标志',
    INDEX idx_user_id (user_id),
    INDEX idx_enabled_next_run (enabled, next_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析任务表';

-- 分析报告表
CREATE TABLE IF NOT EXISTS analysis_reports (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID（雪花算法）',
    task_id VARCHAR(64) NOT NULL COMMENT '关联任务ID',
    symbol VARCHAR(50) NOT NULL COMMENT '交易对',
    decision VARCHAR(20) DEFAULT 'hold' COMMENT 'AI决策（buy/sell/hold/REJECT）',
    confidence INT DEFAULT 50 COMMENT '置信度（0-100）',
    summary VARCHAR(500) DEFAULT '' COMMENT '分析摘要',
    analysis TEXT COMMENT '完整分析内容（JSON）',
    risks VARCHAR(500) DEFAULT '' COMMENT '风险提示',
    trigger_type VARCHAR(30) DEFAULT 'SCHEDULED' COMMENT '触发方式（SCHEDULED/MANUAL）',
    report_json TEXT COMMENT '原始分析报告（JSON）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT(1) DEFAULT 0 COMMENT '删除标志',
    INDEX idx_task_id (task_id),
    INDEX idx_symbol (symbol),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析报告表';
