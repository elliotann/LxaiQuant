package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiAnalysisTaskCreateRequest {
    private List<String> symbols;
    private Integer intervalMin;
    private List<String> notifyChannels;
}
