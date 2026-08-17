package com.chain.ai.trade.agent.tools;

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
public class MarketAnalysisInfo {
    private String symbol;
    private String interval;
    private String trend;
    private BigDecimal currentPrice;
    private BigDecimal rsi;
    private BigDecimal ma7;
    private BigDecimal ma25;
    private BigDecimal ma99;
    private BigDecimal support;
    private BigDecimal resistance;
    private BigDecimal volatility;
    private BigDecimal volume;
    private String suggestion;
}
