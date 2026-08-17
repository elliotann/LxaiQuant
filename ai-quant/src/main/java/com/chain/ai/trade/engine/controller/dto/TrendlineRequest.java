package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class TrendlineRequest {
    private String symbol;
    private String interval;
    private int size = 500;
    private List<String> indicators;
    private TrendlineParams params;
}
