package com.chain.ai.trade.engine.signal.entity.dos;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.constants.OrderPriceType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 技术指标信号表 - 纯技术层面
 */
@TableName("technical_signal")
@Data
public class TechnicalSignal implements Serializable {

    private Long id;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @ApiModelProperty(value = "数据源")
    private String dataSource;

    @ApiModelProperty(value = "信号来源（如 DEEPSEEK / OPENCLAW / SYSTEM）")
    @TableField("signal_source")
    private String signalSource;

    @ApiModelProperty(value = "关联的 DeepSeek 建议ID")
    @TableField("source_advice_id")
    private String sourceAdviceId;

    @ApiModelProperty(value = "数据周期")
    private String timeframe;  // 1m, 5m, 1h, 4h, 1d

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

    @ApiModelProperty(value = "交易对")
    private String symbol;  // BTCUSDT, ETHUSDT

    @ApiModelProperty(value = "信号类型/策略标识，如 MACD、RANGE_FILTER、AI_TREND")
    private String indicator;  // SignFactory.SignType.name()

    @ApiModelProperty(value = "策略名称")
    private String strategyName;

    @ApiModelProperty(value = "技术信号方向")
    private String technicalDirection;  // STRONG_BULLISH, BULLISH, NEUTRAL, BEARISH, STRONG_BEARISH

    @ApiModelProperty(value = "技术信号强度")
    private BigDecimal signalStrength;  // 0-1 或 0-100



    @ApiModelProperty(value = "指标数值")
    private BigDecimal indicatorValue;

    @ApiModelProperty(value = "阈值触发值")
    private BigDecimal threshold;

    @ApiModelProperty(value = "入场类型：MARKET(市价) / LIMIT(限价)")
    private OrderPriceType entryType;


    @ApiModelProperty(value = "限价单价格")
    private BigDecimal limitPrice;

    @ApiModelProperty(value = "额外技术参数")
    private String extraParams;  // JSON存储额外指标参数

    @ApiModelProperty(value = "市场趋势/状态")
    private String marketTrend;

    @ApiModelProperty(value = "信号唯一标识")
    private String signalHash;  // MD5(symbol+timeframe+klineTime+indicator+strategy)

    @ApiModelProperty(value = "AI 过滤结果（JSON格式）：{decision, score, llmDecision, summary}")
    @TableField("ai_filter_result")
    private String aiFilterResult;

}
