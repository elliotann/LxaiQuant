package com.chain.ai.trade.engine.model;

import lombok.Builder;
import lombok.Data;

/**
 * 回测绩效指标
 */
@Data
@Builder
public class PerformanceMetrics {
    private double totalReturn;      // 总收益率
    private double maxDrawdown;      // 最大回撤
    private double winRate;         // 胜率
    private long totalTrades;        // 总交易次数
    private long profitableTrades;   // 盈利交易次数
    private double profitLossRatio;  // 盈亏比：平均盈利额 ÷ 平均亏损额
    private double initialAmount;    // 初始本金
    private double totalCost;       // 总成本（交易成本+持仓成本）
    private double sharpeRatio;     // 夏普比率
    private double annualReturn;    // 年化收益率
    private double volatility;      // 年化波动率
    private double sortinoRatio;    // 索提诺比率
    private double averageWin;      // 平均盈利额
    private double averageLoss;     // 平均亏损额
    private double largestWinTrade; // 最大单笔盈利
    private double largestLossTrade; // 最大单笔亏损
    private int maxConsecutiveWins;      // 最大连续盈利次数
    private int maxConsecutiveLosses;    // 最大连续亏损次数
    private double averageHoldingPeriod; // 平均持仓时间（天）

    public String toFormattedString() {
        double actualProfit = totalReturn * initialAmount;
        String profitStr = actualProfit >= 0 ?
                String.format("(+%d)", (int) Math.round(actualProfit)) :
                String.format("(%d)", (int) Math.round(actualProfit));

        return String.format(
                "总收益率: %.2f%% %s, 最大回撤: %.2f%%, 胜率: %.1f%%, " +
                        "总交易: %d, 盈利交易: %d, 盈亏比: %.2f",
                totalReturn * 100,
                profitStr,
                maxDrawdown * 100,
                winRate * 100,
                totalTrades,
                profitableTrades,
                profitLossRatio
        );
    }
}
