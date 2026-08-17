-- 添加扩展绩效指标字段到backtest_result表
-- 执行时间：2026年

ALTER TABLE backtest_result
ADD COLUMN annual_return DECIMAL(14,6) COMMENT '年化收益率',
ADD COLUMN volatility DECIMAL(14,6) COMMENT '年化波动率',
ADD COLUMN sortino_ratio DECIMAL(14,4) COMMENT '索提诺比率',
ADD COLUMN average_win DECIMAL(18,4) COMMENT '平均盈利额',
ADD COLUMN average_loss DECIMAL(18,4) COMMENT '平均亏损额',
ADD COLUMN largest_win_trade DECIMAL(18,4) COMMENT '最大单笔盈利',
ADD COLUMN largest_loss_trade DECIMAL(18,4) COMMENT '最大单笔亏损',
ADD COLUMN drawdown_series TEXT COMMENT '回撤序列JSON';

-- 添加索引
CREATE INDEX idx_result_annual_return ON backtest_result(annual_return DESC);
CREATE INDEX idx_result_sortino_ratio ON backtest_result(sortino_ratio DESC);
