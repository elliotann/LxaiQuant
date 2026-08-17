package com.chain.ai.trade.order.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 机器人订单收益报表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RobotOrderReportVO {

    /**
     * 汇总：订单数
     */
    private Integer orderCount;

    /**
     * 汇总：盈利订单数
     * 定义：单笔订单净利润（income - charge）> 0 视为盈利订单
     */
    private Integer profitOrderCount;

    /**
     * 汇总：总收益
     */
    private BigDecimal totalIncome;

    /**
     * 汇总：总成本(手续费)
     */
    private BigDecimal totalCharge;

    /**
     * 汇总：净利润
     */
    private BigDecimal netProfit;

    /**
     * 按日/月维度的明细
     */
    private List<RobotOrderReportPeriodVO> items;

    /**
     * 权益曲线数据（含回撤）
     */
    private List<EquityCurvePoint> equityCurve;
}
