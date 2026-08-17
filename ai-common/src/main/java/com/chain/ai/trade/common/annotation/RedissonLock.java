package com.chain.ai.trade.common.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Redisson分布式锁注解
 * 用于方法级别，自动加锁和解锁
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedissonLock {

    /**
     * 锁的key
     * 支持SpEL表达式，例如："#symbol"、"#strategy.id"
     */
    String key();

    /**
     * 锁类型
     */
    LockType lockType() default LockType.REENTRANT;

    /**
     * 等待时间（毫秒）
     * 默认5秒
     */
    long waitTime() default 5000;

    /**
     * 持有时间（毫秒）
     * 默认30秒
     * 注意：Redisson有看门狗机制，如果设置为-1则会自动续期
     */
    long leaseTime() default 30000;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 获取锁失败时的处理策略
     */
    LockFailStrategy failStrategy() default LockFailStrategy.THROW_EXCEPTION;

    /**
     * 锁类型枚举
     */
    enum LockType {
        /**
         * 可重入锁（默认）
         */
        REENTRANT,
        /**
         * 公平锁
         */
        FAIR,
        /**
         * 读锁
         */
        READ,
        /**
         * 写锁
         */
        WRITE
    }

    /**
     * 锁失败策略枚举
     */
    enum LockFailStrategy {
        /**
         * 抛出异常（默认）
         */
        THROW_EXCEPTION,
        /**
         * 忽略，继续执行
         */
        IGNORE,
        /**
         * 返回null
         */
        RETURN_NULL,
        /**
         * 返回指定值
         */
        RETURN_VALUE
    }

    /**
     * 获取锁失败时返回的值（仅当failStrategy为RETURN_VALUE时生效）
     */
    String returnValue() default "";
}