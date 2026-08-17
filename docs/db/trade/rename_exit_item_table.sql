-- ============================================================
-- 迁移脚本: ai_trade_order_close_item → ai_trade_exit_item
-- 改动内容:
--   1. 改表名
--   2. 列重命名: buy_price→entry_price, sell_price→exit_price, sell_time→exit_time
--   3. 新增字段: batch_id, position_id, close_method
--   4. 新增索引
-- 执行前请备份: mysqldump ai_trade_order_close_item > backup.sql
-- ============================================================

-- ============================================================
-- 方案1: 新装环境直接建表
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_trade_exit_item` (
   `id` varchar(64) NOT NULL COMMENT '主键ID',
   `batch_id` varchar(64) NOT NULL COMMENT '所属平仓批次ID（关联 ai_trade_exit_batch.id）',
   `position_id` varchar(64) NOT NULL COMMENT '仓位ID（冗余，直接关联仓位）',
   `entry_sn` varchar(64) NOT NULL COMMENT '入场明细编号（关联 ai_trade_entry.entry_sn）',
   `closed_volume` decimal(30,10) DEFAULT NULL COMMENT '平仓数量',
   `status` varchar(32) DEFAULT NULL COMMENT '状态',
   `entry_price` decimal(30,10) DEFAULT NULL COMMENT '入场价格',
   `exit_price` decimal(30,10) DEFAULT NULL COMMENT '出场价格',
   `exit_time` datetime DEFAULT NULL COMMENT '出场时间',
   `income` decimal(30,10) DEFAULT NULL COMMENT '收益',
   `charge` decimal(30,10) DEFAULT NULL COMMENT '手续费',
   `close_method` varchar(16) DEFAULT NULL COMMENT '平仓方式：AUTO/MANUAL',
   `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
   `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
   `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
   `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
   `delete_flag` tinyint(1) DEFAULT '0' COMMENT '删除标志',
   PRIMARY KEY (`id`),
   KEY `idx_batch_id` (`batch_id`),
   KEY `idx_position_id` (`position_id`),
   KEY `idx_entry_sn` (`entry_sn`)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平仓明细表（原 ai_trade_order_close_item）';

-- ============================================================
-- 方案2: 已有旧表迁移（如已执行过 RENAME 或需要保留数据）
-- ============================================================
-- 如果表已存在但使用的是旧列名(buy_price/sell_price/sell_time)，执行以下迁移：
-- ALTER TABLE ai_trade_exit_item
--   CHANGE COLUMN `buy_price`  `entry_price` decimal(30,10) DEFAULT NULL COMMENT '入场价格',
--   CHANGE COLUMN `sell_price` `exit_price`  decimal(30,10) DEFAULT NULL COMMENT '出场价格',
--   CHANGE COLUMN `sell_time`  `exit_time`  datetime DEFAULT NULL COMMENT '出场时间',
--   ADD COLUMN `batch_id`    varchar(64) NOT NULL COMMENT '所属平仓批次ID' AFTER `id`,
--   ADD COLUMN `position_id` varchar(64) NOT NULL COMMENT '仓位ID' AFTER `batch_id`,
--   ADD COLUMN `close_method` varchar(16) DEFAULT NULL COMMENT '平仓方式：AUTO/MANUAL' AFTER `charge`,
--   ADD INDEX `idx_batch_id` (`batch_id`),
--   ADD INDEX `idx_position_id` (`position_id`),
--   ADD INDEX `idx_entry_sn` (`entry_sn`);
--
-- 注意: batch_id 和 position_id 为 NOT NULL，需确保迁移前数据已补全
