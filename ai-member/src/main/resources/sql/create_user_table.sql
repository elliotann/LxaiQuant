-- 创建用户表
CREATE TABLE IF NOT EXISTS `user` (
    `user_id` VARCHAR(32) NOT NULL COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `email` VARCHAR(100) COMMENT '邮箱地址',
    `phone` VARCHAR(20) COMMENT '手机号码',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
    `role` VARCHAR(20) NOT NULL DEFAULT 'BASIC' COMMENT '用户角色：ADMIN, PREMIUM, BASIC',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态：ACTIVE, INACTIVE, SUSPENDED',
    `preferences` TEXT COMMENT '用户偏好配置（JSON）',
    `security_config` TEXT COMMENT '安全配置（JSON）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_login_time` DATETIME COMMENT '最后登录时间',
    `delete_flag` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',

    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`),
    INDEX `idx_role` (`role`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 初始化管理员用户（密码：admin123）
INSERT INTO `user` (
    `user_id`, `username`, `email`, `phone`, `password_hash`,
    `role`, `status`, `create_time`, `update_time`
) VALUES (
    'U000000000000001',
    'admin',
    'admin@chain.ai',
    '13800000000',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lbdxp6hqHp5FqZrPq', -- admin123
    'ADMIN',
    'ACTIVE',
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 创建交易账户表
CREATE TABLE IF NOT EXISTS `vdr_member_third_account` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键ID',
    `member_id` VARCHAR(32) COMMENT '用户ID',
    `member_platform` VARCHAR(50) COMMENT '交易平台',
    `account_name` VARCHAR(100) COMMENT '账户名称',
    `uid` VARCHAR(50) COMMENT '交易所用户ID',
    `api_key` VARCHAR(255) COMMENT 'API Key',
    `api_secret` VARCHAR(255) COMMENT 'API Secret',
    `passphrase` VARCHAR(255) COMMENT 'Passphrase（某些交易所需要）',
    `api_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'API是否启用：0-禁用，1-启用',
    `balances` TEXT COMMENT '交易所余额（JSON格式）',
    `allocations` TEXT COMMENT '已分配额度（JSON格式）',
    `bind_status` VARCHAR(20) NOT NULL DEFAULT 'UNBIND' COMMENT '绑定状态：BIND-已绑定，UNBIND-未绑定',
    `partner_id` VARCHAR(50) COMMENT '合作商ID',
    `last_sync_time` DATETIME COMMENT '最后同步时间',
    `create_by` VARCHAR(32) COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(32) COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `delete_flag` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',

    PRIMARY KEY (`id`),
    INDEX `idx_member_id` (`member_id`),
    INDEX `idx_member_platform` (`member_platform`),
    INDEX `idx_bind_status` (`bind_status`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方交易账户表';

-- 初始化一些测试交易账户数据
INSERT INTO `vdr_member_third_account` (
    `id`, `member_id`, `member_platform`, `account_name`, `uid`, `api_key`, `api_secret`,
    `bind_status`, `create_time`, `update_time`
) VALUES (
    'TA000000000000001',
    'U000000000000001',
    'BINANCE',
    'Binance主账户',
    '12345678',
    'demo_api_key_1',
    'demo_api_secret_1',
    'BIND',
    NOW(),
    NOW()
), (
    'TA000000000000002',
    'U000000000000001',
    'OKX',
    'OKX交易账户',
    '87654321',
    'demo_api_key_2',
    'demo_api_secret_2',
    'BIND',
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE `update_time` = NOW();