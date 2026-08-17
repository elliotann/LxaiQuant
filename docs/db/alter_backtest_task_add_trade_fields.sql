-- backtest_task 表添加交易字段（回测落库到订单系统使用）
ALTER TABLE backtest_task
    ADD COLUMN robot_id   VARCHAR(50)  DEFAULT NULL COMMENT '机器人ID'  AFTER config,
    ADD COLUMN member_id  VARCHAR(50)  DEFAULT NULL COMMENT '会员ID'    AFTER robot_id,
    ADD COLUMN account_id BIGINT       DEFAULT NULL COMMENT '账户ID'    AFTER member_id,
    ADD COLUMN leverage   INT          DEFAULT 1    COMMENT '杠杆倍数'  AFTER account_id;
