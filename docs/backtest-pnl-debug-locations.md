# 快速回测盈亏计算 - 调试位置说明

## 一、盈亏是在哪里算出来的（建议在这里下断点）

**文件**: `ai-engine/src/main/java/com/chain/ai/trade/engine/backtest/BacktestEngine.java`  
**方法**: `collectTradeRecords`  
**行号**: **928 ~ 934**（平仓时计算 actualPnl）

```java
// 928-934 行：核心计算公式（与 ProfitCalcUtils 一致）
double entryPrice = position.getEntry().getNetPrice().doubleValue();
double exitPrice = position.getExit().getNetPrice().doubleValue();
double priceDiff = "LONG".equals(direction) ? (exitPrice - entryPrice) : (entryPrice - exitPrice);
double actualPnl = contractSize * contractMult * exitRawAmount * priceDiff;
```

- **931 行**：`priceDiff` — 多空价差（LONG = 平仓价-开仓价，SHORT = 开仓价-平仓价）
- **932 行**：`actualPnl` — 实际盈亏 = 面值 × 乘数 × 张数 × 价差
- **952 行**：`safePnl` — 对 actualPnl 做 NaN 保护后写入 TradeRecord
- **969 行**：`.pnl(safePnl)` — 设置到返回的 TradeRecord，后面全链路都用这个值

**调用链**：  
`collectCombinedTradeRecords`（约 1292、1313 行）→ 分别对多单/空单调用 `collectTradeRecords(longRecord,...)` 和 `collectTradeRecords(shortRecord,...)`，两处都用同一套公式。

---

## 二、回测结果里的 tradeRecords 从哪来

**文件**: `ai-engine/src/main/java/com/chain/ai/trade/engine/backtest/BacktestService.java`  
**行号**: **254~271**

- **256 行**：组合策略时 `tradeRecords = backtestEngine.collectCombinedTradeRecords(..., contractSize, contractMult, ...)`
- **267 行**：另一分支同样调用 `collectCombinedTradeRecords`
- **271 行**：单策略时 `tradeRecords = backtestEngine.collectTradeRecords(..., contractSize, contractMult)`

这里的 `tradeRecords` 里每一项的 `getPnl()` 就是上面 BacktestEngine 里算出来的 `safePnl`。

---

## 三、从 TradeRecord 到“保存到订单”的 pnl 传递

**文件**: `ai-quant/src/main/java/com/chain/ai/trade/engine/controller/BacktestController.java`

| 位置 | 行号 | 说明 |
|------|------|------|
| 保存入口 | **851** | `saveBacktestTradeRecordsToOrderSystem(response, request)` |
| 遍历回测记录 | **859-863** | 遍历 `strategyResult.getTradeRecords()`，每个转成 `BacktestTradeRecord` |
| 转换并带走 pnl | **901-982** | `convertTradeRecordToBacktestTradeRecord(tradeRecord, ...)` |
| 写入 BacktestTradeRecord.pnl | **981** | `.pnl(safeToBigDecimal(tradeRecord.getPnl()))` ← **这里用的就是 BacktestEngine 算的 pnl** |
| 调用订单服务保存 | **887-888** | `tradeOrderService.saveBacktestTradeRecords(backtestTradeRecords, ...)` |

调试建议：在 **BacktestController 第 981 行** 打断点，看 `tradeRecord.getPnl()` 是否已经是你在 BacktestEngine 里算出的值。

---

## 四、订单服务里把 pnl 写入订单/平仓表

**文件**: `ai-order/src/main/java/com/chain/ai/trade/order/service/impl/TradeOrderServiceImpl.java`

| 位置 | 行号 | 说明 |
|------|------|------|
| 保存回测记录入口 | **1383** | `saveBacktestTradeRecords(List<BacktestTradeRecord> backtestTradeRecords, ...)` |
| 平仓时取 pnl | **1655** | `BigDecimal pnl = closeRecord.getPnl();` ← 来自 BacktestTradeRecord，即上一步的 tradeRecord.getPnl() |
| 写入平仓表 income | **1631** | `TradeOrderClose.builder().income(closeRecord.getPnl())...` |
| 写入主单 income | **1650** | `order.setIncome(closeRecord.getPnl());` |
| 写入订单项 income | **1694** | `item.setIncome(closeRecord.getPnl());` |

调试建议：在 **TradeOrderServiceImpl 第 1655 行** 打断点，确认 `closeRecord.getPnl()` 是否与 BacktestEngine 中算出的 actualPnl 一致。

---

## 五、调试时建议看的变量（在 BacktestEngine 928-934 行）

- `direction` — "LONG" 或 "SHORT"
- `entryPrice` / `exitPrice` — 开仓价、平仓价
- `priceDiff` — 价差（多空符号已按方向处理）
- `contractSize` / `contractMult` — 合约面值、合约乘数（来自 ContractSpec）
- `exitRawAmount` — 平仓张数（与界面“数量”一致）
- `actualPnl` — 最终算出的盈亏金额

若这里 `actualPnl` 就对，而订单上不对，则问题在 **BacktestController 或 TradeOrderServiceImpl** 的传递/写入；若这里就不对，则重点查 **contractSize/contractMult** 或 **entryPrice/exitPrice** 的来源（如 TA4J 的 NetPrice）。
