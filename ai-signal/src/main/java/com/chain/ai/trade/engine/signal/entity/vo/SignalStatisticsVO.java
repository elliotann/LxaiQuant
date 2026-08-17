package com.chain.ai.trade.engine.signal.entity.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 信号统计视图对象
 */
@Data
public class SignalStatisticsVO {

    @ApiModelProperty(value = "统计时间范围开始")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "统计时间范围结束")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "交易对")
    private String symbol;

    @ApiModelProperty(value = "总信号数量")
    private Long totalSignals;

    @ApiModelProperty(value = "有效信号数量")
    private Long validSignals;

    @ApiModelProperty(value = "无效信号数量")
    private Long invalidSignals;

    @ApiModelProperty(value = "信号有效率")
    private BigDecimal signalValidityRate;

    @ApiModelProperty(value = "平均信号强度")
    private BigDecimal averageSignalStrength;

    @ApiModelProperty(value = "平均置信度")
    private BigDecimal averageConfidence;

    @ApiModelProperty(value = "信号方向分布")
    private Map<String, Long> directionDistribution;

    @ApiModelProperty(value = "指标类型分布")
    private Map<String, Long> indicatorDistribution;

    @ApiModelProperty(value = "策略分布")
    private Map<String, Long> strategyDistribution;

    @ApiModelProperty(value = "时间周期分布")
    private Map<String, Long> timeframeDistribution;

    @ApiModelProperty(value = "信号强度分布区间")
    private Map<String, Long> strengthDistribution;

    @ApiModelProperty(value = "按小时的信号频率")
    private Map<String, Long> hourlyFrequency;

    @ApiModelProperty(value = "按日期的信号频率")
    private Map<String, Long> dailyFrequency;

    @ApiModelProperty(value = "最活跃的指标")
    private String mostActiveIndicator;

    @ApiModelProperty(value = "最活跃的策略")
    private String mostActiveStrategy;

    @ApiModelProperty(value = "信号质量评分(0-100)")
    private BigDecimal signalQualityScore;

    @ApiModelProperty(value = "重复信号比例")
    private BigDecimal duplicateSignalRatio;

    @ApiModelProperty(value = "异常信号数量")
    private Long anomalousSignals;

    @ApiModelProperty(value = "数据完整性评分")
    private BigDecimal dataIntegrityScore;
}
