-- 删除 strategy 表 risk_control 列
-- 风险控制配置已迁移到 strategy_parameter 表，分组为：
--   position_risk（仓位控制）、add_position_config（加仓配置）、
--   exit_rules_config（出场规则）、dynamic_risk_engine（移动止损/止盈）
--
-- 执行前提：
--   1. 历史策略 risk_control 列中的 JSON 数据需先迁移到 strategy_parameter 表；
--   2. 前端保存策略时已不再写入 risk_control 列。
--
-- 注意：本脚本为不可逆操作，执行前请备份 strategy 表。

ALTER TABLE strategy DROP COLUMN risk_control;
