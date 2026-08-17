package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

@Data
public class TrendlineResponse {
    private boolean success;
    private TrendlineData data;
}
