# 分布式锁使用指南

## 概述

本项目提供了基于Redis的分布式锁工具类 `RedisDistributedLock`，用于在分布式环境中实现资源的互斥访问。该工具类基于Redis的SETNX命令实现，支持锁的超时、自动释放和防止误释放等功能。

## 核心类

### 1. RedisDistributedLock

分布式锁工具类，提供以下主要功能：

- `tryLock(String lockKey, String requestId, long leaseTime, TimeUnit timeUnit)` - 尝试获取锁
- `unlock(String lockKey, String requestId)` - 释放锁
- `tryLockAndExecute(...)` - 获取锁并执行操作（自动释放）
- `isLocked(String lockKey)` - 检查锁是否存在
- `getLockRemainingTime(String lockKey)` - 获取锁剩余时间

### 2. RedisDistributedLockExampleService

使用示例服务，展示在交易场景中如何使用分布式锁。

### 3. DistributedLockTestController

测试控制器，提供HTTP接口用于测试分布式锁功能。

## 使用方法

### 1. 基本使用

```java
@Autowired
private RedisDistributedLock distributedLock;

// 获取锁
String lockKey = "trade:order:ETH-USDT";
String requestId = RedisDistributedLock.generateRequestId();
boolean locked = distributedLock.tryLock(lockKey, requestId, 30000, TimeUnit.MILLISECONDS);

if (locked) {
    try {
        // 执行需要加锁的操作
        processOrder();
    } finally {
        // 释放锁
        distributedLock.unlock(lockKey, requestId);
    }
}
```

### 2. 使用工具方法（推荐）

```java
@Autowired
private RedisDistributedLock distributedLock;

String lockKey = RedisDistributedLock.generateTradeLockKey("ETH-USDT", "order");
String requestId = RedisDistributedLock.generateRequestId();

try {
    distributedLock.tryLockAndExecute(lockKey, requestId, () -> {
        // 执行需要加锁的操作
        processOrder();
        return "操作结果";
    });
} catch (Exception e) {
    // 处理异常
}
```

### 3. 在交易场景中使用

#### 防止重复下单

```java
public String placeOrder(String symbol, double amount, String userId) {
    String lockKey = RedisDistributedLock.generateTradeLockKey(symbol, "order");
    String requestId = RedisDistributedLock.generateRequestId();
    
    try {
        return distributedLock.tryLockAndExecute(lockKey, requestId, () -> {
            // 检查是否已存在相同订单
            if (checkDuplicateOrder(symbol, userId)) {
                throw new RuntimeException("重复下单");
            }
            
            // 创建订单
            return createOrder(symbol, amount, userId);
        });
    } catch (Exception e) {
        throw new RuntimeException("下单失败", e);
    }
}
```

#### 策略执行锁

```java
public void executeStrategy(String strategyId, String symbol) {
    String lockKey = RedisDistributedLock.generateStrategyLockKey(strategyId, "execute");
    String requestId = RedisDistributedLock.generateRequestId();
    
    try {
        distributedLock.tryLockAndExecute(lockKey, requestId, () -> {
            // 执行策略
            runStrategy(strategyId, symbol);
        });
    } catch (Exception e) {
        log.error("策略执行失败", e);
    }
}
```

#### 机器人交易锁

```java
public void executeRobotTrade(String robotId, String symbol, String side) {
    String lockKey = RedisDistributedLock.generateRobotLockKey(robotId, "trade");
    String requestId = RedisDistributedLock.generateRequestId();
    
    try {
        distributedLock.tryLockAndExecute(lockKey, requestId, () -> {
            // 执行机器人交易
            executeTrade(robotId, symbol, side);
        });
    } catch (Exception e) {
        log.error("机器人交易失败", e);
    }
}
```

## 锁Key生成规则

工具类提供了以下锁key生成方法：

- `generateTradeLockKey(String symbol, String lockType)` - 交易锁
- `generateStrategyLockKey(String strategyId, String lockType)` - 策略锁
- `generateUserLockKey(String userId, String lockType)` - 用户锁
- `generateOrderLockKey(String orderId, String lockType)` - 订单锁
- `generateRobotLockKey(String robotId, String lockType)` - 机器人锁

## 请求ID生成

使用 `RedisDistributedLock.generateRequestId()` 生成唯一的请求ID，确保只有锁的持有者才能释放锁。

## 测试接口

项目提供了测试控制器，可以通过以下HTTP接口测试分布式锁功能：

### 1. 测试基本锁功能
```
POST /api/distributed-lock/test/basic?lockKey=test:lock
```

### 2. 测试并发下单
```
POST /api/distributed-lock/test/concurrent-order?symbol=ETH-USDT&threadCount=5&amount=1000
```

### 3. 测试策略执行
```
POST /api/distributed-lock/test/strategy-execution?strategyId=strategy_001&symbol=ETH-USDT&action=buy
```

### 4. 检查锁状态
```
GET /api/distributed-lock/status/{lockKey}
```

### 5. 测试机器人交易
```
POST /api/distributed-lock/test/robot-trade?robotId=robot_001&symbol=ETH-USDT&side=BUY
```

### 6. 测试批量处理
```
POST /api/distributed-lock/test/batch-processing?userId=user_001&batchSize=10
```

## 最佳实践

1. **锁粒度**：尽量使用细粒度锁，避免锁住整个系统
2. **锁超时**：设置合理的锁超时时间，防止死锁
3. **请求ID**：每次获取锁都使用唯一的请求ID
4. **异常处理**：确保在finally块中释放锁
5. **重试机制**：对于重要的操作，可以实现重试机制
6. **监控**：监控锁的使用情况，及时发现死锁或锁竞争问题

## 注意事项

1. Redis必须是可用的，否则分布式锁将失效
2. 锁的超时时间应该大于业务操作的最长时间
3. 在高并发场景下，可能需要调整重试策略和等待时间
4. 分布式锁不能替代数据库的事务处理

## 故障排除

### 1. 获取锁失败
- 检查Redis连接是否正常
- 检查锁key是否被其他进程持有
- 调整等待时间和重试策略

### 2. 释放锁失败
- 检查请求ID是否正确
- 检查锁是否已超时自动释放
- 查看Redis日志

### 3. 性能问题
- 减少锁的持有时间
- 使用更细粒度的锁
- 优化业务逻辑，减少需要加锁的操作

## 扩展建议

1. 可以添加锁的监控和告警功能
2. 可以实现锁的续期机制
3. 可以添加锁的统计信息
4. 可以支持多种锁类型（公平锁、读写锁等）
