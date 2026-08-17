-- 添加 simulated 字段到 vdr_member_third_account 表（简单版本）
-- 执行时间：2025-01-25
-- 说明：添加是否模拟账户（沙箱环境）字段
--       true: 模拟账户，使用沙箱环境进行交易操作
--       false: 真实账户，使用真实环境进行交易操作

ALTER TABLE vdr_member_third_account
ADD COLUMN simulated TINYINT(1) DEFAULT 0 COMMENT '是否模拟账户（沙箱环境）：1-是，0-否';

