# 持仓状态验证功能

## 概述

在订单系统中添加了持仓状态验证功能，防止在已有持仓的情况下重复开同方向的订单，提高交易系统的风险控制能力。

## 功能特性

### 1. 双向持仓支持
- **单向持仓模式**：严格禁止任何重复开仓（传统模式）
- **双向持仓模式**：允许多空同时持仓，但禁止同方向重复开仓

### 2. 灵活配置
- 可配置是否启用验证功能
- 可配置是否允许补仓操作
- 支持运行时动态调整

### 3. 完善验证
- 检查已成交持仓订单
- 检查进行中订单（OPEN/PENDING状态）
- 支持补仓场景识别

## 配置说明

```yaml
trading:
  # 双向持仓模式
  bidirectional:
    enabled: false  # 是否启用双向持仓

  # 订单验证配置
  order-validation:
    position-validation-enabled: true  # 是否启用持仓验证
    allow-add-position: true          # 是否允许补仓
    max-same-direction-positions: 1   # 单方向最大持仓数
```

## 验证逻辑

### 单向持仓模式 (`bidirectional.enabled: false`)
```
任何已有持仓 → 拒绝开仓
进行中订单 → 拒绝开仓
```

### 双向持仓模式 (`bidirectional.enabled: true`)
```
同方向已有持仓 + 非补仓 → 拒绝开仓
同方向已有持仓 + 补仓场景 → 允许开仓
不同方向已有持仓 → 允许开仓
进行中订单 → 拒绝开仓
```

## 使用示例

### 1. 基本开仓
```java
TradingStrategyParams params = TradingStrategyParams.builder()
    .accountId(123L)
    .symbol("BTC-USDT")
    .side("BUY")
    .amount(new BigDecimal("1.0"))
    .price(new BigDecimal("50000"))
    .build();

String orderId = tradeOrderService.createOrder(params);
```

### 2. 补仓操作
```java
TradingStrategyParams params = TradingStrategyParams.builder()
    .accountId(123L)
    .symbol("BTC-USDT")
    .side("BUY")           // 已有BUY持仓，补仓
    .amount(new BigDecimal("0.5"))
    .price(new BigDecimal("51000"))
    .orderSn("ORDER_001")  // 指定原订单号，表示补仓
    .build();

String orderId = tradeOrderService.createOrder(params);
```

## 异常处理

### 验证失败异常
当违反持仓规则时，系统会抛出 `IllegalStateException`：

```java
try {
    tradeOrderService.createOrder(params);
} catch (IllegalStateException e) {
    // 处理验证失败
    log.error("订单验证失败: {}", e.getMessage());
}
```

### 常见异常场景

1. **单向模式已有持仓**
   ```
   已有持仓，禁止重复开仓。现有持仓量: 1.0
   ```

2. **双向模式同方向重复开仓**
   ```
   双向持仓模式下已有BUY持仓，禁止同方向重复开仓。现有持仓量: 1.0, 请求开仓量: 0.5
   ```

3. **进行中订单**
   ```
   已有BUY进行中的订单，禁止重复开仓。进行中订单数量: 1
   ```

## 扩展配置

### 自定义验证规则

```java
@Service
public class CustomPositionValidator {

    public void validatePosition(TradingStrategyParams params) {
        // 自定义验证逻辑
        // 例如：检查总资金使用率
        // 例如：检查单品种持仓比例
        // 例如：检查交易频率限制
    }
}
```

### 集成到订单服务

```java
@Autowired
private CustomPositionValidator customValidator;

@Override
public String createOrder(TradingStrategyParams params) {
    // 原有验证
    if (positionValidationEnabled) {
        validatePositionStatus(params);
    }

    // 自定义验证
    customValidator.validatePosition(params);

    // 创建订单...
}
```

## 监控和日志

### 日志级别
- **DEBUG**: 验证通过的正常情况
- **INFO**: 补仓操作等重要事件
- **WARN**: 验证失败但可处理的异常情况
- **ERROR**: 系统级别错误

### 关键指标监控
```java
// 建议监控的指标
- 验证失败次数
- 补仓操作次数
- 持仓分布统计
- 订单创建成功率
```

## 注意事项

### 1. 性能影响
- 数据库查询会增加订单创建时间
- 建议对高频交易场景进行性能测试

### 2. 数据一致性
- 确保订单状态及时更新
- 处理并发场景下的数据竞争

### 3. 业务规则
- 根据实际业务需求调整配置参数
- 定期review验证规则的有效性

### 4. 向后兼容
- 默认启用验证功能
- 可通过配置关闭以保持兼容性

## 测试建议

### 单元测试
```java
@Test
public void testPositionValidation() {
    // 测试各种验证场景
    // 1. 单向模式验证
    // 2. 双向模式验证
    // 3. 补仓场景验证
    // 4. 并发场景验证
}
```

### 集成测试
```java
@Test
public void testOrderCreationWithValidation() {
    // 端到端测试订单创建流程
    // 验证数据库状态变化
    // 验证异常处理
}
```

---

*此功能为交易系统的核心风控组件，建议在生产环境前进行充分测试。*