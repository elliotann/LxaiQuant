package com.chain.ai.trade.common.utils;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import lombok.extern.slf4j.Slf4j;

/**
 * 合约规格信息获取工具类
 * 统一从 Redis 获取合约基本信息（面值、乘数），供收益计算等模块使用
 */
@Slf4j
public final class ContractSpecUtils {

    private static final String CONTRACT_INFO_KEY_PREFIX = "contract:info:";

    private ContractSpecUtils() {
    }

    /**
     * 构建 Redis key
     *
     * @param platform 平台
     * @param symbol   交易对（如 BTC-USDT-SWAP）
     * @return Redis key
     */
    public static String buildRedisKey(Exchange platform, String symbol) {
        return CONTRACT_INFO_KEY_PREFIX + platform.name() + ":" + symbol;
    }

    /**
     * 从 Redis 获取合约规格（需要注入 RedisCache）
     *
     * @param redisCache Redis 缓存实例（通常是 RedisCache 或 RedisTemplate）
     * @param platform   平台
     * @param symbol     交易对（如 BTC-USDT-SWAP）
     * @return ContractSpec，如果不存在则返回默认规格
     */
    public static ContractSpec getContractSpec(Object redisCache, Exchange platform, String symbol) {
        return getContractSpec(redisCache, platform, symbol, null);
    }

    /**
     * 规范化交易对格式（确保有 -SWAP 后缀等）
     *
     * @param platform 平台
     * @param symbol   原始交易对
     * @return 规范化后的交易对
     */
    public static String normalizeSymbol(Exchange platform, String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return symbol;
        }
        symbol = symbol.trim();
        // OKX 永续合约：纯币种名（如 BTC）→ BTC-USDT-SWAP
        if (platform == Exchange.OKX && !symbol.contains("-SWAP") && !symbol.contains("-")) {
            return symbol + "-USDT-SWAP";
        }
        // 所有平台：带分隔符但未以 -SWAP 结尾的永续合约，统一补 -SWAP 后缀
        if (!symbol.endsWith("-SWAP")) {
            return symbol + "-SWAP";
        }
        return symbol;
    }

    /**
     * 从 Redis 获取合约规格，如果不存在则通过解析器获取
     *
     * @param redisCache   Redis 缓存实例
     * @param platform     平台
     * @param symbol       交易对（如 BTC-USDT-SWAP）
     * @param specResolver 合约规格解析器（可选，用于动态获取）
     * @return ContractSpec，如果不存在则返回默认规格
     */
    public static ContractSpec getContractSpec(Object redisCache, Exchange platform, String symbol,
                                                ContractSpecResolver specResolver) {
        if (platform == null || symbol == null || symbol.trim().isEmpty()) {
            log.warn("平台或交易对为空，返回默认规格: platform={}, symbol={}", platform, symbol);
            return ContractSpec.defaultSpec();
        }

        // 规范化 symbol，与 ContractInfoGetTaskExecute 存入时的 key 保持一致
        String normalizedSymbol = normalizeSymbol(platform, symbol);
        String redisKey = buildRedisKey(platform, normalizedSymbol);

        try {
            // 从 Redis 获取
            Object cached = invokeRedisGet(redisCache, redisKey);
            if (cached instanceof ContractSpec) {
                ContractSpec spec = (ContractSpec) cached;
                log.debug("从 Redis 获取合约规格: platform={}, symbol={}, contractSize={}, contractMult={}",
                        platform, normalizedSymbol, spec.getContractSize(), spec.getContractMult());
                return spec;
            }

            // Redis 中没有，尝试通过解析器获取
            if (specResolver != null) {
                try {
                    ContractSpec spec = specResolver.resolve(platform, symbol);
                    if (spec != null) {
                        log.info("通过解析器获取合约规格: platform={}, symbol={}, contractSize={}, contractMult={}",
                                platform, normalizedSymbol, spec.getContractSize(), spec.getContractMult());
                        // 可选：存入 Redis（如果解析器支持）
                        if (specResolver.shouldCache()) {
                            invokeRedisPut(redisCache, redisKey, spec, 60L * 60 * 24 * 30);
                        }
                        return spec;
                    }
                } catch (Exception e) {
                    log.warn("解析器获取合约规格失败: platform={}, symbol={}, error={}", platform, symbol, e.getMessage());
                }
            }

            log.warn("Redis 中未找到合约规格，返回默认规格: platform={}, symbol={}", platform, normalizedSymbol);
            return ContractSpec.defaultSpec();

        } catch (Exception e) {
            log.error("获取合约规格失败: platform={}, symbol={}, error={}", platform, symbol, e.getMessage(), e);
            return ContractSpec.defaultSpec();
        }
    }

    /**
     * 调用 RedisCache.get 方法
     */
    private static Object invokeRedisGet(Object redisCache, String key) {
        if (redisCache == null) {
            return null;
        }
        if (redisCache instanceof RedisCache) {
            return ((RedisCache) redisCache).get(key);
        }
        // fallback: 反射调用
        try {
            java.lang.reflect.Method getMethod = redisCache.getClass().getMethod("get", Object.class);
            return getMethod.invoke(redisCache, key);
        } catch (Exception e) {
            log.warn("调用 RedisCache.get 失败: key={}, error={}", key, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 调用 RedisCache.put 方法
     */
    private static void invokeRedisPut(Object redisCache, String key, Object value, long expireSeconds) {
        if (redisCache == null) {
            return;
        }
        if (redisCache instanceof RedisCache) {
            ((RedisCache) redisCache).put(key, value, expireSeconds);
            return;
        }
        // fallback: 反射调用
        try {
            java.lang.reflect.Method putMethod = redisCache.getClass().getMethod("put", Object.class, Object.class, Long.class);
            putMethod.invoke(redisCache, key, value, expireSeconds);
        } catch (Exception e) {
            log.warn("调用 RedisCache.put 失败: key={}, error={}", key, e.getMessage(), e);
        }
    }

    /**
     * 合约规格解析器接口
     * 用于在 Redis 中没有缓存时，动态从交易所 API 获取合约规格
     */
    @FunctionalInterface
    public interface ContractSpecResolver {
        /**
         * 解析合约规格
         *
         * @param platform 平台
         * @param symbol   交易对
         * @return ContractSpec，如果无法获取返回 null
         */
        ContractSpec resolve(Exchange platform, String symbol);

        /**
         * 是否应该缓存解析结果到 Redis
         * 默认返回 true
         */
        default boolean shouldCache() {
            return true;
        }
    }
}
