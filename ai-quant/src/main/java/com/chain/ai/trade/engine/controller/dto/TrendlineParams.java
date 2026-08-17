package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

@Data
public class TrendlineParams {
    private int surroundingBars = 3;
    private int barCount = 50;
    private String scoringWeightsPreset;
    private String toleranceMode;
    private Double toleranceValue;
    private Double toleranceMinimum;
}
