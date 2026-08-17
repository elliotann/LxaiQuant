package com.chain.ai.trade.extension.ta4j.indicator.chanlun.model;

import lombok.Data;
import java.util.List;

/**
 * 线段数据模型
 */
@Data
public class Duan {
    private List<Bi> biList;
    private int startBiIndex;
    private int endBiIndex;
    private String direction;
    private double high;
    private double low;
    private double atrAverage;
}
