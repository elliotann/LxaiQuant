package com.chain.ai.trade.engine.xchange.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 市场深度数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDepth {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 买单深度 (价格, 数量)
     */
    private List<DepthLevel> bids;

    /**
     * 卖单深度 (价格, 数量)
     */
    private List<DepthLevel> asks;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 深度级别
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepthLevel {
        private BigDecimal price;
        private BigDecimal quantity;
    }
}
