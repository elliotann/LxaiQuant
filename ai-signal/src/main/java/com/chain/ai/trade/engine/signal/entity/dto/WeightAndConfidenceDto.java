package com.chain.ai.trade.engine.signal.entity.dto;

import com.chain.ai.trade.common.entity.constants.CompositeState;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 权重和置信度DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeightAndConfidenceDto {

    @ApiModelProperty(value = "权重", example = "1.5")
    private BigDecimal weight;

    @ApiModelProperty(value = "目标")
    private PriceTargetsInfo priceTargetsInfo;

    @ApiModelProperty(value = "拒绝/否决原因，仅在权重为0时有意义")
    private String reason;

    private CompositeState trendState;
}

