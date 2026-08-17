package com.chain.ai.trade.engine.service.ai.filter.dto;

import com.chain.ai.trade.engine.strategy.entity.dos.Strategy;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AiFilterRequest {
    private Strategy strategy;
    private String symbol;
    private String direction;
    private BigDecimal signalStrength;
    private Long signalTime;
}
