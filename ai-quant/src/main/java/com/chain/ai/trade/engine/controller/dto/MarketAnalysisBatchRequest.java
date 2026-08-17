package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class MarketAnalysisBatchRequest {
    private List<String> symbols;
    private String interval;
    private Integer limit;
}

