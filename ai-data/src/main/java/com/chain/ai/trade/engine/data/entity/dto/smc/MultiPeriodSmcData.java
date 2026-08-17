package com.chain.ai.trade.engine.data.entity.dto.smc;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多周期 SMC 聚合数据 — 通用版
 * <p>采用 Map 存储周期数据，支持动态周期组合</p>
 */
@Data
public class MultiPeriodSmcData {

    // ==================== ★ 新架构（核心） ====================
    /** 周期数据 Map，key = "4H", "1H", "15M" ... */
    private Map<String, PeriodData> periods = new LinkedHashMap<>();

    /** 全局评估指标 */
    private GlobalMetrics global;

    /** 数据生成时间戳 */
    private long timestamp;

    // ==================== 兼容过渡字段（逐步废弃） ====================
    // ★ 保留旧字段，便于前端渐进式迁移，待稳定后删除

    // 4H 层
    private int waveIndex4h;
    private String wavePhase4h;
    private double positionRatio4h;
    private int flipCount4h;
    private int structureAge4h;
    private int swingTrend4h;
    private boolean swingBullishBOS4h;
    private boolean swingBearishBOS4h;
    private boolean swingBullishCHOCH4h;
    private boolean swingBearishCHOCH4h;

    // 1H 层
    private int waveIndex1h;
    private String wavePhase1h;
    private double positionRatio1h;
    private int structureAge1h;
    private int swingTrend1h;
    private boolean swingBullishBOS1h;
    private boolean swingBearishBOS1h;
    private boolean swingBullishCHOCH1h;
    private boolean swingBearishCHOCH1h;

    // 15M 层
    private int waveIndex15m;
    private String wavePhase15m;
    private double positionRatio15m;
    private int structureAge15m;
    private int swingTrend15m;
    private boolean swingBullishBOS15m;
    private boolean swingBearishBOS15m;

    // 全局字段（兼容）
    private double riskRewardRatio;
    private double riskPercent;
    private boolean chaosException;
    private double chaosForcedMultiplier;
    private double compositeScore;
    private double suggestedMultiplier;
    private String phaseDescription;

    // ==================== 兼容方法 ====================

    /**
     * ★ 核心兼容方法：将新架构数据同步到旧字段
     * 在返回给前端前调用一次，保证前后端兼容
     */
    public void syncToLegacyFields() {
        // 4H
        PeriodData p4h = periods.get("4H");
        if (p4h != null) {
            this.waveIndex4h = p4h.getWaveIndex();
            this.wavePhase4h = p4h.getWavePhase();
            this.positionRatio4h = p4h.getPositionRatio();
            this.swingTrend4h = p4h.getSwingTrend();
            this.swingBullishBOS4h = p4h.isBullishBOS();
            this.swingBearishBOS4h = p4h.isBearishBOS();
            this.swingBullishCHOCH4h = p4h.isBullishCHOCH();
            this.swingBearishCHOCH4h = p4h.isBearishCHOCH();
            this.structureAge4h = p4h.getStructureAge();
        }

        // 1H
        PeriodData p1h = periods.get("1H");
        if (p1h != null) {
            this.waveIndex1h = p1h.getWaveIndex();
            this.wavePhase1h = p1h.getWavePhase();
            this.positionRatio1h = p1h.getPositionRatio();
            this.structureAge1h = p1h.getStructureAge();
            this.swingTrend1h = p1h.getSwingTrend();
            this.swingBullishBOS1h = p1h.isBullishBOS();
            this.swingBearishBOS1h = p1h.isBearishBOS();
            this.swingBullishCHOCH1h = p1h.isBullishCHOCH();
            this.swingBearishCHOCH1h = p1h.isBearishCHOCH();
        }

        // 15M
        PeriodData p15m = periods.get("15M");
        if (p15m != null) {
            this.waveIndex15m = p15m.getWaveIndex();
            this.wavePhase15m = p15m.getWavePhase();
            this.positionRatio15m = p15m.getPositionRatio();
            this.structureAge15m = p15m.getStructureAge();
            this.swingTrend15m = p15m.getSwingTrend();
            this.swingBullishBOS15m = p15m.isBullishBOS();
            this.swingBearishBOS15m = p15m.isBearishBOS();
        }

        // 全局
        if (global != null) {
            this.riskRewardRatio = global.getRiskRewardRatio();
            this.riskPercent = global.getRiskPercent();
            this.chaosException = global.isChaosException();
            this.chaosForcedMultiplier = global.getChaosForcedMultiplier();
            this.compositeScore = global.getCompositeScore();
            this.suggestedMultiplier = global.getSuggestedMultiplier();
            this.phaseDescription = global.getPhaseDescription();
        }

        this.timestamp = System.currentTimeMillis();
    }
}