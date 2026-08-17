package com.chain.ai.trade.engine.data.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CriticalLevel {
    private String type;
    private String side;
    private Double price;
    private Double high;
    private Double low;
    private String period;
    private String action;
    private Integer priority;
    private Double distancePercent;
}
