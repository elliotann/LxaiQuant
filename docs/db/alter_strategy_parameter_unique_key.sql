-- strategy_parameter 表唯一索引改为包含 group_name
-- 原有唯一键 uk_strategy_param (strategy_id, name) 不支持多个参数组使用相同参数名
-- 如 add_position_config/config 与 exit_rules_config/config 冲突
-- 新唯一键 uk_strategy_param (strategy_id, group_name, name)

ALTER TABLE strategy_parameter
    DROP INDEX uk_strategy_param,
    ADD UNIQUE KEY uk_strategy_param (strategy_id, group_name, name);
