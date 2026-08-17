package com.chain.ai.trade.engine.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TickerDTO {
    private String symbol;
    private String interval;
    private Long time;
    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal volume;
    private BigDecimal changePercent;
}
