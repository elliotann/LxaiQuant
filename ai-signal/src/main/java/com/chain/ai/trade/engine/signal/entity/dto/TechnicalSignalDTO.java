package com.chain.ai.trade.engine.signal.entity.dto;

import com.chain.ai.trade.common.entity.constants.OrderPriceType;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 技术信号DTO（用于API传输）
 */
@Data
public class TechnicalSignalDTO {

    @ApiModelProperty(value = "数据源（交易所平台）", example = "OKX")
    private String dataSource;

    @ApiModelProperty(value = "信号来源（如 DEEPSEEK / OPENCLAW / SYSTEM）", example = "DEEPSEEK")
    private String signalSource;

    @ApiModelProperty(value = "关联的 DeepSeek 建议ID", example = "adv_...")
    private String sourceAdviceId;

    @ApiModelProperty(value = "交易对", required = true, example = "BTCUSDT")
    @NotBlank(message = "交易对不能为空")
    private String symbol;

    @ApiModelProperty(value = "时间周期", required = true, example = "1m, 5m, 1h, 4h, 1d")
    @NotBlank(message = "时间周期不能为空")
    private String timeframe;

    @ApiModelProperty(value = "K线时间", required = true, example = "2025-01-01 12:00:00")
    @NotBlank(message = "K线时间不能为空")
    private String klineTime;

    @ApiModelProperty(value = "K线时间戳")
    private Long klineTimestamp;

    @ApiModelProperty(value = "信号类型/策略标识", required = true, example = "MACD, RANGE_FILTER, AI_TREND")
    @NotBlank(message = "信号类型不能为空")
    private String indicator;

    @ApiModelProperty(value = "策略名称", required = true, example = "TrendFollowing")
    @NotBlank(message = "策略名称不能为空")
    private String strategyName;

    @ApiModelProperty(value = "技术信号方向", required = true, example = "STRONG_BULLISH, BULLISH, NEUTRAL, BEARISH, STRONG_BEARISH")
    @NotBlank(message = "技术信号方向不能为空")
    private String technicalDirection;

    @ApiModelProperty(value = "技术信号强度", required = true, example = "0.8")
    @NotNull(message = "技术信号强度不能为空")
    private BigDecimal signalStrength;



    @ApiModelProperty(value = "当前价格", required = true, example = "45000.00")
    @NotNull(message = "当前价格不能为空")
    private BigDecimal currentPrice;

    @ApiModelProperty(value = "开盘价")
    private BigDecimal openPrice;

    @ApiModelProperty(value = "收盘价")
    private BigDecimal closePrice;

    @ApiModelProperty(value = "高点")
    private BigDecimal highPrice;

    @ApiModelProperty(value = "低点")
    private BigDecimal lowPrice;

    @ApiModelProperty(value = "各指标数值", example = "{\"rsi\": 65.5, \"macd\": 0.0021}")
    private Map<String, Object> indicatorValues;  // 各指标数值

    @ApiModelProperty(value = "信号唯一标识", required = true, example = "md5_hash_string")
    @NotBlank(message = "信号唯一标识不能为空")
    private String signalHash;

    @ApiModelProperty(value = "额外技术参数")
    private String extraParams;  // JSON存储额外指标参数

    @ApiModelProperty(value = "市场趋势/状态", example = "RANGING/TRENDING/BREAKOUT_UP/BREAKOUT_DOWN")
    private String marketTrend;

    @ApiModelProperty(value = "入场类型：MARKET(市价) / LIMIT(限价)")
    private OrderPriceType entryType;

    @ApiModelProperty(value = "限价单价格")
    private BigDecimal limitPrice;
}
