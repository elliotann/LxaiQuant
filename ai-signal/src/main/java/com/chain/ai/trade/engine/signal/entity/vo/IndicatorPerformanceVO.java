package com.chain.ai.trade.engine.signal.entity.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标性能分析视图对象
 */
@Data
public class IndicatorPerformanceVO {

    @ApiModelProperty(value = "交易对")
    private String symbol;

    @ApiModelProperty(value = "指标类型")
    private String indicator;

    @ApiModelProperty(value = "策略名称")
    private String strategyName;

    @ApiModelProperty(value = "分析时间段开始")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "分析时间段结束")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "总信号数量")
    private Long totalSignals;

    @ApiModelProperty(value = "多头信号数量")
    private Long bullishSignals;

    @ApiModelProperty(value = "空头信号数量")
    private Long bearishSignals;

    @ApiModelProperty(value = "中性信号数量")
    private Long neutralSignals;

    @ApiModelProperty(value = "信号成功率")
    private BigDecimal successRate;

    @ApiModelProperty(value = "平均信号强度")
    private BigDecimal averageStrength;

    @ApiModelProperty(value = "平均延迟时间(秒)")
    private Long averageDelaySeconds;

    @ApiModelProperty(value = "最大信号强度")
    private BigDecimal maxStrength;

    @ApiModelProperty(value = "最小信号强度")
    private BigDecimal minStrength;

    @ApiModelProperty(value = "信号强度标准差")
    private BigDecimal strengthStdDev;

    @ApiModelProperty(value = "信号频率(每小时)")
    private BigDecimal signalFrequencyPerHour;

    @ApiModelProperty(value = "信号一致性评分")
    private BigDecimal consistencyScore;

    @ApiModelProperty(value = "虚假信号比例")
    private BigDecimal falseSignalRatio;

    @ApiModelProperty(value = "信号时效性评分")
    private BigDecimal timelinessScore;

    @ApiModelProperty(value = "市场适应性评分")
    private BigDecimal marketAdaptabilityScore;

    @ApiModelProperty(value = "综合性能评分")
    private BigDecimal overallPerformanceScore;

    @ApiModelProperty(value = "性能等级")
    private String performanceGrade;

    @ApiModelProperty(value = "改进建议")
    private String improvementSuggestions;
}
