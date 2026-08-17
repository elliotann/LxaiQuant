-- ============================================================
-- 充值地址表（每个用户每个业务类型一个专属TRC20地址）
-- ============================================================
CREATE TABLE `recharge_address` (
    `id`               VARCHAR(32)   NOT NULL COMMENT '主键ID',
    `user_id`          VARCHAR(32)   NOT NULL COMMENT '用户ID',
    `recharge_address` VARCHAR(100)  NOT NULL COMMENT 'TRC20收款地址',
    `private_key_enc`  VARCHAR(512)  NOT NULL COMMENT '私钥（AES加密存储）',
    `business_type`    VARCHAR(20)   NOT NULL DEFAULT 'RECHARGE' COMMENT '业务类型：RECHARGE / MEMBER_VIP',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_user_business` (`user_id`, `business_type`),
    INDEX `idx_recharge_address` (`recharge_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值地址';
