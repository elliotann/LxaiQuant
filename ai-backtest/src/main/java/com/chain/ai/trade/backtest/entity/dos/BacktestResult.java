package com.chain.ai.trade.backtest.entity.dos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回测结果表 - 存储核心绩效指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("backtest_result")
public class BacktestResult {

    /**
     * 关联标识
     */
    @TableId
    private String taskId;

    /**
     * 策略信息
     */
    private String strategyName;

    /**
     * 核心绩效指标
     */
    private BigDecimal totalReturn;        // 总收益率
    private BigDecimal maxDrawdown;        // 最大回撤
    private BigDecimal winRate;            // 胜率
    private Integer totalTrades;           // 总交易次数
    private Integer winningTrades;         // 盈利交易数
    private BigDecimal profitFactor;       // 盈亏比
    private BigDecimal finalValue;         // 最终价值
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
    private Integer maxConsecutiveWins;     // 最大连续盈利次数
    private Integer maxConsecutiveLosses;   // 最大连续亏损次数
    private Double avgTradeDuration;        // 平均持仓时间（天）

    /**
     * 回撤序列（JSONB格式存储）
     * 注意：权益曲线已迁移到 backtest_equity_curve 表
     */
    private String drawdownSeries;

    /**
     * 时间戳
     */
    private LocalDateTime calculatedAt;
}
