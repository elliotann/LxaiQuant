# 信号类型 & 交易操作类型 规范整理方案

## 目标

统一项目中 `SignalType`、`BuyType`、`OrderAction` 三套枚举的职责边界，最终只保留两个层级：

1. **`SignalType`** — 策略产生的信号（方向意图）
2. **`OrderAction`** — 业务执行的操作（具体动作）

---

## 层级一：SignalType（策略信号）

**定位**：策略引擎/指标计算产出的信号，表达"想要做什么方向"。

合并当前 `SignalType` + `BuyType`，扩展为完整集合：

| 信号值 | 含义 | 来源（替代谁） |
|--------|------|-------------|
| `LONG` | 做多 | `SignalType.BUY` + `BuyType.BUY` |
| `SHORT` | 做空 | `SignalType.SELL` + `BuyType.SELL` |
| `CLOSE_LONG` | 平多 | `BuyType.BUY_CLOSE` |
| `CLOSE_SHORT` | 平空 | `BuyType.SELL_CLOSE` |
| `CALLBACK_LONG` | 回调做多 | `BuyType.CALLBACK_BUY` |
| `CALLBACK_SHORT` | 反弹做空 | `BuyType.CALLBACK_SELL` |
| `HOLD` | 持有/无信号 | `SignalType.HOLD`（保留） |

### 改名说明

`BUY → LONG`、`SELL → SHORT` 是为了和 `OrderAction` 区分开，避免语义混淆：
- `SignalType.LONG` = 策略信号说"可以做多"
- `OrderAction.OPEN_LONG` = 实际执行"开多仓"操作

---

## 层级二：OrderAction（交易操作）

**定位**：订单执行层实际发送给交易所的操作指令，统一 LONG/SHORT 命名，清理废弃/冗余值。

| 操作值 | 含义 | 变更 |
|--------|------|------|
| `OPEN_LONG` | 开多 | 不动 |
| `OPEN_SHORT` | 开空 | 不动 |
| `CLOSE_LONG` | 平多 | 不动 |
| `CLOSE_SHORT` | 平空 | 不动 |
| `LBAP` | 加多仓 | 不动 |
| `LBSP` | 减多仓 | 不动 |
| `SBAP` | 加空仓 | 不动 |
| `SBSP` | 减空仓 | 不动 |
| `LONG_GAIN` | 多止盈 | ✅ `BUY_GAIN` 改名 ➔ 统一用 LONG |
| `LONG_LOSS` | 多止损 | ✅ `BUY_LOSS` 改名 ➔ 统一用 LONG |
| `SHORT_GAIN` | 空止盈 | ✅ `SELL_GAIN` 改名 ➔ 统一用 SHORT |
| `SHORT_LOSS` | 空止损 | ✅ `SELL_LOSS` 改名 ➔ 统一用 SHORT |
| `ADJUST_LEVERAGE` | 调整杠杆 | 不动 |
| `CANCEL_ORDER` | 取消订单 | 不动 |
| ~~LB~~ | ~~开多~~（旧别名） | ❌ 删除（替换引用） |
| ~~LS~~ | ~~平多~~（旧别名） | ❌ 删除（替换引用） |
| ~~SB~~ | ~~开空~~（旧别名） | ❌ 删除（替换引用） |
| ~~SS~~ | ~~平空~~（旧别名） | ❌ 删除（替换引用） |
| ~~CLOSE~~ | ~~关单~~（兼容旧版） | ❌ 删除（替换引用） |

---

## SignalType → OrderAction 映射关系

策略产生 `SignalType` 后，由协调层转为 `OrderAction` 执行：

| SignalType | → OrderAction | 说明 |
|-----------|-------------|------|
| `LONG` | `OPEN_LONG` | 做多信号 → 开多仓 |
| `SHORT` | `OPEN_SHORT` | 做空信号 → 开空仓 |
| `CLOSE_LONG` | `CLOSE_LONG` | 平多信号 → 平多仓 |
| `CLOSE_SHORT` | `CLOSE_SHORT` | 平空信号 → 平空仓 |
| `CALLBACK_LONG` | `OPEN_LONG` | 回调做多信号 → 开多仓（择时不同） |
| `CALLBACK_SHORT` | `OPEN_SHORT` | 回调做空信号 → 开空仓（择时不同） |
| `HOLD` | — | 无操作 |

---

## 涉及文件变更统计

### 删除
- `BuyAndSellWeightDto.BuyType` 内联枚举
- `BuyAndSellWeightDto` 中 4 个字段合并为 1 个 `signalType`

### 字段名变更（BuyAndSellWeightDto）
```
buyType        → signalType
sellType       → 删除（重复）
closeType      → 删除（重复）
buyTypeNew     → 删除（遗留字段）
```

### SignalType 引用文件（需改 BUY→LONG, SELL→SHORT）
- `ai-risk/` — 4 个 evaluator + 1 个 service
- `ai-signal/` — SignalEvaluator, BollingerRsiSignService
- `ai-quant/` — AnalysisScheduler

### BuyType 引用文件（需改为 SignalType）
- `ai-signal/service/support/` — ~12 个 service
- `ai-signal/service/DefaultSignService.java`
- `ai-task/` — SignTaskExecute, BotSignalTaskExecute, HistorySignTaskExecute
- `ai-engine/` — BacktestService
- `ai-signal/` — IndicatorCalcDto

### OrderAction 命名统一（BUY_GAIN → LONG_GAIN 等）
- `ai-common/.../constants/OrderAction.java` — 改 4 个枚举值
- `ai-order/.../TradeOrderServiceImpl.java` — 改 4 处引用

### OrderAction 废弃值删除（LBLSSBSS/CLOSE）
- `ai-common/.../constants/OrderAction.java` — 删除 5 个废弃常量
- `ai-signal/.../TradeSignalSignalService.java` — 替换 `LBLSSBSS` → `OPEN_LONG/CLOSE_LONG/OPEN_SHORT/CLOSE_SHORT`
- `ai-quant/.../BacktestController.java` — 替换 `LBSBCLOSE` → `OPEN_LONG/OPEN_SHORT/CLOSE_LONG/CLOSE_SHORT`

---

## 执行顺序

1. 更新 `SignalType.java` — 扩展枚举值
2. 更新所有 `SignalType.BUY/SELL` → `LONG/SHORT`
3. 清理 `BuyAndSellWeightDto` — 删除 `BuyType`，合并字段
4. 更新所有 `BuyType.X` → `SignalType.X`
5. **更新 OrderAction.java** — 改命名 + 删废弃值
6. **更新 OrderAction 引用文件** — TradeSignalSignalService + BacktestController + TradeOrderServiceImpl
7. 编译验证
