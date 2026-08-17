-- 权重规则版本管理表
CREATE TABLE IF NOT EXISTS `weight_rule_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_id` BIGINT NOT NULL COMMENT '信号服务配置ID',
    `version` INT NOT NULL COMMENT '版本号',
    `config_json` TEXT COMMENT '权重规则配置JSON快照',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '变更说明',
    `created_by` VARCHAR(100) DEFAULT 'system' COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_config_id` (`config_id`),
    KEY `idx_config_version` (`config_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权重规则版本历史';
