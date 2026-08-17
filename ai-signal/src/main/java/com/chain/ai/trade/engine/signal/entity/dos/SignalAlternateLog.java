package com.chain.ai.trade.engine.signal.entity.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 信号交替流水表 - L1 特征工程层
 * <p>
 * 主关联键 strategy_name 对应 technical_signal.strategy_name（SignFactory.SignType 名）。
 * 配对前仅填 entry_* 字段，exit_* 与 space_pct / minutes_between 留空。
 */
@TableName("signal_alternate_log")
@Data
public class SignalAlternateLog implements Serializable {

    @ApiModelProperty(value = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "策略标识（technical_signal.strategy_name，即 SignFactory.SignType 名）")
    private String strategyName;

    @ApiModelProperty(value = "交易对")
    private String symbol;

    @ApiModelProperty(value = "周期（CandlestickIntervalEnum.name()，如 OKXMIN5）")
    private String timeframe;

    @ApiModelProperty(value = "开仓信号时间戳(毫秒)")
    private Long entryTime;

    @ApiModelProperty(value = "开仓价")
    private BigDecimal entryPrice;

    @ApiModelProperty(value = "开仓方向(LONG/SHORT)")
    private String entryDirection;

    @ApiModelProperty(value = "开仓信号ID（关联 technical_signal.id）")
    private Long entrySignalId;

    @ApiModelProperty(value = "平仓信号时间戳(毫秒)")
    private Long exitTime;

    @ApiModelProperty(value = "平仓价")
    private BigDecimal exitPrice;

    @ApiModelProperty(value = "平仓方向(LONG/SHORT)")
    private String exitDirection;

    @ApiModelProperty(value = "平仓信号ID（关联 technical_signal.id）")
    private Long exitSignalId;

    @ApiModelProperty(value = "交替空间(%)，配对后计算")
    private BigDecimal spacePct;

    @ApiModelProperty(value = "间隔分钟数，配对后计算")
    private Integer minutesBetween;

    @ApiModelProperty(value = "连续同向序列号")
    private Integer directionSequence;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;
}
