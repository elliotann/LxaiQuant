-- ai-logs模块数据库表结构
-- 业务日志模块数据库设计

-- 订单日志表
CREATE TABLE IF NOT EXISTS `business_log_order` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '追踪ID',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
    `event_time` TIMESTAMP NOT NULL COMMENT '事件发生时间',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `account_id` BIGINT NOT NULL COMMENT '账户ID',
    `strategy_id` VARCHAR(64) COMMENT '策略ID',
    `order_id` VARCHAR(64) NOT NULL COMMENT '订单ID',
    `symbol` VARCHAR(32) NOT NULL COMMENT '交易对',
    `order_side` VARCHAR(10) NOT NULL COMMENT '订单方向(BUY/SELL)',
    `order_type` VARCHAR(20) NOT NULL COMMENT '订单类型(MARKET/LIMIT/STOP)',
    `price` DECIMAL(20,8) COMMENT '订单价格',
    `quantity` DECIMAL(20,8) NOT NULL COMMENT '订单数量',
    `amount` DECIMAL(20,8) COMMENT '订单金额',
    `status` VARCHAR(20) NOT NULL COMMENT '订单状态',
    `client_order_id` VARCHAR(64) COMMENT '客户端订单ID',
    `remark` TEXT COMMENT '备注信息',
    `extra_data` JSON COMMENT '扩展数据',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_trace_id` (`trace_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_account_id` (`account_id`),
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_event_time` (`event_time`),
    INDEX `idx_symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单业务日志表';

-- 成交日志表
CREATE TABLE IF NOT EXISTS `business_log_trade` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '追踪ID',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
    `event_time` TIMESTAMP NOT NULL COMMENT '事件发生时间',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `account_id` BIGINT NOT NULL COMMENT '账户ID',
    `strategy_id` VARCHAR(64) COMMENT '策略ID',
    `trade_id` VARCHAR(64) NOT NULL COMMENT '成交ID',
    `order_id` VARCHAR(64) NOT NULL COMMENT '订单ID',
    `symbol` VARCHAR(32) NOT NULL COMMENT '交易对',
    `trade_side` VARCHAR(10) NOT NULL COMMENT '成交方向(BUY/SELL)',
    `price` DECIMAL(20,8) NOT NULL COMMENT '成交价格',
    `quantity` DECIMAL(20,8) NOT NULL COMMENT '成交数量',
    `amount` DECIMAL(20,8) NOT NULL COMMENT '成交金额',
    `fee` DECIMAL(20,8) COMMENT '手续费',
    `fee_currency` VARCHAR(10) COMMENT '手续费币种',
    `is_maker` BOOLEAN COMMENT '是否做市商',
    `client_order_id` VARCHAR(64) COMMENT '客户端订单ID',
    `remark` TEXT COMMENT '备注信息',
    `extra_data` JSON COMMENT '扩展数据',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_trace_id` (`trace_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_account_id` (`account_id`),
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_trade_id` (`trade_id`),
    INDEX `idx_event_time` (`event_time`),
    INDEX `idx_symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成交业务日志表';

-- 账户资金变动日志表
CREATE TABLE IF NOT EXISTS `business_log_account_fund` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '追踪ID',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
    `event_time` TIMESTAMP NOT NULL COMMENT '事件发生时间',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `account_id` BIGINT NOT NULL COMMENT '账户ID',
    `strategy_id` VARCHAR(64) COMMENT '策略ID',
    `currency` VARCHAR(10) NOT NULL COMMENT '币种',
    `change_type` VARCHAR(20) NOT NULL COMMENT '变动类型(DEPOSIT/WITHDRAW/TRANSFER/TRADE/FEE)',
    `amount` DECIMAL(20,8) NOT NULL COMMENT '变动金额',
    `balance_before` DECIMAL(20,8) NOT NULL COMMENT '变动前余额',
    `balance_after` DECIMAL(20,8) NOT NULL COMMENT '变动后余额',
    `available_before` DECIMAL(20,8) NOT NULL COMMENT '变动前可用余额',
    `available_after` DECIMAL(20,8) NOT NULL COMMENT '变动后可用余额',
    `frozen_before` DECIMAL(20,8) NOT NULL COMMENT '变动前冻结金额',
    `frozen_after` DECIMAL(20,8) NOT NULL COMMENT '变动后冻结金额',
    `related_order_id` VARCHAR(64) COMMENT '关联订单ID',
    `related_trade_id` VARCHAR(64) COMMENT '关联成交ID',
    `remark` TEXT COMMENT '备注信息',
    `extra_data` JSON COMMENT '扩展数据',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_trace_id` (`trace_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_account_id` (`account_id`),
    INDEX `idx_event_time` (`event_time`),
    INDEX `idx_currency` (`currency`),
    INDEX `idx_change_type` (`change_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户资金变动业务日志表';

-- 风控触发日志表
CREATE TABLE IF NOT EXISTS `business_log_risk_control` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '追踪ID',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
    `event_time` TIMESTAMP NOT NULL COMMENT '事件发生时间',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `account_id` BIGINT NOT NULL COMMENT '账户ID',
    `strategy_id` VARCHAR(64) COMMENT '策略ID',
    `risk_type` VARCHAR(50) NOT NULL COMMENT '风控类型(POSITION_LIMIT/ORDER_LIMIT/LOSS_LIMIT/BLACKLIST)',
    `risk_level` VARCHAR(20) NOT NULL COMMENT '风险等级(LOW/MEDIUM/HIGH/CRITICAL)',
    `trigger_value` VARCHAR(200) NOT NULL COMMENT '触发值',
    `threshold_value` VARCHAR(200) NOT NULL COMMENT '阈值',
    `action_taken` VARCHAR(50) NOT NULL COMMENT '采取的行动(BLOCK/WARN/REDUCE)',
    `related_order_id` VARCHAR(64) COMMENT '关联订单ID',
    `related_symbol` VARCHAR(32) COMMENT '关联交易对',
    `description` TEXT COMMENT '详细描述',
    `remark` TEXT COMMENT '备注信息',
    `extra_data` JSON COMMENT '扩展数据',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_trace_id` (`trace_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_account_id` (`account_id`),
    INDEX `idx_event_time` (`event_time`),
    INDEX `idx_risk_type` (`risk_type`),
    INDEX `idx_risk_level` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控触发业务日志表';

-- 策略状态变更日志表
CREATE TABLE IF NOT EXISTS `business_log_strategy_status` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '追踪ID',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
    `event_time` TIMESTAMP NOT NULL COMMENT '事件发生时间',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `account_id` BIGINT NOT NULL COMMENT '账户ID',
    `strategy_id` VARCHAR(64) NOT NULL COMMENT '策略ID',
    `strategy_name` VARCHAR(200) COMMENT '策略名称',
    `status_before` VARCHAR(50) NOT NULL COMMENT '变更前状态',
    `status_after` VARCHAR(50) NOT NULL COMMENT '变更后状态',
    `change_reason` VARCHAR(200) COMMENT '变更原因',
    `performance_data` JSON COMMENT '性能数据',
    `position_data` JSON COMMENT '持仓数据',
    `running_parameters` JSON COMMENT '运行参数',
    `description` TEXT COMMENT '详细描述',
    `remark` TEXT COMMENT '备注信息',
    `extra_data` JSON COMMENT '扩展数据',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_trace_id` (`trace_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_account_id` (`account_id`),
    INDEX `idx_strategy_id` (`strategy_id`),
    INDEX `idx_event_time` (`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='策略状态变更业务日志表';

-- 系统错误日志表
CREATE TABLE IF NOT EXISTS `business_log_system_error` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '追踪ID',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
    `event_time` TIMESTAMP NOT NULL COMMENT '事件发生时间',
    `user_id` BIGINT COMMENT '用户ID',
    `account_id` BIGINT COMMENT '账户ID',
    `strategy_id` VARCHAR(64) COMMENT '策略ID',
    `error_code` VARCHAR(50) NOT NULL COMMENT '错误代码',
    `error_type` VARCHAR(50) NOT NULL COMMENT '错误类型(API_ERROR/NETWORK_ERROR/DATABASE_ERROR/SYSTEM_ERROR)',
    `error_level` VARCHAR(20) NOT NULL COMMENT '错误等级(INFO/WARN/ERROR/FATAL)',
    `error_message` TEXT NOT NULL COMMENT '错误消息',
    `error_stack` TEXT COMMENT '错误堆栈',
    `related_order_id` VARCHAR(64) COMMENT '关联订单ID',
    `related_api` VARCHAR(200) COMMENT '关联API',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `resolved` BOOLEAN DEFAULT FALSE COMMENT '是否已解决',
    `description` TEXT COMMENT '详细描述',
    `remark` TEXT COMMENT '备注信息',
    `extra_data` JSON COMMENT '扩展数据',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_trace_id` (`trace_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_account_id` (`account_id`),
    INDEX `idx_event_time` (`event_time`),
    INDEX `idx_error_code` (`error_code`),
    INDEX `idx_error_type` (`error_type`),
    INDEX `idx_error_level` (`error_level`),
    INDEX `idx_resolved` (`resolved`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统错误业务日志表';

-- 业务日志汇总视图（可选）
CREATE OR REPLACE VIEW `business_log_summary` AS
SELECT 
    'ORDER' as log_type,
    id,
    trace_id,
    event_type,
    event_time,
    user_id,
    account_id,
    strategy_id,
    created_at
FROM business_log_order
UNION ALL
SELECT 
    'TRADE' as log_type,
    id,
    trace_id,
    event_type,
    event_time,
    user_id,
    account_id,
    strategy_id,
    created_at
FROM business_log_trade
UNION ALL
SELECT 
    'ACCOUNT_FUND' as log_type,
    id,
    trace_id,
    event_type,
    event_time,
    user_id,
    account_id,
    strategy_id,
    created_at
FROM business_log_account_fund
UNION ALL
SELECT 
    'RISK_CONTROL' as log_type,
    id,
    trace_id,
    event_type,
    event_time,
    user_id,
    account_id,
    strategy_id,
    created_at
FROM business_log_risk_control
UNION ALL
SELECT 
    'STRATEGY_STATUS' as log_type,
    id,
    trace_id,
    event_type,
    event_time,
    user_id,
    account_id,
    strategy_id,
    created_at
FROM business_log_strategy_status
UNION ALL
SELECT 
    'SYSTEM_ERROR' as log_type,
    id,
    trace_id,
    event_type,
    event_time,
    user_id,
    account_id,
    strategy_id,
    created_at
FROM business_log_system_error;

-- 添加表注释
ALTER TABLE `business_log_order` COMMENT = '订单业务日志表 - 记录订单相关事件';
ALTER TABLE `business_log_trade` COMMENT = '成交业务日志表 - 记录成交相关事件';
ALTER TABLE `business_log_account_fund` COMMENT = '账户资金变动业务日志表 - 记录资金变动事件';
ALTER TABLE `business_log_risk_control` COMMENT = '风控触发业务日志表 - 记录风控相关事件';
ALTER TABLE `business_log_strategy_status` COMMENT = '策略状态变更业务日志表 - 记录策略状态变更事件';
ALTER TABLE `business_log_system_error` COMMENT = '系统错误业务日志表 - 记录系统错误事件';