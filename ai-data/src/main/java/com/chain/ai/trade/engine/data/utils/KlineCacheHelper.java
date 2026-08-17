package com.chain.ai.trade.engine.data.utils;

import com.alibaba.fastjson.JSON;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.common.entity.constants.Exchange;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * K线Redis缓存辅助类
 * 使用 ZSET 存储最近1200条K线，score=时间戳(id)，member=JSON序列化的Candlestick
 */
@Slf4j
@Component
public class KlineCacheHelper {

    private static final String KEY_PREFIX = "kline:cache:";
    private static final int MAX_CACHE_SIZE = 1200;

    @Autowired
    private RedisCache redisCache;

    @Value("${kline.redis-cache.enabled:false}")
    private boolean cacheEnabled;

    private boolean ready;

    @PostConstruct
    public void init() {
        ready = true;
        if (cacheEnabled) {
            log.info("K线Redis缓存已开启，每个Key最大缓存{}条", MAX_CACHE_SIZE);
        }
    }

    /**
     * 缓存是否开启
     */
    public boolean isEnabled() {
        return cacheEnabled;
    }

    /**
     * 构建 Redis Key
     */
    public String buildKey(Exchange exchange, String symbol, CandlestickIntervalEnum interval) {
        return KEY_PREFIX + exchange + ":" + symbol.toUpperCase() + ":" + interval;
    }

    /**
     * 批量写入K线数据到 ZSET（增量更新）
     * 每个Candlestick的id作为score，时间戳
     */
    public void putAll(List<Candlestick> list) {
        if (!cacheEnabled || list == null || list.isEmpty()) {
            return;
        }

        // 按 (exchange, symbol, interval) 分组
        Map<String, List<Candlestick>> grouped = list.stream()
                .filter(c -> c.getExchange() != null && c.getSymbol() != null && c.getCandlestickIntervalEnum() != null)
                .collect(Collectors.groupingBy(c ->
                        buildKey(c.getExchange(), c.getSymbol(), c.getCandlestickIntervalEnum())));

        for (Map.Entry<String, List<Candlestick>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            List<Candlestick> candles = entry.getValue();
            for (Candlestick candle : candles) {
                long score = candle.getId() != null ? candle.getId() : 0;
                // 先删除同score的旧member，避免ZSET中同一时间戳产生重复数据
                redisCache.zRemoveRangeByScore(key, score, score);
                redisCache.zAdd(key, score, JSON.toJSONString(candle));
            }
            // 只保留最新1200条
            redisCache.zRemRangeByRank(key, 0, -(MAX_CACHE_SIZE + 1));
        }
    }

    /**
     * 从 ZSET 获取最新 N 条K线（降序取，反转回升序）
     * @return 如果缓存未命中或不足，返回空列表
     */
    public List<Candlestick> getLatest(String key, int size) {
        Long total = redisCache.zCard(key);
        if (total == null || total == 0) {
            return Collections.emptyList();
        }

        int fetchSize = Math.min(size, MAX_CACHE_SIZE);
        Set<Object> members = redisCache.zRange(key, Math.max(0, total - fetchSize), total - 1);
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }

        // 按id去重（保留最后一个），避免ZSET中同一时间戳有重复member
        return members.stream()
                .map(m -> JSON.parseObject(m.toString(), Candlestick.class))
                .collect(Collectors.toMap(Candlestick::getId, c -> c, (old, latest) -> latest, LinkedHashMap::new))
                .values().stream().collect(Collectors.toList());
    }

    /**
     * 获取最新的1条K线
     */
    public Candlestick getLast(String key) {
        List<Candlestick> list = getLatest(key, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 获取 ZSET 元素数量
     */
    public long size(String key) {
        Long card = redisCache.zCard(key);
        return card != null ? card : 0;
    }

    /**
     * 清空指定 Key 的缓存
     */
    public void clear(String key) {
        redisCache.remove(key);
    }

    /**
     * 判断是否为"查询最新N条K线"，仅这种场景走Redis缓存
     * 条件：缓存开启 + 实盘 + startTime和endTime都为空（纯最新查询）+ 查询数量≤1200
     */
    public boolean shouldUseCache(boolean isTest, Long startTime, Long endTime, int querySize) {
        if (!cacheEnabled || isTest) {
            return false;
        }
        if (querySize > MAX_CACHE_SIZE) {
            return false;
        }
        // 只有纯"最新N条"查询走缓存（无时间范围过滤）
        return startTime == null && endTime == null;
    }
}
