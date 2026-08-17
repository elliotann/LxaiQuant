package com.chain.ai.trade.engine.risk.intraday.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class OpenPositionRef implements Serializable {
    private String orderSn;
    private String symbol;
    private String accountId;
}

