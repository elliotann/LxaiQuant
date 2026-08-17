-- 信号交替流水表（L1 特征工程层）
-- 主关联键使用 strategy_name（即 technical_signal.strategy_name，SignFactory.SignType 名），不关联 strategy.id
-- 说明：exit_* 与 space_pct / minutes_between 在配对前为空（pending 记录），配对后填充
CREATE TABLE IF NOT EXISTS `signal_alternate_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `strategy_name` varchar(100) NOT NULL COMMENT '策略标识（technical_signal.strategy_name，即 SignFactory.SignType 名）',
    `symbol` varchar(20) NOT NULL COMMENT '交易对',
    `timeframe` varchar(20) NOT NULL COMMENT '周期（CandlestickIntervalEnum.name()，如 OKXMIN5）',

    -- 开仓信息
    `entry_time` bigint NOT NULL COMMENT '开仓信号时间戳(毫秒)',
    `entry_price` decimal(30,10) NOT NULL COMMENT '开仓价',
    `entry_direction` varchar(10) NOT NULL COMMENT '开仓方向(LONG/SHORT)',
    `entry_signal_id` bigint NOT NULL COMMENT '开仓信号ID（关联 technical_signal.id）',

    -- 平仓信息（未配对时为空）
    `exit_time` bigint DEFAULT NULL COMMENT '平仓信号时间戳(毫秒)',
    `exit_price` decimal(30,10) DEFAULT NULL COMMENT '平仓价',
    `exit_direction` varchar(10) DEFAULT NULL COMMENT '平仓方向(LONG/SHORT)',
    `exit_signal_id` bigint DEFAULT NULL COMMENT '平仓信号ID（关联 technical_signal.id）',

    -- 核心衍生字段（配对后计算）
    `space_pct` decimal(10,4) DEFAULT NULL COMMENT '交替空间(%)：(exit_price - entry_price) / entry_price * 100',
    `minutes_between` int DEFAULT NULL COMMENT '间隔分钟数：(exit_time - entry_time) / 60000',

    -- 趋势序列号（由 SlidingWindow 计算后更新）
    `direction_sequence` int DEFAULT NULL COMMENT '连续同向序列号',

    -- 元数据
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    KEY `idx_strategy_name` (`strategy_name`),
    KEY `idx_strategy_symbol_timeframe` (`strategy_name`, `symbol`, `timeframe`),
    KEY `idx_entry_time` (`entry_time`),
    KEY `idx_direction_sequence` (`direction_sequence`),
    KEY `idx_entry_signal_id` (`entry_signal_id`),
    KEY `idx_exit_signal_id` (`exit_signal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信号交替流水表';
