package com.chain.ai.trade.extension.ta4j.indicator.chanlun.model;

import lombok.Data;
import java.util.Map;

/**
 * 买卖点信号数据模型
 */
@Data
public class Signal {
    private String id;
    private String type;              // "BUY" / "SELL"
    private int level;                // 1 / 2 / 3
    private int barIndex;
    private double price;
    private String strength;
    private Map<String, Object> metadata;
}
