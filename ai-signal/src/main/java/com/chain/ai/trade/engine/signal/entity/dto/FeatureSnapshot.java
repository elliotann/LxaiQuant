package com.chain.ai.trade.engine.signal.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基础特征快照 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureSnapshot {

    /**
     * 平均绝对空间（%）
     */
    private double avgSpace;

    /**
     * 累积比：SUM(space) / SUM(abs_space)，判断趋势/震荡
     */
    private double cumRatio;

    /**
     * 最近连续同向笔数
     */
    private int directionSeq;

    /**
     * 上一次信号时间戳（毫秒）
     */
    private long lastSignalTime;

    /**
     * 最新一笔 space_pct
     */
    private double latestSpace;

    /**
     * 上一次信号方向（LONG/SHORT）
     */
    private String lastDirection;
}
