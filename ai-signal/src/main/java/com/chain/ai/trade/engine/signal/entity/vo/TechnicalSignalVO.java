package com.chain.ai.trade.engine.signal.entity.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 技术信号视图对象（用于前端展示）
 */
@Data
public class TechnicalSignalVO {

    @ApiModelProperty(value = "信号ID")
    private Long id;

    @ApiModelProperty(value = "交易对")
    private String symbol;

    @ApiModelProperty(value = "时间周期")
    private String timeframe;

    @ApiModelProperty(value = "K线时间戳")
    private Long klineTimestamp;

    @ApiModelProperty(value = "K线时间")
    private String klineTime;

    @ApiModelProperty(value = "开盘价")
    private BigDecimal openPrice;

    @ApiModelProperty(value = "收盘价")
    private BigDecimal closePrice;

    @ApiModelProperty(value = "高点")
    private BigDecimal highPrice;

    @ApiModelProperty(value = "低点")
    private BigDecimal lowPrice;

    @ApiModelProperty(value = "成交量")
    private BigDecimal volume;

    @ApiModelProperty(value = "信号类型/策略标识，如 MACD、RANGE_FILTER、AI_TREND")
    private String indicator;

    @ApiModelProperty(value = "策略名称")
    private String strategyName;

    @ApiModelProperty(value = "技术信号方向")
    private String technicalDirection;

    @ApiModelProperty(value = "技术信号方向描述")
    private String technicalDirectionDesc;

    @ApiModelProperty(value = "技术信号强度")
    private BigDecimal signalStrength;

    @ApiModelProperty(value = "指标数值")
    private BigDecimal indicatorValue;

    @ApiModelProperty(value = "阈值触发值")
    private BigDecimal threshold;

    @ApiModelProperty(value = "信号唯一标识")
    private String signalHash;

    @ApiModelProperty(value = "创建时间")
    private String createTime;

    @ApiModelProperty(value = "是否已生成交易信号")
    private Boolean hasTradeSignal;

    @ApiModelProperty(value = "额外技术参数")
    private String extraParams;  // JSON存储额外指标参数

    @ApiModelProperty(value = "市场趋势/状态")
    private String marketTrend;
}
