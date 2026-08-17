package com.chain.ai.trade.engine.signal.service.impl;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.dos.SmcBarResult;
import com.chain.ai.trade.engine.data.entity.dos.SmcOrderBlock;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 独立 SMC（Smart Money Concepts）指标计算服务。
 * 提供请求级（线程级）缓存，确保同一线程内多次调用共享结果，避免重复计算。
 *
 * <p>缓存策略：
 * <ul>
 *   <li>每个 key（symbol:interval:signalTimeMs）缓存一对 (Result, Snapshot)</li>
 *   <li>缓存命中时，getSmcSnapshot 直接返回 Snapshot，无需再次加载 K 线</li>
 *   <li>缓存未命中时，加载 K 线并计算完整结果，同时存入缓存</li>
 * </ul>
 */
@Slf4j
@Service
public class SmcIndicatorService {

    // ==================== 缓存 ====================
    /**
     * 静态 ThreadLocal 缓存，key 格式：symbol:interval:signalTimeMs
     * 值：AbstractMap.SimpleEntry<Result, SmcBarResult>
     */
    private static final ThreadLocal<Map<String, AbstractMap.SimpleEntry<SmartMoneyConceptsIndicator.Result, SmcBarResult>>> CACHE =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    /**
     * SMC 历史结果列表缓存，key 格式：symbol:interval:signalTimeMs:limit
     * 用于复用波次/翻转/结构年龄等历史序列计算，避免同一线程内重复计算
     */
    private static final ThreadLocal<Map<String, List<SmartMoneyConceptsIndicator.Result>>> HISTORY_CACHE =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    /** 清除当前线程的缓存（通常在请求结束或开始前调用） */
    public static void clearCache() {
        CACHE.remove();
        HISTORY_CACHE.remove();
    }

    // ==================== 常量 ====================
    private static final int MIN_KLINES_FOR_SMC = 80;
    private static final int DEFAULT_KLINE_LIMIT = 300;   // 从 1000 减至 300，降低 IO 和内存开销
    private static final int SMC_SWINGS_LENGTH = 50;
    private static final int SMC_INTERNAL_OB_COUNT = 3;
    private static final int SMC_SWING_OB_COUNT = 3;
    private static final double EQUAL_HIGH_LOW_THRESHOLD = 0.1;
    private static final int EQUAL_HIGH_LOW_LENGTH = 3;

    // ==================== 依赖注入 ====================
    @Autowired
    private ICandlestickService candlestickService;

    // ==================== 公开方法 ====================

    /**
     * 获取 SMC 快照数据（SmcBarResult）。
     * 优先从缓存返回，若未命中则计算并缓存。
     */
    public SmcBarResult getSmcSnapshot(String symbol,
                                       CandlestickIntervalEnum interval,
                                       long signalTimeMs) {
        long t0 = System.currentTimeMillis();
        if (interval == null || interval.getMinNum() == null) return null;

        String key = buildCacheKey(symbol, interval, signalTimeMs);
        Map<String, AbstractMap.SimpleEntry<SmartMoneyConceptsIndicator.Result, SmcBarResult>> cache = CACHE.get();
        AbstractMap.SimpleEntry<SmartMoneyConceptsIndicator.Result, SmcBarResult> cached = cache.get(key);

        if (cached != null) {
            log.info("信号耗时 - getSmcSnapshot {} {}: 缓存命中 ({}ms)", symbol, interval, System.currentTimeMillis() - t0);
            return cached.getValue();  // 直接返回 Snapshot
        }

        // 缓存未命中：加载 K 线，计算并缓存
        List<Candlestick> klines = loadKlines(symbol, interval, signalTimeMs, DEFAULT_KLINE_LIMIT);
        if (klines == null || klines.size() < MIN_KLINES_FOR_SMC) {
            log.info("信号耗时 - getSmcSnapshot {} {}: K线不足 ({}条)，跳过", symbol, interval, klines != null ? klines.size() : 0);
            return null;
        }
        long t1 = System.currentTimeMillis();

        int targetIndex = findIndexById(klines, signalTimeMs);
        if (targetIndex < 0) {
            targetIndex = klines.size() - 1;
            log.warn("未找到指定 id={} 的K线，使用最新K线 index={}", signalTimeMs, targetIndex);
        }

        BarSeries series = buildSeries(klines, interval);
        long t2 = System.currentTimeMillis();

        SmartMoneyConceptsIndicator indicator = new SmartMoneyConceptsIndicator(
                series, createSmcConfig(), null, null, null);
        SmartMoneyConceptsIndicator.Result result = indicator.getValue(targetIndex);
        long t3 = System.currentTimeMillis();

        var bar = series.getBar(targetIndex);
        SmcBarResult snapshot = buildSmcBarResult(result, bar, klines.get(targetIndex).getId());

        // 同时缓存 Result 和 Snapshot
        cache.put(key, new AbstractMap.SimpleEntry<>(result, snapshot));

        log.debug("信号耗时 - getSmcSnapshot {} {}: loadKlines={}ms, buildSeries={}ms, indicator={}ms, buildResult={}ms, 总计={}ms",
                symbol, interval, t1 - t0, t2 - t1, t3 - t2, System.currentTimeMillis() - t3, System.currentTimeMillis() - t0);
        return snapshot;
    }

    /**
     * 获取指定周期和时间的 SMC 原始结果（SmartMoneyConceptsIndicator.Result）。
     * 会从缓存中获取，若不存在则调用 getSmcSnapshot 触发计算并缓存。
     */
    public SmartMoneyConceptsIndicator.Result getSmcResult(String symbol,
                                                           CandlestickIntervalEnum interval,
                                                           long signalTimeMs) {
        if (interval == null || interval.getMinNum() == null) return null;

        String key = buildCacheKey(symbol, interval, signalTimeMs);
        Map<String, AbstractMap.SimpleEntry<SmartMoneyConceptsIndicator.Result, SmcBarResult>> cache = CACHE.get();
        AbstractMap.SimpleEntry<SmartMoneyConceptsIndicator.Result, SmcBarResult> cached = cache.get(key);

        if (cached != null) {
            return cached.getKey();  // 返回 Result
        }

        // 若 Result 不在缓存中，调用 getSmcSnapshot 触发完整计算（会同时缓存 Result 和 Snapshot）
        getSmcSnapshot(symbol, interval, signalTimeMs);
        AbstractMap.SimpleEntry<SmartMoneyConceptsIndicator.Result, SmcBarResult> newCached = cache.get(key);
        return newCached != null ? newCached.getKey() : null;
    }

    // ==================== 历史结果列表（供波次/翻转/年龄计算）====================

    /**
     * 获取指定周期和时间的 SMC 历史结果列表（从最早可用数据到目标时间）。
     * <p>用于需要历史序列的计算，如 WaveIndexCalculator、StructureHealthCalculator 等。</p>
     *
     * @param symbol       交易品种
     * @param interval     K线周期
     * @param signalTimeMs 目标时间戳（毫秒）
     * @param limit        最大 K 线条数
     * @return 历史 SmartMoneyConceptsIndicator.Result 列表（按时间升序），不足则返回空列表；K线不足时返回空列表
     */
    public List<SmartMoneyConceptsIndicator.Result> getSmcResultHistory(String symbol,
                                                                         CandlestickIntervalEnum interval,
                                                                         long signalTimeMs,
                                                                         int limit) {
        long t0 = System.currentTimeMillis();
        if (interval == null || interval.getMinNum() == null) return Collections.emptyList();

        String cacheKey = symbol + ":" + interval.name() + ":" + signalTimeMs + ":" + limit;
        Map<String, List<SmartMoneyConceptsIndicator.Result>> cache = HISTORY_CACHE.get();
        List<SmartMoneyConceptsIndicator.Result> cached = cache.get(cacheKey);
        if (cached != null) {
            log.debug("getSmcResultHistory {} {} limit={}: 缓存命中 ({}ms)", symbol, interval, limit, System.currentTimeMillis() - t0);
            return cached;
        }

        // 加载足够多的 K 线（至少 = SMC 前置需求 + 所需历史长度）
        int klineLimit = Math.max(limit, MIN_KLINES_FOR_SMC);
        List<Candlestick> klines = loadKlines(symbol, interval, signalTimeMs, klineLimit);
        if (klines == null || klines.size() < MIN_KLINES_FOR_SMC) {
            log.info("getSmcResultHistory {} {}: K线不足 ({}条)，跳过", symbol, interval, klines != null ? klines.size() : 0);
            return Collections.emptyList();
        }

        BarSeries series = buildSeries(klines, interval);
        SmartMoneyConceptsIndicator indicator = new SmartMoneyConceptsIndicator(
                series, createSmcConfig(), null, null, null);

        // 从 swingsLength 开始迭代到最新
        List<SmartMoneyConceptsIndicator.Result> results = new ArrayList<>();
        int startIdx = SMC_SWINGS_LENGTH;
        int endIdx = series.getEndIndex();
        for (int i = startIdx; i <= endIdx; i++) {
            results.add(indicator.getValue(i));
        }

        cache.put(cacheKey, results);

        log.debug("getSmcResultHistory {} {}: loadKlines={}条, results={}条, 耗时={}ms",
                symbol, interval, klines.size(), results.size(), System.currentTimeMillis() - t0);
        return results;
    }

    // ==================== 内部计算方法 ====================

    private SmcBarResult buildSmcBarResult(SmartMoneyConceptsIndicator.Result result,
                                           org.ta4j.core.Bar bar,
                                           long timestamp) {
        SmcBarResult dto = new SmcBarResult();
        dto.setTimestamp(timestamp);
        dto.setOpen(bar.getOpenPrice().doubleValue());
        dto.setHigh(bar.getHighPrice().doubleValue());
        dto.setLow(bar.getLowPrice().doubleValue());
        dto.setClose(bar.getClosePrice().doubleValue());
        dto.setVolume(bar.getVolume().longValue());
        dto.setInternalTrend(result.getInternalTrend());
        dto.setSwingTrend(result.getSwingTrend());
        dto.setInternalBullishBOS(result.isInternalBullishBOS());
        dto.setInternalBearishBOS(result.isInternalBearishBOS());
        dto.setInternalBullishCHOCH(result.isInternalBullishCHOCH());
        dto.setInternalBearishCHOCH(result.isInternalBearishCHOCH());
        dto.setSwingBullishBOS(result.isSwingBullishBOS());
        dto.setSwingBearishBOS(result.isSwingBearishBOS());
        dto.setSwingBullishCHOCH(result.isSwingBullishCHOCH());
        dto.setSwingBearishCHOCH(result.isSwingBearishCHOCH());
        dto.setInternalBullishOrderBlockBreak(result.isInternalBullishOrderBlockBreak());
        dto.setInternalBearishOrderBlockBreak(result.isInternalBearishOrderBlockBreak());
        dto.setSwingBullishOrderBlockBreak(result.isSwingBullishOrderBlockBreak());
        dto.setSwingBearishOrderBlockBreak(result.isSwingBearishOrderBlockBreak());
        dto.setEqualHighs(result.isEqualHighs());
        dto.setEqualLows(result.isEqualLows());
        dto.setBullishFairValueGap(result.isBullishFairValueGap());
        dto.setBearishFairValueGap(result.isBearishFairValueGap());
        dto.setBullishFVGBroken(result.isBullishFVGBroken());
        dto.setBearishFVGBroken(result.isBearishFVGBroken());
        dto.setLastBullishFVGTop(result.getLastBullishFVGTop());
        dto.setLastBullishFVGBottom(result.getLastBullishFVGBottom());
        dto.setLastBearishFVGTop(result.getLastBearishFVGTop());
        dto.setLastBearishFVGBottom(result.getLastBearishFVGBottom());
        dto.setPremiumZoneTop(result.getPremiumZoneTop());
        dto.setPremiumZoneBottom(result.getPremiumZoneBottom());
        dto.setDiscountZoneTop(result.getDiscountZoneTop());
        dto.setDiscountZoneBottom(result.getDiscountZoneBottom());
        dto.setEquilibriumZoneTop(result.getEquilibriumZoneTop());
        dto.setEquilibriumZoneBottom(result.getEquilibriumZoneBottom());
        dto.setEquilibriumCenter(result.getEquilibriumCenter());
        dto.setCurrentZone(result.getCurrentZone());
        dto.setStrongHigh(result.getStrongHigh());
        dto.setWeakHigh(result.getWeakHigh());
        dto.setStrongLow(result.getStrongLow());
        dto.setWeakLow(result.getWeakLow());
        dto.setTrailingHigh(result.getTrailingHigh());
        dto.setTrailingLow(result.getTrailingLow());
        dto.setTrailingHighTime(result.getTrailingHighTime());
        dto.setTrailingLowTime(result.getTrailingLowTime());
        dto.setCandleColor(result.getCandleColor());
        dto.setSwingOrderBlocks(toOrderBlocks(result.getSwingOrderBlocks()));
        dto.setInternalOrderBlocks(toOrderBlocks(result.getInternalOrderBlocks()));
        return dto;
    }

    // ==================== 辅助方法 ====================

    private String buildCacheKey(String symbol, CandlestickIntervalEnum interval, long signalTimeMs) {
        return symbol + ":" + interval.name() + ":" + signalTimeMs;
    }

    private List<Candlestick> loadKlines(String symbol, CandlestickIntervalEnum interval, long to, int limit) {
        long t0 = System.currentTimeMillis();
        try {
            KlineParam param = KlineParam.builder()
                    .symbol(symbol)
                    .klineInterval(interval)
                    .endTime(to)
                    .size(limit)
                    .build();
            List<Candlestick> result = candlestickService.listByLtId(param);
            log.debug("信号耗时 - loadKlines {} {} limit={}: {}ms, 返回{}条", symbol, interval, limit,
                    System.currentTimeMillis() - t0, result != null ? result.size() : 0);
            return result;
        } catch (Exception e) {
            log.error("加载K线失败: symbol={}, interval={}, error={}", symbol, interval, e.getMessage());
            return null;
        }
    }

    private int findIndexById(List<Candlestick> klines, long id) {
        for (int i = 0; i < klines.size(); i++) {
            Candlestick k = klines.get(i);
            if (k != null && k.getId() != null && k.getId() == id) return i;
        }
        return -1;
    }

    private BarSeries buildSeries(List<Candlestick> klines, CandlestickIntervalEnum interval) {
        Duration duration = Duration.ofMinutes(interval.getMinNum().longValue());
        BarSeries series = new BaseBarSeriesBuilder().withName("smc").build();
        ZoneId shanghaiZone = ZoneId.of("Asia/Shanghai");
        for (Candlestick k : klines) {
            var startTime = java.time.ZonedDateTime.ofInstant(Instant.ofEpochMilli(k.getId()), shanghaiZone);
            var endTime = startTime.plus(duration);
            Instant endInstant = endTime.toLocalDateTime().atOffset(java.time.ZoneOffset.UTC).toInstant();
            var bar = series.barBuilder()
                    .timePeriod(duration)
                    .endTime(endInstant)
                    .openPrice(series.numFactory().numOf(k.getOpenPrice()))
                    .highPrice(series.numFactory().numOf(k.getHighPrice()))
                    .lowPrice(series.numFactory().numOf(k.getLowPrice()))
                    .closePrice(series.numFactory().numOf(k.getClosePrice()))
                    .volume(series.numFactory().numOf(k.getVolume()))
                    .build();
            series.addBar(bar);
        }
        return series;
    }

    private SmartMoneyConceptsIndicator.Config createSmcConfig() {
        SmartMoneyConceptsIndicator.Config config = new SmartMoneyConceptsIndicator.Config();
        config.setSwingsLength(SMC_SWINGS_LENGTH);
        config.setShowInternalOrderBlocks(true);
        config.setShowSwingOrderBlocks(true);
        config.setInternalOrderBlocksCount(SMC_INTERNAL_OB_COUNT);
        config.setSwingOrderBlocksCount(SMC_SWING_OB_COUNT);
        config.setOrderBlockFilter("Atr");
        config.setOrderBlockMitigation("High/Low");
        config.setShowEqualHighsLows(true);
        config.setEqualHighsLowsLength(EQUAL_HIGH_LOW_LENGTH);
        config.setEqualHighsLowsThreshold(EQUAL_HIGH_LOW_THRESHOLD);
        config.setShowFairValueGaps(true);
        config.setFairValueGapsAutoThreshold(true);
        config.setShowDailyLevels(false);
        config.setShowWeeklyLevels(false);
        config.setShowMonthlyLevels(false);
        config.setShowPremiumDiscountZones(true);
        return config;
    }

    private List<SmcOrderBlock> toOrderBlocks(List<SmartMoneyConceptsIndicator.OrderBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return List.of();
        List<SmcOrderBlock> list = new ArrayList<>(blocks.size());
        for (SmartMoneyConceptsIndicator.OrderBlock b : blocks) {
            SmcOrderBlock dto = new SmcOrderBlock();
            dto.setHigh(b.barHigh);
            dto.setLow(b.barLow);
            dto.setTime(b.barTime);
            dto.setBias(b.bias);
            list.add(dto);
        }
        return list;
    }
}