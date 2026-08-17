package com.chain.ai.trade.engine.data.entity.dto.smc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SMC 单周期结构 DTO
 * <p>包含指标原始字段 + 扩展计算字段，供行情看板和权重引擎使用</p>
 */
@Data
public class SmcStructureDTO {

    // ==================== 基础信息 ====================
    private String symbol;
    private String period;
    private long timestamp;

    // ==================== 原始指标字段（SmartMoneyConceptsIndicator.Result） ====================

    // -- 趋势 --
    private int swingTrend;
    private int internalTrend;

    // -- 摆动点 --
    private double lastSwingHigh;
    private double lastSwingLow;
    private double prevSwingHigh;
    private double prevSwingLow;
    private double lastHigherLow;
    private double lastLowerHigh;

    // -- 结构信号 --
    private boolean swingBullishBOS;
    private boolean swingBearishBOS;
    private boolean swingBullishCHOCH;
    private boolean swingBearishCHOCH;
    private boolean internalBullishBOS;
    private boolean internalBearishBOS;
    private boolean internalBullishCHOCH;
    private boolean internalBearishCHOCH;

    // -- 事件类型 --
    private int lastSwingEventType;
    private int lastInternalEventType;

    // -- 订单块突破 --
    private boolean swingBullishOrderBlockBreak;
    private boolean swingBearishOrderBlockBreak;
    private boolean internalBullishOrderBlockBreak;
    private boolean internalBearishOrderBlockBreak;

    // -- EQH / EQL --
    private boolean equalHighs;
    private boolean equalLows;

    // -- FVG --
    private boolean bullishFairValueGap;
    private boolean bearishFairValueGap;
    private boolean bullishFVGBroken;
    private boolean bearishFVGBroken;
    private double lastBullishFVGTop;
    private double lastBullishFVGBottom;
    private double lastBearishFVGTop;
    private double lastBearishFVGBottom;

    // -- 溢价 / 折扣区域 --
    private double premiumZoneTop;
    private double premiumZoneBottom;
    private double discountZoneTop;
    private double discountZoneBottom;
    private double equilibriumCenter;
    private String currentZone;

    // -- 强弱高低点 --
    private double strongHigh;
    private double strongLow;
    private double weakHigh;
    private double weakLow;

    // -- 波段跟踪 --
    private double trailingHigh;
    private double trailingLow;
    private long trailingHighTime;
    private long trailingLowTime;

    // -- 蜡烛颜色 --
    private int candleColor;

    // -- MTF 水平 --
    private double dailyHigh;
    private double dailyLow;
    private double weeklyHigh;
    private double weeklyLow;
    private double monthlyHigh;
    private double monthlyLow;

    // -- pivot 映射 --
    private Map<String, Long> pivotTimestamps;
    private Map<String, Double> pivotLevels;

    // -- 订单块列表 --
    private List<OrderBlockDTO> swingOrderBlocks;
    private List<OrderBlockDTO> internalOrderBlocks;

    // ==================== 扩展计算字段（后处理器产出） ====================
    private int waveIndex;
    private String wavePhase;
    private double positionRatio;
    private int structureAge;
    private int flipCount;
    private double riskRewardRatio;
    private double riskPercent;
    private boolean chaosException;

    // ==================== 内部类 ====================

    /**
     * 订单块 DTO（简化版，脱敏自 OrderBlock）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderBlockDTO {
        private double high;
        private double low;
        private long time;
        private int bias;
    }
}
