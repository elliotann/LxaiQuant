package com.chain.ai.trade.engine.signal.entity.dto;

import lombok.Data;

@Data
public class OrderBlockPosition {
    boolean inside = false, near = false, exact = false;
    double distanceRatio = 1.0;
}