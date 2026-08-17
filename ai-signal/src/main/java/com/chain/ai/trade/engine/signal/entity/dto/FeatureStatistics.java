package com.chain.ai.trade.engine.signal.entity.dto;

import lombok.Data;

/**
 * 完整统计信息 VO（含动态分位数）
 */
@Data
public class FeatureStatistics {

    /**
     * 平均绝对空间（%）
     */
    private double avgSpace;

    /**
     * 累积比：SUM(space) / SUM(abs_space)
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

    /**
     * 当前窗口大小
     */
    private int windowSize;

    /**
     * abs_space 的 20% 分位数（低波因子分段）
     */
    private double percentile_20;

    /**
     * abs_space 的 40% 分位数（低波因子分段）
     */
    private double percentile_40;

    /**
     * abs_space 的 70% 分位数（低波因子分段）
     */
    private double percentile_70;

    /**
     * abs_space 的 85% 分位数（极端判定-较大）
     */
    private double percentile_85;

    /**
     * abs_space 的 95% 分位数（极端判定-极端）
     */
    private double percentile_95;

    /**
     * space 的 40% 分位数（方向偏置下界）
     */
    private double cumRatioPercentile_40;

    /**
     * space 的 60% 分位数（方向偏置上界）
     */
    private double cumRatioPercentile_60;
}
