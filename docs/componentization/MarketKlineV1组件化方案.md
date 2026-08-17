# MarketKlineV1.vue 组件化方案

## 1. 现状分析

| 指标 | 数值 |
|---|---|
| 文件总行数 | ~13,000+ 行 |
| 功能模块数 | 10+ 个独立业务域 |
| 前端框架 | Vue 3 + TypeScript |
| 图表库 | lightweight-charts |
| 状态管理 | 组件内 ref (目前无 Pinia/Vuex) |

### 1.1 已存在的基础设施

**已有 composable** (`src/composables/`):

| composable | 当前状态 | 问题 |
|---|---|---|
| `useKlineData.ts` | 已提取但依赖 `state: any` 传参 | 与组件强耦合 |
| `useOrders.ts` | 同上 | 同上 |
| `usePositions.ts` | 同上 | 同上 |
| `useBacktest.ts` | 同上 | 同上 |
| `useIndicators.ts` | 同上 | 同上 |
| `useChartInteractions.ts` | 已提取 | 待核实完整度 |
| `useSmcIndicator.ts` | 已提取 | 待核实完整度 |
| `useOrderPriceLines.ts` | 已提取 | 待核实完整度 |
| `useTrendAnalysis.ts` | 已提取 | 待核实完整度 |
| `useXiaoLingBaoChat.ts` | ✅ **已完成** | AI 聊天逻辑已完整提取，含 SSE/持久化/信号生成 |

**已有组件** (`src/components/kline/`):

| 组件 | 当前状态 |
|---|---|
| `BottomPanel.vue` | 已提取，需接入数据流 |
| `IndicatorDropdownMenu.vue` | 已提取，MarketKlineV1 内仍有内联版本 |
| `BacktestRunPanel.vue` | 已提取，已引用 |
| `BacktestRecordsPanel.vue` | 已提取，已引用 |
| `PositionsPanel.vue` | 已提取，需替换内联持仓列表 |
| `OrderEntitiesPanel.vue` | 已提取，需替换内联订单列表 |
| `EquityCurvePanel.vue` | 已提取，需接入 equityCurvePoints 数据 |
| `KlineInfoBar.vue` | 已提取，需接入最新行情数据 |
| `XiaoLingBaoDialog.vue` | ✅ **已完成** | `position: fixed` 悬浮弹框，替代内联面板 |
| `LingSheAiPanel.vue` | 已提取，已引用 | 行情看板面板，非聊天组件 |

### 1.2 核心问题

1. **单体文件过大**：13,000+ 行，单个文件难以维护
2. **逻辑与视图混杂**：composable 仍以 `state: any` 方式与组件强耦合
3. **已有组件未充分利用**：部分组件已提取但未被使用，或数据流未对接
4. **缺乏类型定义**：大量内联类型定义，未集中管理
5. **加载性能**：所有代码一次性加载，无异步组件/懒加载

---

## 2. 总体架构设计

### 2.1 目标架构

```
MarketKlineV1.vue (编排层，~500 行)
├── composables/                    # 逻辑层
│   ├── useKlineData.ts             # K线数据加载
│   ├── useSymbolSearch.ts          # 交易对搜索
│   ├── useIntervalManagement.ts    # 周期切换
│   ├── useChartCore.ts             # 图表核心(初始化/主题/容器)
│   ├── useIndicatorManager.ts      # 指标统一管理
│   ├── useDrawingTools.ts          # 绘图工具
│   ├── useRightPanel.ts            # 右侧面板状态
│   ├── useBottomPanel.ts           # 底部面板状态
│   ├── useTradeLogs.ts             # 交易日志
│   ├── useXiaoLingBaoChat.ts       # ✅ 已完成 AI聊天
│   ├── useSignalMarkers.ts         # 信号标记覆盖层
│   ├── usePriceAxisLabel.ts        # 价格轴标签
│   ├── useMacdPane.ts              # MACD 副图
│   ├── useRsiPane.ts               # RSI 副图
│   └── useMultiTimeframeTrend.ts   # 多时间框架趋势
│
├── components/kline/               # 视图层
│   ├── XiaoLingBaoDialog.vue       # ✅ 已完成 悬浮弹框 (替代内联 AI 面板)
│   ├── SymbolSearchModal.vue       # 交易对搜索弹窗 [新]
│   ├── ChartContextMenu.vue        # 图表右键菜单 [新]
│   ├── SignalTooltip.vue           # 信号 tooltip [新]
│   ├── ManualOrderPanel.vue        # 手动开单面板 [新]
│   ├── OrderDetailsDialog.vue      # 订单详情弹窗 [新]
│   ├── GainLossDialog.vue          # 止盈止损弹窗 [新]
│   ├── ClosePositionDialog.vue     # 平仓弹窗 [新]
│   ├── ReversePositionDialog.vue   # 反手弹窗 [新]
│   ├── AnalysisPanelContent.vue    # 分析面板内容 [新]
│   ├── SmcPanelContent.vue         # SMC面板内容 [新]
│   ├── QuickTradePanel.vue         # 快速交易面板 [新]
│   ├── RightIconBar.vue            # 右侧图标栏 [新]
│   ├── BottomPanel.vue             # 底部面板 [已有]
│   ├── IndicatorDropdownMenu.vue   # 指标下拉菜单 [已有]
│   ├── BacktestRunPanel.vue        # 回测运行 [已有]
│   ├── BacktestRecordsPanel.vue    # 回测记录 [已有]
│   ├── PositionsPanel.vue          # 持仓列表 [已有]
│   ├── OrderEntitiesPanel.vue      # 订单列表 [已有]
│   ├── EquityCurvePanel.vue        # 权益曲线 [已有]
│   ├── KlineInfoBar.vue            # 信息栏 [已有]
│   └── LingSheAiPanel.vue          # 行情看板面板 [已有]
│
└── types/                          # 类型定义
    └── market.ts                   # K线/订单/信号等统一类型
```

### 2.2 数据流原则

```
API/WebSocket → composable (响应式数据) → 组件 (props 接收) → DOM 渲染
                        ↑                              ↓
                    emit 事件 ← ← ← ← ← ← ← ← ← 用户交互
```

- **单向数据流**：数据从 composable 向下流向组件
- **事件冒泡**：子组件通过 emit 通知父组件，父组件调用 composable 方法
- **杜绝 prop 双向绑定**：不使用 `.sync` 或 `v-model` 进行深层数据传递

---

## 3. 详细执行计划

### 第一阶段：基础设施 — composable 重构与提取

**目标**：将逻辑从组件中分离到独立的 composable，主组件只做编排。

**风险评级**：⭐ 低风险

#### 任务 1.1：重构 useKlineData (独立 composable)

**现状**：`useKlineData(state: any)` 接收整个组件的 state 对象。

**目标**：改为独立的 composable，内部管理自己的响应式状态。

**涉及代码区域**：

| 函数/数据 | 说明 |
|---|---|
| `loadData(symbol, interval)` | 加载K线数据（先从cache读，再请求增量） |
| `loadMore()` | 加载更多历史数据 |
| `loadByTime(params)` | 按时间范围加载 |
| `dataCache` | Map<string, any[]> 数据缓存 |
| `handleKLineUpdate(wsData)` | WebSocket 增量更新 |
| `onKLineWsMessage`, `onKLineWsError` | WebSocket 事件 |
| `loadKlineWithIndicators` | 加载K线后联动更新指标 |

**重构方案**：

```typescript
// composables/useKlineData.ts
export function useKlineData() {
  const klineData = ref<KLineDto[]>([])
  const isLoading = ref(false)
  const dataCache = new Map<string, KLineDto[]>()
  let ws: WebSocket | null = null

  async function loadData(symbol: string, interval: string) { /* ... */ }
  async function loadMore(symbol: string, interval: string) { /* ... */ }
  function handleKLineUpdate(data: KLineUpdate) { /* ... */ }
  function connectWebSocket(symbol: string) { /* ... */ }
  function disconnectWebSocket() { /* ... */ }

  return { klineData, isLoading, loadData, loadMore, handleKLineUpdate, connectWebSocket, disconnectWebSocket }
}
```

#### 任务 1.2：提取 useSymbolSearch (交易对搜索)

**涉及数据**：

| 数据 | 类型 | 说明 |
|---|---|---|
| `showSymbolSearch` | `ref<boolean>` | 搜索弹窗可见性 |
| `symbolSearchTerm` | `ref<string>` | 搜索关键词 |
| `groupedSymbols` | `computed` | 按交易所分组的交易对 |
| `symbolList` | `ref<SymbolInfo[]>` | 原始交易对列表 |

**涉及函数**：`fetchSymbolList()`, `handleSymbolSelect(item)`, `filterSymbols()`

#### 任务 1.3：提取 useIntervalManagement (周期切换)

**涉及数据**：

| 数据 | 类型 | 说明 |
|---|---|---|
| `currentInterval` | `ref<string>` | 当前周期 |
| `topIntervals` | `string[]` | 顶部快捷周期按钮 |

**涉及函数**：`onIntervalClick(interval)`, `convertIntervalToBackend(interval)`, `getIntervalSeconds(interval)`

**前置条件**：依赖 1.1 `useKlineData.loadData()`

#### 任务 1.4：提取 useIndicatorManager (指标统一管理)

**涉及数据**：

| 数据 | 类型 | 说明 |
|---|---|---|
| `indicators` | `reactive` | 所有指标开关 `{ boll, reversal, trendStrength, macd, rsi, ma }` |
| `bollConfig` | `ref<BollConfig>` | BOLL 参数 |
| `reversalConfig` | `ref<ReversalConfig>` | 反转确认参数 |
| `trendStrengthConfig` | `ref` | 趋势强度参数 |

**涉及函数**：
- `updateBoll()`, `updateReversalConfirmation()`, `updateTrendStrength()`
- `updateMACD()`, `updateRSI()`, `updateMA()`
- `updateAllIndicators(klineData)`, `clearAllIndicators()`

**此模块最独立，建议优先实施**。

#### 任务 1.5：提取 useChartCore (图表核心)

**涉及数据**：`chart`, `candleSeries`, `chartContainerRef`

**涉及函数**：
- `initChart(container, options)` — 创建 lightweight-charts 实例
- `applyChartTheme(darkMode)` — 主题切换
- `updateContainerSize(width, height)` — 容器尺寸自适应
- `destroyChart()` — 销毁图表实例
- `setCrosshairMode(mode)`, `handleCrosshairMove()`

**风险点**：图表实例不能被 reactive 包裹，需用 `shallowRef` 或普通变量管理。

#### 任务 1.6：提取 useDrawingTools (绘图工具)

**涉及数据**：

| 数据 | 说明 |
|---|---|
| `drawingMode` | 当前绘图模式 |
| `drawingToolStates` | 各工具状态 |

**涉及函数**：
- `activateTool(toolType)`, `deactivateTool()`
- `handleKeyDown(event)` — ESC 取消/Delete 删除
- `renderDrawingTooltip()` — 绘图提示

**依赖**：1.5 `useChartCore`

#### 任务 1.7：提取 useRightPanel (右侧面板)

**涉及数据**：

| 数据 | 类型 | 说明 |
|---|---|---|
| `rightPanelWidth` | `ref<number>` | 面板宽度(px) |
| `rightPanelCollapsed` | `ref<boolean>` | 是否折叠 |
| `rightMainTab` | `ref<string>` | 当前 Tab (analysis/smc/quick-trade/xiaolingbao) |

**涉及函数**：
- `startRightPanelResize(e)`, `stopRightPanelResize()`, `doRightPanelResize(e)`
- `toggleRightPanel()`, `setRightPanelTab(tab)`

#### 任务 1.8：提取 useBottomPanel (底部面板)

**涉及数据**：

| 数据 | 类型 | 说明 |
|---|---|---|
| `bottomActiveTab` | `ref<string>` | 当前 Tab |
| `bottomPanelHeight` | `ref<number>` | 面板高度 |
| `backtestSubTab` | `ref<string>` | 回测子 Tab |

**涉及函数**：
- `startResize(e)`, `stopResize()`, `doResize(e)`
- `switchBottomTab(tab)`

#### 任务 1.9：提取 useTradeLogs (交易日志)

**涉及数据**：

| 数据 | 说明 |
|---|---|
| `tradeLogs` | `ref<TradeLog[]>` 交易日志列表 |
| `tradeLogWs` | WebSocket 实例 |

**涉及函数**：
- `connectTradeLogsWebSocket()`, `disconnectTradeLogsWebSocket()`
- `addBacktestLog(log)`, `clearTradeLogs()`

#### 任务 1.10：提取 useXiaoLingBaoChat (AI 聊天) ✅ 已完成

**涉及数据**：

| 数据 | 说明 |
|---|---|
| `messages` | 聊天消息列表 |
| `sending` | 是否正在请求 |

**涉及函数**：
- `sendMessage()` — SSE 流式发送
- `parseSSEStream(reader)` — SSE 解析
- `loadChatHistory()`, `persistChatHistoryNow()` — localStorage 持久化
- `clearHistory()`

**此模块最独立，已优先实施完成**。

**实际产出**：

| 文件 | 说明 |
|---|---|
| `src/composables/useXiaoLingBaoChat.ts` | 完整提取，含 SSE 流式解析、Markdown 渲染、信号生成、药丸按钮、持久化全部逻辑 |
| `src/components/kline/XiaoLingBaoDialog.vue` | `position: fixed` 悬浮弹框组件，360×560px，右下角定位，遮罩外部关闭 |
| `MarketKlineV1.vue` | 删除约 1100 行内联代码，替换为 `<XiaoLingBaoDialog>` 组件 |

---

### 第二阶段：UI 组件化 — 弹窗提取

**目标**：将独立弹窗提取为 Vue SFC。

**风险评级**：⭐⭐ 中风险

#### 任务 2.1：SymbolSearchModal.vue

**从 MarketKlineV1 提取**：

- 模板：`<el-dialog v-model="showSymbolSearch">` 内部所有内容
- 逻辑：搜索过滤、分组渲染、键盘导航
- Props: 无（内部管理状态，或接收 `visible`）
- Emits: `select(symbol)`, `close`

#### 任务 2.2：统一 IndicatorDropdownMenu

**当前问题**：MarketKlineV1 内使用了内联的指标下拉菜单，而 `src/components/kline/IndicatorDropdownMenu.vue` 已存在但未被使用。

**行动**：
1. 确认 `IndicatorDropdownMenu.vue` 的功能完整度
2. 替换内联版本为组件版本

#### 任务 2.3：ChartContextMenu.vue

**提取内容**：
- 模板：`<div v-if="contextMenuVisible" class="context-menu">` 内部所有内容
- 逻辑：右键菜单定位、下单/标记操作
- Props: `{ x, y, symbol }`
- Emits: `openOrder(type)`, `addMarker(type)`, `close`

#### 任务 2.4：SignalTooltip.vue

**提取内容**：
- 模板：`<div v-if="signalTooltip.visible" class="signal-tooltip">`
- 逻辑：信号格式化、定位
- Props: `{ signal, position }`
- Emits: `close`

#### 任务 2.5：ManualOrderPanel.vue

**提取内容**：
- 模板：快速开单面板表单
- 逻辑：方向/金额/杠杆/止盈止损表单逻辑、`submitManualOpenOrder`
- 对接已有 composable `useOrders`

#### 任务 2.6：OrderDetailsDialog.vue

**提取内容**：
- 模板：订单详情弹窗
- 逻辑：`openOrderDetailsDialog(order)`
- Props: `{ order, visible }`
- Emits: `close`

#### 任务 2.7：GainLossDialog.vue

**提取内容**：
- 模板：止盈止损设置弹窗
- 逻辑：`submitGainLoss(position)`
- Props: `{ position, visible }`
- Emits: `close`, `submitted`

#### 任务 2.8：ClosePositionDialog.vue

**提取内容**：
- 模板：平仓弹窗
- 逻辑：`submitManualClose(position)`
- Props: `{ position, visible }`
- Emits: `close`, `submitted`

#### 任务 2.9：ReversePositionDialog.vue

**提取内容**：
- 模板：反手弹窗
- 逻辑：`submitReverse(position)`
- Props: `{ position, visible }`
- Emits: `close`, `submitted`

---

### 第三阶段：UI 组件化 — 面板分区

**目标**：将右侧/底部面板内容拆分为独立组件。

**风险评级**：⭐⭐⭐ 中高风险

#### 任务 3.1：AnalysisPanelContent.vue

**提取内容**：
- `analysis-tab-content` 模板（趋势状态、支撑阻力、价格提醒）
- 相关格式化逻辑

#### 任务 3.2：SmcPanelContent.vue

**提取内容**：
- `smc-tab-content` 模板
- SMC 级别渲染逻辑 `renderSmcLevels()`

#### 任务 3.3：QuickTradePanel.vue

**提取内容**：
- `qt-panel-body` 模板
- 快速交易操作逻辑

#### 任务 3.4：LingSheAiPanel.vue (已有)

**现状**：`LingSheAiPanel.vue` 已存在且已被 MarketKlineV1 引用，它是一个行情看板面板（非AI聊天），与 `XiaoLingBaoDialog.vue`（AI聊天悬浮弹框）功能不同。

**说明**：AI 聊天（小灵宝）已通过任务 1.10 完整提取为 `useXiaoLingBaoChat` + `XiaoLingBaoDialog.vue`，不再需要关联此任务。

**行动**：
1. 确认 `LingSheAiPanel.vue` 定位为行情看板，与聊天面板区分
2. 确认其数据流对接是否完整

#### 任务 3.5：RightIconBar.vue

**提取内容**：右侧竖排图标栏（SMC/分析/快交/AI等Tab切换按钮）

#### 任务 3.6~3.10：完善已有组件

| 组件 | 需要完善的内容 |
|---|---|
| `EquityCurvePanel.vue` | 接入 `equityCurvePoints` 数据 |
| `BacktestRunPanel.vue` | 确认数据流与 `useBacktest` 对接 |
| `BacktestRecordsPanel.vue` | 确认数据流与 `useBacktest` 对接 |
| `OrderEntitiesPanel.vue` | 替换内联订单列表模板 |
| `PositionsPanel.vue` | 替换内联持仓列表模板 |
| `KlineInfoBar.vue` | 接入最新价格/涨跌幅数据 |

---

### 第四阶段：图表特性组件化

**目标**：将图表上的覆盖层/特性提取为 composable。

**风险评级**：⭐⭐⭐⭐ 高风险（图表渲染逻辑精细，容易出 bug）

#### 任务 4.1：useSignalMarkers

```typescript
export function useSignalMarkers(chart: IChartApi, candleSeries: ISeriesApi<'Candlestick'>) {
  const signalMarkers = ref<SignalMarker[]>([])
  const signalLines = ref<SignalLine[]>([])

  function updateSignalMarkers(data: KLineDto[], signals: Signal[]) { /* ... */ }
  function clearSignalMarkers() { /* ... */ }

  return { signalMarkers, signalLines, updateSignalMarkers, clearSignalMarkers }
}
```

#### 任务 4.2：完善 useOrderPriceLines

**现状**：已有 `useOrderPriceLines`，需整合订单路径标记/连线逻辑。

#### 任务 4.3：usePriceAxisLabel

```typescript
export function usePriceAxisLabel(chart: IChartApi) {
  const priceAxisLabel = ref<PriceAxisLabel | null>(null)
  let rafId: number | null = null

  function startLabelUpdate() { /* RAF 循环 */ }
  function stopLabelUpdate() { /* 取消 RAF */ }

  return { priceAxisLabel, startLabelUpdate, stopLabelUpdate }
}
```

#### 任务 4.4：useMacdPane

**职责**：MACD 副图的创建、数据更新、销毁。

#### 任务 4.5：useRsiPane

**职责**：RSI 副图的创建、数据更新、销毁。

#### 任务 4.6：useMultiTimeframeTrend

**职责**：多时间框架趋势线/标记的创建、更新、清理。

---

### 第五阶段：收尾

**风险评级**：⭐⭐⭐⭐⭐ 最高风险（集成测试复杂）

| 任务 | 说明 | 检查项 |
|---|---|---|
| 5.1 主组件瘦身 | 删除所有已提取模板 + 脚本 | 对比原文件，确保无遗漏 |
| 5.2 类型提取 | 统一移到 `src/types/market.ts` | `KLineDto`, `Signal`, `Order`, `Position`, `BollConfig`, `ReversalConfig` 等 |
| 5.3 props/emit 收敛 | 确保组件接口完整 | 每个组件有完善的 props 类型 + emits 声明 |
| 5.4 回归测试 | 人工验证所有功能 | K线加载/指标/下单/持仓/回测/AI聊天/绘图工具等 |

---

## 4. 组件目录结构（最终）

```
src/
├── components/kline/
│   ├── BacktestRecordsPanel.vue    # [已有] 回测记录
│   ├── BacktestRunPanel.vue        # [已有] 回测运行
│   ├── BottomPanel.vue             # [已有] 底部面板容器
│   ├── ChartContextMenu.vue        # [新增] 图表右键菜单
│   ├── ClosePositionDialog.vue     # [新增] 平仓弹窗
│   ├── EquityCurvePanel.vue        # [已有] 权益曲线
│   ├── GainLossDialog.vue          # [新增] 止盈止损弹窗
│   ├── IndicatorDropdownMenu.vue   # [已有] 指标下拉菜单
│   ├── KlineInfoBar.vue            # [已有] K线信息栏
│   ├── LingSheAiPanel.vue          # [已有] 行情看板面板
│   ├── ManualOrderPanel.vue        # [新增] 手动开单面板
│   ├── OrderDetailsDialog.vue      # [新增] 订单详情弹窗
│   ├── OrderEntitiesPanel.vue      # [已有] 订单列表
│   ├── PositionsPanel.vue          # [已有] 持仓列表
│   ├── ReversePositionDialog.vue   # [新增] 反手弹窗
│   ├── RightIconBar.vue            # [新增] 右侧图标栏
│   ├── SignalTooltip.vue           # [新增] 信号提示浮层
│   ├── SymbolSearchModal.vue       # [新增] 交易对搜索弹窗
│   ├── AnalysisPanelContent.vue    # [新增] 分析面板
│   ├── SmcPanelContent.vue         # [新增] SMC面板
│   ├── QuickTradePanel.vue         # [新增] 快速交易面板
│   └── XiaoLingBaoDialog.vue       # ✅ 已完成 position:fixed 悬浮弹框
│
├── composables/
│   ├── useChartCore.ts             # [重构] 图表核心
│   ├── useChartInteractions.ts     # [已有] 图表交互
│   ├── useDrawingTools.ts          # [新增] 绘图工具
│   ├── useIndicatorManager.ts      # [新增] 指标统一管理
│   ├── useIndicators.ts            # [已有保留] 旧版兼容
│   ├── useIntervalManagement.ts    # [新增] 周期切换
│   ├── useKlineData.ts             # [重构] K线数据
│   ├── useMacdPane.ts              # [新增] MACD副图
│   ├── useMultiTimeframeTrend.ts   # [新增] 多时间框架
│   ├── useOrderPriceLines.ts       # [已有] 订单价格线
│   ├── useOrders.ts                # [重构] 订单操作
│   ├── usePositions.ts             # [重构] 持仓管理
│   ├── useBacktest.ts              # [重构] 回测功能
│   ├── usePriceAxisLabel.ts        # [新增] 价格轴标签
│   ├── useRightPanel.ts            # [新增] 右侧面板状态
│   ├── useBottomPanel.ts           # [新增] 底部面板状态
│   ├── useRsiPane.ts               # [新增] RSI副图
│   ├── useSignalMarkers.ts         # [新增] 信号标记
│   ├── useSmcIndicator.ts          # [已有] SMC指标
│   ├── useSymbolSearch.ts          # [新增] 交易对搜索
│   ├── useTradeLogs.ts             # [新增] 交易日志
│   └── useXiaoLingBaoChat.ts       # ✅ 已完成 AI聊天
│
├── types/
│   └── market.ts                   # [新增] 市场相关类型
│
└── views/market/
    └── MarketKlineV1.vue           # [瘦身] 仅编排层
```

---

## 5. 风险评估与应对

| 风险 | 等级 | 应对措施 |
|---|---|---|
| 图表实例管理复杂 | 高 | 使用 `shallowRef`，composable 返回 `{ chart, candleSeries }` |
| WebSocket 连接生命周期 | 中 | composable 内 `onMounted`/`onUnmounted` 管理 |
| 计算密集型指标导致卡顿 | 中 | 指标计算使用 `requestAnimationFrame` 或 Web Worker |
| 组件间事件冒泡链路长 | 中 | 定义清晰的 props/emit 契约文档 |
| 回测数据流耦合度高 | 高 | useBacktest 内部封装完整，不依赖外部状态 |
| 回归测试覆盖不全 | 高 | 每完成一个阶段做一次完整功能验证 |

---

## 6. 建议实施顺序

```
第一阶段 (1.1 → 1.10)    基础逻辑抽取，不影响 UI
    ↓
第二阶段 (2.1 → 2.9)     弹窗提取，风险可控
    ↓
第三阶段 (3.1 → 3.10)    面板分区，需谨慎
    ↓
第四阶段 (4.1 → 4.6)     图表特性，最精细
    ↓
第五阶段 (5.1 → 5.4)     收尾集成
```

**已完成的优先任务** ✅：

| 原优先级 | 任务 | 产出 |
|---|---|---|
| 🥇 P1 | **1.10 useXiaoLingBaoChat** — AI聊天模块独立，与图表无耦合 | `useXiaoLingBaoChat.ts` + `XiaoLingBaoDialog.vue`，MarketKlineV1 减少 ~1100 行 |

**待实施的优先任务**：

1. **1.4 useIndicatorManager** — 指标模块最独立，无外部依赖
2. **2.3 ChartContextMenu** — 右键菜单UI独立，提取简单、收益快

---

*文档版本: v1.1*
*生成日期: 2026-06-02*
*更新内容: 完成第一期 — useXiaoLingBaoChat composable + XiaoLingBaoDialog.vue 悬浮弹框组件提取，MarketKlineV1.vue 瘦身 ~1100 行*
