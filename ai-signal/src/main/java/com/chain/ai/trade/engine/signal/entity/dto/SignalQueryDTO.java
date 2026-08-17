package com.chain.ai.trade.engine.signal.entity.dto;

import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.engine.signal.entity.constants.TradeStatus;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 信号查询DTO
 */
@Data
public class SignalQueryDTO {

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

    @ApiModelProperty(value = "订单操作")
    private OrderAction orderAction;

    @ApiModelProperty(value = "订单状态")
    private List<TradeStatus> statuses;

    @ApiModelProperty(value = "风控等级")
    private String riskLevel;

    @ApiModelProperty(value = "开始时间")
    private Date startTime;

    @ApiModelProperty(value = "结束时间")
    private Date endTime;

    @ApiModelProperty(value = "最小信号强度")
    private Double minSignalStrength;

    @ApiModelProperty(value = "最大信号强度")
    private Double maxSignalStrength;

    @ApiModelProperty(value = "是否盈利")
    private Boolean isProfitable;

    @ApiModelProperty(value = "分页页码", example = "1")
    private Integer pageNum = 1;

    @ApiModelProperty(value = "分页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "createTime")
    private String orderBy = "createTime";

    @ApiModelProperty(value = "排序方向", example = "desc")
    private String orderDirection = "desc";
}
