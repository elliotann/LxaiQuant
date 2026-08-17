# 交易机器人后台管理功能

## 概述

⚠️ **重要说明**：TradingBotController 已迁移至 ai-quant 模块中，提供统一的API接口访问。

交易机器人(TradingBot)是量化交易系统的核心组件之一，用于执行交易策略、管理交易订单和控制风险。本模块实现了TradingBot的完整后台管理功能。

交易机器人(TradingBot)是量化交易系统的核心组件之一，用于执行交易策略、管理交易订单和控制风险。本模块实现了TradingBot的完整后台管理功能。

## 核心实体

### TradingBot 实体

```java
@Data
@TableName("trading_bot")
public class TradingBot implements Serializable {
    private Long id;                    // 主键ID
    private String botId;              // 机器人唯一标识
    private String botName;            // 机器人名称
    private String userId;             // 所属用户ID
    private String accountId;          // 使用的交易账户ID
    private String strategyId;         // 使用的策略ID

    // 交易配置
    private String tradingPair;        // 交易对
    private BigDecimal allocatedCapital; // 分配的资金额度
    private BigDecimal currentCapital;   // 当前剩余资金

    // 状态管理
    private String status;             // 机器人状态
    private LocalDateTime startTime;   // 开始时间
    private LocalDateTime lastSignalTime; // 最后信号时间

    // 配置和统计
    private String configuration;      // 配置信息（JSON）
    private String statistics;         // 统计信息（JSON）

    // 其他字段
    private Boolean enabled;           // 是否启用
    private String remark;             // 备注
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
    private String createdBy;          // 创建者ID
    private String updatedBy;          // 更新者ID
}
```

### 状态枚举

```java
public enum BotStatus {
    CREATED("CREATED", "已创建"),
    RUNNING("RUNNING", "运行中"),
    PAUSED("PAUSED", "已暂停"),
    STOPPED("STOPPED", "已停止"),
    ERROR("ERROR", "错误状态");
}
```

## 核心功能

### 1. 机器人生命周期管理

- **创建机器人**：创建新的交易机器人实例
- **启动机器人**：将机器人状态从CREATED/STOPPED/PAUSED转换为RUNNING
- **停止机器人**：将机器人状态设置为STOPPED
- **暂停机器人**：将运行中的机器人暂停
- **恢复机器人**：恢复已暂停的机器人
- **删除机器人**：删除已停止的机器人

### 2. 机器人配置管理

- **基本配置**：机器人名称、交易对、资金分配等
- **关联配置**：用户、账户、策略关联
- **扩展配置**：JSON格式的配置信息存储

### 3. 状态监控

- **运行状态查询**：获取机器人当前运行状态
- **统计信息管理**：更新和查询机器人统计数据
- **最后信号时间**：记录机器人最后产生交易信号的时间

### 4. 数据查询

- **分页查询**：支持多条件分页查询机器人列表
- **按用户查询**：查询特定用户的所有机器人
- **按策略查询**：查询使用特定策略的所有机器人
- **按账户查询**：查询使用特定账户的所有机器人
- **按状态查询**：查询特定状态的所有机器人

## API 接口

### 基本 CRUD 操作

```
GET    /api/trading-bots          # 分页查询机器人
GET    /api/trading-bots/{botId}  # 根据ID查询机器人
POST   /api/trading-bots          # 创建机器人
PUT    /api/trading-bots/{botId}  # 更新机器人
DELETE /api/trading-bots/{botId}  # 删除机器人
```

### 状态管理

```
POST   /api/trading-bots/{botId}/start     # 启动机器人
POST   /api/trading-bots/{botId}/stop      # 停止机器人
POST   /api/trading-bots/{botId}/pause     # 暂停机器人
POST   /api/trading-bots/{botId}/resume    # 恢复机器人
POST   /api/trading-bots/{botId}/status    # 更新机器人状态
POST   /api/trading-bots/batch/status      # 批量更新状态
```

### 专项查询

```
GET    /api/trading-bots/user/{userId}       # 按用户查询
GET    /api/trading-bots/strategy/{strategyId} # 按策略查询
GET    /api/trading-bots/account/{accountId}   # 按账户查询
GET    /api/trading-bots/status/{status}       # 按状态查询
```

### 监控和管理

```
POST   /api/trading-bots/{botId}/capital         # 更新资金
POST   /api/trading-bots/{botId}/statistics      # 更新统计信息
GET    /api/trading-bots/{botId}/running-status # 获取运行状态
GET    /api/trading-bots/stats/status           # 获取状态统计
```

## 数据库设计

### trading_bot 表结构

```sql
CREATE TABLE trading_bot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bot_id VARCHAR(64) NOT NULL UNIQUE,
    bot_name VARCHAR(100) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    strategy_id VARCHAR(64) NOT NULL,
    trading_pair VARCHAR(50) NOT NULL,
    allocated_capital DECIMAL(18,4) NOT NULL,
    current_capital DECIMAL(18,4) NOT NULL,
    status VARCHAR(20) DEFAULT 'CREATED',
    start_time DATETIME(3),
    last_signal_time DATETIME(3),
    configuration JSON,
    statistics JSON,
    enabled BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by VARCHAR(64),
    updated_by VARCHAR(64),

    INDEX idx_bot_id (bot_id),
    INDEX idx_user_id (user_id),
    INDEX idx_account_id (account_id),
    INDEX idx_strategy_id (strategy_id),
    INDEX idx_status (status),
    INDEX idx_enabled (enabled),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_updated_at (updated_at DESC)
);
```

## 架构特点

### 1. 领域驱动设计 (DDD)

- **聚合根**：TradingBot 作为交易机器人的聚合根
- **实体完整性**：确保业务规则和数据一致性
- **职责分离**：清晰的层次结构和职责划分

### 2. 状态机模式

- **状态转换**：明确定义的状态转换规则
- **状态验证**：防止非法状态转换
- **状态持久化**：状态变更的数据库记录

### 3. 配置灵活性

- **JSON配置**：支持复杂的配置信息存储
- **扩展性**：易于添加新的配置项
- **版本控制**：配置变更的可追溯性

### 4. 监控和统计

- **运行状态**：实时监控机器人运行状态
- **统计信息**：交易绩效和风险指标统计
- **审计日志**：完整的操作日志记录

## 使用示例

### 创建机器人

```json
POST /api/trading-bots
{
    "botName": "BTC趋势跟踪机器人",
    "userId": "USER001",
    "accountId": "ACCOUNT001",
    "strategyId": "STRATEGY001",
    "tradingPair": "BTC-USDT",
    "allocatedCapital": 10000.00,
    "configuration": "{\"riskLevel\": \"MEDIUM\", \"maxPosition\": 1}"
}
```

### 启动机器人

```json
POST /api/trading-bots/BOT-001/start
```

### 查询机器人列表

```json
GET /api/trading-bots?page=1&limit=10&status=RUNNING
```

## 注意事项

1. **状态管理**：严格按照状态机规则进行状态转换
2. **资金管理**：allocatedCapital 和 currentCapital 的同步更新
3. **关联验证**：创建时验证用户、账户、策略的存在性
4. **并发控制**：多线程环境下的状态一致性保证
5. **错误处理**：完善的异常处理和错误信息返回
6. **审计记录**：重要操作的日志记录和审计

## 扩展计划

- [ ] 机器人模板功能
- [ ] 批量操作优化
- [ ] 实时监控仪表板
- [ ] 性能指标统计
- [ ] 风险控制集成
- [ ] 策略回测集成
