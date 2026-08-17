package com.chain.ai.trade.engine.signal.feature;

import com.chain.ai.trade.engine.signal.entity.dos.SignalAlternateLog;
import com.chain.ai.trade.engine.signal.entity.dto.FeatureSnapshot;
import com.chain.ai.trade.engine.signal.entity.dto.FeatureStatistics;

/**
 * 特征提供者接口
 * <p>
 * 实盘和回测共用此接口，仅存储策略不同：实盘走 Redis，回测走内存 Map。
 */
public interface SignalFeatureProvider {

    /**
     * 平均绝对空间（%）
     */
    double getAvgSpace(String strategyName, String symbol, String timeframe);

    /**
     * 累积比：SUM(space) / SUM(abs_space)
     */
    double getCumRatio(String strategyName, String symbol, String timeframe);

    /**
     * 最近连续同向笔数
     */
    int getDirectionSeq(String strategyName, String symbol, String timeframe);

    /**
     * 上一次信号时间戳（毫秒）
     */
    long getLastSignalTime(String strategyName, String symbol, String timeframe);

    /**
     * 上一次信号方向（LONG/SHORT）
     */
    String getLastDirection(String strategyName, String symbol, String timeframe);

    /**
     * 最新一笔 space_pct
     */
    double getLatestSpace(String strategyName, String symbol, String timeframe);

    /**
     * 基础特征快照
     */
    FeatureSnapshot getFullSnapshot(String strategyName, String symbol, String timeframe);

    /**
     * abs_space 的指定分位数（percentile 取值 0~1）
     */
    double getAbsSpacePercentile(String strategyName, String symbol, String timeframe, double percentile);

    /**
     * space 的指定分位数（percentile 取值 0~1）
     */
    double getSpacePercentile(String strategyName, String symbol, String timeframe, double percentile);

    /**
     * 完整统计信息（含动态分位数）
     */
    FeatureStatistics getFullStatistics(String strategyName, String symbol, String timeframe);

    /**
     * 新增一笔交替信号后更新特征
     */
    void onNewSignal(SignalAlternateLog signal);
}
