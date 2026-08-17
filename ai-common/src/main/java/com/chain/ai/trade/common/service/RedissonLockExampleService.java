package com.chain.ai.trade.common.service;

import com.chain.ai.trade.common.annotation.RedissonLock;
import com.chain.ai.trade.common.utils.RedissonDistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redisson分布式锁使用示例服务
 * 展示两种使用方式：注解方式和工具类方式
 */
@Slf4j
@Service
public class RedissonLockExampleService {

    @Autowired
    private RedissonDistributedLock distributedLock;

    /**
     * 使用注解方式加锁（可重入锁）
     * 锁key支持SpEL表达式
     * 
     * @param symbol 交易对
     * @param amount 数量
     * @param userId 用户ID
     * @return 处理结果
     */
    @RedissonLock(
            key = "trade:#symbol:order",
            lockType = RedissonLock.LockType.REENTRANT,
            waitTime = 3000,
            leaseTime = 10000,
            timeUnit = TimeUnit.MILLISECONDS,
            failStrategy = RedissonLock.LockFailStrategy.THROW_EXCEPTION
    )
    public String processTradeOrder(String symbol, double amount, String userId) {
        log.info("📊 处理交易订单: symbol={}, amount={}, userId={}", symbol, amount, userId);
        
        // 模拟业务处理
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 模拟订单处理逻辑
        boolean success = simulateOrderProcessing(symbol, amount, userId);
        
        if (success) {
            return "✅ 订单处理成功: " + symbol + " - " + amount;
        } else {
            return "❌ 订单处理失败: " + symbol + " - " + amount;
        }
    }

    /**
     * 使用注解方式加锁（公平锁）
     * 适用于需要公平调度的场景
     * 
     * @param strategyId 策略ID
     * @param symbol     交易对
     * @return 执行结果
     */
    @RedissonLock(
            key = "strategy:#strategyId:execute",
            lockType = RedissonLock.LockType.FAIR,
            waitTime = 5000,
            leaseTime = 30000,
            timeUnit = TimeUnit.MILLISECONDS
    )
    public String executeTradingStrategy(String strategyId, String symbol) {
        log.info("🎯 执行交易策略: strategyId={}, symbol={}", strategyId, symbol);
        
        // 模拟策略执行
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 模拟策略执行逻辑
        boolean success = simulateStrategyExecution(strategyId, symbol);
        
        if (success) {
            return "✅ 策略执行成功: " + strategyId + " - " + symbol;
        } else {
            return "❌ 策略执行失败: " + strategyId + " - " + symbol;
        }
    }

    /**
     * 使用工具类方式加锁
     * 更灵活，可以处理更复杂的锁逻辑
     * 
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 处理结果
     */
    public String processUserOrder(String userId, String orderId) {
        String lockKey = RedissonDistributedLock.generateOrderLockKey(orderId, "process");
        
        try {
            return distributedLock.tryLockAndExecute(lockKey, () -> {
                log.info("👤 处理用户订单: userId={}, orderId={}", userId, orderId);
                
                // 模拟业务处理
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // 模拟订单处理逻辑
                boolean success = simulateUserOrderProcessing(userId, orderId);
                
                if (success) {
                    return "✅ 用户订单处理成功: " + userId + " - " + orderId;
                } else {
                    return "❌ 用户订单处理失败: " + userId + " - " + orderId;
                }
            });
        } catch (Exception e) {
            log.error("处理用户订单失败", e);
            throw new RuntimeException("处理用户订单失败", e);
        }
    }

    /**
     * 使用读写锁处理数据
     * 读多写少的场景
     * 
     * @param symbol    交易对
     * @param operation 操作类型（read/write）
     */
    public void processMarketData(String symbol, String operation) {
        String lockKey = RedissonDistributedLock.generateTradeLockKey(symbol, "market-data");
        
        try {
            if ("read".equalsIgnoreCase(operation)) {
                // 使用读锁（允许多个线程同时读）
                distributedLock.tryReadLockAndExecute(lockKey, 3000, 10000, TimeUnit.MILLISECONDS, () -> {
                    log.info("📖 读取市场数据: symbol={}", symbol);
                    
                    // 模拟读取数据
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // 模拟读取逻辑
                    simulateMarketDataReading(symbol);
                    return null;
                });
            } else if ("write".equalsIgnoreCase(operation)) {
                // 使用写锁（只允许一个线程写）
                distributedLock.tryWriteLockAndExecute(lockKey, 5000, 15000, TimeUnit.MILLISECONDS, () -> {
                    log.info("✏️ 更新市场数据: symbol={}", symbol);
                    
                    // 模拟更新数据
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // 模拟更新逻辑
                    simulateMarketDataWriting(symbol);
                    return null;
                });
            }
        } catch (Exception e) {
            log.error("处理市场数据失败", e);
            throw new RuntimeException("处理市场数据失败", e);
        }
    }

    /**
     * 机器人交易执行
     * 使用公平锁确保执行顺序
     * 
     * @param robotId 机器人ID
     * @param symbol  交易对
     * @param side    交易方向
     */
    public void executeRobotTrade(String robotId, String symbol, String side) {
        String lockKey = RedissonDistributedLock.generateRobotLockKey(robotId, "trade");
        
        try {
            distributedLock.tryFairLockAndExecute(lockKey, 10000, 60000, TimeUnit.MILLISECONDS, () -> {
                log.info("🤖 机器人执行交易: robotId={}, symbol={}, side={}", robotId, symbol, side);
                
                // 模拟交易执行
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // 模拟交易逻辑
                simulateRobotTrade(robotId, symbol, side);
                
                log.info("✅ 机器人交易执行完成: robotId={}", robotId);
                return null;
            });
        } catch (Exception e) {
            log.error("机器人交易执行失败", e);
            throw new RuntimeException("机器人交易执行失败", e);
        }
    }

    /**
     * 批量处理信号数据
     * 使用写锁确保数据一致性
     * 
     * @param signalType 信号类型
     * @param batchSize  批量大小
     */
    public void batchProcessSignals(String signalType, int batchSize) {
        String lockKey = RedissonDistributedLock.generateSignalLockKey(signalType, "batch-process");
        
        try {
            distributedLock.tryLockAndExecute(lockKey, () -> {
                log.info("📦 批量处理信号数据: signalType={}, batchSize={}", signalType, batchSize);
                
                // 模拟批量处理
                for (int i = 0; i < batchSize; i++) {
                    try {
                        Thread.sleep(100);
                        log.debug("处理第 {} 个信号", i + 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                log.info("✅ 批量处理完成: signalType={}, processed={}", signalType, batchSize);
                return null;
            });
        } catch (Exception e) {
            log.error("批量处理信号数据失败", e);
            throw new RuntimeException("批量处理信号数据失败", e);
        }
    }

    /**
     * 检查锁状态
     * 
     * @param lockKey 锁key
     * @return 锁状态信息
     */
    public String checkLockStatus(String lockKey) {
        boolean isLocked = distributedLock.isLocked(lockKey);
        boolean isHeldByCurrentThread = distributedLock.isLockedByCurrentThread(lockKey);
        
        return String.format("🔒 锁状态: key=%s, isLocked=%s, isHeldByCurrentThread=%s",
                lockKey, isLocked, isHeldByCurrentThread);
    }

    // 模拟方法
    private boolean simulateOrderProcessing(String symbol, double amount, String userId) {
        log.debug("模拟订单处理: symbol={}, amount={}, userId={}", symbol, amount, userId);
        return Math.random() > 0.1; // 90%成功率
    }

    private boolean simulateStrategyExecution(String strategyId, String symbol) {
        log.debug("模拟策略执行: strategyId={}, symbol={}", strategyId, symbol);
        return Math.random() > 0.2; // 80%成功率
    }

    private boolean simulateUserOrderProcessing(String userId, String orderId) {
        log.debug("模拟用户订单处理: userId={}, orderId={}", userId, orderId);
        return Math.random() > 0.15; // 85%成功率
    }

    private void simulateMarketDataReading(String symbol) {
        log.debug("模拟读取市场数据: symbol={}", symbol);
        // 读取逻辑
    }

    private void simulateMarketDataWriting(String symbol) {
        log.debug("模拟更新市场数据: symbol={}", symbol);
        // 更新逻辑
    }

    private void simulateRobotTrade(String robotId, String symbol, String side) {
        log.debug("模拟机器人交易: robotId={}, symbol={}, side={}", robotId, symbol, side);
        // 交易逻辑
    }
}