package com.chain.ai.trade.engine.signal.entity.dos;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import com.chain.ai.trade.common.entity.constants.OrderAction;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;


/**
 * @Description 交易信号模块-信号表
 * @Author liangchen
 * @Date 2025/3/20 11:53
 **/
@TableName("tradesignal_signal")
@Data
public class TradeSignalSignal implements Serializable {


    private Long id;

    @ApiModelProperty(value = "创建人")
    private String creator;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @ApiModelProperty(value = "更新人")
    private String updater;

    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    @ApiModelProperty(value = "是否删除")
    @TableLogic
    private Boolean deleted;

    @ApiModelProperty(value = "数据来源")
    private String dataFrom;

    @ApiModelProperty(value = "数据周期")
    private String dataInterval;

    @ApiModelProperty(value = "K线时间")
    private String klineTime;

    @ApiModelProperty(value = "收盘价")
    private BigDecimal closePrice;

    @ApiModelProperty(value = "关键KEY高点")
    private BigDecimal highPrice;

    @ApiModelProperty(value = "关键KEY低点")
    private BigDecimal lowPrice;

    @ApiModelProperty(value = "币种")
    private String symbol;

    @ApiModelProperty(value = "趋势")
    private String trend;

    @ApiModelProperty(value = "指标名称")
    private String indicatorType;

    @ApiModelProperty(value = "策略名称")
    private String strategyType;


    /**
     * 订单操作
     */
    private OrderAction orderAction;

    private String orderSn;

    private String orderItemSn;
}
