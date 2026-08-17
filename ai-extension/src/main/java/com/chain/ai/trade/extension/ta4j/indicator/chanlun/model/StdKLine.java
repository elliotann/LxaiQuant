package com.chain.ai.trade.extension.ta4j.indicator.chanlun.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 标准K线数据模型
 */
@Data
public class StdKLine {
    private LocalDateTime time;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private double atr;

    /** 在原始K线序列中的索引（用于映射回原始数据） */
    private int originalIndex;
}
