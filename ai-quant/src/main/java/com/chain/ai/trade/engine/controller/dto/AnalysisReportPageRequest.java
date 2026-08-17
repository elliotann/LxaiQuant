package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

@Data
public class AnalysisReportPageRequest {
    private String taskId;
    private int page = 1;
    private int size = 20;
}
