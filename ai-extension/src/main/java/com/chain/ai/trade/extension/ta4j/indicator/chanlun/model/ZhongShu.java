package com.chain.ai.trade.extension.ta4j.indicator.chanlun.model;

import lombok.Data;
import java.util.List;

/**
 * 中枢数据模型
 */
@Data
public class ZhongShu {
    private String id;
    private String type;              // "BI" / "DUAN"
    private List<Integer> componentIndices;
    private int startIndex;
    private int endIndex;
    private double high;
    private double low;
    private int direction;
    private String growthType;        // "EXTEND" / "NEW" / "EXPAND"

    /** 波动高点 GG（中枢内所有笔的最高价的最大值） */
    private double gg;
    /** 波动低点 DD（中枢内所有笔的最低价的最小值） */
    private double dd;
}
