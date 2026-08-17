-- RBAC 权限管理表
-- 前端可配置：角色、角色-权限映射、用户-角色映射均可在管理页面配置
-- 权限点由 PermissionRegistry 启动时自动注册，无需手动 INSERT

CREATE TABLE IF NOT EXISTS `permission` (
    `id`         INT          NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    `perm_code`  VARCHAR(100) NOT NULL COMMENT '权限编码（如 strategy:create）',
    `perm_name`  VARCHAR(100) NOT NULL COMMENT '权限名称（如 创建策略）',
    `module`     VARCHAR(50)  NOT NULL COMMENT '所属模块（如 策略管理）',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1-启用 0-禁用',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE IF NOT EXISTS `role` (
    `id`        INT          NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_code` VARCHAR(50)  NOT NULL COMMENT '角色编码（如 ADMIN/PREMIUM/BASIC）',
    `role_name` VARCHAR(100) NOT NULL COMMENT '角色名称（如 管理员）',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
    `status`    TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1-启用 0-禁用',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `role_permission` (
    `id`            INT NOT NULL AUTO_INCREMENT,
    `role_id`       INT NOT NULL COMMENT '角色ID',
    `permission_id` INT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

CREATE TABLE IF NOT EXISTS `user_role_rel` (
    `id`       INT          NOT NULL AUTO_INCREMENT,
    `user_id`  VARCHAR(50)  NOT NULL COMMENT '用户ID',
    `role_id`  INT          NOT NULL COMMENT '角色ID',
    `role_type` VARCHAR(20) DEFAULT NULL COMMENT '角色类型（冗余字段）',
    `created_at` DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- 预置角色
INSERT INTO `role` (`role_code`, `role_name`, `description`) VALUES
('ADMIN', '管理员', '拥有系统全部权限'),
('PREMIUM', '高级会员', '可使用高级功能'),
('BASIC', '普通用户', '基础功能权限');
