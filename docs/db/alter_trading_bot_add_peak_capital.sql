-- trading_bot 表新增 peak_capital 字段
-- 历史最高权益，用于计算最大回撤
-- 仅在机器人的 current_capital 创历史新高时更新，无需每日刷新
ALTER TABLE `trading_bot`
ADD COLUMN `peak_capital` decimal(20,4) DEFAULT NULL COMMENT '历史最高权益，用于计算最大回撤';
