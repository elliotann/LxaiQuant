-- 添加夏普比率和卡玛比率字段到backtest_result表
-- 执行时间：2024年

ALTER TABLE backtest_result
ADD COLUMN sharpe_ratio DECIMAL(10,4) COMMENT '夏普比率',
ADD COLUMN calmar_ratio DECIMAL(10,4) COMMENT '卡玛比率';

-- 添加索引（可选，用于排序和查询）
CREATE INDEX idx_result_sharpe_ratio ON backtest_result(sharpe_ratio DESC);
CREATE INDEX idx_result_calmar_ratio ON backtest_result(calmar_ratio DESC);

