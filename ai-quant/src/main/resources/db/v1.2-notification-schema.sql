-- ============================================================
-- 通知推送模块 - 数据库迁移脚本 v1.2
-- 适用数据库：MySQL 5.7+
-- 创建表：notification_config（通知配置）、site_message（站内信）、notification_log（通知日志）
-- ============================================================

-- 通知配置表
CREATE TABLE IF NOT EXISTS notification_config (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID（雪花算法）',
    user_id VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '用户ID',
    channel VARCHAR(50) NOT NULL COMMENT '通知渠道（site_msg/email/telegram/sms/app）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    config_json TEXT COMMENT '渠道配置（JSON）',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT(1) DEFAULT 0 COMMENT '删除标志',
    INDEX idx_user_id (user_id),
    INDEX idx_channel (channel),
    UNIQUE KEY uk_user_channel (user_id, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知配置表';

-- 站内信表
CREATE TABLE IF NOT EXISTS site_message (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID（雪花算法）',
    user_id VARCHAR(64) NOT NULL DEFAULT '1' COMMENT '接收用户ID',
    type VARCHAR(30) NOT NULL DEFAULT 'system' COMMENT '消息类型（trade/risk/system/strategy）',
    title VARCHAR(200) DEFAULT '' COMMENT '消息标题',
    content TEXT COMMENT '消息内容',
    severity VARCHAR(20) DEFAULT 'info' COMMENT '严重级别（info/warning/critical）',
    is_read TINYINT(1) DEFAULT 0 COMMENT '是否已读',
    read_at DATETIME COMMENT '阅读时间',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT(1) DEFAULT 0 COMMENT '删除标志',
    INDEX idx_user_id (user_id),
    INDEX idx_user_read (user_id, is_read),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内信表';

-- 通知日志表
CREATE TABLE IF NOT EXISTS notification_log (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID（雪花算法）',
    user_id VARCHAR(64) DEFAULT '1' COMMENT '用户ID',
    channel VARCHAR(50) NOT NULL COMMENT '通知渠道',
    type VARCHAR(30) DEFAULT '' COMMENT '通知类型',
    title VARCHAR(200) DEFAULT '' COMMENT '通知标题',
    content TEXT COMMENT '通知内容',
    status VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '发送状态（SUCCESS/FAILED）',
    error_msg VARCHAR(500) DEFAULT '' COMMENT '错误信息',
    sent_at DATETIME COMMENT '发送时间',
    create_by VARCHAR(64) DEFAULT '' COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) DEFAULT '' COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT(1) DEFAULT 0 COMMENT '删除标志',
    INDEX idx_user_id (user_id),
    INDEX idx_channel (channel),
    INDEX idx_sent_at (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知日志表';
