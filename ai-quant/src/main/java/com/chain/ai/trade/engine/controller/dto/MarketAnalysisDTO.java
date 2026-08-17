package com.chain.ai.trade.engine.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MarketAnalysisDTO {
    private String symbol;
    private String interval;
    private Long time;
    private BigDecimal price;
    private BigDecimal changePercent;

    private Integer sentimentScore;
    private String sentimentLabel;

    private String trendLabel;
    private Integer trendStrength;
    private BigDecimal ema9;
    private BigDecimal ema21;

    private BigDecimal rsi14;
    private BigDecimal atr14Percent;
    private BigDecimal bollingerWidthPercent;

    private List<BigDecimal> supports;
    private List<BigDecimal> resistances;
    private List<String> tags;
}

