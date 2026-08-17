package com.chain.ai.trade.engine.data.mtf;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import org.ta4j.core.BarSeries;

/**
 * 多周期 K 线数据提供者。
 * <p>
 * 抽象不同数据来源（回测预计算 / 实盘 DB 按需加载 / 重采样），
 * 使规则层无需关心数据来源，通过统一接口获取指定周期的 BarSeries 和时间戳对应 bar 索引。
 * </p>
 */
public interface MultiTimeFrameProvider {

    /**
     * 获取指定周期的 BarSeries。
     * 每次调用应返回同一实例，以便规则层内部缓存指标计算结果。
     */
    BarSeries getSeries(CandlestickIntervalEnum interval);

    /**
     * 在指定周期的 BarSeries 中二分查找本地时间戳对应的 bar 索引。
     *
     * @param interval      周期
     * @param localTimestamp 本地时间戳（毫秒，即 UTC - 时区偏移）
     * @return bar 索引，未找到时返回 -1
     */
    int getBarIndex(CandlestickIntervalEnum interval, long localTimestamp);

    /**
     * 获取指定周期 BarSeries 的版本号，版本变化说明 series 被重新加载（动态加载模式）。
     * 下游可据此清除缓存指标和计算结果，默认返回 0 表示版本不变。
     */
    default long getSeriesVersion(CandlestickIntervalEnum interval) {
        return 0L;
    }
}
