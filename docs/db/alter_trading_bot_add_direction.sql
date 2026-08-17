ALTER TABLE `trading_bot`
  ADD COLUMN `direction` VARCHAR(16) NOT NULL DEFAULT 'BOTH' COMMENT '做单方向: LONG-只做多, SHORT-只做空, BOTH-双向',
  ADD INDEX `idx_direction` (`direction`);
