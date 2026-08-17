package com.chain.ai.trade.engine.signal.entity.query;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 技术信号查询条件
 */
@Data
public class TechnicalSignalQuery {

    @ApiModelProperty(value = "交易对")
    private String symbol;

    @ApiModelProperty(value = "时间周期")
    private String timeframe;

    @ApiModelProperty(value = "指标类型")
    private String indicator;

    @ApiModelProperty(value = "策略名称")
    private String strategyName;

    @ApiModelProperty(value = "技术信号方向")
    private String technicalDirection;

    @ApiModelProperty(value = "信号哈希")
    private String signalHash;

    @ApiModelProperty(value = "最小信号强度")
    private Double minSignalStrength;

    @ApiModelProperty(value = "最大信号强度")
    private Double maxSignalStrength;

    @ApiModelProperty(value = "最小置信度")
    private Double minConfidence;

    @ApiModelProperty(value = "最大置信度")
    private Double maxConfidence;

    @ApiModelProperty(value = "开始时间")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间")
    private LocalDateTime endTime;

    @ApiModelProperty(value = "K线时间戳开始")
    private Long klineTimestampStart;

    @ApiModelProperty(value = "K线时间戳结束")
    private Long klineTimestampEnd;

    @ApiModelProperty(value = "数据源")
    private String dataSource;

    @ApiModelProperty(value = "信号方向列表")
    private List<String> directions;

    @ApiModelProperty(value = "指标类型列表")
    private List<String> indicators;

    @ApiModelProperty(value = "策略名称列表")
    private List<String> strategies;

    @ApiModelProperty(value = "分页页码", example = "1")
    private Integer pageNum = 1;

    @ApiModelProperty(value = "分页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "createTime")
    private String orderBy = "createTime";

    @ApiModelProperty(value = "排序方向", example = "desc")
    private String orderDirection = "desc";

    @ApiModelProperty(value = "是否包含已删除的记录", example = "false")
    private Boolean includeDeleted = false;

    @ApiModelProperty(value = "查询模式: SIMPLE-简单查询, ADVANCED-高级查询")
    private String queryMode = "SIMPLE";
}
