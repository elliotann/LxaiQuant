# SmartMoneyConceptsIndicator（SMC）指标逻辑说明

> 本文档描述 SMC 指标的核心算法逻辑、模块构成及使用方式，适用于策略开发与调试。

---

## 1. 概述

`SmartMoneyConceptsIndicator` 是一个基于 **Smart Money Concepts（聪明钱概念）** 的多模块量化指标，继承自 `CachedIndicator<Result>`，覆盖以下模块：

| 模块 | 功能 |
|------|------|
| **摆动点识别** | 识别 swing / internal 级别的高低点 |
| **结构突破检测** | 检测 BOS（Break of Structure）和 CHoCH（Change of Character） |
| **趋势方向** | 维持 internal 和 swing 两级趋势 |
| **订单块** | 识别并管理需求区/供给区 |
| **EQH/EQL** | 检测等高点/等低点 |
| **FVG** | 检测公允价值缺口 |
| **MTF 水平** | 日/周/月线高低点 |
| **溢价/折扣区域** | 识别 Premium / Discount / Equilibrium 区域 |
| **事件类型持久化** | 记录最近一次内部/摆动事件是 BOS 还是 CHoCH |

---

## 2. 核心概念

### 2.1 结构层级

| 层级 | 参数 | 用途 |
|------|------|------|
| **Internal**（内部） | `internalLength = 5` | 检测局部结构变化，敏感度较高 |
| **Swing**（摆动） | `config.swingsLength`（默认 50） | 检测主要趋势结构 |

### 2.2 BOS vs CHoCH

| 事件 | 含义 | 产生条件 |
|------|------|----------|
| **BOS** (Break of Structure) | 趋势延续 | 突破方向与原趋势方向一致 |
| **CHoCH** (Change of Character) | 特征改变 / 结构破坏 | 突破方向与原趋势方向相反 |

判断逻辑（`detectStructureBreaks` 中）：

```
向上突破时：isChoCh = (trend.bias == -1)  // 之前是下跌→突破为 CHoCH
向下突破时：isChoCh = (trend.bias == 1)   // 之前是上涨→突破为 CHoCH
```

### 2.3 趋势 bias 约定

| 值 | 含义 |
|----|------|
| `1` | 看涨（上升趋势） |
| `-1` | 看跌（下降趋势） |
| `0` | 无方向 |

### 2.4 HH/HL/LH/LL 结构

在 `updateSwingPoints` 中标记：

| 标签 | 含义 | 判断条件 |
|------|------|----------|
| **HH** (Higher High) | 更高的高点 | 新 swingHigh > 前一个 swingHigh |
| **LH** (Lower High) | 更低的高点 | 新 swingHigh < 前一个 swingHigh |
| **HL** (Higher Low) | 更高的低点 | 新 swingLow > 前一个 swingLow |
| **LL** (Lower Low) | 更低的低点 | 新 swingLow < 前一个 swingLow |

- `lastHigherLow`：最近一次出现的 HL（用于上升趋势的判断依据）
- `lastLowerHigh`：最近一次出现的 LH（用于下降趋势的判断依据）

---

## 3. 架构设计

### 3.1 类继承

```
CachedIndicator<Result>
    └── SmartMoneyConceptsIndicator
```

`CachedIndicator` 保证 `getValue(index)` 的结果被缓存，且顺序计算（`calculate` 内部有补算逻辑 `if (index > lastCalculatedIndex + 1)`）。

### 3.2 calculate() 执行顺序

```
1. ensureParsedUpTo(index)        — 填充数据缓冲区
2. updateSwingPoints              — 更新 swing / internal / equal 摆动点
3. 赋值摆动点到 Result             — lastSwingHigh/Low, prevSwingHigh/Low 等
4. 从上一 K 线 Result 复制事件类型 — 持久化最近事件类型
5. detectStructureBreaks(internal) — 检测内部结构突破
6. detectStructureBreaks(swing)    — 检测摆动结构突破
7. deleteBrokenOrderBlocks         — 删除已突破的订单块
8. 设置趋势方向                    — internalTrend, swingTrend
9. updateTrailingExtremes          — 更新波段高低点
10. detectEqualHighLow             — 检测等高点/等低点
11. detectFairValueGaps            — 检测 FVG
12. deleteFairValueGaps            — 删除已填平的 FVG
13. computeMultiTimeframeLevels    — 多时间框架水平
14. computePremiumDiscountZones    — 溢价/折扣区域
15. 赋值强弱高低点、蜡烛颜色等     — 其他输出
```

---

## 4. 模块详解

### 4.1 摆动点识别（Swing Points）

#### Internal / EqualHighLow 级别

- 使用 `leg(index, size)` 判断当前波段方向
- `currentLeg == 1` 表示当前波段为上涨 → 产生低点 pivot
- `currentLeg == 0` 表示当前波段为下跌 → 产生高点 pivot
- 仅在 `newLeg`（leg 发生变化）时更新 pivot

#### Swing 级别

- 使用独立的 `swingLeg` 机制，与 internal 完全隔离
- 取 `index - size` 位置的 K 线作为潜在 pivot 点
- 比较该 pivot 点右侧 `size` 根 K 线的最高/最低值来确认是否为有效摆动点
- 摆动点产生后立即更新 `lastHigherLow` / `lastLowerHigh` 及 `trailing` 极值

### 4.2 结构突破检测（BOS / CHoCH）

位置：`detectStructureBreaks()`

#### 向上突破条件

```
highPivot 未突破 && highPivot 有效
  && (internal 时: 该高点与 swingHigh 不同 && 看涨K线)
  && 前收盘 <= highPivot.currentLevel && 当前收盘 > highPivot.currentLevel
```

#### 向下突破条件

```
lowPivot 未突破 && lowPivot 有效
  && (internal 时: 该低点与 swingLow 不同 && 看跌K线)
  && 前收盘 >= lowPivot.currentLevel && 当前收盘 < lowPivot.currentLevel
```

突破后：
1. 设置对应的瞬时布尔标志（`internalBullishBOS` 等）
2. 更新趋势方向（`trend.bias = 1` 或 `-1`）
3. 设置持久化事件类型（`lastInternalEventType` / `lastSwingEventType`）
4. 记录 pivot 时间戳和价格
5. 生成订单块（如启用）

### 4.3 趋势方向（Trend）

- `swingTrend` / `internalTrend` 通过 `detectStructureBreaks` 维护
- 每次结构突破时自动更新 `trend.bias`
- `Result.getInternalTrend()` / `getSwingTrend()` 输出到外部

### 4.4 订单块（Order Blocks）

- 突破 pivot 后，取 `[pivot.barIndex, index - 1]` 区间内 `parsedHighs` / `parsedLows` 的极值作为订单块边界
- 订单块突破判断：使用 `close` 或 `high/low`（取决于 `config.orderBlockMitigation`）
- 突破后从列表中移除

### 4.5 EQH / EQL（等高点 / 等低点）

- 基于 `equalHighsLowsLength`（默认 3）级别的 pivot
- 当相邻等高点/等低点之间的差值小于 `equalHighsLowsThreshold * ATR` 时触发信号

### 4.6 FVG（公允价值缺口）

- 对比 `bar2.high/low` 与 `bar0.low/high` 之间的重叠关系
- 需满足 K 线实体变化幅度超过自适应阈值
- FVG 被价格回填后自动标记为已突破

### 4.7 多时间框架水平（MTF Levels）

- 取上一根已完成 K 线的日线/周线/月线 High/Low
- 需要外部传入对应的 BarSeries

### 4.8 溢价/折扣区域（Premium / Discount Zones）

- 基于 `trailing.top` 和 `trailing.bottom` 计算

```
Premium 区域：    [95% H + 5% L, H]
Equilibrium 区域：[52.5% L + 47.5% H, 52.5% H + 47.5% L]
Discount 区域：   [L, 5% H + 95% L]
```

- 当前收盘价所在的区域输出到 `result.currentZone`

---

## 5. 事件类型持久化（Persistence）

### 5.1 背景

原始的 BOS/CHoCH 信号以瞬时布尔标志存储（如 `internalBullishBOS`），仅在事件发生的 K 线上为 `true`，后续 K 线立即变回 `false`。复合状态判断需要知道**最近一次事件是 BOS 还是 CHoCH**，无法从瞬时标志获取。

### 5.2 持久化机制

利用 `CachedIndicator` 的缓存特性实现跨 K 线持久化：

```
calculate(index) 中：
  1. 创建新 Result
  2. if (index > 0) {
       prev = getValue(index - 1)    ← CachedIndicator 保证已缓存
       result.lastInternalEventType = prev.lastInternalEventType
       result.lastSwingEventType = prev.lastSwingEventType
     }
  3. detectStructureBreaks() 中：
     有事件时直接覆盖为 1（BOS）或 2（CHoCH）
     无事件时保持复制过来的值，自然持久化
```

### 5.3 字段定义

```
字段名                    | 类型 | 含义
--------------------------|------|----------------------------
lastInternalEventType     | int  | 最近一次内部事件：0=无, 1=BOS, 2=CHoCH
lastSwingEventType        | int  | 最近一次摆动事件：0=无, 1=BOS, 2=CHoCH
```

### 5.4 更新逻辑

```
向上突破：
  isChoCh = (trend.bias == -1)   // 之前是下降趋势→CHoCH
  setLastInternalEventType(isChoCh ? 2 : 1)

向下突破：
  isChoCh = (trend.bias == 1)    // 之前是上升趋势→CHoCH
  setLastInternalEventType(isChoCh ? 2 : 1)

swing 层级同理，更新 lastSwingEventType。
```

---

## 6. Result 字段速查

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `internalTrend` | int | 内部趋势方向（1/-1/0） |
| `swingTrend` | int | 摆动趋势方向 |
| `internalBullishBOS` | boolean | 内部看涨 BOS（瞬时） |
| `internalBearishBOS` | boolean | 内部看跌 BOS（瞬时） |
| `internalBullishCHOCH` | boolean | 内部看涨 CHoCH（瞬时） |
| `internalBearishCHOCH` | boolean | 内部看跌 CHoCH（瞬时） |
| `swingBullishBOS` | boolean | 摆动看涨 BOS（瞬时） |
| `swingBearishBOS` | boolean | 摆动看跌 BOS（瞬时） |
| `swingBullishCHOCH` | boolean | 摆动看涨 CHoCH（瞬时） |
| `swingBearishCHOCH` | boolean | 摆动看跌 CHoCH（瞬时） |
| `lastInternalEventType` | int | 最近一次内部事件类型（持久化） |
| `lastSwingEventType` | int | 最近一次摆动事件类型（持久化） |
| `lastSwingHigh` / `lastSwingLow` | double | 最新摆动高低点 |
| `prevSwingHigh` / `prevSwingLow` | double | 前一个摆动高低点 |
| `lastHigherLow` | double | 最近一次 HL（更高的低点） |
| `lastLowerHigh` | double | 最近一次 LH（更低的高点） |
| `trailingHigh` / `trailingLow` | double | 波段高低点 |
| `strongHigh` / `strongLow` | double | 强高低点（最新 swing pivot） |
| `weakHigh` / `weakLow` | double | 弱高低点（trailing 极值） |
| `candleColor` | int | 趋势蜡烛颜色（1=看涨, -1=看跌, 0=中性） |
| `equalHighs` / `equalLows` | boolean | 等高点/等低点信号 |
| `bullishFairValueGap` / `bearishFairValueGap` | boolean | FVG 信号 |
| `currentZone` | String | 当前价格所在区域（Premium/Discount/Equilibrium/Neutral） |
| `pivotTimestamps` | Map<String, Long> | 各信号对应 pivot 时间戳 |
| `pivotLevels` | Map<String, Double> | 各信号对应 pivot 价格 |
| `swingOrderBlocks` | List<OrderBlock> | 摆动级别订单块列表 |
| `internalOrderBlocks` | List<OrderBlock> | 内部级别订单块列表 |

---

## 7. 消费示例

### 7.1 基础使用

```java
SmartMoneyConceptsIndicator smc = new SmartMoneyConceptsIndicator(series, config, daily, weekly, monthly);
Result result = smc.getValue(index);

int internalTrend = result.getInternalTrend();   // 内部趋势
int swingTrend = result.getSwingTrend();          // 摆动趋势
int lastEvent = result.getLastInternalEventType(); // 最近内部事件类型
int lastSwingEvent = result.getLastSwingEventType(); // 最近摆动事件类型
```

### 7.2 复合状态判断（SmcTrendUtils）

`SmcTrendUtils.getDetailedTrendState()` 接收多周期 Result，输出 `CompositeState` 枚举：

```java
Map<CandlestickIntervalEnum, Result> resultMap = new HashMap<>();
resultMap.put(OKX4HOUR, result4h);
resultMap.put(OKXMIN60, result1h);
resultMap.put(OKXMIN15, result15m);

CompositeState state = SmcTrendUtils.getDetailedTrendState(resultMap, currentPrice, hlBroken, lhBroken);
```

关键判断逻辑示例：

```
STRONG_BULLISH_HEALTHY        ← 4H/1H 无 CHoCH 预警，15M 无反向
STRONG_BULLISH_WARNING_1H     ← 1H 内部最近事件是 CHoCH 且内部趋势转空
STRONG_BEARISH_WARNING_1H     ← 1H 内部最近事件是 CHoCH 且内部趋势转多
STRONG_BULLISH_CONFIRMED_PULLBACK ← 1H 向下 BOS 已确认
```

### 7.3 事件类型持久化替代瞬时布尔

```java
// 旧：依赖瞬时布尔（事件 K 线外不可靠）
if (result1h.isInternalBearishCHOCH() && !h1DownBOS) { ... }

// 新：使用持久化事件类型
if (result1h.getLastInternalEventType() == 2 && !h1DownBOS) {
    // 内部最近事件是 CHoCH（特征改变），且未确认向下 BOS
}
```

---

## 8. Config 参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `swingsLength` | 50 | Swing 级别摆动点检测窗口大小 |
| `showInternalOrderBlocks` | true | 是否生成内部级别订单块 |
| `showSwingOrderBlocks` | false | 是否生成摆动级别订单块 |
| `orderBlockFilter` | "Atr" | 订单块波动率过滤方式 |
| `orderBlockMitigation` | "High/Low" | 订单块失效判断（Close 或 High/Low） |
| `internalFilterConfluence` | false | 内部结构是否需额外 K 线形态确认 |
| `showEqualHighsLows` | true | 是否启用 EQH/EQL 检测 |
| `equalHighsLowsLength` | 3 | EQH/EQL 检测窗口 |
| `equalHighsLowsThreshold` | 0.1 | EQH/EQL 阈值（乘以 ATR） |
| `showFairValueGaps` | false | 是否启用 FVG 检测 |
| `showDailyLevels` / `showWeeklyLevels` / `showMonthlyLevels` | false | 多时间框架水平开关 |
| `showPremiumDiscountZones` | false | 溢价/折扣区域开关 |
