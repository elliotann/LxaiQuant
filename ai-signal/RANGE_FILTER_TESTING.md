# Range Filter 信号服务测试指南

## 概述

RangeFilterSignService 现在已经完全按照Pine Script逻辑实现。以下是测试和验证信号生成的指南。

## 测试准备

### 1. 配置参数
确保 `application.yml` 中的配置正确：

```yaml
strategy:
  range-filter:
    filter-type: "Type 1"          # 或 "Type 2"
    movement-source: "Close"        # 或 "Wicks"
    range-size: 2.618              # 范围倍数
    range-scale: "Average Change"  # 范围计算方法
    range-period: 14               # 计算周期
    smooth-range: true             # 是否平滑
    smoothing-period: 27           # 平滑周期
    average-filter-changes: true   # 是否平均变化
    average-samples: 2             # 平均样本数
```

### 2. 日志配置
启用DEBUG日志以查看详细计算过程：

```yaml
logging:
  level:
    com.chain.ai.trade.engine.signal.service.support.RangeFilterSignService: DEBUG
```

## 信号生成逻辑验证

### 核心逻辑

RangeFilterSignService 严格按照以下Pine Script逻辑实现：

```pine
// 过滤器方向计算
fdir := filt > filt[1] ? 1 : filt < filt[1] ? -1 : fdir
upward = fdir == 1
downward = fdir == -1

// 基础条件
longCond = close > filt and upward
shortCond = close < filt and downward

// 状态机
CondIni := longCond ? 1 : shortCond ? -1 : CondIni[1]

// 信号条件
longCondition = longCond and CondIni[1] == -1  // 从空头状态转为多头
shortCondition = shortCond and CondIni[1] == 1  // 从多头状态转为空头
```

### 状态机说明

状态机确保只有在市场状态发生变化时才产生信号：

| 当前条件 | 上一个状态 | 信号结果 | 说明 |
|----------|------------|----------|------|
| longCond = true | -1 (空头) | ✅ 多头信号 | 从空头转为多头 |
| longCond = true | 1 (多头) | ❌ 无信号 | 继续保持多头 |
| shortCond = true | 1 (多头) | ✅ 空头信号 | 从多头转为空头 |
| shortCond = true | -1 (空头) | ❌ 无信号 | 继续保持空头 |

## 测试步骤

### 1. 验证过滤器计算

查看DEBUG日志，确认过滤器值正确计算：

```
Range Filter状态 - 价格: 45000.0, 过滤器: 44800.0, 方向: 1, 上一个条件状态: -1
```

- **价格**: 当前收盘价或蜡烛价格
- **过滤器**: 计算出的过滤器值
- **方向**: 1(向上) 或 -1(向下)
- **状态**: 上一个周期的条件状态

### 2. 验证信号生成

当满足信号条件时，会看到：

```
Range Filter信号计算 - 收盘价: 45100.0, 上根收盘价: 44900.0, 过滤器: 44800.0, 方向: 1, longCond: true, shortCond: false, 当前状态: 1, 上一个状态: -1, 多头信号: true, 空头信号: false
Range Filter多头信号生成 - 收盘价: 45100.0, 过滤器: 44800.0, 从状态 -1 转为多头
Range Filter信号生成 - 信号类型: BUY, 收盘价: 45100.0, 过滤器: 44800.0, 方向: 1
```

### 3. 常见问题诊断

#### 问题1: 没有信号生成

**可能原因**:
- K线数据不足（需要至少 `rangePeriod` 根K线）
- 过滤器方向始终为0（未初始化）
- 价格始终在同一侧（没有穿越过滤器）

**检查方法**:
```
Range Filter状态 - 价格: xxx, 过滤器: xxx, 方向: 0, 上一个条件状态: 0
```

#### 问题2: 信号生成太频繁

**可能原因**:
- 范围参数设置太小
- 市场过于波动

**调整建议**:
```yaml
strategy:
  range-filter:
    range-size: 3.0        # 增加范围倍数
    range-period: 20       # 增加计算周期
    smooth-range: true     # 启用平滑
```

#### 问题3: 信号生成太少

**可能原因**:
- 范围参数设置太大
- 市场趋势不明显

**调整建议**:
```yaml
strategy:
  range-filter:
    range-size: 1.5        # 减少范围倍数
    range-scale: "ATR"     # 使用ATR作为范围计算
```

## 参数调优建议

### 保守设置（适合稳定市场）
```yaml
strategy:
  range-filter:
    filter-type: "Type 1"
    range-size: 3.0
    range-scale: "ATR"
    range-period: 20
    smooth-range: true
    smoothing-period: 14
```

### 激进设置（适合波动市场）
```yaml
strategy:
  range-filter:
    filter-type: "Type 2"
    range-size: 1.5
    range-scale: "Standard Deviation"
    range-period: 10
    smooth-range: false
    average-filter-changes: true
    average-samples: 3
```

## 验证成功标志

当看到以下日志时，表示Range Filter工作正常：

1. **过滤器计算正常**:
   ```
   Range Filter状态 - 价格: xxx, 过滤器: xxx, 方向: ±1
   ```

2. **状态机正常工作**:
   ```
   Range Filter信号计算 - ... longCond: true, shortCond: false, 当前状态: 1, 上一个状态: -1
   ```

3. **信号成功生成**:
   ```
   Range Filter多头信号生成 - 收盘价: xxx, 过滤器: xxx, 从状态 -1 转为多头
   Range Filter信号生成 - 信号类型: BUY
   ```

现在Range Filter应该能够正确产生买卖信号了！🎯
