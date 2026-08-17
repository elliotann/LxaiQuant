-- 修复回测报告表结构
USE dev_new;

-- 删除可能存在的旧表
DROP TABLE IF EXISTS backtest_report;

-- 创建新的回测报告表
CREATE TABLE backtest_report (
    -- 关联标识
    task_id VARCHAR(50) PRIMARY KEY,

    -- 报告基本信息
    title VARCHAR(200) NOT NULL,
    version INTEGER DEFAULT 1,
    report_type VARCHAR(20) DEFAULT 'AUTO',  -- AUTO/MANUAL/TEMPLATE

    -- 文字分析和总结
    summary TEXT,                            -- 策略表现文字总结
    analysis TEXT,                           -- 深度分析结果JSON

    -- 图表和可视化
    charts TEXT,                             -- 图表配置JSON
    metrics TEXT,                            -- 关键指标JSON

    -- 用户交互和标记
    tags TEXT,                               -- 标签JSON数组
    is_favorite TINYINT(1) DEFAULT 0,
    is_archived TINYINT(1) DEFAULT 0,
    rating INTEGER,                          -- 评分1-5

    -- 用户笔记
    notes TEXT,

    -- 时间戳
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    -- 审计信息
    created_by VARCHAR(50),
    updated_by VARCHAR(50),

    -- 外键约束
    CONSTRAINT fk_report_task FOREIGN KEY (task_id)
        REFERENCES backtest_task(task_id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX idx_report_created_at ON backtest_report(created_at DESC);
CREATE INDEX idx_report_type ON backtest_report(report_type);
CREATE INDEX idx_report_favorite ON backtest_report(is_favorite);

SELECT '回测报告表修复完成' AS status;
