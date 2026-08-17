package com.chain.ai.trade.engine.signal.entity.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 信号仪表板统计视图对象
 */
@Data
public class SignalDashboardVO {

    // === 技术信号统计 ===
    @ApiModelProperty(value = "今日技术信号总数")
    private Integer todayTechnicalSignals;

    @ApiModelProperty(value = "本周技术信号总数")
    private Integer weekTechnicalSignals;

    @ApiModelProperty(value = "技术信号成功率")
    private BigDecimal technicalSignalSuccessRate;

    // === 交易信号统计 ===
    @ApiModelProperty(value = "待执行交易信号数")
    private Integer pendingTradeSignals;

    @ApiModelProperty(value = "执行中交易信号数")
    private Integer executingTradeSignals;

    @ApiModelProperty(value = "今日完成交易信号数")
    private Integer todayCompletedSignals;

    @ApiModelProperty(value = "本周完成交易信号数")
    private Integer weekCompletedSignals;

    // === 绩效统计 ===
    @ApiModelProperty(value = "今日总盈亏")
    private BigDecimal todayTotalPnl;

    @ApiModelProperty(value = "今日盈亏百分比")
    private BigDecimal todayPnlPercentage;

    @ApiModelProperty(value = "本周总盈亏")
    private BigDecimal weekTotalPnl;

    @ApiModelProperty(value = "本周盈亏百分比")
    private BigDecimal weekPnlPercentage;

    @ApiModelProperty(value = "胜率")
    private BigDecimal winRate;

    @ApiModelProperty(value = "平均盈利")
    private BigDecimal avgProfit;

    @ApiModelProperty(value = "平均亏损")
    private BigDecimal avgLoss;

    @ApiModelProperty(value = "盈亏比")
    private BigDecimal profitLossRatio;

    @ApiModelProperty(value = "夏普比率")
    private BigDecimal sharpeRatio;

    @ApiModelProperty(value = "最大回撤")
    private BigDecimal maxDrawdown;

    // === 信号分布统计 ===
    @ApiModelProperty(value = "多头信号比例")
    private BigDecimal bullishSignalRatio;

    @ApiModelProperty(value = "空头信号比例")
    private BigDecimal bearishSignalRatio;

    @ApiModelProperty(value = "中性信号比例")
    private BigDecimal neutralSignalRatio;

    // === 风险指标 ===
    @ApiModelProperty(value = "当前风险等级")
    private String currentRiskLevel;

    @ApiModelProperty(value = "活跃仓位数量")
    private Integer activePositions;

    @ApiModelProperty(value = "总仓位比例")
    private BigDecimal totalPositionRatio;

    @ApiModelProperty(value = "今日最大亏损")
    private BigDecimal todayMaxLoss;

    @ApiModelProperty(value = "风控警告数量")
    private Integer riskWarnings;
}
