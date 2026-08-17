package com.chain.ai.trade.extension.ta4j.indicator.chanlun.model;

import lombok.Data;

/**
 * 笔数据模型
 */
@Data
public class Bi {
    private FenXing start;
    private FenXing end;
    private String direction;   // "UP" / "DOWN"
    private double high;
    private double low;
    private int klineCount;
}
