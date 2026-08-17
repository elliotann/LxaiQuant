-- ============================================================
-- 充值/支付订单
-- ============================================================
CREATE TABLE `payment_transaction` (
    `id`              VARCHAR(32)   NOT NULL COMMENT '订单ID',
    `user_id`         VARCHAR(32)   NOT NULL COMMENT '用户ID',
    `type`            VARCHAR(20)   NOT NULL COMMENT '订单类型：CREDITS_PACKAGE / SUBSCRIPTION',
    `plan_id`         INT           COMMENT '套餐/积分包ID',
    `amount_usdt`     DECIMAL(18,8) NOT NULL COMMENT '支付金额(USDT)',
    `payment_currency` VARCHAR(10)  NOT NULL DEFAULT 'USDT' COMMENT '支付币种',
    `payment_address` VARCHAR(100)  COMMENT '生成的收款地址（TRC20）',
    `memo`            VARCHAR(50)   COMMENT '链上Memo/Tag',
    `tx_id`           VARCHAR(100)  COMMENT '链上交易哈希',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / CONFIRMING / SUCCESS / EXPIRED / FAILED',
    `expire_at`       DATETIME      COMMENT '过期时间（生成后30分钟）',
    `completed_at`    DATETIME      COMMENT '完成时间',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_tx_id` (`tx_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单';

-- ============================================================
-- 会员订阅记录
-- ============================================================
CREATE TABLE `membership_subscription` (
    `id`               VARCHAR(32)  NOT NULL,
    `user_id`          VARCHAR(32)  NOT NULL COMMENT '用户ID',
    `plan_level`       VARCHAR(20)  NOT NULL COMMENT '订阅等级：PREMIUM / PRO',
    `billing_cycle`    VARCHAR(10)  NOT NULL COMMENT '计费周期：MONTHLY / YEARLY',
    `status`           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / EXPIRED / CANCELLED',
    `current_period_start` DATETIME NOT NULL COMMENT '当前周期开始',
    `current_period_end`   DATETIME NOT NULL COMMENT '当前周期结束',
    `next_billing_at`  DATETIME     COMMENT '下次扣费时间',
    `auto_renew`       TINYINT(1)   NOT NULL DEFAULT 1,
    `cancel_at_period_end` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员订阅记录';
