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

-- 索引
CREATE INDEX idx_task_status ON backtest_task(status);
CREATE INDEX idx_task_created_at ON backtest_task(created_at DESC);
CREATE INDEX idx_task_created_by ON backtest_task(created_by, created_at DESC);
CREATE INDEX idx_task_dates ON backtest_task(start_date, end_date);
CREATE INDEX idx_task_strategy ON backtest_task(strategy_name, created_at DESC);
CREATE INDEX idx_task_partition ON backtest_task(partition_key, created_at);

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
    annual_return DECIMAL(14,6) DEFAULT 0.0,                -- 年化收益率
    volatility DECIMAL(14,6) DEFAULT 0.0,                   -- 年化波动率

    -- 其他指标
    final_value DECIMAL(18,4) NOT NULL,                     -- 最终价值

    -- 时间戳
    calculated_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    -- 外键约束
    CONSTRAINT fk_result_task FOREIGN KEY (task_id)
        REFERENCES backtest_task(task_id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_result_task_fk ON backtest_result(task_id);
CREATE INDEX idx_result_total_return ON backtest_result(total_return DESC);
CREATE INDEX idx_result_max_drawdown ON backtest_result(max_drawdown ASC);
CREATE INDEX idx_result_win_rate ON backtest_result(win_rate DESC);
CREATE INDEX idx_result_calculated_at ON backtest_result(calculated_at DESC);

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

-- 索引
CREATE INDEX idx_report_task_fk ON backtest_report(task_id);
CREATE INDEX idx_report_title ON backtest_report(title);
CREATE INDEX idx_report_created_at ON backtest_report(created_at DESC);
CREATE INDEX idx_report_updated_at ON backtest_report(updated_at DESC);
CREATE INDEX idx_report_rating ON backtest_report(rating DESC);
CREATE INDEX idx_report_view_count ON backtest_report(view_count DESC);
CREATE INDEX idx_report_created_by ON backtest_report(created_by, created_at DESC);

-- ===========================================
-- 触发器 (PostgreSQL语法，MySQL需要调整)
-- ===========================================

-- 自动更新updated_at字段
DELIMITER //

CREATE TRIGGER IF NOT EXISTS trg_backtest_report_updated
BEFORE UPDATE ON backtest_report
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END;
//

-- 计算任务执行时长
CREATE TRIGGER IF NOT EXISTS trg_task_duration
BEFORE UPDATE ON backtest_task
FOR EACH ROW
BEGIN
    IF NEW.status = 'COMPLETED' AND OLD.status != 'COMPLETED' THEN
        IF NEW.started_at IS NOT NULL THEN
            SET NEW.duration_seconds = TIMESTAMPDIFF(SECOND, NEW.started_at, NEW.completed_at);
        END IF;
    END IF;
END;
//

DELIMITER ;

-- ===========================================
-- 视图
-- ===========================================

-- 回测概览视图
CREATE OR REPLACE VIEW backtest_overview AS
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
CREATE OR REPLACE VIEW strategy_performance_rank AS
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

-- 月度回测统计视图
CREATE OR REPLACE VIEW monthly_backtest_stats AS
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
-- 示例数据
-- ===========================================

-- 插入示例任务
INSERT INTO backtest_task (
    task_id, strategy_name, strategy_code, strategy_version,
    start_date, end_date, initial_capital, currency,
    benchmark, universe, config, status, created_by
) VALUES (
    'TASK_20240116_143022_ABC123',
    '均线策略',
    'class MeanReversionStrategy:
    def __init__(self, fast_period=20, slow_period=60):
        self.fast_period = fast_period
        self.slow_period = slow_period

    def generate_signals(self, data):
        # 策略实现
        pass',
    '1.0.0',
    '2023-01-01',
    '2023-12-31',
    1000000.0000,
    'CNY',
    '000300.SH',
    ARRAY['000001.SZ', '000002.SZ', '600000.SH'],
    '{
        "strategy": {
            "fast_period": 20,
            "slow_period": 60,
            "stop_loss": 0.1,
            "take_profit": 0.2
        },
        "backtest": {
            "commission": 0.0003,
            "tax": 0.001
        }
    }',
    'COMPLETED',
    'system'
);
