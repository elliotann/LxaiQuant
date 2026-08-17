package com.chain.ai.trade.engine.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AiRadarOpportunityDTO {
    private String symbol;
    private String name;
    private BigDecimal price;
    @JsonProperty("change_24h")
    private BigDecimal change24h;
    private String signal;
    private String strength;
    private String reason;
    private String impact;
    private String market;
    private Long timestamp;
}
