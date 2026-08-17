package com.chain.ai.trade.order.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 机器人订单收益报表 - 按日/月维度单期数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotOrderReportPeriodVO {

    /**
     * 期键：按日为 yyyy-MM-dd，按月为 yyyy-MM
     */
    private String periodKey;

    /**
     * 订单数
     */
    private Integer orderCount;

    /**
     * 收益合计
     */
    private BigDecimal totalIncome;

    /**
     * 成本(手续费)合计
     */
    private BigDecimal totalCharge;

    /**
     * 净利润 = totalIncome - totalCharge
     */
    private BigDecimal netProfit;

    /**
     * 止盈金额合计（仅统计止盈订单的净利润）
     */
    private BigDecimal takeProfitAmount;

    /**
     * 止损金额合计（仅统计止损订单的净亏损，返回为正值）
     */
    private BigDecimal stopLossAmount;
}
