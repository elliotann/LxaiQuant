package com.chain.ai.trade.common.utils;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 统一 Redis 缓存实现。
 * <p>
 * Key 强制使用 StringRedisSerializer，Value 使用默认的 Jackson2JsonRedisSerializer。
 * 所有公共方法 key 参数接受 Object 以兼容 Cache 接口，内部统一转换为 String。
 * </p>
 *
 * @author andy
 */
@Component
@SuppressWarnings({"unchecked", "rawtypes"})
public class RedisCache implements Cache {

    private static final Logger LOG = LoggerFactory.getLogger(RedisCache.class);

    /** SCAN 每批数量 */
    private static final int SCAN_COUNT = 1000;
    /** 批量删除每批数量 */
    private static final int BATCH_DELETE_SIZE = 500;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ==================== 内部工具 ====================

    /** Object key → String key */
    private String toKey(Object key) {
        return key instanceof String ? (String) key : String.valueOf(key);
    }

    // ==================== 基础读写 ====================

    @Override
    public Object get(Object key) {
        return redisTemplate.opsForValue().get(toKey(key));
    }

    @Override
    public String getString(Object key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        LOG.warn("Key [{}] 的值类型非 String，实际类型: {}", toKey(key), value.getClass().getSimpleName());
        return value.toString();
    }

    @Override
    public void put(Object key, Object value) {
        redisTemplate.opsForValue().set(toKey(key), value);
    }

    @Override
    public void put(Object key, Object value, Long exp) {
        redisTemplate.opsForValue().set(toKey(key), value, exp, TimeUnit.SECONDS);
    }

    @Override
    public void put(Object key, Object value, Long exp, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(toKey(key), value, exp, timeUnit);
    }

    @Override
    public Boolean remove(Object key) {
        return redisTemplate.delete(toKey(key));
    }

    /**
     * setIfAbsent（SETNX + PEXPIRE 原子操作）
     */
    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit timeUnit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, timeUnit);
    }

    /**
     * 设置 key 过期时间
     */
    public Boolean expire(String key, long timeout, TimeUnit timeUnit) {
        return redisTemplate.expire(key, timeout, timeUnit);
    }

    /**
     * Set 添加成员（SADD）
     */
    public Long setAdd(String key, String... values) {
        return redisTemplate.opsForSet().add(key, (Object[]) values);
    }

    /**
     * Set 移除成员（SREM）
     */
    public Long setRemove(String key, String... values) {
        return redisTemplate.opsForSet().remove(key, (Object[]) values);
    }

    /**
     * Set 获取所有成员（SMEMBERS）
     */
    public Set<Object> setMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 删除 key
     */
    public Boolean deleteObject(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 写入缓存（带过期时间）
     */
    public void setCacheObject(String key, Object value, long timeout, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    @Override
    public boolean hasKey(Object key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(toKey(key)));
    }

    // ==================== 批量操作 ====================

    @Override
    public List<Object> multiGet(Collection keys) {
        List<String> stringKeys = toStringKeys(keys);
        List<Object> result = redisTemplate.opsForValue().multiGet(stringKeys);
        return result != null ? result : Collections.emptyList();
    }

    @Override
    public void multiSet(Map map) {
        Map<String, Object> stringMap = new HashMap<>(map.size());
        map.forEach((k, v) -> stringMap.put(toKey(k), v));
        redisTemplate.opsForValue().multiSet(stringMap);
    }

    @Override
    public void multiDel(Collection keys) {
        redisTemplate.delete(toStringKeys(keys));
    }

    // ==================== Hash 操作 ====================

    @Override
    public void putHash(Object key, Object hashKey, Object hashValue) {
        redisTemplate.opsForHash().put(toKey(key), toKey(hashKey), hashValue);
    }

    @Override
    public void putAllHash(Object key, Map map) {
        redisTemplate.opsForHash().putAll(toKey(key), map);
    }

    @Override
    public Object getHash(Object key, Object hashKey) {
        return redisTemplate.opsForHash().get(toKey(key), toKey(hashKey));
    }

    @Override
    public Map<Object, Object> getHash(Object key) {
        return redisTemplate.opsForHash().entries(toKey(key));
    }

    // ==================== SCAN 安全删除 ====================

    /**
     * 使用 SCAN 安全获取匹配 pattern 的所有 key。
     */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        redisTemplate.execute((RedisConnection connection) -> {
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match(pattern).count(SCAN_COUNT).build())) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                LOG.error("SCAN keys 异常: pattern={}", pattern, e);
            }
            return null;
        });
        return keys;
    }

    /**
     * 分批安全删除匹配 pattern 的所有 key。
     */
    private void scanDelete(String pattern) {
        Set<String> keys = scanKeys(pattern);
        if (keys.isEmpty()) {
            return;
        }
        // 分批删除，避免一次性删除过多导致阻塞
        List<String> keyList = new ArrayList<>(keys);
        for (int i = 0; i < keyList.size(); i += BATCH_DELETE_SIZE) {
            int end = Math.min(i + BATCH_DELETE_SIZE, keyList.size());
            redisTemplate.delete(keyList.subList(i, end));
        }
        LOG.debug("SCAN 模糊删除完成: pattern={}, total={}", pattern, keys.size());
    }

    @Override
    public void vagueDel(Object key) {
        scanDelete(toKey(key) + "*");
    }

    @Override
    public void clear() {
        scanDelete("*");
    }

    // ==================== Keys 查询 ====================

    @Override
    public List<Object> keys(String pattern) {
        return new ArrayList<>(scanKeys(pattern));
    }

    @Override
    public List<Object> keysBlock(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        return keys != null ? new ArrayList<>(keys) : Collections.emptyList();
    }

    // ==================== 计数器（去重统计） ====================

    /**
     * Lua 脚本：原子性批量获取 HyperLogLog 计数
     */
    private static final DefaultRedisScript<List> HLL_COUNT_SCRIPT;

    static {
        HLL_COUNT_SCRIPT = new DefaultRedisScript<>();
        HLL_COUNT_SCRIPT.setResultType(List.class);
        HLL_COUNT_SCRIPT.setScriptText(
                "local results = {} " +
                        "for i, key in ipairs(KEYS) do " +
                        "  results[i] = tonumber(redis.call('pfcount', key)) or 0 " +
                        "end " +
                        "return results");
    }

    @Override
    public Long cumulative(Object key, Object value) {
        return redisTemplate.opsForHyperLogLog().add(toKey(key), toKey(value));
    }

    @Override
    public Long counter(Object key) {
        return redisTemplate.opsForHyperLogLog().size(toKey(key));
    }

    @Override
    public List multiCounter(Collection keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> stringKeys = toStringKeys(keys);
        return redisTemplate.execute(HLL_COUNT_SCRIPT, stringKeys);
    }

    @Override
    public Long mergeCounter(Object... keys) {
        if (keys == null || keys.length == 0) {
            return 0L;
        }
        String targetKey = toKey(keys[0]);
        String[] sourceKeys = new String[keys.length - 1];
        for (int i = 1; i < keys.length; i++) {
            sourceKeys[i - 1] = toKey(keys[i]);
        }
        return redisTemplate.opsForHyperLogLog().union(targetKey, sourceKeys);
    }

    // ==================== 原子计数器（incr） ====================

    @Override
    public Long incr(String key, long liveTime) {
        RedisAtomicLong counter = new RedisAtomicLong(key, redisTemplate.getConnectionFactory());
        Long increment = counter.getAndIncrement();
        if (increment == 0 && liveTime > 0) {
            counter.expire(liveTime, TimeUnit.SECONDS);
        }
        return increment;
    }

    @Override
    public Long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    // ==================== Sorted Set 操作 ====================

    @Override
    public void incrementScore(String sortedSetName, String keyword) {
        redisTemplate.opsForZSet().incrementScore(sortedSetName, keyword, 1);
    }

    @Override
    public void incrementScore(String sortedSetName, String keyword, Integer score) {
        redisTemplate.opsForZSet().incrementScore(sortedSetName, keyword, score);
    }

    @Override
    public Set<ZSetOperations.TypedTuple<Object>> reverseRangeWithScores(String sortedSetName, Integer start, Integer end) {
        return redisTemplate.opsForZSet().reverseRangeWithScores(sortedSetName, start, end);
    }

    @Override
    public Set<ZSetOperations.TypedTuple<Object>> reverseRangeWithScores(String sortedSetName, Integer count) {
        return reverseRangeWithScores(sortedSetName, 0, count - 1);
    }

    @Override
    public boolean zAdd(String key, long score, String value) {
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(key, value, score));
    }

    @Override
    public Set<ZSetOperations.TypedTuple<Object>> zRangeByScore(String key, long from, long to) {
        return redisTemplate.opsForZSet().rangeByScoreWithScores(key, from, to);
    }

    @Override
    public Long zRemove(String key, String... value) {
        return redisTemplate.opsForZSet().remove(key, (Object[]) value);
    }

    @Override
    public Long zRemoveRangeByScore(String key, long from, long to) {
        return redisTemplate.opsForZSet().removeRangeByScore(key, from, to);
    }

    @Override
    public Long zCard(String key) {
        return redisTemplate.opsForZSet().zCard(key);
    }

    @Override
    public Long zRemRangeByRank(String key, long start, long end) {
        return redisTemplate.opsForZSet().removeRange(key, start, end);
    }

    @Override
    public Set<Object> zRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    // ==================== 内部辅助 ====================

    private List<String> toStringKeys(Collection keys) {
        List<String> stringKeys = new ArrayList<>(keys.size());
        for (Object k : keys) {
            stringKeys.add(toKey(k));
        }
        return stringKeys;
    }
}
