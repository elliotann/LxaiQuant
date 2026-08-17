package com.chain.ai.trade.extension.ta4j.indicator.chanlun.model;

import lombok.Data;

/**
 * 分型数据模型（顶分型/底分型）
 */
@Data
public class FenXing {
    private int index;
    private String type;        // "TOP" / "BOTTOM"
    private double high;
    private double low;
    private double powerScore;

    /** 在原始K线序列中的索引（用于前端定位） */
    private int originalIndex;
}
