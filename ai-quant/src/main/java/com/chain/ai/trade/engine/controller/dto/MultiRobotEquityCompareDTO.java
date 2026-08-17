package com.chain.ai.trade.engine.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiRobotEquityCompareDTO {
    private String robotId;
    private String robotName;
    private List<String> dates;
    private List<BigDecimal> equities;
    private List<BigDecimal> navs;
}
