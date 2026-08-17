package com.chain.ai.trade.engine.data.entity.dto.smc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单周期结构数据（通用）
 * 用于替代 4H/1H/15M 的硬编码字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodData {
    // —— 趋势 ——
    private int swingTrend;              // 1=多头, -1=空头, 0=震荡

    // —— 波次 ——
    private int waveIndex;               // 多头: 0~4+，空头: 0~-4+
    private String wavePhase;            // 混沌/试盘/确认/加速/赶顶/赶底

    // —— 位置 ——
    private double positionRatio;        // 0.00 ~ 1.00

    // —— 年龄 ——
    private int structureAge;            // K线根数

    // —— 结构信号 ——
    private boolean bullishBOS;
    private boolean bearishBOS;
    private boolean bullishCHOCH;
    private boolean bearishCHOCH;
}