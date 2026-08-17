-- 添加总成本字段到backtest_result表
-- 执行时间：2024年

ALTER TABLE backtest_result
ADD COLUMN total_cost DECIMAL(18,4) DEFAULT 0.0 COMMENT '总成本（交易成本+持仓成本）';

-- 添加索引（可选，用于排序和查询）
CREATE INDEX idx_result_total_cost ON backtest_result(total_cost DESC);

