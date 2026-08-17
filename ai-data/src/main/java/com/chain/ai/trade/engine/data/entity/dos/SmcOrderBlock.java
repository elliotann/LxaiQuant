package com.chain.ai.trade.engine.data.entity.dos;

import lombok.Data;

@Data
public class SmcOrderBlock {
    private Double high;
    private Double low;
    private Long time;
    private Integer bias;
}

