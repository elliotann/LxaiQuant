package com.chain.ai.trade.common.aspect;

import com.chain.ai.trade.common.annotation.RedissonLock;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/**
 * Redisson分布式锁切面（简化版）
 * 由于AspectJ依赖问题，暂时提供简化实现
 * 完整功能请参考RedissonDistributedLock工具类
 */
@Slf4j
@Component
public class RedissonLockAspect {

    @Autowired
    private RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "distributed:lock:";

    /**
     * 简化的锁执行方法
     * 由于AspectJ依赖问题，暂时不实现完整的切面功能
     * 可以使用RedissonDistributedLock工具类替代
     */
    public <T> T executeWithLock(RedissonLock redissonLock, String lockKey, LockableOperation<T> operation) throws Exception {
        String fullKey = LOCK_PREFIX + lockKey;

        // 获取锁
        RLock lock = getLock(fullKey, redissonLock.lockType());

        boolean locked = false;
        try {
            // 尝试获取锁
            locked = lock.tryLock(redissonLock.waitTime(), redissonLock.leaseTime(), redissonLock.timeUnit());

            if (locked) {
                log.debug("✅ 成功获取分布式锁: {}", lockKey);
                // 执行操作
                return operation.execute();
            } else {
                log.warn("⚠️ 获取分布式锁失败: {}", lockKey);
                // 处理获取锁失败的情况
                return handleLockFailure(redissonLock, operation);
            }
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("🔓 释放分布式锁: {}", lockKey);
            }
        }
    }

    /**
     * 根据锁类型获取对应的锁
     */
    private RLock getLock(String lockKey, RedissonLock.LockType lockType) {
        switch (lockType) {
            case FAIR:
                return redissonClient.getFairLock(lockKey);
            case READ:
                RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
                return readWriteLock.readLock();
            case WRITE:
                RReadWriteLock writeLock = redissonClient.getReadWriteLock(lockKey);
                return writeLock.writeLock();
            case REENTRANT:
            default:
                return redissonClient.getLock(lockKey);
        }
    }

    /**
     * 处理获取锁失败的情况
     */
    private <T> T handleLockFailure(RedissonLock redissonLock, LockableOperation<T> operation) throws Exception {
        switch (redissonLock.failStrategy()) {
            case IGNORE:
                log.warn("⚠️ 获取分布式锁失败，忽略并继续执行方法");
                return operation.execute();
            case RETURN_NULL:
                log.warn("⚠️ 获取分布式锁失败，返回null");
                return null;
            case RETURN_VALUE:
                String returnValue = redissonLock.returnValue();
                log.warn("⚠️ 获取分布式锁失败，返回指定值: {}", returnValue);
                return convertReturnValue(returnValue, operation.getReturnType());
            case THROW_EXCEPTION:
            default:
                throw new RuntimeException("获取分布式锁失败，无法执行方法");
        }
    }

    /**
     * 转换返回值
     */
    @SuppressWarnings("unchecked")
    private <T> T convertReturnValue(String returnValue, Class<T> returnType) {
        if (returnValue == null || returnValue.isEmpty()) {
            return null;
        }

        try {
            if (returnType == String.class) {
                return (T) returnValue;
            } else if (returnType == Integer.class || returnType == int.class) {
                return (T) Integer.valueOf(Integer.parseInt(returnValue));
            } else if (returnType == Long.class || returnType == long.class) {
                return (T) Long.valueOf(Long.parseLong(returnValue));
            } else if (returnType == Boolean.class || returnType == boolean.class) {
                return (T) Boolean.valueOf(Boolean.parseBoolean(returnValue));
            } else if (returnType == Double.class || returnType == double.class) {
                return (T) Double.valueOf(Double.parseDouble(returnValue));
            } else if (returnType == Float.class || returnType == float.class) {
                return (T) Float.valueOf(Float.parseFloat(returnValue));
            } else {
                // 对于其他类型，尝试使用字符串构造
                return (T) returnValue;
            }
        } catch (Exception e) {
            log.warn("无法转换返回值: {} 到类型: {}", returnValue, returnType, e);
            return null;
        }
    }

    /**
     * 可锁操作接口
     */
    @FunctionalInterface
    public interface LockableOperation<T> {
        T execute() throws Exception;

        default Class<T> getReturnType() {
            // 这是一个简化实现，实际使用时需要更复杂的类型推断
            return (Class<T>) Object.class;
        }
    }
}
