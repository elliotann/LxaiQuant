ALTER TABLE signal_service_config
    ADD COLUMN weight_rules_json TEXT DEFAULT NULL COMMENT '权重规则配置(JSON)';
