package com.chain.ai.trade.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于Redisson的分布式锁工具类
 * 提供多种锁类型：可重入锁、公平锁、读写锁等
 */
@Slf4j
@Component
public class RedissonDistributedLock {

    @Autowired
    private RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "distributed:lock:";
    private static final long DEFAULT_WAIT_TIME = 5000; // 默认等待5秒
    private static final long DEFAULT_LEASE_TIME = 30000; // 默认持有30秒
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MILLISECONDS;
    private static final int FAIR_LOCK_RETRY_COUNT = 2;
    private static final long FAIR_LOCK_RETRY_DELAY_MS = 200;

    /**
     * 获取可重入锁
     *
     * @param lockKey 锁的key
     * @return RLock对象
     */
    public RLock getLock(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        return redissonClient.getLock(fullKey);
    }

    /**
     * 获取公平锁
     *
     * @param lockKey 锁的key
     * @return RLock对象
     */
    public RLock getFairLock(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        return redissonClient.getFairLock(fullKey);
    }

    /**
     * 获取读锁
     *
     * @param lockKey 锁的key
     * @return RLock对象
     */
    public RLock getReadLock(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(fullKey);
        return readWriteLock.readLock();
    }

    /**
     * 获取写锁
     *
     * @param lockKey 锁的key
     * @return RLock对象
     */
    public RLock getWriteLock(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(fullKey);
        return readWriteLock.writeLock();
    }

    /**
     * 尝试获取锁并执行操作
     *
     * @param lockKey     锁的key
     * @param waitTime    等待时间
     * @param leaseTime   持有时间
     * @param timeUnit    时间单位
     * @param supplier    需要执行的操作
     * @param <T>         返回值类型
     * @return 操作结果
     * @throws Exception 获取锁失败或执行操作异常
     */
    public <T> T tryLockAndExecute(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit, Supplier<T> supplier) throws Exception {
        RLock lock = getLock(lockKey);
        boolean locked = false;
        
        try {
            locked = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (locked) {
                log.debug("✅ 成功获取分布式锁: {}", lockKey);
                return supplier.get();
            } else {
                log.warn("⚠️ 获取分布式锁失败: {}", lockKey);
                throw new RuntimeException("获取分布式锁失败: " + lockKey);
            }
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("🔓 释放分布式锁: {}", lockKey);
            }
        }
    }

    /**
     * 尝试获取锁并执行操作（使用默认参数）
     *
     * @param lockKey   锁的key
     * @param supplier  需要执行的操作
     * @param <T>       返回值类型
     * @return 操作结果
     * @throws Exception 获取锁失败或执行操作异常
     */
    public <T> T tryLockAndExecute(String lockKey, Supplier<T> supplier) throws Exception {
        return tryLockAndExecute(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, DEFAULT_TIME_UNIT, supplier);
    }

    /**
     * 尝试获取锁并执行操作（无返回值）
     *
     * @param lockKey     锁的key
     * @param waitTime    等待时间
     * @param leaseTime   持有时间
     * @param timeUnit    时间单位
     * @param runnable    需要执行的操作
     * @throws Exception 获取锁失败或执行操作异常
     */
    public void tryLockAndExecute(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit, Runnable runnable) throws Exception {
        tryLockAndExecute(lockKey, waitTime, leaseTime, timeUnit, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 尝试获取锁并执行操作（无返回值，使用默认参数）
     *
     * @param lockKey   锁的key
     * @param runnable  需要执行的操作
     * @throws Exception 获取锁失败或执行操作异常
     */
    public void tryLockAndExecute(String lockKey, Runnable runnable) throws Exception {
        tryLockAndExecute(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, DEFAULT_TIME_UNIT, runnable);
    }

    /**
     * 使用公平锁执行操作
     *
     * @param lockKey     锁的key
     * @param waitTime    等待时间
     * @param leaseTime   持有时间
     * @param timeUnit    时间单位
     * @param supplier    需要执行的操作
     * @param <T>         返回值类型
     * @return 操作结果
     * @throws Exception 获取锁失败或执行操作异常
     */
    public <T> T tryFairLockAndExecute(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit, Supplier<T> supplier) throws Exception {
        RLock lock = getFairLock(lockKey);
        boolean locked = false;

        try {
            int attempts = 0;
            while (!locked && attempts <= FAIR_LOCK_RETRY_COUNT) {
                attempts++;
                locked = lock.tryLock(waitTime, leaseTime, timeUnit);
                if (locked) {
                    log.debug("✅ 成功获取公平锁: {}", lockKey);
                    return supplier.get();
                }
                log.warn("⚠️ 获取公平锁失败: {}, attempt={}", lockKey, attempts);
                if (attempts <= FAIR_LOCK_RETRY_COUNT) {
                    TimeUnit.MILLISECONDS.sleep(FAIR_LOCK_RETRY_DELAY_MS);
                }
            }
            throw new RuntimeException("获取公平锁失败: " + lockKey);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("🔓 释放公平锁: {}", lockKey);
            }
        }
    }

    /**
     * 使用读写锁执行操作（读锁）
     *
     * @param lockKey     锁的key
     * @param waitTime    等待时间
     * @param leaseTime   持有时间
     * @param timeUnit    时间单位
     * @param supplier    需要执行的操作
     * @param <T>         返回值类型
     * @return 操作结果
     * @throws Exception 获取锁失败或执行操作异常
     */
    public <T> T tryReadLockAndExecute(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit, Supplier<T> supplier) throws Exception {
        RLock lock = getReadLock(lockKey);
        boolean locked = false;
        
        try {
            locked = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (locked) {
                log.debug("✅ 成功获取读锁: {}", lockKey);
                return supplier.get();
            } else {
                log.warn("⚠️ 获取读锁失败: {}", lockKey);
                throw new RuntimeException("获取读锁失败: " + lockKey);
            }
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("🔓 释放读锁: {}", lockKey);
            }
        }
    }

    /**
     * 使用读写锁执行操作（写锁）
     *
     * @param lockKey     锁的key
     * @param waitTime    等待时间
     * @param leaseTime   持有时间
     * @param timeUnit    时间单位
     * @param supplier    需要执行的操作
     * @param <T>         返回值类型
     * @return 操作结果
     * @throws Exception 获取锁失败或执行操作异常
     */
    public <T> T tryWriteLockAndExecute(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit, Supplier<T> supplier) throws Exception {
        RLock lock = getWriteLock(lockKey);
        boolean locked = false;
        
        try {
            locked = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (locked) {
                log.debug("✅ 成功获取写锁: {}", lockKey);
                return supplier.get();
            } else {
                log.warn("⚠️ 获取写锁失败: {}", lockKey);
                throw new RuntimeException("获取写锁失败: " + lockKey);
            }
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("🔓 释放写锁: {}", lockKey);
            }
        }
    }

    /**
     * 检查锁是否被当前线程持有
     *
     * @param lockKey 锁的key
     * @return 是否被当前线程持有
     */
    public boolean isLockedByCurrentThread(String lockKey) {
        RLock lock = getLock(lockKey);
        return lock.isHeldByCurrentThread();
    }

    /**
     * 检查锁是否存在（是否被任何线程持有）
     *
     * @param lockKey 锁的key
     * @return 锁是否存在
     */
    public boolean isLocked(String lockKey) {
        RLock lock = getLock(lockKey);
        return lock.isLocked();
    }

    /**
     * 强制释放锁（谨慎使用）
     *
     * @param lockKey 锁的key
     */
    public void forceUnlock(String lockKey) {
        RLock lock = getLock(lockKey);
        if (lock.isLocked() && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.warn("⚠️ 强制释放分布式锁: {}", lockKey);
        }
    }

    /**
     * 生成交易相关的锁key
     *
     * @param symbol    交易对
     * @param lockType  锁类型
     * @return 锁key
     */
    public static String generateTradeLockKey(String symbol, String lockType) {
        return String.format("trade:%s:%s", symbol, lockType);
    }

    /**
     * 生成策略相关的锁key
     *
     * @param strategyId 策略ID
     * @param lockType   锁类型
     * @return 锁key
     */
    public static String generateStrategyLockKey(String strategyId, String lockType) {
        return String.format("strategy:%s:%s", strategyId, lockType);
    }

    /**
     * 生成用户相关的锁key
     *
     * @param userId   用户ID
     * @param lockType 锁类型
     * @return 锁key
     */
    public static String generateUserLockKey(String userId, String lockType) {
        return String.format("user:%s:%s", userId, lockType);
    }

    /**
     * 生成订单相关的锁key
     *
     * @param orderId  订单ID
     * @param lockType 锁类型
     * @return 锁key
     */
    public static String generateOrderLockKey(String orderId, String lockType) {
        // 锁key: period:robot:account:symbol:period:direction

        return String.format("order:%s:%s", orderId, lockType);
    }

    /**
     * 生成机器人相关的锁key
     *
     * @param robotId  机器人ID
     * @param lockType 锁类型
     * @return 锁key
     */
    public static String generateRobotLockKey(String robotId, String lockType) {
        return String.format("robot:%s:%s", robotId, lockType);
    }

    /**
     * 生成信号相关的锁key
     *
     * @param signalId 信号ID
     * @param lockType 锁类型
     * @return 锁key
     */
    public static String generateSignalLockKey(String signalId, String lockType) {
        return String.format("signal:%s:%s", signalId, lockType);
    }
}
