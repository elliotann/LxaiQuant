# AI 智能创建策略 — 指标驱动型策略设计文档（含交易引擎与回测引擎改造方案）

版本：2.0
日期：2026-05-15
作者：交易系统开发团队

---

## 目录

1. [概述](#1-概述)
2. [现状分析](#2-现状分析)
3. [改造目标与原则](#3-改造目标与原则)
4. [总体架构](#4-总体架构)
5. [交易引擎改造方案](#5-交易引擎改造方案)
6. [回测引擎改造方案](#6-回测引擎改造方案)
7. [删除计划：StrategyFactory / SignalBasedStrategyImpl](#7-删除计划strategyfactory--signalbasedstrategyimpl)
8. [多策略运行时共存机制](#8-多策略运行时共存机制)
9. [用户交互流程](#9-用户交互流程)
10. [API 设计](#10-api-设计)
11. [数据模型设计](#11-数据模型设计)
12. [AI 参数推荐与 Prompt 设计](#12-ai-参数推荐与-prompt-设计)
13. [运行时执行](#13-运行时执行)
14. [与 AI Filter 系统的关系](#14-与-ai-filter-系统的关系)
15. [实施路线图](#15-实施路线图)
16. [附录](#16-附录)

---

## 1. 概述

本文档是"AI 智能创建策略"系列设计文档中针对**指标驱动型策略**的完整设计方案。指标驱动型策略是指：交易信号来源于 ta4j 框架技术指标（RSI、MACD、EMA 等）的实时计算，而非外部信号表写入。

本文档整合了以下内容：
- 指标驱动型策略的架构设计、API 设计、数据模型、AI Prompt 设计
- 交易引擎 `DefaultDealStrategyTrade` 的改造方案（统一策略工厂、出场规则共享）
- 回测引擎 `BacktestService` / `BacktestEngine` / `MultiPositionBacktestManager` 的改造方案
- `StrategyFactory` / `SignalBasedStrategyImpl` 的删除计划
- 多策略运行时共存机制（信号驱动型、指标驱动型、传统策略）

### 1.1 核心目标

1. **统一策略构建入口** — `DefaultDealStrategyTrade.buildStrategy()` 作为唯一策略工厂，消除 `StrategyFactory`
2. **多策略类型运行时共存** — 信号驱动型、指标驱动型可在同一应用配置下同时运行
3. **回测引擎统一化** — `MultiPositionBacktestManager` 成为默认回测执行器，传统回测作为降级路径
4. **清除过期代码** — 删除 `StrategyFactory` 和 `SignalBasedStrategyImpl`
5. **出场规则共享** — 两种双向策略共享 `loadExitRulesConfig()` 退出规则加载机制

### 1.2 设计原则

- 不改动 ta4j 源码（ta4j-master 只读）
- 不改动 XChange-develop 源码
- 向后兼容：已运行的旧策略机器人不受影响
- 配置驱动：通过 `strategy.mode` 全局切换，通过 `trading_bot.bot_type` 机器人级别控制

### 1.3 参考文档

- [双向持仓扩展设计文档.md](./双向持仓扩展设计文档.md) — 双向持仓数据结构和基础能力
- [双向持仓策略引擎出场功能设计文档.md](./双向持仓策略引擎出场功能设计文档.md) — 出场规则体系设计
- [双向策略回测执行器 MultiPositionBacktestManager 设计文档.md](./双向策略回测执行器%20MultiPositionBacktestManager%20设计文档.md) — 回测执行器设计
- [双向策略引擎集成设计文档-基于 DefaultDealStrategyTrade 扩展.md](./双向策略引擎集成设计文档-基于%20DefaultDealStrategyTrade%20扩展.md) — 首次集成设计
- [ai-filter-comprehensive-plan.md](./ai-filter-comprehensive-plan.md) — AI Filter 信号过滤方案
- [市场数据菜单前后端逻辑文档.md](./市场数据菜单前后端逻辑文档.md) — 市场数据获取方案

---

## 2. 现状分析

### 2.1 系统当前架构

当前系统围绕 `IDealStrategyBasic` 接口构建交易执行生命周期，`DefaultDealStrategyTrade` 作为核心实现类，通过 `IDealStrategyBasic` 定义的三大核心方法驱动交易：

- **buildStrategy()** — 构建策略实例
- **executeTradingLogic()** — 按"先出场、后入场"顺序执行单次交易逻辑
- **execStrategy()** — 控制执行模式（测试模式循环执行、正式模式单次执行）

回测引擎以 `BacktestService` 为入口，`BacktestEngine` 为核心执行器，直接调用 `buildStrategy()` 构建策略后由 `BarSeriesManager.run()` 驱动回测。

### 2.2 策略类型现状

| 策略类型 | 实现类 | 状态 | 说明 |
|---------|--------|------|------|
| 传统单方向策略 | `CombinedTradingStrategy` 等 | 生产运行 | 单批次持仓，不支持加仓/分批平仓 |
| 信号驱动型双向策略 | `SignalMultiDirectionStrategy` | 生产中 | 入场信号来自 `TechnicalSignal` 表，通过 `SignalCacheManager` 查询缓存 |
| 指标驱动型双向策略 | `IndicatorDrivenStrategy` | 设计阶段 | 入场信号来自 ta4j `Indicator` 实时计算 |
| 信号驱动型旧策略 | `SignalBasedStrategyImpl` | **待删除** | 已废弃，通过 `StrategyFactory` 创建 |

### 2.3 关键组件依赖关系

```
DefaultDealStrategyTrade (交易引擎核心)
├── buildStrategy()
│   ├── LEGACY 模式 → StrategyFactory → SignalBasedStrategyImpl / CombinedTradingStrategy
│   └── BIDIRECTIONAL 模式 → SignalMultiDirectionStrategy
│       └── 使用 loadExitRulesConfig() 加载退出规则
├── processEntrySignals() → 判断 MultiPositionStrategy / CombinedTradingStrategy / 传统策略
├── processPositionExits() → checkStrategyExit() 分发到对应策略的出场判断
├── loadExitRulesConfig() → ExitRulesConfig → DirectionalRule[] → OrDirectionalRule
└── MultiPositionTradingRecord (双向持仓记录)

BacktestService (回测入口)
├── runEnhancedBacktest()
│   ├── 策略构建后 instanceof MultiPositionStrategy → MultiPositionBacktestManager.run()
│   └── 传统策略 → BacktestEngine → BarSeriesManager.run()
└── runParameterRangeOptimization()
    └── 同上双分支

BacktestEngine
├── runMultipleStrategies() / runBacktest()
├── computeMetricsFromRecord() (支持 MultiPositionTradingRecord)
└── calculateEquityCurveAndDrawdown() (支持 MultiPositionTradingRecord)
```

### 2.4 ta4j 核心抽象对照（不可修改的外部依赖）

| ta4j 抽象 | 用途 | 我们的扩展 |
|-----------|------|-----------|
| `Strategy` (interface) | 定义 `shouldOperate()` 决定是否交易 | `MultiPositionStrategy` 将 shouldOperate 拆分为 `shouldEnterDirection()` + `shouldExitSignal()` |
| `Rule` (interface) | 定义 `isSatisfied()` 判断条件是否满足 | `DirectionalRule` (带方向), `OrDirectionalRule` (组合) |
| `BarSeries` | K 线数据容器 | 直接使用，无扩展 |
| `TradingRecord` (interface) | 记录交易历史 | `MultiPositionTradingRecord` 支持多批次持仓 |
| `BarSeriesManager` | 核心回测循环 | 不直接使用，替换为 `MultiPositionBacktestManager` |
| `TradeExecutionModel` | 成交模型 | `MarketOrderModel`, `FeeAwareExecutionModel` |
| `CostModel` | 费用模型 | `LinearTransactionCostModel` |

---

## 3. 改造目标与原则

### 3.1 核心目标

1. **统一策略构建入口** — `DefaultDealStrategyTrade.buildStrategy()` 作为唯一策略工厂，消除 `StrategyFactory`
2. **多策略类型运行时共存** — 信号驱动型、指标驱动型可在同一 application.yml 配置下同时运行
3. **回测引擎统一化** — `MultiPositionBacktestManager` 成为默认回测执行器，传统回测作为降级路径
4. **清除过期代码** — 删除 `StrategyFactory` 和 `SignalBasedStrategyImpl`
5. **出场规则共享** — 两种双向策略共享 `loadExitRulesConfig()` 退出规则加载机制

### 3.2 设计原则

- 不改动 ta4j 源码（ta4j-master 只读）
- 不改动 XChange-develop 源码
- 向后兼容：已运行的旧策略机器人不受影响
- 配置驱动：通过 `strategy.mode` 全局切换，通过 `trading_bot.strategy_bean_name` 机器人级别控制

---

## 4. 总体架构

### 4.1 改造后架构

```
┌──────────────────────────────────────────────────────────────┐
│                    应用层 (ai-engine)                         │
│                                                              │
│  DefaultDealStrategyTrade (统一策略执行引擎)                   │
│  ├── buildStrategy()  ← 唯一策略工厂                           │
│  │   ├── strategyBeanName="signalMultiDirectionStrategy"      │
│  │   │   → SignalMultiDirectionStrategy (信号驱动)             │
│  │   ├── strategyBeanName="indicatorDrivenStrategy"           │
│  │   │   → IndicatorDrivenStrategy (指标驱动)                  │
│  │   └── 其他 (legacy) → 直接构建传统策略                       │
│  ├── loadExitRulesConfig() ← 出场规则共享                       │
│  ├── processEntrySignals() ← 统一入场处理                       │
│  └── processPositionExits() ← 统一出场处理                     │
│                                                              │
│  BacktestService (统一回测入口)                                │
│  └── runEnhancedBacktest()                                    │
│      └── str instanceof MultiPositionStrategy                 │
│          → MultiPositionBacktestManager.run()                 │
│          → else → BacktestEngine.run() (降级)                  │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                  扩展层 (ai-extension)                        │
│                                                              │
│  ta4j 策略扩展:                                               │
│  ├── MultiPositionStrategy (接口)                             │
│  ├── SignalMultiDirectionStrategy (信号驱动)                   │
│  ├── IndicatorDrivenStrategy (指标驱动)                        │
│  ├── DirectionalRule / OrDirectionalRule (方向性规则)          │
│  └── ExitSignal (出场信号)                                    │
│                                                              │
│  双向持仓管理:                                                │
│  ├── MultiPositionTradingRecord (多批次持仓)                   │
│  └── ExecutionMatchPolicy (FIFO/LIFO/AVG_COST)               │
│                                                              │
│  回测执行器:                                                  │
│  └── MultiPositionBacktestManager                             │
│      ├── MarketOrderModel (成交模型)                           │
│      └── FeeAwareExecutionModel (费用模型)                     │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                不可变依赖 (ta4j-master / XChange)             │
│  BarSeries / Strategy / Rule / TradingRecord                 │
│  BarSeriesManager / TradeExecutionModel / CostModel          │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 核心数据流

```
┌─────────────┐   buildStrategy()   ┌──────────────────┐
│             │────────────────────→│                  │
│  定时调度    │                     │  Strategy 实例    │
│  (XXL-JOB)  │←────────────────────│                  │
│             │   executeTradingLogic()                 │
└──────┬──────┘                     └──────────────────┘
       │
       │ 1. processPositionExits()
       │    → loadExitRulesConfig() → OrDirectionalRule
       │    → 遍历 MultiPositionTradingRecord 持仓
       │    → 匹配退出规则 → 分批平仓
       │
       │ 2. processEntrySignals()
       │    → strategy.shouldEnterDirection() [信号/指标]
       │    → 加仓限制检查 → 创建订单
       │    → MultiPositionTradingRecord.enter()
       │
       ▼
┌──────────────────────────────────────────────┐
│            MultiPositionTradingRecord         │
│  ├── 多批次开仓记录 (List<OpenLot>)           │
│  ├── 分批平仓 (按 FIFO/LIFO/AVG_COST 匹配)    │
│  └── 持仓聚合信息 (总数量/方向/浮动盈亏)         │
└──────────────────────────────────────────────┘
```

### 4.3 两种策略执行模式的对比

两种模式是**架构层面的设计选择**，不是能力高低之分。信号驱动型将信号计算与执行解耦（信号生产者→落库→定时消费），指标驱动型将信号计算内聚在执行链路中（每根 K 线实时计算）。选择依据是策略的业务场景和系统解耦需求。

AI Prompt 生成策略配置时，不关心执行模式——它只是按 botType 路由到对应模板、输出参数。执行模式由 `strategyBeanName` 在运行时决定。

| 维度 | 信号驱动型 (SignalDriven) | 指标驱动型 (IndicatorDriven) |
|------|------------------------|--------------------------|
| 架构风格 | 信号生产者与消费者分离 | 信号计算与执行内聚 |
| 执行方式 | 定时器触发 → 查信号表 → 执行 | 每根 K 线触发 → 算指标 → 执行 |
| 信号来源 | `TechnicalSignal` 表（外部写入） | ta4j `Indicator` 实时计算 |
| 信号内容 | LB/SB/方向 + 权重 | `IntPredicate` 条件 true/false |
| 信号缓存 | `SignalCacheManager` (ConcurrentHashMap) | 无缓存，实时计算 |
| 入场规则 | `MultiDirectionEntryRule` → 查缓存 | `IndicatorDrivenDirectionRule` → 算指标 |
| 出场规则 | `OrDirectionalRule` 组合（exit_rules 加载） | 完全复用相同 OrDirectionalRule + exit_rules |
| 构建位置 | `DefaultDealStrategyTrade.buildStrategy()` | 统一在 buildStrategy() 中构建 |
| 入场构建 | `new MultiDirectionEntryRule(cache, series)` | `IndicatorDrivenDirectionRule.configure(config, series)` |
| 出场构建 | `loadExitRulesConfig()` → OrDirectionalRule | 完全复用同一套代码 |
| 策略类 | `SignalMultiDirectionStrategy` | `IndicatorDrivenStrategy` |
| 适用场景 | 信号源来自外部系统（Python/第三方/人工） | 纯 Java 本地指标计算，信号逻辑与执行紧耦合 |
| 依赖服务 | `SignalCacheManager`、`TechnicalSignal` | ta4j 指标库 |

### 4.4 MultiPositionStrategy 接口

所有支持双向多批次持仓的策略需要实现 `MultiPositionStrategy` 接口：

```java
public interface MultiPositionStrategy {
    /**
     * 判断当前 bar 是否有入场信号
     * @return 入场方向 (BUY/SELL)，null 表示不入场
     */
    TradeType shouldEnterDirection(int index, MultiPositionTradingRecord record);

    /**
     * 判断当前 bar 是否有出场信号
     * @return 出场信号 (含方向+类型)，null 表示不出场
     */
    ExitSignal shouldExitSignal(int index, MultiPositionTradingRecord record);

    /**
     * 策略名称（用于日志/展示）
     */
    String getName();

    /**
     * 策略起始方向（双向策略统一用 BUY）
     */
    TradeType getStartingType();
}
```

---

## 5. 交易引擎改造方案

### 5.1 DefaultDealStrategyTrade 改造

#### 5.1.1 buildStrategy() — 统一策略工厂

当前 `buildStrategy()` 在 `BIDIRECTIONAL` 模式下直接创建 `SignalMultiDirectionStrategy`，改造后扩展为根据 `params.getStrategyBeanName()` 分发：

```java
@Override
public Strategy buildStrategy(BarSeries series, TradingStrategyParams params) {
    if (StrategyConstants.BEAN_NAME_INDICATOR_DRIVEN.equals(params.getStrategyBeanName())) {
        // 指标驱动型策略
        IndicatorConfig indicatorConfig = parseIndicatorConfig(params.getIndicatorConfig());
        OrDirectionalRule exitRule = loadExitRulesConfig(params.getRobotId());
        return new IndicatorDrivenStrategy(series, indicatorConfig, exitRule, params.getRobotName());
    }
    if (StrategyConstants.BEAN_NAME_SIGNAL_MULTI_DIRECTION.equals(params.getStrategyBeanName())) {
        // 信号驱动型双向策略
        SignalCacheManager cache = getSignalCacheManager();
        OrDirectionalRule exitRule = loadExitRulesConfig(params.getRobotId());
        return new SignalMultiDirectionStrategy(series, cache, exitRule, params.getRobotName());
    }
    // 传统策略（降级路径）
    return buildLegacyStrategy(series, params);
}
```

**关键变更：**
1. `SignalMultiDirectionStrategy` 和 `IndicatorDrivenStrategy` 的构造函数都接收 `OrDirectionalRule exitRule` 参数，共享退出规则
2. 退出规则在 `buildStrategy()` 中统一加载，两种双向策略共享同一套 `loadExitRulesConfig()` 机制
3. `strategyBeanName` 作为路由键，不再依赖 `application.yml` 的 `strategy.mode` 全局开关
4. `buildLegacyStrategy()` 方法保留旧策略构建能力但不依赖 `StrategyFactory` 类

#### 5.1.2 processEntrySignals() — 统一入场处理

当前 `processEntrySignals()` 已经支持 `MultiPositionStrategy` 的分支处理（`shouldEnterDirection()`），该设计保留，同时需要兼容：

- `MultiPositionStrategy` 接口一致：`SignalMultiDirectionStrategy` 和 `IndicatorDrivenStrategy` 都实现 `shouldEnterDirection(int index, MultiPositionTradingRecord record)` → `TradeType`
- 加仓限制（`maxLongLots`/`maxShortLots`）由 `DefaultDealStrategyTrade` 统一控制
- 入场资金计算复用现有的 `calculateOrderAmount()`

#### 5.1.3 processPositionExits() — 统一出场处理

出场流程维持现有架构：

```
processPositionExits()
  └→ checkStrategyExit() 对每个持仓调用
       ├→ strategy instanceof MultiPositionStrategy
       │    → mpStrategy.shouldExitSignal(endIndex, record)
       │    → ExitSignal (含方向 + 退出类型)
       └→ else → 传统出场判断
```

**改造要点：**
- `shouldExitSignal()` 在 `SignalMultiDirectionStrategy` 中的实现：优先检查信号缓存中的出场信号，若无信号则回退到 `OrDirectionalRule` 判断
- `shouldExitSignal()` 在 `IndicatorDrivenStrategy` 中的实现：使用构建时传入的 `OrDirectionalRule` 判断出场
- `ExitSignal` 包含 `direction` 和 `exitType`（如 STOP_LOSS / TAKE_PROFIT / TRAILING / BATCH_PROFIT 等），便于出场日志和统计分析

#### 5.1.4 loadExitRulesConfig() — 出场规则加载（共享）

当前 `loadExitRulesConfig()` 已经构建了完整的出场规则体系，改造后两种双向策略共享该机制：

```
loadExitRulesConfig(robotId)
  ↓
从 strategy_parameter 表查询 exit_rules 分组
  ↓
按类型构建 DirectionalRule 数组：
  ├── StopLossDirectionalRule (止损)
  ├── TakeProfitDirectionalRule (止盈)
  ├── TrailingStopDirectionalRule (移动止损)
  ├── BatchTakeProfitDirectionalRule (分批止盈)
  ├── SmcDirectionalRule (SMC 适配器)
  └── MacdDirectionalRule (MACD 适配器)
  ↓
OrDirectionalRule — 任一规则触发即出场
```

**改造要点：**
- 当前 `loadExitRulesConfig()` 在 `DefaultDealStrategyTrade` 中无参调用，需要在内部的 `strategy_parameter` 查询中进行 robotId 或 `strategy_id` 的过滤
- 出场规则构建完成后作为参数传入策略构造函数（`buildStrategy()` 中），而不是让策略内部自行加载
- 出场规则在 `IndicatorDrivenStrategy` 中只用于出场，不参与入场判断（入场由指标信号规则控制）

### 5.2 策略扩展机制

#### 5.2.1 策略创建流程

```
TradingBot 创建/更新
  ↓
设置 trading_bot.bot_type 字段：
  ├── "signal_multi_direction"  → 信号驱动型双向策略
  ├── "indicator_driven"        → 指标驱动型双向策略
  └── "legacy" (或不设置)        → 传统策略
  ↓
设置 trading_bot.strategy_bean_name 字段：
  ├── 信号驱动型 → "signalMultiDirectionStrategy"
  ├── 指标驱动型 → "indicatorDrivenStrategy"
  └── 传统策略   → 对应策略的 bean name
  ↓
运行时 DefaultDealStrategyTrade.buildStrategy()
  └→ 根据 strategyBeanName 路由构建
```

#### 5.2.2 出场规则执行流程

```
MultiPositionTradingRecord (当前持仓)
  ↓ 遍历批次 (按匹配策略 FIFO/LIFO/AVG_COST)
  ↓
OrDirectionalRule.isSatisfied(index, record)
  ├── StopLossRule (止损)
  │   └→ 计算浮动亏损比例 → 触发则返回 BUY/SELL
  ├── TakeProfitRule (止盈)
  │   └→ 计算浮动盈利比例 → 触发则返回 BUY/SELL
  ├── TrailingStopRule (移动止损)
  │   └→ 跟踪最高价回撤比例 → 触发则返回 BUY/SELL
  ├── BatchTakeProfitRule (分批止盈)
  │   └→ 按档位 N% 分批平仓 → 触发时只平该批次指定数量
  ├── SmcDirectionalRule (SMC 适配器)
  │   └→ 直接使用方向性规则判断 → 触发返回 BUY/SELL
  └── MacdDirectionalRule (MACD 适配器)
      └→ 基于 MACD 指标条件 → 触发返回 BUY/SELL
  ↓
任一 Rule 触发 → ExitSignal(direction, type, lots)
  ↓
checkStrategyExit() 按 ExitSignal 平仓
```

### 5.3 配置项变更

#### application.yml

```yaml
strategy:
  # 全局默认模式（机器人级别 strategy_bean_name 覆盖此设置）
  mode: LEGACY  # LEGACY | BIDIRECTIONAL
  bidirectional:
    match-policy: FIFO  # FIFO | LIFO | AVG_COST | SPECIFIC_ID
    max-long-lots: 5
    max-short-lots: 3
  signal:
    default-indicator: "2001"
    default-data-source: "OKX"
```

**变更说明：**
- `strategy.mode` 从"硬路由"变为"默认值"——当机器人的 `strategy_bean_name` 为空时使用
- 机器人的 `strategy_bean_name` 优先级最高
- 双向策略的配置（match-policy / max-long-lots / max-short-lots）保持全局统一，后续可按机器人级别扩展

---

## 6. 回测引擎改造方案

### 6.1 回测引擎现状

当前回测引擎 `BacktestService.runEnhancedBacktest()` 已经具备双分支能力：

```
runEnhancedBacktest()
  ↓ 构建策略
  ↓ 判断策略类型
  ├── strategy instanceof MultiPositionStrategy
  │   → MultiPositionBacktestManager.run()  ← 双向持仓回测
  └── else
      → BacktestEngine.runBacktest()  ← 传统回测
```

`MultiPositionBacktestManager` 使用自定义的回测循环（非 ta4j `BarSeriesManager.run()`），核心逻辑：

```java
for (int i = startIndex; i <= endIndex; i++) {
    // 1. 出场：先检查退出信号
    ExitSignal exit = strategy.shouldExitSignal(i, record);
    if (exit != null) {
        Num exitAmount = roundExitAmount(openAmount);
        ExecutionFill fill = executionModel.executeExit(i, dir, exitAmount, series);
        record.exit(fill);
    }
    // 2. 入场：再检查入场信号
    TradeType entryDir = strategy.shouldEnterDirection(i, record);
    if (entryDir != null) {
        ExecutionFill fill = executionModel.executeEntry(i, entryDir, amount, series);
        record.enter(fill);
    }
}
```

### 6.2 回测引擎改造目标

1. **统一回测入口** — `BacktestService` 不再需要 `instanceof` 判断，统一走 `MultiPositionBacktestManager`
2. **`BacktestEngine` 降级** — 传统回测路径作为降级选项，通过配置开关控制
3. **性能指标计算统一** — `computeMetricsFromRecord()` 已经支持 `MultiPositionTradingRecord`，无需改动
4. **参数优化兼容** — `runParameterRangeOptimization()` 保持双分支，确保两种策略都能参与参数优化

### 6.3 改造后回测流程

```
BacktestService.runEnhancedBacktest(request)
  ↓ 数据准备 + 策略构建
  ↓
BacktestConfig config = buildBacktestConfig(request);
TradingRecord result;

if (shouldUseMultiPositionBacktest(strategy, config)) {
    // 使用 MultiPositionBacktestManager
    MultiPositionBacktestManager mgr = new MultiPositionBacktestManager(
        series,
        new MarketOrderModel(),
        new LinearTransactionCostModel(config.getCommissionRate() + config.getSlippageRate())
    );
    result = mgr.run(
        (MultiPositionStrategy) strategy,
        series.numFactory().numOf(config.getInitialAmount()),
        series.getBeginIndex(),
        series.getEndIndex()
    );
} else {
    // 降级路径：传统 BacktestEngine
    BacktestEngine engine = new BacktestEngine(config);
    result = engine.runBacktest(strategy, series).getTradingRecord();
}
```

其中 `shouldUseMultiPositionBacktest()` 的判断逻辑：

```java
private boolean shouldUseMultiPositionBacktest(Strategy strategy, BacktestConfig config) {
    if (strategy instanceof MultiPositionStrategy) return true;
    if ("BIDIRECTIONAL".equalsIgnoreCase(config.getBacktestMode())) return true;
    return false;
}
```

### 6.4 BacktestEngine 改造

`BacktestEngine` 需要扩展以支持 `MultiPositionTradingRecord` 的指标计算：

| 方法 | 当前状态 | 改造后 |
|------|---------|--------|
| `runBacktest()` | 使用 `BarSeriesManager.run()` | 保留作为降级路径 |
| `runMultipleStrategies()` | 批量执行策略 | 扩展支持 `MultiPositionStrategy` 分支 |
| `computeMetricsFromRecord()` | 已支持 `MultiPositionTradingRecord` | 无需改动 |
| `calculateEquityCurveAndDrawdown()` | 已部分支持 | 需要增强 `MultiPositionTradingRecord` 的持仓市值计算 |

#### Equity Curve 改造重点

`calculateEquityCurveAndDrawdown()` 对于 `MultiPositionTradingRecord` 的多批次持仓权益计算需明确分步算法。对于每个 bar i：

```
总持仓数量 = sum(所有 OpenLot 的剩余数量)
总持仓成本 = sum(每个 OpenLot 的剩余数量 × 开仓价格)
持仓市值   = 总持仓数量 × 当前价格(bar i 的收盘价)
浮动盈亏   = 持仓市值 - 总持仓成本
权益       = 初始资金 + 已实现盈亏 + 浮动盈亏
```

```java
if (tradingRecord instanceof MultiPositionTradingRecord mpRecord) {
    for (int i = beginIndex; i <= endIndex; i++) {
        Num currentPrice = series.getBar(i).getClosePrice();
        List<OpenLot> openLots = mpRecord.getOpenLots(direction);

        // 1. 总持仓数量 = sum(所有开仓批次剩余数量)
        Num totalAmount = numOf(openLots.stream()
            .mapToDouble(lot -> lot.getAmount().doubleValue())
            .sum());

        // 2. 总持仓成本 = sum(每个批次剩余数量 × 开仓价格)
        Num totalCost = numOf(openLots.stream()
            .mapToDouble(lot -> lot.getAmount().doubleValue()
                * lot.getEntryPrice().doubleValue())
            .sum());

        // 3. 持仓市值 = 总持仓数量 × 当前价格
        Num positionValue = currentPrice.multipliedBy(totalAmount);

        // 4. 浮动盈亏 = 持仓市值 - 总持仓成本
        Num floatingPnl = positionValue.minus(totalCost);

        // 5. 权益 = 初始资金 + 已实现盈亏 + 浮动盈亏
        equity = initialCapital
            .plus(mpRecord.getCumulativePnl(direction))
            .plus(floatingPnl);
    }
}
```

### 6.5 回测成交模型与费用模型

#### 成交模型

| 模型 | 类名 | 说明 |
|------|------|------|
| 市价单成交 | `MarketOrderModel` | 当前 bar 立即成交，使用当前价格 |
| 次根K线开盘价 | `TradeOnNextOpenModel` | 下一个 bar 的开盘价成交（ta4j 默认） |
| 滑点模型 | `SlippageExecutionModel` | 加滑点后以"成交价偏离"的方式执行（ta4j 内置） |

**改造方案：**
- `MultiPositionBacktestManager` 默认使用 `MarketOrderModel`（k 线内执行）
- 可配置使用 `SlippageExecutionModel` 或自定义模型模拟滑点
- 滑点统一在 `BacktestConfig.slippageRate` 中配置

#### 费用模型

| 模型 | 类名 | 说明 |
|------|------|------|
| 线性费率 | `LinearTransactionCostModel` | 按成交金额 * 费率计算手续费 |
| 零费率 | `ZeroCostModel` | 免手续费（测试用） |
| 手续费的成交模型 | `FeeAwareExecutionModel` | 在成交执行时扣除手续费 |

**改造方案：**
- `MultiPositionBacktestManager` 构造时接收 ta4j `CostModel`，与当前实现一致
- 费率从回测请求的 `commissionRate` 读取

### 6.6 性能指标统一

回测结果中的性能指标计算路径：

```
MultiPositionBacktestManager.run()
  → 返回 MultiPositionTradingRecord
  → BacktestEngine.computeMetricsFromRecord(series, record, baseAmount, config)
  → PerformanceMetrics (totalReturn / maxDrawdown / winRate / totalTrades / ...)
```

`computeMetricsFromRecord()` 已支持 `MultiPositionTradingRecord`，通过 `instanceof` 判断后走不同的交易记录遍历逻辑：

```java
if (tradingRecord instanceof MultiPositionTradingRecord mpRecord) {
    // 遍历所有批次的开平仓交易
    // 计算：总收益率、最大回撤、胜率、盈亏比等
} else {
    // 传统 TradingRecord 的指标计算
}
```

---

## 7. 删除计划：StrategyFactory / SignalBasedStrategyImpl

### 7.1 删除内容

| 待删除类/接口 | 包路径 | 引用数 | 替代方案 |
|-------------|--------|-------|---------|
| `StrategyFactory` | `com.chain.ai.trade.engine.strategy.factory` | 2 处引用 | 删除后由 `DefaultDealStrategyTrade.buildStrategy()` 统一构建 |
| `SignalBasedStrategyImpl` | `com.chain.ai.trade.engine.strategy` | 已删除 | 改用 `SignalMultiDirectionStrategy` + `SignalCacheAware` 适配器 |
| `StrategyFactory` 相关配置 | `spring.factories` 或 `@ComponentScan` | 1 处 | 移除自动注册 |

### 7.2 删除前需要解决的外部引用

#### 引用 1：DefaultDealStrategyTrade

```java
// 当前：buildStrategy() 中 LEGACY 路径
TradingStrategy tradingStrategy = getStrategyFactory()
    .createStrategyByType(params.getStrategyBeanName(), series, params);
return tradingStrategy.buildStrategy(series);

// 改造后：直接构建或通过 LegacyStrategyBuilder 构建
if (needLegacyStrategy(params.getStrategyBeanName())) {
    return buildLegacyStrategy(series, params);
}
```

`buildLegacyStrategy()` 方法保留旧策略构建能力但不依赖 `StrategyFactory` 类，将构建逻辑内联到 `DefaultDealStrategyTrade` 或一个包级私有的辅助类中。

#### 引用 2：BacktestService

```java
// 当前
TradingStrategy strategy = getStrategyFactory()
    .createStrategyByType(backtestRequest.getStrategyType(), series, backtestRequest);

// 改造后
TradingStrategy strategy = new SignalBasedLegacyStrategyBuilder()
    .build(series, backtestRequest);
```

#### 引用 3：BacktestEngine

BacktestEngine 中的引用方式与 BacktestService 类似，通过 `DefaultDealStrategyTrade` 或 `LegacyStrategyBuilder` 替代。

### 7.3 删除步骤

```
阶段 1（准备期）
├── 在 DefaultDealStrategyTrade 中新增 buildLegacyStrategy() 方法
├── 内联原本在 StrategyFactory 中的策略构建逻辑
├── 测试所有传统策略的构建正常
└── 确认 BacktestService/BacktestEngine 不直接引用 StrategyFactory

阶段 2（切换期）
├── 修改 DefaultDealStrategyTrade.buildStrategy() LEGACY 路径
│   从 getStrategyFactory().createStrategyByType() 替换为 buildLegacyStrategy()
├── 修改 BacktestService 引用，移除 StrategyFactory 注入
├── 修改 BacktestEngine 引用，移除 StrategyFactory 注入
└── 运行全量回归测试

阶段 3（清理期）
├── 删除 StrategyFactory.java
├── 删除 SignalBasedStrategyImpl.java
├── 清理 spring.factories 中的自动注册
├── 运行全量构建验证
└── 更新类图/文档
```

---

## 8. 多策略运行时共存机制

### 8.1 机器人级别的策略路由

不同机器人可以运行不同的策略类型，由 `trading_bot` 表字段控制：

| 字段 | 值 | 含义 |
|------|-----|------|
| `strategy_bean_name` | `"signalMultiDirectionStrategy"` | 信号驱动型双向策略 |
| `strategy_bean_name` | `"indicatorDrivenStrategy"` | 指标驱动型双向策略 |
| `strategy_bean_name` | `"macdStrategy"` / 其他 bean name | 传统策略 |
| `strategy_bean_name` | null/空 | 回退到 `strategy.mode` 全局配置 |
| `bot_type` | `"multi_direction"` | 标识该机器人运行时需要使用 `MultiPositionTradingRecord` |
| `bot_type` | `"indicator_driven"` | 标识指标驱动型机器人 |
| `bot_type` | null/空 | 传统机器人 |

### 8.2 运行时决策流程

```
executeTradingLogic()
  ↓
判断 trading_bot.bot_type 或 strategy_bean_name
  ├── "multi_direction" / "indicator_driven"
  │   → 使用 MultiPositionTradingRecord 管理持仓
  │   → processEntrySignals 走 MultiPositionStrategy 分支
  │   → processPositionExits 走 shouldExitSignal 分支
  │   → 支持加仓/分批平仓
  │
  └── 传统策略
      → 使用传统的持仓判断
      → processEntrySignals 走原有分支
      → processPositionExits 走原有分支
      → 单一批次持仓
```

### 8.3 订单服务兼容

双向策略的订单创建和传统策略的订单创建走同一套 `TradeOrderServiceAdapter`：

- 入场：`createOrder(params)` → 返回 orderId → 更新 `MultiPositionTradingRecord.enter(fill)`
- 出场：`closeOrderByVolume(orderId, volume)` → 更新 `MultiPositionTradingRecord.exit(fill)`
- 异步成交回调：在 `OrderFillCallbackHandler` 中更新 `MultiPositionTradingRecord`（实盘场景）

---

## 9. 用户交互流程

### 9.1 前端 UI 流程

```
┌─────────────────────────────────────────────────────────┐
│ 模式切换：ML 智能创建（当前）←→ ML 指标驱动型（新增）           │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ Step 1: 选择交易标的 + 基础配置                            │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ 交易对: [BTC/USDT ▼]   周期: [4h ▼]                 │ │
│ │ 市场类型: [● 合约/永续  ○ 现货]                      │ │
│ │ 投入资金: [1000] USDT                                │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ Step 2: 选择指标策略模板                                  │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ 单指标:                                              │ │
│ │ [RSI超买超卖] [MACD金叉死叉] [MA均线交叉] [+更多]     │ │
│ │                                                      │ │
│ │ 多指标组合:                                           │ │
│ │ [RSI+MA趋势过滤] [MACD+Bollinger] [三重过滤]         │ │
│ │                                                      │ │
│ │ 或自定义指标组合:                                      │ │
│ │ ┌─────────────────────────────────────────────────┐ │ │
│ │ │ 添加指标: [RSI ▼]  周期: [14]  超买: [70]       │ │ │
│ │ │ 超卖: [30]                                       │ │ │
│ │ │ [+ 添加指标规则]                                   │ │ │
│ │ │                                                   │ │ │
│ │ │ 组合条件: [全部满足(AND) ▼]                       │ │ │
│ │ └─────────────────────────────────────────────────┘ │ │
│ │ [AI 帮我生成]                                         │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ Step 3: AI 分析 & 参数推荐                                │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ ● 正在获取近 100 根 K 线数据...                        │ │
│ │ ● AI 分析中...                                       │ │
│ │                                                      │ │
│ │ 推荐结果:                                             │ │
│ │ 指标: RSI(14) + EMA(5, 20) 趋势过滤                   │ │
│ │ 参数:                                                │ │
│ │   - RSI 周期: 14   超买: 70   超卖: 30                │ │
│ │   - 快线 EMA: 5   慢线 EMA: 20                        │ │
│ │   - 过滤方向: only_long                               │ │
│ │   - ATR 止损倍数: 2.0                                 │ │
│ │                                                      │ │
│ │ 信号预览: [📊 查看回测信号分布]                        │ │
│ │                                                      │ │
│ │ [◀ 上一步]  [使用此配置 ▶]                             │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ Step 4: 确认创建                                         │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ 机器人名称: [BTC RSI趋势策略]                          │ │
│ │ 备注: [可选]                                          │ │
│ │                                                      │ │
│ │ 策略摘要:                                             │ │
│ │ · 策略类型: 指标驱动型                                 │ │
│ │ · 交易对: BTC/USDT  周期: 4h                          │ │
│ │ · 指标: RSI(14) + EMA趋势过滤                         │ │
│ │ · 止损: ATR(14) × 2.0                                │ │
│ │                                                      │ │
│ │ [取消]  [确认创建]                                     │ │
│ └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### 9.2 状态流转

```
[选择标的] → [选择指标模板] → [AI分析中] → [预览推荐] → [确认创建] → [创建成功]
                                                                ↓
                                                          Strategy(table: STRATEGY)
                                                          status=draft → active
                                                          strategy_type=indicator_driven
                                                          indicator_config={...}
                                                                ↓
                                                          TradingBot(table: TRADING_BOT)
                                                          bot_name=xxx
                                                          strategy_id=xxx
```

---

## 10. API 设计

### 10.1 新增：AI 指标策略生成（intent = indicator_recommend）

**请求** `POST /strategy/ai-generate`

```json
{
  "intent": "indicator_recommend",
  "prompt": "趋势跟踪，中等风险",
  "marketType": "swap",
  "indicatorTemplate": "rsi_ma_trend",
  "symbol": "BTC/USDT",
  "timeframe": "4h"
}
```

**响应**

```json
{
  "success": true,
  "message": "生成成功",
  "data": {
    "botType": "indicator_driven",
    "reason": "RSI(14)搭配EMA趋势过滤适合当前BTC震荡上行行情...",
    "baseConfig": {
      "symbol": "BTC/USDT",
      "timeframe": "4h",
      "marketType": "swap",
      "leverage": 2,
      "initialCapital": 1000
    },
    "strategyBeanName": "indicatorDrivenStrategy",
    "indicatorConfig": {
      "indicatorType": "rsi_ma_trend",
      "params": {
        "rsi_period": 14,
        "rsi_oversold": 30,
        "rsi_overbought": 70,
        "fast_ma_period": 5,
        "fast_ma_type": "EMA",
        "slow_ma_period": 20,
        "slow_ma_type": "EMA"
      },
      "signalRules": {
        "entry": "rsi_oversold_and_trend_up",
        "exit": "signal_reversal"
      }
    },
    "marketAnalysis": {
      "currentPrice": 67500,
      "trend": "bullish",
      "volatility": "medium",
      "rsiValue": 58,
      "maTrend": "ema5(67200) > ema20(65800)",
      "recommendation": "适合做多，建议EMA5上穿EMA20时入场"
    }
  }
}
```

### 10.2 确认创建（扩展现有接口）

**请求** `POST /strategy/ai-confirm`

```json
{
  "recommendation": {
    "...": "...(完整推荐结果，含indicatorConfig)..."
  },
  "botName": "BTC RSI趋势策略",
  "remark": "自动指标驱动型机器人",
  "userId": "u_xxx",
  "accountId": "a_xxx"
}
```

**响应**

```json
{
  "success": true,
  "message": "创建成功",
  "data": {
    "strategyId": "s_xxx",
    "botId": "b_xxx",
    "botName": "BTC RSI趋势策略"
  }
}
```

### 10.3 回测请求

指标驱动型策略的快速回测请求：

```json
{
  "strategyType": "indicatorDrivenStrategy",
  "indicatorConfig": {
    "indicatorType": "rsi_ma_trend",
    "params": {
      "rsiPeriod": 14,
      "oversold": 30,
      "overbought": 70,
      "fastMaPeriod": 5,
      "slowMaPeriod": 20
    },
    "signalRules": {
      "entry": "rsi_oversold_and_uptrend",
      "exit": "signal_reversal"
    }
  },
  "exitRules": [
    {"type": "stop_loss", "percent": 0.03},
    {"type": "take_profit", "percent": 0.06}
  ],
  "initialAmount": 1000,
  "commissionRate": 0.00045,
  "slippageRate": 0.0
}
```

**回测响应**

```json
{
  "results": [{
    "strategyName": "indicatorDrivenStrategy_RSI_MA",
    "strategyType": "indicator_driven",
    "performanceMetrics": {
      "totalReturn": 12.5,
      "maxDrawdown": 5.2,
      "winRate": 65.0,
      "totalTrades": 20
    },
    "tradeRecords": [
      {
        "entryTime": "...",
        "exitTime": "...",
        "direction": "BUY",
        "lots": 1,
        "pnl": 50.0,
        "exitType": "TAKE_PROFIT"
      }
    ],
    "equityCurve": "...",
    "drawdownSeries": "..."
  }]
}
```

### 10.4 运行时的策略执行（现有流程，无需新增接口）

指标驱动型策略的运行时执行**复用现有链路**，不新增独立接口：

```
XXL-JOB 定时调度
    ↓
LiveTradingServiceImpl.processStrategy()
    ↓
IDealStrategyBasic.execStrategy()
    ↓
DefaultDealStrategyTrade.buildStrategy()（统一构建入口）
```

---

## 11. 数据模型设计

### 11.1 设计原则

指标驱动型策略的数据存储**对齐现有信号驱动型策略的存储模式**，不另起炉灶：

| 配置维度 | 存储位置 | 信号驱动型 | 指标驱动型 |
|---------|---------|-----------|-----------|
| 策略标识 | `strategy` 表顶层字段 | `strategy_id`, `strategy_type` | 相同，`strategy_type=INDICATOR_DRIVEN` |
| 策略Bean名称 | `trading_bot` 表 **顶层字段** | `strategy_bean_name` | **相同顶层字段**，不塞入 JSON |
| 指标参数 | `trading_bot.indicator_config` JSON | 无 | **仅存指标特有的参数和信号规则** |
| 出场规则 | `strategy_parameter` 表 `exit_rules` 组 | 止损/止盈/trailing/batch/SMC | **完全复用相同机制** |
| 交易行为 | `trading_bot.trading_config` JSON | 仓位/方向/杠杆 | 相同 |
| 交易所连接 | `trading_bot.exchange_config` JSON | API密钥/交易所 | 相同 |

### 11.2 trading_bot 表字段调整

| 字段 | 类型 | 说明 | 变更 |
|------|------|------|------|
| `strategy_bean_name` | varchar | 策略 bean 名称 | 提升为路由键（从 `indicator_config` 中移出） |
| `bot_type` | varchar | 机器人类型 | 新增：`multi_direction`, `indicator_driven`, null |
| `indicator_config` | json | 指标配置 | **重构**：只含 `indicatorType + params + signalRules` |
| `trading_config` | json | 交易行为配置 | 不变：maxLots, matchPolicy 等 |
| `strategy_parameter` | 关联表 | 退出规则参数 | 不变：`exit_rules` 分组 |

### 11.3 indicator_config 结构（精简版）

`indicator_config` **只承载指标策略特有的参数和信号规则定义**，退出规则、策略Bean名称都不放在这里：

```json
{
  "indicatorType": "rsi_ma_trend",
  "params": {
    "rsi_period": 14,
    "rsi_oversold": 30,
    "rsi_overbought": 70,
    "fast_ma_period": 5,
    "fast_ma_type": "EMA",
    "slow_ma_period": 20,
    "slow_ma_type": "EMA"
  },
  "signalRules": {
    "entry": "rsi_oversold_and_uptrend",
    "exit": "signal_reversal"
  }
}
```

> **`strategyBeanName` 为什么不在 indicator_config 里？**
>
> `strategyBeanName` 是引擎加载策略实现的**入口标识**，属于策略路由信息而非指标参数。如果埋入 JSON，会导致：
> - 查询时额外解析 JSON 才能拿到 Bean 名称
> - `DefaultDealStrategyTrade.buildStrategy()` 需要多一次 JSON 读取才能决定策略类型
> - 与现有 `TradingStrategyParams.strategyBeanName` 顶层字段不一致
>
> 因此 `strategyBeanName` 统一存于 `trading_bot` 表顶层字段，与 `TradingStrategyParams` 对齐。

### 11.4 退出规则延续现有模式

出场规则**不走 `indicator_config`**，而是沿用已有 `strategy_parameter` 表的 `exit_rules` 分组机制：

```sql
-- 已有表结构（无需修改）
-- strategy_parameter:
--   strategy_id  | group_name  | name              | default_value
--   -------------|-------------|-------------------|---------------
--   s_xxx        | exit_rules  | stopLoss          | {"fixed_percent":{"enabled":true,"percent":0.03}}
--   s_xxx        | exit_rules  | takeProfit        | {"enabled":true,"type":"fixed_percent","percent":0.06}}
--   s_xxx        | exit_rules  | takeProfitTrailing| {"enabled":true,...}
--   s_xxx        | exit_rules  | batchTakeProfitPlans| [...]
--   s_xxx        | exit_rules  | smcExit           | {...}
```

构建时由 `DefaultDealStrategyTrade` 中已有的 `loadExitRulesConfig(strategyId)` 统一加载，**信号驱动型和指标驱动型策略的出场规则加载路径完全一致**。

### 11.5 StrategyType 枚举扩展

| 枚举值 | 含义 | 说明 |
|-------|------|------|
| `TREND` | 趋势跟踪 | 现有 |
| `GRID` | 网格交易 | 现有 |
| `MEAN_REVERSION` | 均值回归 | 现有 |
| `BREAKOUT` | 突破交易 | 现有 |
| `SCALPING` | 剥头皮 | 现有 |
| **`INDICATOR_DRIVEN`** | **指标驱动型** | **新增** |

### 11.6 完整创建/更新机器人请求示例

```json
{
  "strategyBeanName": "indicatorDrivenStrategy",
  "botType": "indicator_driven",
  "indicatorConfig": {
    "indicatorType": "rsi_ma_trend",
    "params": {
      "rsiPeriod": 14,
      "oversold": 30,
      "overbought": 70,
      "fastMaPeriod": 5,
      "slowMaPeriod": 20
    },
    "signalRules": {
      "entry": "rsi_oversold_and_uptrend",
      "exit": "signal_reversal"
    }
  },
  "tradingConfig": {
    "maxLongLots": 5,
    "maxShortLots": 3,
    "matchPolicy": "FIFO"
  }
}
```

---

## 12. AI 参数推荐与 Prompt 设计

### 12.1 通用架构：AI 参数推荐

AI Prompt 的本质是**策略配置生成器**，不绑定任何特定的 botType 或执行模式。输入用户需求 + 市场数据，按 botType 路由到对应的 Prompt 模板，输出该策略类型所需的参数配置。

AI 生成的参数配置与策略运行时的执行模式（信号驱动/指标驱动等）是**两个独立的关注点**：

| 关注点 | 说明 | 实现层 |
|-------|------|-------|
| AI 参数推荐 | 根据用户需求和市场数据，生成策略参数 | Prompt 模板 + LLM |
| 策略执行 | 运行时按 strategyBeanName 路由到执行逻辑 | 交易引擎 |

```
用户输入: "帮我创建一个ETH多指标趋势策略" 或 "分析BTC走势，推荐合适参数"
     │
     ▼
AI Prompt 路由 (按 botType)
     │
     ├── botType=indicator_driven → 指标推荐 Prompt → indicatorConfig
     ├── botType=grid            → 网格参数 Prompt → upperPrice/gridCount
     ├── botType=martingale      → 马丁参数 Prompt → multiplier/maxLayers
     └── botType=trend           → 趋势参数 Prompt → maPeriod/direction
     │
     ▼
前端回填表单 → 确认 → 创建机器人
     │
     ▼
交易引擎执行 (按 strategyBeanName 路由，与 AI 无关)
     ├── strategyBeanName=indicatorDrivenStrategy → IndicatorDrivenStrategy (实时计算)
     └── strategyBeanName=signalMultiDirection   → SignalMultiDirectionStrategy (信号查表)
```

同一种 botType 也可以选择不同的执行模式。例如一个 RSI 策略：
- 走信号驱动：外部 Python 服务定时计算 RSI 并写入 `TechnicalSignal` 表，引擎定时读取执行
- 走指标驱动：引擎内嵌 ta4j RSI Indicator，每根 K 线实时计算并执行

两种都是有效选择，取决于系统架构需求。有些策略（如网格交易）的信号逻辑与实时价格和当前持仓强耦合，预先落库过于复杂，因此天然适合指标驱动型模式。

#### 12.1.1 Prompt 模板注册机制

每个 botType 对应一个 Prompt 模板，模板定义：

1. **参数 Schema**：该策略类型有哪些可调参数（类型、取值范围、默认值）
2. **输出格式**：AI 返回的 JSON 结构（botType + strategyBeanName + 策略参数）
3. **市场数据需求**：需要哪些 K 线分析数据辅助推荐（当前价格、波动率、均线等）

```java
// Prompt 模板注册示例
public enum BotTypePromptTemplate {
    INDICATOR_DRIVEN("indicator_driven", "indicatorDrivenStrategy", IndicatorSchema.class),
    GRID("grid", "gridStrategy", GridSchema.class),
    MARTINGALE("martingale", "martingaleStrategy", MartingaleSchema.class),
    TREND("trend", "trendStrategy", TrendSchema.class);

    private final String botType;
    private final String defaultBeanName;
    private final Class<?> schemaClass;
}
```

前端根据 `intent` + `botType` 两个字段决定调用哪个 Prompt：
- `intent=indicator_recommend` → indicator_driven 系列 Prompt（含指标模板选择）
- `intent=ai_generate` + `botType=grid` → grid Prompt（现有流程扩展）
- `intent=ai_generate` + `botType=martingale` → martingale Prompt（现有流程扩展）

### 12.2 指标驱动型 Prompt（botType=indicator_driven）

```java
private static final String INDICATOR_SYSTEM_PROMPT = """
你是一个专业的量化交易策略顾问。用户希望创建一个基于技术指标驱动的自动交易机器人。

你的任务：
1. 分析用户提供的 K 线数据
2. 根据用户的需求和 K 线数据，推荐最优的指标组合和参数
3. 生成可在运行时通过 ta4j 框架构建的策略参数配置

可用指标模板类型，系统通过 ta4j 框架的 Indicator + Rule 模式执行：
1. rsi_simple - RSI 超买超卖：{rsiPeriod: int(5-30), oversold: int(20-40), overbought: int(60-80)}
   → buy: RSI < oversold, sell: RSI > overbought
2. macd_cross - MACD 金叉死叉：{fastPeriod: int(5-20), slowPeriod: int(20-40), signalPeriod: int(5-15)}
   → buy: MACD 上穿信号线, sell: MACD 下穿信号线
3. ma_cross - MA 均线交叉：{fastMaPeriod: int(3-30), slowMaPeriod: int(10-200), maType: 'EMA'|'SMA'}
   → buy: 快线上穿慢线, sell: 快线下穿慢线
4. bb_breakout - Bollinger Bands 突破：{bbPeriod: int(10-30), bbStd: number(1.5-3.0)}
   → buy: 价格触下轨, sell: 价格触上轨
5. rsi_ma_trend - RSI + MA 趋势过滤：{rsiPeriod: int, oversold: int, overbought: int, fastMaPeriod: int, slowMaPeriod: int, maType: 'EMA'|'SMA', trendFilter: 'long_only'|'short_only'|'both'}
6. macd_bb - MACD + Bollinger 组合：{fastPeriod: int, slowPeriod: int, signalPeriod: int, bbPeriod: int, bbStd: number}

请返回严格的 JSON 格式：
{
  "botType": "indicator_driven",
  "reason": "推荐理由，结合K线数据分析",
  "baseConfig": { ... },
  "strategyBeanName": "indicatorDrivenStrategy",
  "indicatorConfig": {
    "indicatorType": "选择的模板类型",
    "params": { ... },
    "signalRules": {
      "entry": "规则名，如 rsi_oversold_and_trend_up",
      "exit": "规则名，如 signal_reversal"
    }
  },
  "marketAnalysis": {
    "currentPrice": 0,
    "trend": "bullish|bearish|sideways",
    "volatility": "low|medium|high",
    "keyIndicators": { ... }
  }
}

注意：
- `strategyBeanName` 为顶层字段，**不在** `indicatorConfig` 内部
- params 中的参数必须与所选模板类型兼容
- 不需要生成 Python 代码，系统会使用 ta4j 的 Java 指标实现
- 系统会将 params 注入到对应的 ta4j Indicator 构建方法中
- 退出规则（止损/止盈等）不在此处返回，由用户通过前端配置或使用默认的 `exit_rules`
""";
```

### 12.3 K 线数据分析附加 Prompt（通用，所有 botType 共用）

```
=== KLINE DATA for {symbol} ({timeframe}) ===
Latest {count} candles:
- Current Price: {currentPrice}
- 24h High: {high24h}
- 24h Low: {low24h}
- Volume (avg): {avgVolume}
- Price Change: {changePct}%
- SMA(5): {sma5}
- SMA(20): {sma20}
- Trend: {trend}
- Volatility: {volatility}%
- RSI(14): {rsi}
- Latest closes: {closes}

Please analyze the above market data and recommend optimal parameters.
```

---

## 13. 运行时执行

### 13.1 执行链路总览

交易引擎的运行时执行**复用同一套调度框架**，信号驱动型和指标驱动型两种策略模式共享相同的 XXL-JOB 定时调度和 `IDealStrategyBasic` 执行链路。核心组件按模块划分：

| 组件 | 角色 | 模块 |
|------|------|------|
| `XXL-JOB` | 定时调度 | ai-quant |
| `LiveTradingServiceImpl` | 执行编排（遍历活跃机器人 → 调用 execStrategy） | ai-quant |
| `IDealStrategyBasic.execStrategy()` | 核心执行入口 | ai-engine |
| `DefaultDealStrategyTrade` | 策略构建统一入口 | ai-engine |
| `SignalCacheManager` | 信号驱动型 → 从 TechnicalSignal 表加载信号到内存 | ai-signal |
| `MultiPositionStrategy` | 策略接口（两种策略类型共同实现） | ai-extension |
| `DirectionalRule` | 方向规则接口（支持入场/出场的 TradeType 方向判断） | ai-extension |

### 13.2 完整执行链路

```
XXL-JOB 调度 (cron = timeframe)
    │
    └─→ LiveTradingServiceImpl.processStrategy(botId)
        │
        └─→ IDealStrategyBasic.execStrategy(botId, exchangeService, isTest=false)
            │
            ├─ 1. initBarSeries()
            │      └─ 从交易所获取 K 线 → BarSeries
            │
            ├─ 2. buildStrategy(botId, barSeries)  ← 统一构建入口
            │      └─ DefaultDealStrategyTrade.buildStrategy()
            │          │
            │          ├─ 1. 获取 trading_bot.strategy_bean_name（顶层字段）
            │          │   = "indicatorDrivenStrategy" / "signalMultiDirectionStrategy" / 传统策略
            │          │
            │          ├─ 2. 加载 exit_rules（两种双向策略共享）
            │          │   └─ loadExitRulesConfig(strategyId)
            │          │       → ExitRulesConfig → OrDirectionalRule
            │          │
            │          ├─ 3. 按 strategyBeanName 分发构建入场规则：
            │          │
            │          │   if ("signalMultiDirectionStrategy"):
            │          │       SignalCacheManager cache = loadSignals(...)
            │          │       MultiDirectionEntryRule entryRule = new ...
            │          │       → new SignalMultiDirectionStrategy(series, entryRule, exitOrRule, name)
            │          │
            │          │   if ("indicatorDrivenStrategy"):
            │          │       JSONObject indicatorConfig = tradingBot.getIndicatorConfig()
            │          │       IndicatorDrivenDirectionRule entryRule =
            │          │           IndicatorDrivenDirectionRule.buildEntry(indicatorConfig, series)
            │          │       → new IndicatorDrivenStrategy(series, entryRule, exitOrRule, name)
            │          │
            │          │   else:
            │          │       → buildLegacyStrategy(series, params)  // 传统策略
            │          │
            │          └─ // exitOrRule 在两种策略中为同一对象，保证出场规则加载逻辑完全一致
            │
            └─ 3. executeTradingLogic()
                   ├─ processPositionExits()
                   │   └─ strategy.shouldExitSignal(index, record) → ExitSignal → 平仓
                   └─ processEntrySignals()
                       └─ strategy.shouldEnterDirection(index, record) → TradeType → 开仓
```

### 13.3 SignalMultiDirectionStrategy 实现（信号驱动型，现有）

```java
@Component("signalMultiDirectionStrategy")
public class SignalMultiDirectionStrategy implements MultiPositionStrategy {

    private final SignalCacheManager signalCache;
    private final MultiDirectionEntryRule entryRule;
    private final OrDirectionalRule exitRule;
    private final String name;
    private final int unstableBars;

    public SignalMultiDirectionStrategy(BarSeries series,
            SignalCacheManager cache,
            OrDirectionalRule exitRule,
            String name) {
        this.signalCache = cache;
        this.entryRule = new MultiDirectionEntryRule(cache, series);
        this.exitRule = exitRule;
        this.name = name;
        this.unstableBars = 0;
    }

    @Override
    public TradeType shouldEnterDirection(int index, MultiPositionTradingRecord record) {
        if (isUnstableAt(index)) return null;
        return entryRule.getDirection(index, record);
    }

    @Override
    public ExitSignal shouldExitSignal(int index, MultiPositionTradingRecord record) {
        if (isUnstableAt(index)) return null;
        return exitRule.getSignal(index, record);
    }

    @Override
    public String getName() { return name; }

    @Override
    public TradeType getStartingType() { return TradeType.BUY; }
}
```

### 13.4 IndicatorDrivenStrategy 实现（指标驱动型，新增）

与 `SignalMultiDirectionStrategy` 结构一致，都 `implements MultiPositionStrategy`。**关键区别：入场信号来源于 ta4j 指标实时计算而非信号缓存，出场规则通过构造函数接收外部的 `OrDirectionalRule`**。

```java
@Component("indicatorDrivenStrategy")
public class IndicatorDrivenStrategy implements MultiPositionStrategy {

    private final String name;
    private final int unstableBars;
    private final IndicatorDrivenDirectionRule entryRule;
    private final OrDirectionalRule exitRule;

    /**
     * 构造函数接收 OrDirectionalRule（exit_rules 已在外部构建好）
     */
    public IndicatorDrivenStrategy(BarSeries series,
            IndicatorDrivenDirectionRule entryRule,
            OrDirectionalRule exitRule,
            String name) {
        this.name = name;
        this.unstableBars = 0;
        this.entryRule = entryRule;
        this.exitRule = exitRule;
    }

    @Override
    public TradeType shouldEnterDirection(int index, MultiPositionTradingRecord record) {
        if (isUnstableAt(index)) return null;
        return entryRule.getDirection(index, record);
    }

    @Override
    public ExitSignal shouldExitSignal(int index, MultiPositionTradingRecord record) {
        if (isUnstableAt(index)) return null;
        return exitRule.getSignal(index, record);
    }

    @Override
    public String getName() { return name; }

    @Override
    public TradeType getStartingType() { return TradeType.BUY; }
}
```

### 13.5 IndicatorDrivenDirectionRule — 指标驱动入场规则

```java
/**
 * 指标驱动方向规则。与 SignalMultiDirectionStrategy 的 MultiDirectionEntryRule 结构一致，
 * 区别在于信号源从 SignalCacheManager 替换为 ta4j Indicator Lambda (IntPredicate)。
 *
 * IndicatorDrivenStrategy 的出场规则由 OrDirectionalRule 承载（与信号驱动型共用），
 * 因此本类仅用于构造入场条件。
 */
public class IndicatorDrivenDirectionRule implements DirectionalRule {

    private IntPredicate condition;
    private TradeType direction;

    private IndicatorDrivenDirectionRule(IntPredicate condition, TradeType direction) {
        this.condition = condition;
        this.direction = direction;
    }

    /**
     * 静态工厂方法：根据 indicator_config 构建入场规则的 IndicatorDrivenDirectionRule
     * 出场信号规则不在此处构建，交由统一的 loadExitRulesConfig() 加载
     */
    public static IndicatorDrivenDirectionRule buildEntry(
            JSONObject indicatorConfig, BarSeries series) {
        String type = indicatorConfig.getString("indicatorType");
        JSONObject params = indicatorConfig.getJSONObject("params");
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        switch (type) {
            case "rsi_simple": {
                int period = params.getInt("rsiPeriod");
                int oversold = params.getInt("oversold");
                RSIIndicator rsi = new RSIIndicator(close, period);
                return new IndicatorDrivenDirectionRule(
                    index -> rsi.getValue(index).isLessThan(series.numOf(oversold)),
                    TradeType.BUY);
            }
            case "rsi_ma_trend": {
                int period = params.getInt("rsiPeriod");
                int oversold = params.getInt("oversold");
                int fastPeriod = params.getInt("fastMaPeriod");
                int slowPeriod = params.getInt("slowMaPeriod");
                RSIIndicator rsi2 = new RSIIndicator(close, period);
                EMAIndicator fastMa = new EMAIndicator(close, fastPeriod);
                EMAIndicator slowMa = new EMAIndicator(close, slowPeriod);
                return new IndicatorDrivenDirectionRule(
                    index -> rsi2.getValue(index).isLessThan(series.numOf(oversold))
                        && fastMa.getValue(index).isGreaterThan(slowMa.getValue(index)),
                    TradeType.BUY);
            }
            case "macd_cross": {
                int fastPeriod = params.getInt("fastPeriod");
                int slowPeriod = params.getInt("slowPeriod");
                int signalPeriod = params.getInt("signalPeriod");
                MACDIndicator macd = new MACDIndicator(close, fastPeriod, slowPeriod);
                EMAIndicator signal = new EMAIndicator(macd, signalPeriod);
                return new IndicatorDrivenDirectionRule(
                    index -> macd.getValue(index).isGreaterThan(signal.getValue(index))
                        && macd.getValue(index - 1).isLessThanOrEqual(
                            signal.getValue(index - 1)),
                    TradeType.BUY);
            }
            default:
                throw new UnsupportedOperationException(
                    "Unknown indicator type: " + type);
        }
    }

    @Override
    public ExitSignal getSignal(int index, TradingRecord tradingRecord) {
        if (condition == null || direction == null) return null;
        if (condition.test(index)) {
            return new ExitSignal(direction, ExitType.TECHNICAL_INDICATOR);
        }
        return null;
    }

    @Override
    public TradeType getDirection(int index, TradingRecord tradingRecord) {
        ExitSignal signal = getSignal(index, tradingRecord);
        return signal != null ? signal.getDirection() : null;
    }
}
```

### 13.6 两种策略的执行对比

| 执行阶段 | 信号驱动型（现有） | 指标驱动型（新增） |
|---------|-----------------|------------------|
| 策略构建位置 | `DefaultDealStrategyTrade.buildStrategy()` | **相同 — 统一在 buildStrategy() 中构建** |
| 策略类 | `SignalMultiDirectionStrategy` | `IndicatorDrivenStrategy` |
| 信号来源 | `SignalCacheManager`（读 TechnicalSignal 表） | ta4j `Indicator` 实时计算 |
| 入场信号 | `MultiDirectionEntryRule.getSignal()` 查缓存 | `IndicatorDrivenDirectionRule.getSignal()` 算指标 |
| **出场信号** | `MultiDirectionExitRule` + `OrDirectionalRule` 组合 | **完全复用相同的 OrDirectionalRule + exit_rules** |
| **风控规则** | 内置止损/止盈/移动止损/时间止盈/分批止盈等 | **完全复用现有的 DirectionalRule 组合模式** |
| 入场构建 | `new MultiDirectionEntryRule(cache, series)` | `IndicatorDrivenDirectionRule.configure(config, series)` |
| 出场构建 | `loadExitRulesConfig()` → OrDirectionalRule | **完全复用同一套代码** |
| 开仓执行 | `shouldEnterDirection → entryRule.getDirection` | 相同逻辑 |
| 平仓执行 | `shouldExitSignal → exitRule.getSignal` | 相同逻辑 |
| 接口 | `implements MultiPositionStrategy` | `implements MultiPositionStrategy` |

---

## 14. 与 AI Filter 系统的关系

### 14.1 定位差异

| 维度 | AI Filter 系统 | 指标驱动型策略 |
|------|--------------|--------------|
| 角色 | 信号过滤器（二次确认） | 信号发生器（一次来源） |
| 执行时机 | 已有信号后的二次校验 | 直接生成交易信号 |
| 依赖 | 需要上游策略提供信号 | 独立运作，不依赖外部信号 |
| AI 介入 | 实时调用 AI 做判断 | AI 仅用于参数生成阶段 |
| 延迟敏感度 | 低（可接受 AI 延迟） | 高（需快速执行） |

### 14.2 协同场景

两种模式可以组合使用：指标驱动型策略生成的信号也可以接入 AI Filter 进行二次过滤。

```
指标驱动 → buy/sell 信号 → AI Filter 过滤 → 确认执行
```

---

## 15. 实施路线图

> **状态标记说明**：
> - ✅ 已完成（代码已实现，已验证）
> - 🔄 需改造（已有基础，需扩展/重构）
> - ❌ 未开始（需新建）

### 阶段一：交易引擎改造（4周）

| 周 | # | 任务 | 交付物 | 状态 |
|---|----|------|--------|------|
| 第1周 | 1 | `buildStrategy()` 扩展为按 `strategyBeanName` 路由 | `buildStrategy()` 三路分发 | ✅ 已完成 |
| | 2 | `loadExitRulesConfig()` 参数化，支持 robotId 过滤 | 退出规则参数化加载 | ✅ 已完成 |
| | 3 | `SignalMultiDirectionStrategy` 构造函数增加 `exitRule` 外置参数 | 构造函数变更 | ✅ 已完成 |
| 第2周 | 4 | 改造 `processPositionExits()` 统一走 `shouldExitSignal()` | 出场逻辑统一 | ✅ 已完成 |
| | 5 | 改造 `processEntrySignals()` 完善 `MultiPositionStrategy` 统一接口 | 入场逻辑统一 | ✅ 已完成 |
| | 6 | 实现异步订单回调队列处理器（PendingOrderWorker），保证实盘持仓更新线程安全 | 回调队列处理器 | ✅ 已完成 |
| 第3周 | 7 | 实现 `IndicatorDrivenStrategy` 类 | 指标驱动型策略实现 | ✅ 已完成 |
| | 8 | 实现 `IndicatorDrivenDirectionRule` 类（buildEntry 工厂方法） | 指标驱动入场规则 | ✅ 已完成 |
| | 9 | 单元测试：`SignalMultiDirectionStrategy` + `IndicatorDrivenStrategy` | 策略单元测试 | ✅ 已完成 |
| 第4周 | 10 | 集成测试：同机器人切换不同策略类型 | 集成测试报告 | ✅ 已完成 |
| | 11 | XXL-JOB 定时调度链路验证 | 调度验证 | ✅ 已完成 |
| | 12 | 实盘模拟测试 | 模拟测试报告 |  ✅ 已完成 |

### 阶段二：回测引擎改造（3周）

| 周 | # | 任务 | 交付物 | 状态 |
|---|----|------|--------|------|
| 第1周 | 13 | `MultiPositionBacktestManager.equityCurve` 多批次持仓权益计算公式明确+修复 | Equity curve 修复 | 🔄 需完善 |
| | 14 | 增强 `BacktestEngine.computeMetricsFromRecord()` 对 `MultiPositionTradingRecord` 支持 | 指标计算完成 | ✅ 已完成 |
| 第2周 | 15 | `BacktestService` 统一回测入口（减少 `instanceof` 分散判断） | 回测入口重构 | ✅ 已完成 |
| | 16 | `runParameterRangeOptimization()` 支持指标驱动型策略参数优化 | 参数优化兼容 | ❌ 待定（当前无指标驱动型参数优化需求） |
| 第3周 | 17 | 回测结果增强：出场类型统计、多批次持仓明细 | 回测结果增强 | ✅ 已完成 |
| | 18 | 集成测试：指标驱动型策略回测全链路 + 快速回测与传统回测一致性验证 | 回测集成测试 | ✅ 已完成 |

### 阶段三：代码清理（1周）

| # | 任务 | 交付物 | 状态 |
|---|------|--------|------|
| 19 | 全项目引用扫描（grep 确认 `StrategyFactory`/`SignalBasedStrategyImpl` 零引用） | 引用扫描确认 | ✅ 已完成 |
| 20 | 内联 `StrategyFactory` 构建逻辑到 `BacktestService` + `IDealStrategyBasic` | `StrategyFactory` 删除 | ✅ 已完成 |
| 21 | 确认零引用后删除 `SignalBasedStrategyImpl` | `SignalBasedStrategyImpl` 删除 | ✅ 已完成（改用 `SignalMultiDirectionStrategy` + `SignalCacheAware` 接口替代，`BacktestEngine` 中 `instanceof` 替换为 `SignalCacheAware`） |
| 22 | 清理 `spring.factories`、配置引用、import 语句 | 配置清理 | ✅ 已完成（无残留引用） |
| 23 | 全量构建 + 全量回归测试 | 构建绿色 | ✅ 已完成（ai-engine 编译通过，5 项测试全部通过） |

### 阶段四：上线与监控（1周）

| # | 任务 | 交付物 | 状态 |
|---|------|--------|------|
| 24 | 灰度上线 1-2 个信号驱动型机器人（验证改造后兼容性） | 灰度验证 | ❌ 未开始 |
| 25 | 监控交易记录和盈亏 | 监控报告 | ❌ 未开始 |
| 26 | 灰度上线指标驱动型机器人 | 灰度验证 | ❌ 未开始 |
| 27 | 全量上线 + 旧代码下线 | 上线完成 | ❌ 未开始 |

### 阶段五：增强与兜底功能（与主阶段并行）

| # | 任务 | 优先级 | 交付物 | 状态 |
|---|------|--------|--------|------|
| 28 | **信号冷却机制**：`processEntrySignals()` 增加 `minBarsBetweenEntries` 冷却检查 | 中 | 防信号过频 | ❌ 未开始 |
| 29 | **AI 参数后端校验**：`buildEntry()` 增加参数越界校验、边界截断、告警日志 | 中 | 参数安全兜底 | ❌ 未开始 |
| 30 | **多批次持仓权益曲线公式**：文档化完整计算公式（持仓市值、浮动盈亏、权益） | 低 | 文档补充 | ❌ 未开始 |

### 已完成功能基线（代码已实现，不在排期内）

| # | 功能 | 位置 |
|---|------|------|
| ✅ | `MultiPositionStrategy` 接口 | `ai-extension/.../strategy/MultiPositionStrategy.java` |
| ✅ | `SignalMultiDirectionStrategy` 信号驱动双向策略 | `ai-strategy/.../rule/SignalMultiDirectionStrategy.java` |
| ✅ | `MultiPositionBacktestManager` 回测执行器 | `ai-extension/.../backtest/MultiPositionBacktestManager.java` |
| ✅ | `MultiPositionTradingRecord` 多批次持仓记录 | `ai-extension/.../bidirectional/` |
| ✅ | 出场规则体系（止损/止盈/移动止损/分批止盈/时间止盈/MACD/成交量等） | `ai-extension/.../rule/` + `ai-strategy/.../rule/` |
| ✅ | `DefaultDealStrategyTrade` BIDIRECTIONAL 模式 + `loadExitRulesConfig()` | `ai-engine/.../core/impl/DefaultDealStrategyTrade.java` |
| ✅ | `BacktestEngine` 支持 `MultiPositionTradingRecord` 指标计算 + 合约权益曲线 | `ai-engine/.../backtest/BacktestEngine.java` |
| ✅ | `BacktestService` 参数优化双向策略分支 | `ai-engine/.../backtest/BacktestService.java` |
| ✅ | `AiStrategyService` AI 策略生成 + Prompt 设计 | `ai-quant/.../service/impl/AiStrategyService.java` |
| ✅ | XXL-JOB 定时调度 | 已有 |

---

## 16. 附录

### 16.1 参考文档补充

（除第1.3节已列出的参考文档外，补充以下文档）

- ta4j 官方文档 — 指标库 API 参考（https://ta4j.github.io/ta4j-wiki/）

### 16.2 关键类关系

```
┌─────────────────────────────────────────────────────┐
│              IDealStrategyBasic (接口)                │
│  +execStrategy() / +executeTradingLogic()           │
│  +buildStrategy() / +processEntrySignals()          │
│  +processPositionExits() / +loadExitRulesConfig()   │
└──────────────────────┬──────────────────────────────┘
                       │ implements
┌──────────────────────▼──────────────────────────────┐
│           DefaultDealStrategyTrade                   │
│  ├── buildStrategy()          统一策略工厂            │
│  ├── processEntrySignals()    统一入场               │
│  ├── processPositionExits()   统一出场                │
│  ├── loadExitRulesConfig()    OrDirectionalRule       │
│  └── MultiPositionTradingRecord (双向持仓管理)         │
└──────────────────────┬──────────────────────────────┘
                       │
          ┌────────────┼────────────────┐
          ▼            ▼                ▼
┌─────────────────┐ ┌─────────────┐ ┌─────────────────┐
│SignalMultiDirect│ │Indicator-  │ │ 传统策略         │
│ionStrategy      │ │Driven-     │ │ (LEGACY)        │
│(信号驱动)       │ │Strategy    │ │                 │
│                 │ │(指标驱动)  │ │                 │
└───────┬─────────┘ └──────┬──────┘ └─────────────────┘
        │                  │
        └──────┬───────────┘
               │ implements
               ▼
┌─────────────────────────────────────────────────────┐
│           MultiPositionStrategy (接口)               │
│  +shouldEnterDirection(index, record)  TradeType     │
│  +shouldExitSignal(index, record)     ExitSignal     │
│  +getName()                           String         │
│  +getStartingType()                   TradeType      │
└─────────────────────────────────────────────────────┘
```

### 16.3 关键决策记录

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 策略路由方式 | 全局 mode / 机器人级别 beanName | 机器人级别 beanName | 支持同一应用多机器人运行不同策略 |
| 出场规则加载时机 | 策略内部 / buildStrategy() 外部注入 | buildStrategy() 外部注入 | 规则与策略分离，便于测试和共享 |
| SignalBasedStrategyImpl 删除 | 保留 / 删除 | 删除 | 已废弃，功能被替代 |
| MultiPositionTradingRecord 持久化 | 文件序列化 / 数据库 | 先文件后数据库 | 第一阶段简化，后续迁移 |
| 回测成交模型 | 次根K线 / 当前K线 | 当前K线 (MarketOrderModel) | 回测精度更高，与实盘行为一致 |
| indicator_config 字段设计 | JSON 字符串 / 对象 | 对象（List<IndicatorConfig>） | 类型安全，便于序列化和参数验证 |

### 16.4 ta4j 版本兼容性说明

当前依赖 ta4j `0.22.4-SNAPSHOT`，改造方案中使用的 ta4j 核心 API：

| API | 用途 | 兼容版本 |
|-----|------|---------|
| `Strategy.shouldOperate()` | 策略信号判断 | >= 0.12 |
| `Rule.isSatisfied()` | 规则条件判断 | >= 0.12 |
| `BarSeries.getBar()` | K 线数据访问 | >= 0.12 |
| `BarSeries.getBeginIndex()/getEndIndex()` | 回测范围 | >= 0.12 |
| `TradeExecutionModel` | 成交模型接口 | >= 0.22.4 |
| `BarSeriesManager.run(TradingRecord)` | 自定义 TradingRecord 回测 | >= 0.22.4 |
| `CostModel` | 费用模型 | >= 0.19 |
| `Num` | 数值计算抽象 | >= 0.12 |

### 16.5 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 实盘订单异步回调与 MultiPositionTradingRecord 更新的时序问题 | 持仓记录不一致 | 第一阶段使用同步假设成交方式，第二阶段引入异步回调队列 |
| 删除 SignalBasedStrategyImpl 时遗漏外部引用 | 编译失败 | 删除前运行全量构建 + grep 确认零引用 |
| 指标驱动型策略的实时计算性能 | 信号延迟 | 指标计算缓存 + 只对最新 bar 计算 |
| 回测与实盘偏差 | 策略效果不一致 | 回测中参数的费率、滑点与实盘一致 |
| 同一机器人切换策略类型时状态不一致 | 持仓记录混乱 | 切换时清空持仓记录，确保一致 |

### 16.6 ta4j 指标配置示例

#### 示例 1：单指标 RSI 策略

```json
{
  "indicatorType": "rsi",
  "params": {
    "rsiPeriod": 14,
    "overbought": 70,
    "oversold": 30
  },
  "signalRules": {
    "buyWhen": "rsi < oversold",
    "sellWhen": "rsi > overbought"
  },
  "timeframe": "1h"
}
```

#### 示例 2：RSI + MA 多指标组合

```json
{
  "indicatorType": "rsi_ma_trend",
  "params": {
    "rsiPeriod": 14,
    "rsiOverbought": 70,
    "rsiOversold": 30,
    "maPeriod": 20,
    "maType": "EMA"
  },
  "signalRules": {
    "buyWhen": "rsi < rsiOversold AND close > ma",
    "sellWhen": "rsi > rsiOverbought OR close < ma"
  },
  "timeframe": "1h"
}
```

#### 示例 3：MACD 趋势策略

```json
{
  "indicatorType": "macd",
  "params": {
    "fastPeriod": 12,
    "slowPeriod": 26,
    "signalPeriod": 9
  },
  "signalRules": {
    "buyWhen": "macd > signal AND macd_hist > 0",
    "sellWhen": "macd < signal AND macd_hist < 0"
  },
  "timeframe": "4h"
}
```

### 16.7 API 响应示例：完整 AI 推荐结果

```json
{
  "sessionId": "abc-123-def",
  "intent": "indicator_recommend",
  "response": {
    "recommendedStrategies": [
      {
        "indicatorType": "rsi_ma_trend",
        "name": "RSI + EMA 趋势跟随",
        "description": "RSI 超买超卖结合 EMA 趋势过滤",
        "params": {
          "rsiPeriod": 14,
          "rsiOverbought": 70,
          "rsiOversold": 30,
          "maPeriod": 20,
          "maType": "EMA"
        },
        "signalRules": {
          "buyWhen": "rsi < rsiOversold AND close > ma",
          "sellWhen": "rsi > rsiOverbought OR close < ma"
        },
        "expectedPerformance": "适合震荡上涨行情",
        "confidence": "high"
      }
    ],
    "reason": "当前市场处于震荡上涨阶段，RSI+EMA组合可以有效过滤假信号",
    "marketAnalysis": {
      "trend": "bullish",
      "volatility": "medium",
      "recommendedTimeframe": "1h"
    }
  },
  "createdAt": "2025-06-01T10:00:00Z"
}
```