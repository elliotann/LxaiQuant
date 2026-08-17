package com.chain.ai.trade.engine.data.entity.dos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SmcBosChochSignal {
    private long timestamp;
    private double price;
    private String type;
    private long pivotTimestamp;
}
