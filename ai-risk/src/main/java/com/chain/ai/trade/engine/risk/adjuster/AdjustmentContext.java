package com.chain.ai.trade.engine.risk.adjuster;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 调节上下文
 */
@Data
@Builder
public class AdjustmentContext {
    private String symbol;                // 交易对
    private double currentPrice;          // 当前价格
    private double accountBalance;        // 账户余额
    private double currentPosition;       // 当前持仓
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>(); // 其他元数据
}

