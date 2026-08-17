-- 添加 simulated 字段到 vdr_member_third_account 表
-- 执行时间：2025-01-25
-- 说明：添加是否模拟账户（沙箱环境）字段
--       true: 模拟账户，使用沙箱环境进行交易操作
--       false: 真实账户，使用真实环境进行交易操作

-- 检查列是否存在，如果不存在则添加
SET @dbname = DATABASE();
SET @tablename = 'vdr_member_third_account';
SET @columnname = 'simulated';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' TINYINT(1) DEFAULT 0 COMMENT ''是否模拟账户（沙箱环境）：1-是，0-否''')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 或者直接执行（如果确定列不存在）：
-- ALTER TABLE vdr_member_third_account
-- ADD COLUMN simulated TINYINT(1) DEFAULT 0 COMMENT '是否模拟账户（沙箱环境）：1-是，0-否';

