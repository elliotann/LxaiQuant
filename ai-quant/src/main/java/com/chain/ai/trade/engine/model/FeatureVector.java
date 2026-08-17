package com.chain.ai.trade.engine.model;

import lombok.Data;

@Data
public class FeatureVector {
    private double rsi;
    private double macd;
    private double macdSignal;
    private double emaDiff;
}
