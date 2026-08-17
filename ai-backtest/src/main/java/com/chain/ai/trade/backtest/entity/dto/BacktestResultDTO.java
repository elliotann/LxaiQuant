package com.chain.ai.trade.backtest.entity.dto;

import com.chain.ai.trade.common.entity.dto.BacktestTradeRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回测结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResultDTO {

    private String taskId;

    // 策略信息
    private String strategyName;

    // 核心绩效指标
    private BigDecimal totalReturn;
    private BigDecimal maxDrawdown;
    private BigDecimal winRate;
    private Integer totalTrades;
    private Integer winningTrades;
    private BigDecimal profitFactor;

    // 其他指标
    private BigDecimal finalValue;
    private BigDecimal sharpeRatio;        // 夏普比率
    private BigDecimal calmarRatio;       // 卡玛比率
    private BigDecimal totalCost;          // 总成本（交易成本+持仓成本）

    // 扩展指标
    private BigDecimal annualReturn;       // 年化收益率
    private BigDecimal volatility;          // 年化波动率
    private BigDecimal sortinoRatio;       // 索提诺比率
    private BigDecimal averageWin;          // 平均盈利额
    private BigDecimal averageLoss;         // 平均亏损额
    private BigDecimal largestWinTrade;     // 最大单笔盈利
    private BigDecimal largestLossTrade;    // 最大单笔亏损
    private Integer maxConsecutiveWins;      // 最大连续盈利次数
    private Integer maxConsecutiveLosses;    // 最大连续亏损次数
    private Double avgTradeDuration;         // 平均持仓时间（天）

    // 交易记录（从backtest_trade_record表查询）
    private List<BacktestTradeRecord> tradeRecords;

    // 权益曲线（JSON格式存储，格式：[{index: 0, equity: 10000}, {index: 1, equity: 10500}, ...]）
    // 注意：权益曲线已迁移到 backtest_equity_curve 表，此字段保留用于向后兼容
    private String equityCurve;

    // 回撤序列（JSON格式存储，格式：[{index: 0, drawdown: 0}, {index: 1, drawdown: -0.05}, ...]）
    private String drawdownSeries;

    private LocalDateTime calculatedAt;
}
