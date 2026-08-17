CREATE TABLE `bot_parameter` (
  `id`          bigint       AUTO_INCREMENT,
  `bot_id`      varchar(32)  NOT NULL COMMENT '机器人ID',
  `group_name`  varchar(64)  NOT NULL COMMENT '分组名：config/positionRisk/exitRules',
  `name`        varchar(128) NOT NULL COMMENT '参数名（与group_name同名，存单条完整JSON）',
  `value`       json         DEFAULT NULL COMMENT '参数值（完整JSON对象）',
  `created_at`  datetime     DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bot_param` (`bot_id`, `group_name`, `name`),
  KEY `idx_bot_group` (`bot_id`, `group_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器人参数表';
