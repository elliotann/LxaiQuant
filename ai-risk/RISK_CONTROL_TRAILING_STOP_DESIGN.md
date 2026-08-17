# 移动止盈止损风控参数设计文档

## 1. 概述

本文档基于 ta4j 库中的移动止盈止损规则类，分析如何将这些规则抽象为风控设置参数。这些规则类包括：

- `TrailingStopLossRule` - 移动止损规则
- `AverageTrueRangeStopLossRule` - 基于 ATR 的止损规则
- `AverageTrueRangeStopGainRule` - 基于 ATR 的止盈规则
- `AverageTrueRangeTrailingStopLossRule` - 基于 ATR 的移动止损规则

## 2. 现有规则类分析

### 2.1 TrailingStopLossRule（移动止损规则）

**类路径：** `org.ta4j.core.rules.TrailingStopLossRule`

**核心逻辑：**
- 基于固定百分比（lossPercentage）的移动止损
- 使用自入场以来的最高价（买入）或最低价（卖出）作为基准
- 当价格朝着有利方向移动时，止损价格会随之调整（移动）
- 当价格朝着不利方向移动时，止损价格保持不变
- 当价格触及或穿越止损价格时，触发止损

**构造函数参数：**
```java
TrailingStopLossRule(Indicator<Num> indicator, Num lossPercentage, int barCount)
TrailingStopLossRule(Indicator<Num> indicator, Num lossPercentage) // barCount默认为Integer.MAX_VALUE
```
- `indicator` - 价格指标（通常是收盘价）
- `lossPercentage` - 止损百分比（例如：2 表示 2%）
- `barCount` - 回溯的K线数量（可选，用于限制计算范围）

**关键参数：**
- `lossPercentage` - 止损百分比（Num 类型，例如：2.0 表示 2%）
- `barCount` - 回溯K线数量（限制计算最高/最低价的范围）

**实现特点：**
- 使用 `HighestValueIndicator` 跟踪自入场以来的最高价（买入时）
- 使用 `LowestValueIndicator` 跟踪自入场以来的最低价（卖出时）
- 买入时：
  - 计算自入场以来的最高价 `highestCloseNum`
  - 止损价格 = `highestCloseNum * (1 - lossPercentage/100)`
  - 当当前价格 <= 止损价格时触发止损
- 卖出时：
  - 计算自入场以来的最低价 `lowestCloseNum`
  - 止损价格 = `lowestCloseNum * (1 + lossPercentage/100)`
  - 当当前价格 >= 止损价格时触发止损

### 2.2 AverageTrueRangeStopLossRule（基于 ATR 的止损规则）

**类路径：** `org.ta4j.core.rules.AverageTrueRangeStopLossRule`

**核心逻辑：**
- 使用 Average True Range (ATR) 指标来计算止损距离
- ATR 反映了市场的波动性，波动性越大，止损距离越大
- 止损距离 = ATR × multiplier（倍数）
- 买入方向：止损价格 = 入场价格 - (ATR × multiplier)
- 卖出方向：止损价格 = 入场价格 + (ATR × multiplier)

**构造函数参数：**
```java
AverageTrueRangeStopLossRule(BarSeries series, int barCount, Num multiplier)
```
- `series` - K线数据序列
- `barCount` - ATR 计算周期（通常为 14）
- `multiplier` - ATR 倍数（通常为 1.5、2.0、3.0 等）

**关键参数：**
- `atrIndicator` - ATR 指标（内部创建 `ATRIndicator`）
- `multiplier` - ATR 倍数
- `barCount` - ATR 计算周期

**实现特点：**
- 使用 `ATRIndicator` 计算 ATR 值
- 使用 `EntryPriceIndicator` 获取入场价格
- 买入时：`stopLossPrice = entryPrice - (atr × multiplier)`
- 卖出时：`stopLossPrice = entryPrice + (atr × multiplier)`
- 止损价格在入场时确定，后续不改变（非移动止损）

### 2.3 AverageTrueRangeStopGainRule（基于 ATR 的止盈规则）

**类路径：** `org.ta4j.core.rules.AverageTrueRangeStopGainRule`

**核心逻辑：**
- 使用 ATR 指标来计算止盈距离
- 止盈距离 = ATR × multiplier
- 买入方向：止盈价格 = 入场价格 + (ATR × multiplier)
- 卖出方向：止盈价格 = 入场价格 - (ATR × multiplier)

**构造函数参数：**
```java
AverageTrueRangeStopGainRule(BarSeries series, int barCount, Num multiplier)
```
- `series` - K线数据序列
- `barCount` - ATR 计算周期（通常为 14）
- `multiplier` - ATR 倍数

**关键参数：**
- `atrIndicator` - ATR 指标
- `multiplier` - ATR 倍数
- `barCount` - ATR 计算周期

**实现特点：**
- 使用 `ATRIndicator` 计算 ATR 值（周期为 `atrBarCount`，通常为 14）
- 使用 `TradingRecord.getCurrentPosition().getEntry().getNetPrice()` 获取入场价格（扣除费用后的净价）
- 使用 `referencePrice`（默认 `ClosePriceIndicator`）作为参考价格
- 买入时：`takeProfitPrice = entryPrice + (atr × atrCoefficient)`，当 `currentPrice >= takeProfitPrice` 时触发止盈
- 卖出时：`takeProfitPrice = entryPrice - (atr × atrCoefficient)`，当 `currentPrice <= takeProfitPrice` 时触发止盈
- 止盈价格在入场时确定，后续不改变（非移动止盈）

### 2.4 AverageTrueRangeTrailingStopLossRule（基于 ATR 的移动止损规则）

**类路径：** `org.ta4j.core.rules.AverageTrueRangeTrailingStopLossRule`

**核心逻辑：**
- 结合 ATR 和移动止损的概念
- 止损距离基于 ATR 计算，但止损价格会随着价格有利移动而移动
- 当价格朝着有利方向移动时，止损价格会移动（但不会反向移动）
- 买入方向：初始止损价格 = 入场价格 - (ATR × multiplier)，然后随着价格上涨，止损价格也向上移动
- 卖出方向：初始止损价格 = 入场价格 + (ATR × multiplier)，然后随着价格下跌，止损价格也向下移动

**构造函数参数：**
```java
AverageTrueRangeTrailingStopLossRule(BarSeries series, int barCount, Num multiplier)
```
- `series` - K线数据序列
- `barCount` - ATR 计算周期（通常为 14）
- `multiplier` - ATR 倍数

**关键参数：**
- `atrIndicator` - ATR 指标
- `multiplier` - ATR 倍数
- `barCount` - ATR 计算周期

**实现特点：**
- 使用 `ATRIndicator` 计算 ATR 值（周期为 `atrBarCount`，通常为 14）
- 使用 `TradingRecord.getCurrentPosition().getEntry().getNetPrice()` 获取入场价格（扣除费用后的净价）
- 使用 `referencePrice`（默认 `ClosePriceIndicator`）作为参考价格
- 使用 `HighestValueIndicator` 跟踪自入场以来的最高价（买入时）
- 使用 `LowestValueIndicator` 跟踪自入场以来的最低价（卖出时）
- 买入时：
  - 计算自入场以来的最高价 `highestPrice`
  - 止损价格 = `max(entryPrice, highestPrice) - (atr × atrCoefficient)`
  - 当 `currentPrice < stopLossPrice` 时触发止损
  - 随着最高价上升，止损价格也会上移（但不会下移）
- 卖出时：
  - 计算自入场以来的最低价 `lowestPrice`
  - 止损价格 = `min(entryPrice, lowestPrice) + (atr × atrCoefficient)`
  - 当 `currentPrice > stopLossPrice` 时触发止损
  - 随着最低价下降，止损价格也会下移（但不会上移）

## 3. 风控参数抽象设计

### 3.1 参数分类

#### 3.1.1 止损参数（Stop Loss）

| 参数名称 | 类型 | 说明 | 默认值 | 取值范围 |
|---------|------|------|--------|----------|
| `stopLossType` | String | 止损类型 | "fixed_percent" | "fixed_percent", "atr_based", "atr_trailing" |
| `stopLossPercent` | BigDecimal | 固定百分比止损（用于 fixed_percent 类型） | 0.02 (2%) | 0.001 - 0.1 (0.1% - 10%) |
| `stopLossBarCount` | Integer | 回溯K线数量（用于 fixed_percent 类型的移动止损，限制最高/最低价计算范围） | Integer.MAX_VALUE | 1 - Integer.MAX_VALUE |
| `stopLossAtrMultiplier` | BigDecimal | ATR 倍数（用于 ATR 类止损） | 2.0 | 1.0 - 5.0 |
| `stopLossAtrPeriod` | Integer | ATR 计算周期 | 14 | 5 - 50 |
| `stopLossTrailingEnabled` | Boolean | 是否启用移动止损（仅用于 atr_based，atr_trailing 自动启用） | false | true/false |

#### 3.1.2 止盈参数（Take Profit）

| 参数名称 | 类型 | 说明 | 默认值 | 取值范围 |
|---------|------|------|--------|----------|
| `takeProfitType` | String | 止盈类型 | "fixed_percent" | "fixed_percent", "atr_based" |
| `takeProfitPercent` | BigDecimal | 固定百分比止盈（用于 fixed_percent 类型） | 0.05 (5%) | 0.001 - 0.5 (0.1% - 50%) |
| `takeProfitAtrMultiplier` | BigDecimal | ATR 倍数（用于 ATR 类止盈） | 3.0 | 1.0 - 10.0 |
| `takeProfitAtrPeriod` | Integer | ATR 计算周期 | 14 | 5 - 50 |

#### 3.1.3 通用参数

| 参数名称 | 类型 | 说明 | 默认值 | 取值范围 |
|---------|------|------|--------|----------|
| `riskRewardRatio` | BigDecimal | 风险收益比（止损:止盈） | 1:2.5 | 1:1 - 1:10 |
| `maxStopLossPercent` | BigDecimal | 最大止损百分比（硬限制） | 0.05 (5%) | 0.01 - 0.2 (1% - 20%) |
| `minStopLossPercent` | BigDecimal | 最小止损百分比（硬限制） | 0.001 (0.1%) | 0.0001 - 0.01 |

### 3.2 参数组合规则

#### 3.2.1 止损类型组合

1. **固定百分比移动止损** (`stopLossType = "fixed_percent"`)
   - 必须设置 `stopLossPercent`（止损百分比，例如：2.0 表示 2%）
   - 可选设置 `stopLossBarCount`（限制回溯K线数量，默认 Integer.MAX_VALUE）
   - 对应 ta4j 的 `TrailingStopLossRule`
   - 实现原理：使用自入场以来的最高价（买入）或最低价（卖出）作为基准，止损价格 = 最高/最低价 × (1 ± 百分比)

2. **基于 ATR 的止损** (`stopLossType = "atr_based"`)
   - 必须设置 `stopLossAtrMultiplier`（ATR 倍数，例如：2.0）
   - 可选设置 `stopLossAtrPeriod`（ATR 计算周期，默认 14）
   - 可选设置 `stopLossTrailingEnabled`（是否启用移动，默认 false）
   - 如果 `stopLossTrailingEnabled = false`：对应 ta4j 的 `AverageTrueRangeStopLossRule`
   - 如果 `stopLossTrailingEnabled = true`：对应 ta4j 的 `AverageTrueRangeTrailingStopLossRule`

3. **基于 ATR 的移动止损** (`stopLossType = "atr_trailing"`)
   - 必须设置 `stopLossAtrMultiplier`
   - 可选设置 `stopLossAtrPeriod`（默认 14）
   - 对应 ta4j 的 `AverageTrueRangeTrailingStopLossRule`
   - `stopLossTrailingEnabled` 自动为 true，无需设置

#### 3.2.2 止盈类型组合

1. **固定百分比止盈** (`takeProfitType = "fixed_percent"`)
   - 必须设置 `takeProfitPercent`（止盈百分比，例如：5.0 表示 5%）
   - 对应 ta4j 的 `StopGainRule`（需参考 ta4j 的实现）

2. **基于 ATR 的止盈** (`takeProfitType = "atr_based"`)
   - 必须设置 `takeProfitAtrMultiplier`（ATR 倍数，例如：3.0）
   - 可选设置 `takeProfitAtrPeriod`（ATR 计算周期，默认 14）
   - 对应 ta4j 的 `AverageTrueRangeStopGainRule`

### 3.3 参数验证规则

1. **止损百分比验证**
   - `stopLossPercent` 必须在 `minStopLossPercent` 和 `maxStopLossPercent` 之间
   - 如果设置了 `riskRewardRatio`，止盈百分比应该 ≥ 止损百分比 × riskRewardRatio

2. **ATR 参数验证**
   - `stopLossAtrMultiplier` 和 `takeProfitAtrMultiplier` 必须 > 0
   - `stopLossAtrPeriod` 和 `takeProfitAtrPeriod` 必须在 5-50 之间

3. **移动止损验证**
   - 如果启用移动止损，止损价格只能朝着有利方向移动
   - `stopLossTrailingOnlyProfit` 为 true 时，只有在盈利状态下才会移动止损

## 4. 配置示例

### 4.1 简单固定百分比止损止盈

```yaml
riskControl:
  stopLoss:
    type: fixed_percent
    percent: 0.02  # 2%
    trailingEnabled: false
  takeProfit:
    type: fixed_percent
    percent: 0.05  # 5%
    trailingEnabled: false
  riskRewardRatio: 1:2.5
```

### 4.2 基于 ATR 的移动止损

```yaml
riskControl:
  stopLoss:
    type: atr_based
    atrMultiplier: 2.0
    atrPeriod: 14
    trailingEnabled: true
    trailingOnlyProfit: true
  takeProfit:
    type: atr_based
    atrMultiplier: 3.0
    atrPeriod: 14
    trailingEnabled: false
```

### 4.3 混合配置（固定百分比止损 + ATR 止盈）

```yaml
riskControl:
  stopLoss:
    type: fixed_percent
    percent: 0.015  # 1.5%
    trailingEnabled: true
    trailingOnlyProfit: true
  takeProfit:
    type: atr_based
    atrMultiplier: 2.5
    atrPeriod: 14
    trailingEnabled: false
```

### 4.4 高级配置（ATR 移动止损 + ATR 移动止盈）

```yaml
riskControl:
  stopLoss:
    type: atr_trailing
    atrMultiplier: 2.0
    atrPeriod: 14
  takeProfit:
    type: atr_based
    atrMultiplier: 3.0
    atrPeriod: 14
    trailingEnabled: true
  maxStopLossPercent: 0.05  # 最大止损 5%
  minStopLossPercent: 0.005  # 最小止损 0.5%
  riskRewardRatio: 1:1.5
```

## 5. 实现建议

### 5.1 配置类设计

建议创建以下配置类：

```java
// 止损配置
public class StopLossConfig {
    private String type;  // fixed_percent, fixed_amount, atr_based, atr_trailing
    private BigDecimal percent;
    private BigDecimal amount;
    private BigDecimal atrMultiplier;
    private Integer atrPeriod;
    private Boolean trailingEnabled;
    private Boolean trailingOnlyProfit;
}

// 止盈配置
public class TakeProfitConfig {
    private String type;  // fixed_percent, fixed_amount, atr_based
    private BigDecimal percent;
    private BigDecimal amount;
    private BigDecimal atrMultiplier;
    private Integer atrPeriod;
    private Boolean trailingEnabled;
}

// 风控配置（顶层）
public class RiskControlConfig {
    private StopLossConfig stopLoss;
    private TakeProfitConfig takeProfit;
    private BigDecimal riskRewardRatio;
    private BigDecimal maxStopLossPercent;
    private BigDecimal minStopLossPercent;
}
```

### 5.2 规则工厂设计

建议创建一个规则工厂类，根据配置参数动态创建对应的 Rule 对象：

```java
public class RiskControlRuleFactory {
    public static Rule createStopLossRule(BarSeries series, StopLossConfig config);
    public static Rule createTakeProfitRule(BarSeries series, TakeProfitConfig config);
}
```

### 5.3 参数验证

需要在配置加载时进行参数验证，确保：
1. 必填参数已设置
2. 参数值在合理范围内
3. 参数组合逻辑正确

## 6. 迁移计划

### 6.1 阶段一：参数定义
- 定义配置类（StopLossConfig, TakeProfitConfig, RiskControlConfig）
- 添加参数验证逻辑
- 添加配置示例和文档

### 6.2 阶段二：规则工厂实现
- 实现 RiskControlRuleFactory
- 根据配置参数创建对应的 Rule 对象
- 添加单元测试

### 6.3 阶段三：集成测试
- 集成到现有策略系统中
- 进行回测验证
- 性能测试

### 6.4 阶段四：文档和示例
- 完善配置文档
- 提供更多配置示例
- 使用指南

## 7. 注意事项

1. **向后兼容**：需要确保现有代码能够继续工作
2. **性能考虑**：ATR 计算会增加计算开销，需要考虑性能影响
3. **测试覆盖**：需要充分测试各种参数组合
4. **文档完善**：需要提供清晰的配置说明和使用示例
5. **参数合理性检查**：需要添加参数合理性检查，避免不合理的配置

## 8. 使用建议

### 8.1 是否只需要移动止损？

**结论：在大多数场景下，使用移动止损就足够了，但固定止盈也有其价值。**

#### 移动止损 vs 固定止损

- ✅ **移动止损可以替代固定止损**
  - 移动止损在不移动的情况下，效果等同于固定止损
  - 移动止损更灵活，可以在价格有利移动时保护利润
  - 建议：优先使用移动止损，固定止损可视为简化版本

#### 移动止损 vs 固定止盈

- ❌ **移动止损不能完全替代固定止盈**
  - **移动止损**：被动保护，只在价格回撤时触发
  - **固定止盈**：主动锁定，在达到目标利润时触发
  - **区别**：如果价格一直上涨不回撤，移动止损永远不会触发平仓

#### 实际应用场景

**场景 1：趋势跟踪策略（只用移动止损）**
```
适合：希望让利润奔跑，跟随大趋势
配置：
  - 移动止损：保护利润，跟随价格上涨
  - 不设置固定止盈：让市场决定何时退出
```

**场景 2：目标利润策略（移动止损 + 固定止盈）**
```
适合：需要锁定目标利润，风险收益比明确
配置：
  - 固定止盈：达到目标利润时主动平仓（例如：10% 利润）
  - 移动止损：价格回撤时保护已有利润
```

**场景 3：简化策略（只用移动止损）**
```
适合：简化配置，减少参数
配置：
  - 只使用移动止损
  - 优点：配置简单，适合趋势市场
  - 缺点：可能在震荡市场中频繁触发
```

### 8.2 推荐配置

1. **新手/简单策略**：只用移动止损
   ```yaml
   stopLoss:
     type: atr_trailing
     atrMultiplier: 2.0
     atrPeriod: 14
   # 不设置止盈，让利润奔跑
   ```

2. **平衡策略**：移动止损 + 固定止盈
   ```yaml
   stopLoss:
     type: atr_trailing
     atrMultiplier: 2.0
   takeProfit:
     type: atr_based
     atrMultiplier: 3.0
   ```

3. **保守策略**：固定止损 + 固定止盈（风险收益比明确）
   ```yaml
   stopLoss:
     type: atr_based
     atrMultiplier: 2.0
     trailingEnabled: false
   takeProfit:
     type: atr_based
     atrMultiplier: 3.0
   ```

## 9. 参考资源

- ta4j Rule 接口文档
- ATR 指标说明文档
- 移动止损/止盈策略说明文档

