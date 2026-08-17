-- 交易机器人表创建脚本
-- 执行此脚本可以手动创建 trading_bot 表

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
