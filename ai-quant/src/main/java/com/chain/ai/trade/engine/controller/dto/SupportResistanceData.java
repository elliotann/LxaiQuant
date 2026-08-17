package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class SupportResistanceData {
    private TrendSegment segment;
    private List<TimeValuePoint> linePoints;
}
