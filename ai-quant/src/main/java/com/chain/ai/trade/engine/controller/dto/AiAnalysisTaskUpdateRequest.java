package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiAnalysisTaskUpdateRequest {
    private Integer intervalMin;
    private List<String> notifyChannels;
    private Boolean enabled;
}
