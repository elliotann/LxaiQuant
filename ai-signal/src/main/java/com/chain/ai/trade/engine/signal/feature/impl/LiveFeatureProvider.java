package com.chain.ai.trade.engine.signal.feature.impl;

import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.signal.entity.dos.SignalAlternateLog;
import com.chain.ai.trade.engine.signal.entity.dto.FeatureSnapshot;
import com.chain.ai.trade.engine.signal.entity.dto.FeatureStatistics;
import com.chain.ai.trade.engine.signal.feature.SignalFeatureProvider;
import com.chain.ai.trade.engine.signal.feature.SlidingWindow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实盘特征提供者：内存滑动窗口 + Redis 存储
 */
@Component
@ConditionalOnProperty(name = "quant.env", havingValue = "live", matchIfMissing = true)
@Slf4j
public class LiveFeatureProvider implements SignalFeatureProvider {

    @Autowired
    private RedisCache redisCache;

    private final Map<String, SlidingWindow> windowMap = new ConcurrentHashMap<>();
    private static final int WINDOW_SIZE = 20;
    private static final long REDIS_TTL_SECONDS = 600L;

    @Override
    public double getAvgSpace(String strategyName, String symbol, String timeframe) {
        String val = redisCache.getString(buildKey(strategyName, symbol, timeframe) + ":avgSpace");
        return val == null ? 0.0 : Double.parseDouble(val);
    }

    @Override
    public double getCumRatio(String strategyName, String symbol, String timeframe) {
        String val = redisCache.getString(buildKey(strategyName, symbol, timeframe) + ":cumRatio");
        return val == null ? 0.0 : Double.parseDouble(val);
    }

    @Override
    public int getDirectionSeq(String strategyName, String symbol, String timeframe) {
        String val = redisCache.getString(buildKey(strategyName, symbol, timeframe) + ":directionSeq");
        return val == null ? 0 : Integer.parseInt(val);
    }

    @Override
    public long getLastSignalTime(String strategyName, String symbol, String timeframe) {
        String val = redisCache.getString(buildKey(strategyName, symbol, timeframe) + ":lastSignalTime");
        return val == null ? 0L : Long.parseLong(val);
    }

    @Override
    public String getLastDirection(String strategyName, String symbol, String timeframe) {
        return redisCache.getString(buildKey(strategyName, symbol, timeframe) + ":lastDirection");
    }

    @Override
    public double getLatestSpace(String strategyName, String symbol, String timeframe) {
        String val = redisCache.getString(buildKey(strategyName, symbol, timeframe) + ":latestSpace");
        return val == null ? 0.0 : Double.parseDouble(val);
    }

    @Override
    public FeatureSnapshot getFullSnapshot(String strategyName, String symbol, String timeframe) {
        return new FeatureSnapshot(
                getAvgSpace(strategyName, symbol, timeframe),
                getCumRatio(strategyName, symbol, timeframe),
                getDirectionSeq(strategyName, symbol, timeframe),
                getLastSignalTime(strategyName, symbol, timeframe),
                getLatestSpace(strategyName, symbol, timeframe),
                getLastDirection(strategyName, symbol, timeframe)
        );
    }

    @Override
    public double getAbsSpacePercentile(String strategyName, String symbol, String timeframe, double percentile) {
        String key = buildKey(strategyName, symbol, timeframe);
        // 优先从内存滑动窗口动态计算（p35 等非固定分位也能算出，与回测一致）
        SlidingWindow window = windowMap.get(key);
        if (window != null) {
            return window.getAbsSpacePercentile(percentile);
        }
        // 内存窗口未建立时回退 Redis 固定分位（兼容重启/多实例）
        String val = redisCache.getString(key + ":p" + String.format("%.0f", percentile * 100));
        return val == null ? 0.0 : Double.parseDouble(val);
    }

    @Override
    public double getSpacePercentile(String strategyName, String symbol, String timeframe, double percentile) {
        String val = redisCache.getString(buildKey(strategyName, symbol, timeframe) + ":cumRatio_p" + String.format("%.0f", percentile * 100));
        return val == null ? 0.0 : Double.parseDouble(val);
    }

    @Override
    public FeatureStatistics getFullStatistics(String strategyName, String symbol, String timeframe) {
        FeatureStatistics stats = new FeatureStatistics();
        stats.setAvgSpace(getAvgSpace(strategyName, symbol, timeframe));
        stats.setCumRatio(getCumRatio(strategyName, symbol, timeframe));
        stats.setDirectionSeq(getDirectionSeq(strategyName, symbol, timeframe));
        stats.setLastSignalTime(getLastSignalTime(strategyName, symbol, timeframe));
        stats.setLatestSpace(getLatestSpace(strategyName, symbol, timeframe));
        stats.setLastDirection(getLastDirection(strategyName, symbol, timeframe));
        stats.setPercentile_20(getAbsSpacePercentile(strategyName, symbol, timeframe, 0.20));
        stats.setPercentile_40(getAbsSpacePercentile(strategyName, symbol, timeframe, 0.40));
        stats.setPercentile_70(getAbsSpacePercentile(strategyName, symbol, timeframe, 0.70));
        stats.setPercentile_85(getAbsSpacePercentile(strategyName, symbol, timeframe, 0.85));
        stats.setPercentile_95(getAbsSpacePercentile(strategyName, symbol, timeframe, 0.95));
        stats.setCumRatioPercentile_40(getSpacePercentile(strategyName, symbol, timeframe, 0.40));
        stats.setCumRatioPercentile_60(getSpacePercentile(strategyName, symbol, timeframe, 0.60));
        String ws = redisCache.getString(buildKey(strategyName, symbol, timeframe) + ":windowSize");
        stats.setWindowSize(ws == null ? 0 : Integer.parseInt(ws));
        return stats;
    }

    @Override
    public void onNewSignal(SignalAlternateLog signal) {
        String key = buildKey(signal.getStrategyName(), signal.getSymbol(), signal.getTimeframe());
        SlidingWindow window = windowMap.computeIfAbsent(key, k -> new SlidingWindow(WINDOW_SIZE));
        window.add(signal);
        writeStatistics(key, window.getFullStatistics());
    }

    /**
     * 将完整统计信息逐项写入 Redis（String 存储 + TTL）
     */
    private void writeStatistics(String key, FeatureStatistics stats) {
        long ttl = REDIS_TTL_SECONDS;
        redisCache.put(key + ":avgSpace", String.valueOf(stats.getAvgSpace()), ttl);
        redisCache.put(key + ":cumRatio", String.valueOf(stats.getCumRatio()), ttl);
        redisCache.put(key + ":directionSeq", String.valueOf(stats.getDirectionSeq()), ttl);
        redisCache.put(key + ":lastSignalTime", String.valueOf(stats.getLastSignalTime()), ttl);
        redisCache.put(key + ":lastDirection", stats.getLastDirection(), ttl);
        redisCache.put(key + ":latestSpace", String.valueOf(stats.getLatestSpace()), ttl);
        redisCache.put(key + ":windowSize", String.valueOf(stats.getWindowSize()), ttl);
        redisCache.put(key + ":p20", String.valueOf(stats.getPercentile_20()), ttl);
        redisCache.put(key + ":p40", String.valueOf(stats.getPercentile_40()), ttl);
        redisCache.put(key + ":p70", String.valueOf(stats.getPercentile_70()), ttl);
        redisCache.put(key + ":p85", String.valueOf(stats.getPercentile_85()), ttl);
        redisCache.put(key + ":p95", String.valueOf(stats.getPercentile_95()), ttl);
        redisCache.put(key + ":cumRatio_p40", String.valueOf(stats.getCumRatioPercentile_40()), ttl);
        redisCache.put(key + ":cumRatio_p60", String.valueOf(stats.getCumRatioPercentile_60()), ttl);
    }

    /**
     * 构建 Redis key 前缀：stats:{strategyName}:{symbol}:{timeframe}
     */
    private String buildKey(String strategyName, String symbol, String timeframe) {
        return String.format("stats:%s:%s:%s", strategyName, symbol, timeframe);
    }
}
