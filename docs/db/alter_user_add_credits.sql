ALTER TABLE `user`
    ADD COLUMN `credits_balance` INT NOT NULL DEFAULT 0 COMMENT '当前积分余额',
    ADD COLUMN `membership_level` VARCHAR(20) NOT NULL DEFAULT 'BASIC' COMMENT '会员等级：BASIC / PREMIUM / PRO',
    ADD COLUMN `membership_expire_time` DATETIME COMMENT '会员到期时间',
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';
