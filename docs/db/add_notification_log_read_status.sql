-- notification_log 表增加已读状态字段
ALTER TABLE `notification_log`
    ADD COLUMN `is_read`   tinyint(1) DEFAULT 0  COMMENT '是否已读 0-未读 1-已读' AFTER `sent_at`,
    ADD COLUMN `read_at`   datetime     DEFAULT NULL COMMENT '读取时间'           AFTER `is_read`;
