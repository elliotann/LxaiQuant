package com.chain.ai.trade.engine.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingSummaryDTO {
    private BigDecimal totalAssets;
    private BigDecimal availableBalance;
    private BigDecimal dailyPnL;
    private BigDecimal totalPnL;
    private BigDecimal dailyPnLPercent;
    private BigDecimal totalPnLPercent;
}
