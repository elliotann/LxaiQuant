package com.chain.ai.trade.engine2.backtest;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 回测请求参数 */
@Data
public class BacktestRequest {

    /** 机器人 ID（非必须，不传则直接使用下方参数） */
    private String robotId;

    /** 交易对 */
    private String symbol;

    /** 初始总资产（USDT），对应机器人当前资金，为空则取机器人配置或默认值 */
    private BigDecimal initialCapital;

    /** 每笔开仓的分配资金（USDT），对应基础仓位资金，为空则取机器人配置或默认值 */
    private BigDecimal positionAmount;

    /** 杠杆倍数，为空则取机器人配置或默认值 */
    private Integer leverage;

    /** 滑点百分比（如 0.001 = 0.1%），为空则取机器人配置或默认值 */
    private Double slippage;

    /** 手续费率（如 0.001 表示 0.1%），为空则不计算手续费 */
    private BigDecimal commissionRate;

    /** 策略 ID（用于加载策略级加减仓配置兜底） */
    private String strategyId;

    /** K 线周期 */
    private String interval;

    /** 回测开始时间 */
    private LocalDateTime startDate;

    /** 回测结束时间 */
    private LocalDateTime endDate;
}
