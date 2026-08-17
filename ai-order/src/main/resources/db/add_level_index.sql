ALTER TABLE ai_trade_exit_batch
ADD COLUMN level_index INT DEFAULT NULL COMMENT '分批止盈触发级别索引';

