package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrendSegment {
    private int firstIndex;
    private int secondIndex;
    private BigDecimal slope;
    private BigDecimal intercept;
    private int touchCount;
    private int outsideCount;
    private double score;
}
