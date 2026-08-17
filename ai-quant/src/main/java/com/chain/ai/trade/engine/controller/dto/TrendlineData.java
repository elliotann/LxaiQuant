package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

@Data
public class TrendlineData {
    private SupportResistanceData support;
    private SupportResistanceData resistance;
}
