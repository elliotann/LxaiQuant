package com.chain.ai.trade.engine.signal.entity.dto;

import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.engine.signal.entity.constants.TradeStatus;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 交易信号DTO（用于API传输）
 */
@Data
public class TradeSignalDTO {

    @ApiModelProperty(value = "信号ID")
    private Long id;

    @ApiModelProperty(value = "技术信号ID")
    private Long technicalSignalId;

    @ApiModelProperty(value = "技术信号摘要")
    private String technicalSignalBrief;

    @ApiModelProperty(value = "交易对")
    private String symbol;

    @ApiModelProperty(value = "时间周期")
    private String timeframe;

    @ApiModelProperty(value = "K线时间")
    private String klineTime;

    @ApiModelProperty(value = "业务决策原因")
    private String decisionReason;

    @ApiModelProperty(value = "风控等级")
    private String riskLevel;

    @ApiModelProperty(value = "仓位比例")
    private BigDecimal positionRatio;

    @ApiModelProperty(value = "优先级")
    private Integer priority;

    @ApiModelProperty(value = "订单操作")
    private OrderAction orderAction;

    @ApiModelProperty(value = "订单状态")
    private TradeStatus status;

    @ApiModelProperty(value = "订单号")
    private String orderSn;

    @ApiModelProperty(value = "预计开仓价格")
    private BigDecimal expectedPrice;

    @ApiModelProperty(value = "实际成交价格")
    private BigDecimal executedPrice;

    @ApiModelProperty(value = "预计数量")
    private BigDecimal expectedAmount;

    @ApiModelProperty(value = "实际成交数量")
    private BigDecimal executedAmount;

    @ApiModelProperty(value = "止损价")
    private BigDecimal stopLossPrice;

    @ApiModelProperty(value = "止盈价")
    private BigDecimal takeProfitPrice;

    @ApiModelProperty(value = "杠杆倍数")
    private Integer leverage;

    @ApiModelProperty(value = "手续费率")
    private BigDecimal feeRate;

    @ApiModelProperty(value = "实际手续费")
    private BigDecimal actualFee;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @ApiModelProperty(value = "执行时间")
    private Date executedTime;

    @ApiModelProperty(value = "盈亏金额")
    private BigDecimal pnlAmount;

    @ApiModelProperty(value = "盈亏百分比")
    private BigDecimal pnlPercentage;

    @ApiModelProperty(value = "执行备注")
    private String executionNote;
}
