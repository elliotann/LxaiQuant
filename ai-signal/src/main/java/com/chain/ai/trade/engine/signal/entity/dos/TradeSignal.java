package com.chain.ai.trade.engine.signal.entity.dos;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.engine.signal.entity.constants.TradeStatus;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 交易信号表 - 业务执行层面
 */
@TableName("trade_signal")
@Data
public class TradeSignal implements Serializable {

    private Long id;

    @ApiModelProperty(value = "创建人/系统")
    private String creator;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @ApiModelProperty(value = "更新人")
    private String updater;

    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    @TableLogic
    @ApiModelProperty(value = "是否删除")
    private Boolean deleted;

    // === 关联技术信号 ===
    @ApiModelProperty(value = "技术信号ID")
    private Long technicalSignalId;

    @ApiModelProperty(value = "技术信号哈希")
    private String technicalSignalHash;

    @ApiModelProperty(value = "技术信号摘要")
    private String technicalSignalBrief;  // 简要描述，如"MACD金叉"

    // === 业务决策信息 ===
    @ApiModelProperty(value = "交易对")
    private String symbol;

    @ApiModelProperty(value = "数据周期")
    private String timeframe;

    @ApiModelProperty(value = "K线时间")
    private String klineTime;

    @ApiModelProperty(value = "业务决策原因")
    private String decisionReason;  // 如"风控通过"、"仓位不足"等

    @ApiModelProperty(value = "风控等级")
    private String riskLevel;  // LOW, MEDIUM, HIGH

    @ApiModelProperty(value = "仓位比例")
    private BigDecimal positionRatio;  // 0.1表示10%

    @ApiModelProperty(value = "优先级")
    private Integer priority;  // 1-10

    // === 订单执行信息 ===
    @ApiModelProperty(value = "订单操作")
    private OrderAction orderAction;  // OPEN_LONG, OPEN_SHORT, CLOSE_LONG, CLOSE_SHORT

    @ApiModelProperty(value = "订单状态")
    private TradeStatus status;  // PENDING, EXECUTING, FILLED, CANCELLED, FAILED

    @ApiModelProperty(value = "订单号")
    private String orderSn;

    @ApiModelProperty(value = "订单项号")
    private String orderItemSn;

    @ApiModelProperty(value = "预计开仓价格")
    private BigDecimal expectedPrice;

    @ApiModelProperty(value = "预计数量")
    private BigDecimal expectedAmount;

    @ApiModelProperty(value = "止损价")
    private BigDecimal stopLossPrice;

    @ApiModelProperty(value = "止盈价")
    private BigDecimal takeProfitPrice;

    @ApiModelProperty(value = "杠杆倍数")
    private Integer leverage;

    @ApiModelProperty(value = "手续费率")
    private BigDecimal feeRate;

    @ApiModelProperty(value = "入场类型：MARKET(市价) / LIMIT(限价)")
    private String entryType;

    @ApiModelProperty(value = "限价单价格")
    private BigDecimal limitPrice;

    // === 执行结果 ===
    @ApiModelProperty(value = "实际成交价格")
    private BigDecimal executedPrice;

    @ApiModelProperty(value = "实际成交数量")
    private BigDecimal executedAmount;

    @ApiModelProperty(value = "实际手续费")
    private BigDecimal actualFee;

    @ApiModelProperty(value = "执行时间")
    private Date executedTime;

    @ApiModelProperty(value = "执行备注")
    private String executionNote;

    // === 绩效统计 ===
    @ApiModelProperty(value = "盈亏金额")
    private BigDecimal pnlAmount;

    @ApiModelProperty(value = "盈亏百分比")
    private BigDecimal pnlPercentage;

    @ApiModelProperty(value = "平仓时间")
    private Date closeTime;

    @ApiModelProperty(value = "持仓时长(秒)")
    private Long holdingSeconds;

    @ApiModelProperty(value = "夏普比率")
    private BigDecimal sharpeRatio;
}
