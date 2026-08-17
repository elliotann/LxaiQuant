package com.chain.ai.trade.engine.data.mtf;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import org.ta4j.core.BarSeries;
import org.ta4j.core.utils.BarSeriesUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多周期 K 线提供者，支持聚合/直查两种模式，由调用方决定。
 * <p>
 * <b>聚合模式（默认）</b>：基于单条基础周期 BarSeries 通过 DurationBarAggregator 聚合生成其他周期数据。<br>
 * 适用于基础周期数据已完整加载，无需额外 DB 查询的场景。
 * </p>
 * <p>
 * <b>直查模式</b>：其他周期直接从 DB 加载真实 K 线数据，不经过聚合，支持两种查询方式：<br>
 * &nbsp;&nbsp;1. <b>时间范围</b>（startTime + endTime）：固定时间区间加载<br>
 * &nbsp;&nbsp;2. <b>结束时间+条数</b>（endTime + barCount，<b>动态</b>）：每次 {@link #getBarIndex} 以当前评估时间为
 * &nbsp;&nbsp;结束时间向历史取 N 条，适用于逐根 bar 递进评估且不可使用未来数据的场景（如 SMC）
 * </p>
 * <p>
 * 三种模式下，基础周期始终返回回测引擎的 series 实例（保持 index 对齐）。
 * </p>
 *
 * <pre>{@code
 * // 聚合模式
 * new ResampleMultiTimeFrameProvider(series, interval);
 *
 * // 直查模式-时间范围
 * new ResampleMultiTimeFrameProvider(series, interval, candlestickService, Exchange.OKX, symbol, startTime, endTime);
 *
 * // 直查模式-动态 tailCount（每次 getBarIndex 以当前时间加载）
 * new ResampleMultiTimeFrameProvider(series, interval, candlestickService, Exchange.OKX, symbol, 1000);
 * }</pre>
 */
public class ResampleMultiTimeFrameProvider implements MultiTimeFrameProvider {

    private final BarSeries mainSeries;
    private final CandlestickIntervalEnum mainInterval;

    // 聚合模式
    private final boolean resample;

    // 直查模式-时间范围（startTime + endTime）
    private final ICandlestickService candlestickService;
    private final Exchange exchange;
    private final String symbol;
    private final long startTime;
    private final long endTime;

    // 直查模式-动态 tailCount（结束时间+条数，结束时间由 getBarIndex 参数决定）
    private final boolean tailCountMode;
    private final int barCount;

    private final Map<CandlestickIntervalEnum, BarSeries> cache = new ConcurrentHashMap<>();

    // 版本追踪：每 reload 一次递增，供下游检测 series 变化后清除指标缓存
    private final Map<CandlestickIntervalEnum, Long> seriesVersions = new ConcurrentHashMap<>();
    private final AtomicLong versionSeq = new AtomicLong(0);

    /** 聚合模式构造 */
    public ResampleMultiTimeFrameProvider(BarSeries mainSeries, CandlestickIntervalEnum mainInterval) {
        this(mainSeries, mainInterval, null, null, null, 0, 0);
    }

    /** 直查模式构造-时间范围 */
    public ResampleMultiTimeFrameProvider(BarSeries mainSeries, CandlestickIntervalEnum mainInterval,
                                          ICandlestickService candlestickService, Exchange exchange,
                                          String symbol, long startTime, long endTime) {
        this.mainSeries = mainSeries;
        this.mainInterval = mainInterval;
        this.resample = false;
        this.tailCountMode = false;
        this.candlestickService = candlestickService;
        this.exchange = exchange;
        this.symbol = symbol;
        this.startTime = startTime;
        this.endTime = endTime;
        this.barCount = 0;
    }

    /** 直查模式构造-动态 tailCount（每次 getBarIndex 以 localTimestamp 为结束时间加载 N 条） */
    public ResampleMultiTimeFrameProvider(BarSeries mainSeries, CandlestickIntervalEnum mainInterval,
                                          ICandlestickService candlestickService, Exchange exchange,
                                          String symbol, int barCount) {
        this.mainSeries = mainSeries;
        this.mainInterval = mainInterval;
        this.resample = false;
        this.tailCountMode = true;
        this.candlestickService = candlestickService;
        this.exchange = exchange;
        this.symbol = symbol;
        this.barCount = barCount;
        this.startTime = 0;
        this.endTime = 0;
    }

    @Override
    public BarSeries getSeries(CandlestickIntervalEnum interval) {
        if (interval.getMinNum().equals(mainInterval.getMinNum())) {
            return mainSeries;
        }
        if (tailCountMode) {
            // 动态加载模式：series 由 getBarIndex → ensureSeriesCovers 按需加载，不在此处自动触发
            return cache.get(interval);
        }
        return cache.computeIfAbsent(interval, this::load);
    }

    private BarSeries load(CandlestickIntervalEnum target) {
        if (resample) {
            // 聚合模式：从 mainSeries 聚合生成
            Duration targetDuration = Duration.ofMinutes(target.getMinNum());
            return BarSeriesUtils.aggregateBars(mainSeries, targetDuration, target.getCode());
        } else if (tailCountMode) {
            throw new IllegalStateException(
                    "tailCount 模式不应通过 load() 加载 series，请使用 getBarIndex 触发动态加载");
        } else {
            // 直查模式-时间范围：固定时间区间（size=0 不限制条数，由时间范围决定）
            KlineParam param = KlineParam.builder()
                    .exchange(exchange)
                    .symbol(symbol)
                    .klineInterval(target.toOkxInterval())
                    .startTime(startTime)
                    .endTime(endTime)
                    .size(0)
                    .build();
            return IndicatorWrapHelper.buildSeries(candlestickService.getKlines(param));
        }
    }

    @Override
    public int getBarIndex(CandlestickIntervalEnum interval, long localTimestamp) {
        BarSeries series;
        if (tailCountMode && !interval.getMinNum().equals(mainInterval.getMinNum())) {
            series = ensureSeriesCovers(interval, localTimestamp);
        } else {
            series = getSeries(interval);
        }
        return binarySearchBarIndex(series, localTimestamp);
    }

    @Override
    public long getSeriesVersion(CandlestickIntervalEnum interval) {
        return seriesVersions.getOrDefault(interval, 0L);
    }

    /**
     * 确保缓存 series 覆盖给定时间戳，不覆盖时以 localTimestamp 为结束时间重新加载。
     * 返回的 series 必然包含 ≤ localTimestamp 的数据。
     */
    private BarSeries ensureSeriesCovers(CandlestickIntervalEnum interval, long localTimestamp) {
        BarSeries cached = cache.get(interval);
        if (cached != null && cached.getBarCount() > 0) {
            // 检查最后一根 bar 的时间是否 ≥ localTimestamp，是则说明覆盖
            Instant lastBarTime = cached.getLastBar().getBeginTime();
            if (!lastBarTime.isBefore(Instant.ofEpochMilli(localTimestamp))) {
                return cached;
            }
        }
        // 以 localTimestamp 为结束时间重新加载 N 条
        KlineParam param = KlineParam.builder()
                .exchange(exchange)
                .symbol(symbol)
                .klineInterval(interval.toOkxInterval())
                .endTime(localTimestamp)
                .size(barCount)
                .build();
        BarSeries newSeries = IndicatorWrapHelper.buildSeries(candlestickService.getKlines(param));
        cache.put(interval, newSeries);
        seriesVersions.put(interval, versionSeq.incrementAndGet());
        return newSeries;
    }

    /**
     * 对 BarSeries 二分查找 localTimestamp 对应的 bar 索引。
     * 返回最后一个 beginTime ≤ localTimestamp 的 bar 索引，series 为空时返回 -1。
     */
    private static int binarySearchBarIndex(BarSeries series, long localTimestamp) {
        int count = series.getBarCount();
        if (count == 0) return -1;

        Instant target = Instant.ofEpochMilli(localTimestamp);
        int lo = 0, hi = count - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = series.getBar(mid).getBeginTime().compareTo(target);
            if (cmp < 0) lo = mid + 1;
            else if (cmp > 0) hi = mid - 1;
            else return mid;
        }
        return lo > 0 ? lo - 1 : 0;
    }
}
