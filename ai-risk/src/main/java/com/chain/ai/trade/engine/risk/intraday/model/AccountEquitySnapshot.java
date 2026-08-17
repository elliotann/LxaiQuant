package com.chain.ai.trade.engine.risk.intraday.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
public class AccountEquitySnapshot implements Serializable {
    private String accountId;
    private BigDecimal equity;
}

