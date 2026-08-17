-- ============================================================
-- AI 智能过滤功能 - 数据库迁移脚本 v1.0
-- 适用数据库：MySQL 5.7+
-- 说明：请先确认列不存在再执行，重复执行会报错
-- 回滚：ALTER TABLE strategy DROP COLUMN auto_signal;
--       ALTER TABLE technical_signal DROP COLUMN ai_filter_result;
-- ============================================================

ALTER TABLE strategy
    ADD COLUMN auto_signal TEXT
    COMMENT 'AI 智能过滤配置（JSON）：{enabled, allowThreshold, rejectThreshold, maxStrength}';

ALTER TABLE technical_signal
    ADD COLUMN ai_filter_result TEXT
    COMMENT 'AI 过滤结果（JSON）：{decision, score, llmDecision, summary}';
