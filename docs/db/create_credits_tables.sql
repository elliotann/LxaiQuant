-- ============================================================
-- 积分变动日志
-- ============================================================
CREATE TABLE `credits_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     VARCHAR(32)  NOT NULL COMMENT '用户ID',
    `amount`      INT          NOT NULL COMMENT '变动数量（正=增加，负=扣减）',
    `balance_after` INT        NOT NULL COMMENT '变动后余额',
    `type`        VARCHAR(30)  NOT NULL COMMENT '变动类型：PURCHASE / API_COST / SUBSCRIPTION_GRANT / ADMIN_ADJUST / EXPIRY',
    `ref_id`      VARCHAR(64)  COMMENT '关联单号或接口标识',
    `description` VARCHAR(255) COMMENT '描述',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_type` (`type`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分变动日志';

-- ============================================================
-- API 计费配置表
-- ============================================================
CREATE TABLE `api_cost_config` (
    `id`          INT          NOT NULL AUTO_INCREMENT,
    `api_name`    VARCHAR(100) NOT NULL COMMENT '接口标识，如 AI_GENERATE_STRATEGY',
    `cost_credits` INT         NOT NULL COMMENT '单次调用所需积分',
    `description` VARCHAR(255) COMMENT '描述',
    `enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用计费',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_name` (`api_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API计费配置';

-- ============================================================
-- 会员权益配置
-- ============================================================
CREATE TABLE `membership_benefit` (
    `id`                     INT         NOT NULL AUTO_INCREMENT,
    `level`                  VARCHAR(20) NOT NULL COMMENT '会员等级：BASIC / PREMIUM / PRO',
    `monthly_credits`        INT         NOT NULL DEFAULT 0 COMMENT '每月赠送积分',
    `max_bots`               INT         NOT NULL DEFAULT 2 COMMENT '最大机器人数量',
    `max_strategies`         INT         NOT NULL DEFAULT 10 COMMENT '最大策略数量',
    `max_backtests_per_day`  INT         NOT NULL DEFAULT 3 COMMENT '每日回测次数',
    `max_ai_analysis_per_day` INT        NOT NULL DEFAULT 0 COMMENT '每日AI分析次数',
    `allow_custom_factor`    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否允许自定义因子',
    `allow_ml_training`      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否允许ML训练',
    `allow_api_access`       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否允许API访问',
    `priority_support`       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否优先客服',
    `price_monthly_usdt`     DECIMAL(10,2) COMMENT '月付价格(USDT)',
    `price_yearly_usdt`      DECIMAL(10,2) COMMENT '年付价格(USDT)',
    `created_at`             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员权益配置';

-- ============================================================
-- 积分包配置
-- ============================================================
CREATE TABLE `credit_package` (
    `id`          INT            NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(100)   NOT NULL COMMENT '积分包名称，如 基础包',
    `credits`     INT            NOT NULL COMMENT '积分数量',
    `price_usdt`  DECIMAL(10,2)  NOT NULL COMMENT '价格(USDT)',
    `bonus_credits` INT          NOT NULL DEFAULT 0 COMMENT '赠送积分',
    `sort_order`  INT            NOT NULL DEFAULT 0 COMMENT '排序',
    `enabled`     TINYINT(1)     NOT NULL DEFAULT 1,
    `created_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分包配置';

-- ============================================================
-- 初始化：API 计费配置
-- ============================================================
INSERT INTO `api_cost_config` (`api_name`, `cost_credits`, `description`, `enabled`) VALUES
('AI_GENERATE_STRATEGY', 100, 'AI生成策略', 1),
('AI_ANALYZE_MARKET', 50, 'AI分析市场', 1),
('BACKTEST_RUN', 10, '运行回测', 1),
('FACTOR_MINING_RUN', 200, '因子挖掘', 1),
('ML_TRAINING_RUN', 500, 'ML模型训练', 1);

-- ============================================================
-- 初始化：会员权益配置
-- ============================================================
INSERT INTO `membership_benefit` (`level`, `monthly_credits`, `max_bots`, `max_strategies`, `max_backtests_per_day`, `max_ai_analysis_per_day`, `allow_custom_factor`, `allow_ml_training`, `allow_api_access`, `priority_support`, `price_monthly_usdt`, `price_yearly_usdt`) VALUES
('BASIC', 0, 2, 10, 3, 0, 0, 0, 0, 0, 0.00, 0.00),
('PREMIUM', 500, 10, 50, 20, 10, 1, 1, 1, 0, 29.99, 299.99),
('PRO', 2000, 50, 200, 100, 50, 1, 1, 1, 1, 99.99, 999.99);

-- ============================================================
-- 初始化：积分包配置
-- ============================================================
INSERT INTO `credit_package` (`name`, `credits`, `price_usdt`, `bonus_credits`, `sort_order`, `enabled`) VALUES
('入门包', 1000, 9.99, 0, 1, 1),
('标准包', 5000, 39.99, 500, 2, 1),
('专业包', 15000, 99.99, 2500, 3, 1),
('旗舰包', 50000, 299.99, 10000, 4, 1);
