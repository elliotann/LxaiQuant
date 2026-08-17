# TechnicalSignal.indicator 字段改造方案

## 背景

当前 `technical_signal` 表的 `indicator` 字段注释为"指标类型"，但实际存储的是 `calcDto.getRobotId()`（[DefaultSignService.java#L179](file:///f:/project/lenzeto/ai-signal/src/main/java/com/chain/ai/trade/engine/signal/service/DefaultSignService.java#L179)），语义错误。

存在以下问题：

1. **语义扭曲** — 字段名 `indicator` 应表示"MACD/RSI/BOLL"等信号类型，实际却是 robotId
2. **数据膨胀** — 用户越多、机器人越多，`indicator` 的 distinct 值线性增长，无法当作信号类型过滤
3. **去重失效** — `signalHash` 公式包含 `indicator`（即 robotId），导致同一个策略的不同机器人产生的相同信号不会被去重

## 目标

`technical_signal.indicator` 改为存 `SignFactory.SignType` 枚举的 name（如 `MACD`、`RANGE_FILTER`、`BOLL_RSI`），真正表示"是什么类型的信号/策略"。

> `trade_signal`（`tradesignal_signal` 表）不在此次改造范围内，继续存 robotId。

---

## 改造范围

### 一、数据写入端（需改代码）

#### 1.1 DefaultSignService.saveSign() — 核心改动点（仅改1行）

```java
// 当前（错误）：存的 robotId
technicalSignalDTO.setIndicator(calcDto.getRobotId());

// 改为：存信号类型（robotName 在主路径中已是 strategyType.name()）
technicalSignalDTO.setIndicator(calcDto.getRobotName());
```

> `robotName` 字段命名有误导性，但它实际上已在关键路径中承载信号类型名（如 `"MACD"`、`"RANGE_FILTER"`），无需额外传参或抽象方法。

各 `SignService` 子类无需改代码，因为信号类型由调用方（`SignTaskExecute`、`BotSignalTaskExecute`、`PriceSignalController`）通过 `calcDto.setRobotName(strategyType.name())` 传入。

#### 1.2 ScheduledAdviceService（ai-quant 模块）

[`ScheduledAdviceService.java#L160`](file:///f:/project/lenzeto/ai-quant/src/main/java/com/chain/ai/trade/engine/service/advice/ScheduledAdviceService.java#L160)

```java
// 当前：
dto.setIndicator("AI_STRATEGY");

// 建议改为对应的 SignType，如 "AI_TREND"
```

---

### 二、信号去重（影响自动修复，无需改代码）

`generateSignalHash()` 公式（[TechnicalSignalServiceImpl.java#L398](file:///f:/project/lenzeto/ai-signal/src/main/java/com/chain/ai/trade/engine/signal/service/impl/TechnicalSignalServiceImpl.java#L398)）：

```java
content = symbol + ":" + timeframe + ":" + klineTime + ":" + indicator + ":" + strategyName
```

`indicator` 从 robotId 变为信号类型后：

- **改造前**：`BTCUSDT:3m:2024-01-01 00:00:00:robot_123:MACD趋势跟踪` ≠ `BTCUSDT:3m:2024-01-01 00:00:00:robot_456:MACD趋势跟踪` → 两条记录，**无法去重**
- **改造后**：`BTCUSDT:3m:2024-01-01 00:00:00:MACD:MACD趋势跟踪` = 仅一条 → **正确去重**

hash 公式的代码本身不需要改，效果随 indicator 值变化自动改善。

---

### 三、数据查询端（理论上无需改代码）

以下位置使用 `indicator` 作为查询过滤条件，字段名不变，语义从"按 robotId 过滤"变为"按信号类型过滤"：

| 位置 | 用途 |
|---|---|
| `TechnicalSignalServiceImpl.buildQueryWrapper()` | MyBatis-Plus 查询构建 |
| `TechnicalSignalServiceImpl.getLatestSignals()` | 获取最新信号列表 |
| `TechnicalSignalServiceImpl.getSignalsByDirection()` | 按方向查询信号 |
| `TechnicalSignalServiceImpl.getTechnicalSignalByTime()` | 按时间范围查询 |
| `TechnicalSignalController` | 前端 API 接口，接收 `indicator` 参数 |
| `TechnicalSignalQuery.java` | 查询条件 DTO |
| `TechnicalSignalVO.java` | 展示 VO |

> 如果前端目前传的是 robotId 来查信号，改造后需要改为传信号类型，否则查询会不匹配（前端联调项）。

---

### 四、实体层（改注释，不改字段结构）

| 文件 | 改动 |
|---|---|
| [`TechnicalSignal.java`](file:///f:/project/lenzeto/ai-signal/src/main/java/com/chain/ai/trade/engine/signal/entity/dos/TechnicalSignal.java) | `indicator` 注释改为 `"信号类型/策略标识，如 MACD、RANGE_FILTER"` |
| [`TechnicalSignalDTO.java`](file:///f:/project/lenzeto/ai-signal/src/main/java/com/chain/ai/trade/engine/signal/entity/dto/TechnicalSignalDTO.java) | 同上 |
| [`TechnicalSignalVO.java`](file:///f:/project/lenzeto/ai-signal/src/main/java/com/chain/ai/trade/engine/signal/entity/vo/TechnicalSignalVO.java) | 同上 |
| [`IndicatorCalcDto.java`](file:///f:/project/lenzeto/ai-signal/src/main/java/com/chain/ai/trade/engine/signal/entity/dto/IndicatorCalcDto.java) | `robotId` 字段注释当前写的是"策略ID" ✅，命名偏误导，建议改名为 `strategyId` |

---

### 五、trade_signal 模块（不改造，仅确认）

以下位置存的是 `robotId`，属于正确行为，不改：

| 位置 | 说明 |
|---|---|
| [`MacdSignService.java#L2404`](file:///f:/project/lenzeto/ai-signal/src/main/java/com/chain/ai/trade/engine/signal/service/support/MacdSignService.java#L2404) | `TradeSignalSignal.setIndicatorType(calcDto.getRobotId())` — 业务信号需绑定机器人 ✅ |

---

### 六、数据库（可能需要新增索引）

```sql
-- 如果原来有按 robotId 查 technical_signal 的索引，建议重建为按 indicator（信号类型）
-- 当前索引（如果有）：
--   idx_indicator (indicator)   ← 存 robotId，无用
-- 建议改为：
--   idx_indicator_strategy (indicator, symbol, kline_time)
ALTER TABLE technical_signal ADD INDEX idx_indicator_strategy (`indicator`, `symbol`, `kline_time`);
```

建议在非高峰期执行。

---

### 七、前端/PC后台

| 影响点 | 说明 | 改动需求 |
|---|---|---|
| 技术信号列表页 | `indicator` 从显示 robotId 变为显示信号类型 | 可能需要调整列展示 |
| 按信号类型筛选 | 如果已有按 `indicator` 筛选，现在值变了 | 筛选条件需改为下拉单选（固定枚举值） |

---

## 方案选型（已确认）：无需额外传参

`strategyType` 本身就是信号类型，各调用方已持有：

| 调用方 | 持有值 | 类型 |
|---|---|---|
| `SignTaskExecute.calculateSignal()` | `strategyType` 参数 | `SignFactory.SignType` |
| `BotSignalTaskExecute.processBot()` | `signType` 局部变量 | `SignFactory.SignType` |
| `PriceSignalController` | `strategyType` 局部变量 | `SignFactory.SignType` |

核心做法：在 `saveSign` 中改一行 `calcDto.getRobotId()` → `calcDto.getRobotName()` 即可。

> 不需要新增字段、不需要抽象方法、不需要改接口签名。

---

## 总结

| 模块 | 文件数 | 是否需改代码 | 改动量 |
|---|---|---|---|
| ai-signal default service | 1 | **是** — `saveSign` 中改1行 | 一行 |
| ai-quant advice | 1 | 建议改 | 一行 |
| ai-signal entity | 3 | 改注释 | 极小 |
| ai-signal query | 1 | 不改（字段名不变） | — |
| 数据库 | — | 建议重建索引 | 可选 |
| 前端 | 若干 | 查询条件值变化 | 需联调确认 |
