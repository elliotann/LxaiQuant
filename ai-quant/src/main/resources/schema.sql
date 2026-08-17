-- 量化回测系统核心表结构
-- 版本：v1.0
-- 更新日期：2024-01-16

-- ===========================================
-- 回测任务表 (backtest_task)
-- ===========================================

-- 回测任务表 - 存储任务配置和状态
CREATE TABLE IF NOT EXISTS backtest_task (
    -- 核心标识
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(50) NOT NULL UNIQUE,

    -- 策略信息
    strategy_name VARCHAR(100) NOT NULL,
    strategy_code TEXT NOT NULL,
    strategy_version VARCHAR(20) DEFAULT '1.0.0',

    -- 时间范围
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,

    -- 资金配置
    initial_capital DECIMAL(18,4) NOT NULL,
    currency VARCHAR(200) DEFAULT 'CNY',

    -- 基准和股票池
    benchmark VARCHAR(20),
    universe TEXT,  -- JSON字符串存储股票池数组

    -- 参数配置（JSON格式）
    config TEXT,

    -- 状态管理
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress INTEGER DEFAULT 0,  -- 进度0-100
    error_message TEXT,

    -- 执行时间追踪
    created_by VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME,
    completed_at DATETIME,
    duration_seconds INTEGER,

    -- 分区和优化
    partition_key DATE

    -- 约束将在应用层处理
    -- chk_dates: start_date <= end_date
    -- chk_status: status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
    -- chk_capital: initial_capital > 0
);

-- 索引 (兼容性创建，如果不存在则创建)
ALTER TABLE backtest_task ADD INDEX idx_task_status (status);
ALTER TABLE backtest_task ADD INDEX idx_task_created_at (created_at DESC);
ALTER TABLE backtest_task ADD INDEX idx_task_created_by (created_by, created_at DESC);
ALTER TABLE backtest_task ADD INDEX idx_task_dates (start_date, end_date);
ALTER TABLE backtest_task ADD INDEX idx_task_strategy (strategy_name, created_at DESC);
ALTER TABLE backtest_task ADD INDEX idx_task_partition (partition_key, created_at);

-- ===========================================
-- 回测结果表 (backtest_result)
-- ===========================================

-- 回测结果表 - 存储核心绩效指标
CREATE TABLE IF NOT EXISTS backtest_result (
    -- 关联标识
    task_id VARCHAR(50) PRIMARY KEY,

    -- 策略信息
    strategy_name VARCHAR(100),

    -- 核心绩效指标
    total_return DECIMAL(10,6) NOT NULL DEFAULT 0.0,        -- 总收益率
    max_drawdown DECIMAL(10,6) NOT NULL DEFAULT 0.0,        -- 最大回撤
    win_rate DECIMAL(10,4) DEFAULT 0.5,                     -- 胜率（默认50%）
    total_trades INTEGER NOT NULL DEFAULT 0,                -- 总交易次数
    winning_trades INTEGER DEFAULT 0,                       -- 盈利交易数
    profit_factor DECIMAL(10,4) DEFAULT 1.0,                -- 盈亏比（默认1.0）

    -- 其他指标
    final_value DECIMAL(18,4) NOT NULL,                     -- 最终价值

    -- 补充指标（后加）
    sharpe_ratio DECIMAL(10,4) DEFAULT 0.0,                 -- 夏普比率
    calmar_ratio DECIMAL(10,4) DEFAULT 0.0,                 -- 卡玛比率
    total_cost DECIMAL(18,4) DEFAULT 0.0,                   -- 总成本

    -- 扩展指标（v1.2 新增）
    annual_return DECIMAL(14,6) DEFAULT 0.0,                -- 年化收益率
    volatility DECIMAL(14,6) DEFAULT 0.0,                   -- 年化波动率
    sortino_ratio DECIMAL(14,4) DEFAULT 0.0,                -- 索提诺比率
    average_win DECIMAL(18,4) DEFAULT 0.0,                  -- 平均盈利额
    average_loss DECIMAL(18,4) DEFAULT 0.0,                 -- 平均亏损额
    largest_win_trade DECIMAL(18,4) DEFAULT 0.0,            -- 最大单笔盈利
    largest_loss_trade DECIMAL(18,4) DEFAULT 0.0,           -- 最大单笔亏损

    -- 回撤序列（JSON格式）
    drawdown_series TEXT,                                    -- 回撤序列JSON

    -- 时间戳
    calculated_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    -- 外键约束
    CONSTRAINT fk_result_task FOREIGN KEY (task_id)
        REFERENCES backtest_task(task_id) ON DELETE CASCADE
);

-- 索引 (兼容性创建，如果不存在则创建)
ALTER TABLE backtest_result ADD INDEX idx_result_task_fk (task_id);
ALTER TABLE backtest_result ADD INDEX idx_result_total_return (total_return DESC);
ALTER TABLE backtest_result ADD INDEX idx_result_max_drawdown (max_drawdown ASC);
ALTER TABLE backtest_result ADD INDEX idx_result_win_rate (win_rate DESC);
ALTER TABLE backtest_result ADD INDEX idx_result_calculated_at (calculated_at DESC);

-- ===========================================
-- 回测报告表 (backtest_report)
-- ===========================================

-- 删除可能存在的旧表
DROP TABLE IF EXISTS backtest_report;

-- 回测报告表 - 存储分析报告和用户交互
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
    CONSTRAINT fk_task_report FOREIGN KEY (task_id)
        REFERENCES backtest_task(task_id) ON DELETE CASCADE
);

-- 索引 (兼容性创建，如果不存在则创建)
ALTER TABLE backtest_report ADD INDEX idx_report_task_fk (task_id);
ALTER TABLE backtest_report ADD INDEX idx_report_title (title);
ALTER TABLE backtest_report ADD INDEX idx_report_created_at (created_at DESC);
ALTER TABLE backtest_report ADD INDEX idx_report_updated_at (updated_at DESC);
ALTER TABLE backtest_report ADD INDEX idx_report_rating (rating DESC);
ALTER TABLE backtest_report ADD INDEX idx_report_created_by (created_by, created_at DESC);

-- ===========================================
-- 视图
-- ===========================================

-- 回测概览视图
DROP VIEW IF EXISTS backtest_overview;
CREATE VIEW backtest_overview AS
SELECT
    bt.task_id,
    bt.strategy_name,
    bt.start_date,
    bt.end_date,
    bt.initial_capital,
    bt.status,
    bt.created_at,
    bt.completed_at,
    COALESCE(br.total_return, 0.0) as total_return,
    COALESCE(br.max_drawdown, 0.0) as max_drawdown,
    COALESCE(br.win_rate, 0.5) as win_rate,
    COALESCE(br.total_trades, 0) as total_trades,
    brp.title as report_title,
    brp.tags as report_tags,
    brp.is_favorite
FROM backtest_task bt
LEFT JOIN backtest_result br ON bt.task_id = br.task_id
LEFT JOIN backtest_report brp ON bt.task_id = brp.task_id
ORDER BY bt.created_at DESC;

-- 策略表现排名视图
DROP VIEW IF EXISTS strategy_performance_rank;
CREATE VIEW strategy_performance_rank AS
SELECT
    bt.strategy_name,
    COUNT(*) as backtest_count,
    COALESCE(AVG(br.total_return), 0.0) as avg_total_return,
    COALESCE(AVG(br.max_drawdown), 0.0) as avg_max_drawdown,
    COALESCE(AVG(br.win_rate), 0.5) as avg_win_rate,
    COALESCE(MAX(br.total_return), 0.0) as best_total_return,
    COALESCE(MIN(br.max_drawdown), 0.0) as best_max_drawdown,
    COALESCE(SUM(CASE WHEN br.total_return > 0.1 THEN 1 ELSE 0 END), 0) as high_return_count
FROM backtest_task bt
JOIN backtest_result br ON bt.task_id = br.task_id
WHERE bt.status = 'COMPLETED'
GROUP BY bt.strategy_name
ORDER BY avg_total_return DESC;

-- ===========================================
-- LLM 配置表 (t_llm_config)
-- ===========================================

CREATE TABLE IF NOT EXISTS t_llm_config (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    config_key VARCHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(128),
    api_base_url VARCHAR(512),
    api_key_enc TEXT,
    api_key_configured TINYINT(1) NOT NULL DEFAULT 0,
    extra_config TEXT,
    create_by VARCHAR(32),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(32),
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    delete_flag TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_llm_provider (provider),
    INDEX idx_llm_update_time (update_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 月度回测统计视图
DROP VIEW IF EXISTS monthly_backtest_stats;
CREATE VIEW monthly_backtest_stats AS
SELECT
    DATE_FORMAT(bt.created_at, '%Y-%m') as month,
    COUNT(*) as total_tasks,
    SUM(CASE WHEN bt.status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_tasks,
    SUM(CASE WHEN bt.status = 'FAILED' THEN 1 ELSE 0 END) as failed_tasks,
    COALESCE(AVG(br.total_return), 0.0) as avg_total_return,
    COALESCE(AVG(br.win_rate), 0.5) as avg_win_rate,
    AVG(bt.duration_seconds) as avg_duration_seconds
FROM backtest_task bt
LEFT JOIN backtest_result br ON bt.task_id = br.task_id
GROUP BY DATE_FORMAT(bt.created_at, '%Y-%m')
ORDER BY month DESC;

-- ===========================================
-- 交易机器人表 (trading_bot)
-- ===========================================

-- 交易机器人表 - 存储交易机器人配置和状态信息
CREATE TABLE IF NOT EXISTS trading_bot (
    -- 核心标识
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    bot_id VARCHAR(64) NOT NULL UNIQUE COMMENT '机器人唯一标识',

    -- 机器人基本信息
    bot_name VARCHAR(100) NOT NULL COMMENT '机器人名称',

    -- 关联信息
    user_id VARCHAR(64) NOT NULL COMMENT '所属用户ID',
    account_id VARCHAR(64) NOT NULL COMMENT '使用的交易账户ID',
    strategy_id VARCHAR(64) NOT NULL COMMENT '使用的策略ID',

    -- 交易配置
    trading_pair VARCHAR(50) NOT NULL COMMENT '交易对',
    allocated_capital DECIMAL(18,4) NOT NULL COMMENT '分配的资金额度',
    current_capital DECIMAL(18,4) NOT NULL COMMENT '当前剩余资金',

    -- 状态管理
    status VARCHAR(20) DEFAULT 'CREATED' COMMENT '机器人状态: CREATED, RUNNING, PAUSED, STOPPED, ERROR',

    -- 时间信息
    start_time DATETIME(3) COMMENT '开始时间',
    last_signal_time DATETIME(3) COMMENT '最后信号时间',

    -- 配置和统计
    configuration JSON COMMENT '配置信息（JSON格式）',
    statistics JSON COMMENT '统计信息（JSON格式）',

    -- 控制字段
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    remark TEXT COMMENT '备注',

    -- 审计字段
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by VARCHAR(64) COMMENT '创建者ID',
    updated_by VARCHAR(64) COMMENT '更新者ID',

    -- 索引
    INDEX idx_bot_id (bot_id),
    INDEX idx_user_id (user_id),
    INDEX idx_account_id (account_id),
    INDEX idx_strategy_id (strategy_id),
    INDEX idx_status (status),
    INDEX idx_enabled (enabled),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_updated_at (updated_at DESC)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易机器人表';

-- 插入示例机器人数据
INSERT INTO trading_bot (
    bot_id,
    bot_name,
    user_id,
    account_id,
    strategy_id,
    trading_pair,
    allocated_capital,
    current_capital,
    status,
    enabled,
    created_by,
    remark
) VALUES (
    'BOT-TEST-001',
    '测试交易机器人',
    'USER-TEST-001',
    'ACCOUNT-TEST-001',
    'STRATEGY_DEMO_001',
    'BTC-USDT',
    10000.00,
    10000.00,
    'CREATED',
    TRUE,
    'SYSTEM',
    '系统创建的测试机器人'
)
ON DUPLICATE KEY UPDATE bot_name = VALUES(bot_name);

CREATE TABLE IF NOT EXISTS signal_service_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    service_key VARCHAR(200) NOT NULL,
    enabled TINYINT(1) DEFAULT 1,
    params_json TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

ALTER TABLE signal_service_config ADD INDEX idx_signal_service_key (service_key);
ALTER TABLE signal_service_config ADD INDEX idx_signal_service_updated_at (updated_at DESC);

-- ===========================================
-- 交易计划表 (ai_trade_plan)
-- ===========================================

CREATE TABLE IF NOT EXISTS ai_trade_plan (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    plan_uuid VARCHAR(64) NOT NULL UNIQUE,
    preview_id VARCHAR(64),
    preview_type VARCHAR(16),
    name VARCHAR(128),
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    plan_content LONGTEXT,
    trace LONGTEXT,
    execution_result LONGTEXT,
    create_by VARCHAR(32),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(32),
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    delete_flag TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_trade_plan_status (status),
    INDEX idx_trade_plan_update_time (update_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OpenClaw 交易计划表';

-- ===========================================
-- DeepSeek 建议表 (trading_advice)
-- ===========================================

CREATE TABLE IF NOT EXISTS trading_advice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    advice_id VARCHAR(64) UNIQUE NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    natural_report TEXT,
    tradeplan_json JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trading_advice_symbol_created_at (symbol, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DeepSeek 实时建议记录';

-- ===========================================
-- 复盘任务表 (review_tasks)
-- ===========================================

CREATE TABLE IF NOT EXISTS review_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    time_range_start DATETIME,
    time_range_end DATETIME,
    robot_id VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'processing',
    report_json LONGTEXT,
    completed_at DATETIME,
    error_message TEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_review_tasks_robot_id (robot_id),
    INDEX idx_review_tasks_status (status),
    INDEX idx_review_tasks_create_time (create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 复盘任务表';
