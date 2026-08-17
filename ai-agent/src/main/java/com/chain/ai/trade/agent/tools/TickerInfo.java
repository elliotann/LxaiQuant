package com.chain.ai.trade.agent.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerInfo {
    private String symbol;
    private String price;
    private String change24h;
    private String volume;
}
