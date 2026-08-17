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
public class RobotEquityLatestDTO {
    private String robotId;
    private String robotName;
    private BigDecimal currentCapital;
    private BigDecimal allocatedCapital;
    private BigDecimal peakCapital;
    private BigDecimal todayPnl;
    private BigDecimal totalReturn;
    private BigDecimal drawdown;
}
