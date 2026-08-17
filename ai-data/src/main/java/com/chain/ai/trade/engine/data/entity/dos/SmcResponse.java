package com.chain.ai.trade.engine.data.entity.dos;

import lombok.Data;

import java.util.List;

@Data
public class SmcResponse {
    private String symbol;
    private String interval;
    private List<SmcBarResult> results;
    private List<SmcBosChochSignal> bosChochSignals;
    private List<SmcSwingPoint> swingPoints;
}