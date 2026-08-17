-- ===========================================
-- 机器学习模块（ML Enhancement Module）表结构
-- 版本：v1.0
-- 更新日期：2026-05-14
-- ===========================================

-- 模型元数据表
CREATE TABLE IF NOT EXISTS `ml_models` (
    `id` VARCHAR(32) PRIMARY KEY,
    `symbol` VARCHAR(20) NOT NULL COMMENT '交易对',
    `model_type` VARCHAR(20) NOT NULL COMMENT '模型类型: DIRECTION, VOLATILITY, REGIME',
    `version` INT NOT NULL COMMENT '版本号',
    `file_path` VARCHAR(500) NOT NULL COMMENT '模型文件的绝对路径',
    `file_size` BIGINT COMMENT '文件大小（字节）',
    `md5_checksum` VARCHAR(64) COMMENT '文件MD5',
    `accuracy` DECIMAL(5,4) COMMENT '验证集准确率',
    `recall` DECIMAL(5,4) COMMENT '召回率',
    `precision` DECIMAL(5,4) COMMENT '精确率',
    `f1_score` DECIMAL(5,4) COMMENT 'F1分数',
    `feature_importance` JSON COMMENT '特征重要性映射',
    `confusion_matrix` JSON COMMENT '混淆矩阵 {tp, fp, fn, tn}',
    `hyperparams` JSON COMMENT '超参数',
    `training_data_range` JSON COMMENT '训练数据时间范围 {start, end}',
    `training_duration_ms` BIGINT COMMENT '训练耗时（毫秒）',
    `is_active` TINYINT(1) DEFAULT 0 COMMENT '当前活跃版本',
    `trained_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `trained_by` VARCHAR(64) COMMENT '触发训练的用户',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` VARCHAR(64) COMMENT '更新人',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_flag` TINYINT(1) DEFAULT 0,
    INDEX idx_symbol_type_active (symbol, model_type, is_active),
    UNIQUE KEY uk_symbol_type_version (symbol, model_type, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ML模型元数据';

-- 训练任务记录表
CREATE TABLE IF NOT EXISTS `ml_training_jobs` (
    `id` VARCHAR(32) PRIMARY KEY,
    `symbol` VARCHAR(20) NOT NULL COMMENT '交易对',
    `model_type` VARCHAR(20) NOT NULL COMMENT '模型类型',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    `accuracy` DECIMAL(5,4) COMMENT '训练结果准确率',
    `error_msg` TEXT COMMENT '错误信息',
    `start_time` TIMESTAMP NULL COMMENT '开始时间',
    `end_time` TIMESTAMP NULL COMMENT '结束时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_by` VARCHAR(64) COMMENT '更新人',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_flag` TINYINT(1) DEFAULT 0,
    INDEX idx_symbol (symbol),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ML训练任务记录';
