package com.chain.ai.trade.engine.signal.entity.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新项目 信号
 */
@Data
public class NewSignalDTO {

    /**
     * @see
     */
    @ApiModelProperty(value = "趋势")
    private String trend;

    @ApiModelProperty(value = "信号")
    private String signal;

    @ApiModelProperty(value = "下单信号")
    private String orderAction;

    @ApiModelProperty(value = "权重")
    private String weight;

    // 以下为订单相关
    @ApiModelProperty(value = "订单号")
    private String orderSn;

    @ApiModelProperty(value = "订单仓位")
    private BigDecimal orderAmount;

    @ApiModelProperty(value = "订单点数")
    private BigDecimal orderPoint;

    @ApiModelProperty(value = "是否平仓单")
    private String isCloseOrder;

    @ApiModelProperty(value = "信号来源")
    private String source;

    @ApiModelProperty(value = "收益")
    private BigDecimal income;

    @ApiModelProperty(value = "手续费")
    private BigDecimal fee;

    @ApiModelProperty(value = "实际收益")
    private BigDecimal realIncome;

    @ApiModelProperty(value = "当日风控")
    private BigDecimal todayIncome;
}