package com.chain.ai.trade.engine.data.entity.dos;


import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class SmcBarResult {
    private long timestamp;          // 毫秒时间戳
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;

    // 趋势
    private int internalTrend;       // -1,0,1
    private int swingTrend;          // -1,0,1

    // 结构信号
    private boolean internalBullishBOS;
    private boolean internalBearishBOS;
    private boolean internalBullishCHOCH;
    private boolean internalBearishCHOCH;
    private boolean swingBullishBOS;
    private boolean swingBearishBOS;
    private boolean swingBullishCHOCH;
    private boolean swingBearishCHOCH;

    // 订单块突破
    private boolean internalBullishOrderBlockBreak;
    private boolean internalBearishOrderBlockBreak;
    private boolean swingBullishOrderBlockBreak;
    private boolean swingBearishOrderBlockBreak;

    // EQH/EQL
    private boolean equalHighs;
    private boolean equalLows;

    // FVG
    private boolean bullishFairValueGap;
    private boolean bearishFairValueGap;
    private boolean bullishFVGBroken;
    private boolean bearishFVGBroken;
    private Double lastBullishFVGTop;
    private Double lastBullishFVGBottom;
    private Double lastBearishFVGTop;
    private Double lastBearishFVGBottom;

    // MTF 水平
    private Double dailyHigh;
    private Double dailyLow;
    private Double weeklyHigh;
    private Double weeklyLow;
    private Double monthlyHigh;
    private Double monthlyLow;

    // 溢价/折扣区域
    private Double premiumZoneTop;
    private Double premiumZoneBottom;
    private Double discountZoneTop;
    private Double discountZoneBottom;
    private Double equilibriumZoneTop;
    private Double equilibriumZoneBottom;
    private Double equilibriumCenter;
    private String currentZone;

    // 强弱高低点
    private Double strongHigh;
    private Double weakHigh;
    private Double strongLow;
    private Double weakLow;

    // 波段高低点
    private Double trailingHigh;
    private Double trailingLow;
    private Long trailingHighTime;
    private Long trailingLowTime;

    // 蜡烛颜色
    private int candleColor;

    // 订单块列表（可选，可能很大，可决定是否返回）
    private List<SmcOrderBlock> swingOrderBlocks;
    private List<SmcOrderBlock> internalOrderBlocks;

    // BOS/CHOCH 信号对应的 pivot 信息
    private Map<String, Long> pivotTimestamps = new HashMap<>();
    private Map<String, Double> pivotLevels = new HashMap<>();
}
