# K线图优化 — 趋势线指标方案

> **日期**：2026-05-22  
> **目标**：在 K 线图上增加基于 ta4j 的支撑/阻力趋势线指标
> **ta4j 版本**：0.22.6  
> **前端图表库**：lightweight-charts v5

---

## 一、概述

### 1.1 什么是趋势线指标

趋势线指标是在 K 线图上绘制的一组**线段**，用于可视化价格运动的支撑位和阻力位。不同于 SMA/EMA 等每个时间点都有值的指标，趋势线指标的特征是：

- **支撑趋势线**：连接摆动低点的射线，斜率反映趋势强度
- **阻力趋势线**：连接摆动高点的射线，斜率反映趋势强度

### 1.2 已存在的相关代码

项目已有趋势线相关基础设施：

| 已有代码 | 位置 | 说明 |
|---------|------|------|
| `TrendAnalysisController` | ai-quant: `/api/member/trend-analysis` | 计算支撑/阻力趋势线 + 布林带 + 摆动点，但返回的是分析型数据，非 K 线指标序列 |
| `TrendLineBacktestService` | ai-engine: 趋势线回测 | 对 TrendLineSupport/ResistanceIndicator 的回测逻辑 |
| `IndicatorWrapHelper` | ai-quant: Candlestick → BarSeries 转换 | 已有标准工具类 |

### 1.3 方案范围

本方案新增以下可切换的趋势线指标（均作为 **主图叠加** 指标）：

| 指标 | ta4j 依赖 | 描述 |
|------|-----------|------|
| **支撑趋势线** | `TrendLineSupportIndicator` | 基于摆动低点的支撑趋势线 |
| **阻力趋势线** | `TrendLineResistanceIndicator` | 基于摆动高点的阻力趋势线 |

---

## 二、后端方案

### 2.1 架构设计

```
┌─────────────┐    POST /api/kline/trendline    ┌──────────────────────┐
│  前端 K线图   │ ──────────────────────────────→ │  TrendlineController  │
│ (MarketKline  │ ←────────────────────────────── │  (新增)              │
│  V1.vue)      │     TrendlineResponse           └──────────┬───────────┘
└─────────────┘                                            │
                                                            ▼
                                              ┌────────────────────────┐
                                              │  TrendlineService      │
                                              │  (新增接口 + 实现)      │
                                              └──┬─────────────────────┘
                                                 │
                                                 ▼
                               ┌─────────────────────────────────┐
                               │ IndicatorWrapHelper             │
                               │ (已有: Candlestick→BarSeries)    │
                               └─────────────────────────────────┘
                                                 │
                                                 ▼
                               ┌─────────────────────────────────┐
                               │ ta4j Indicators                 │
                               │ - TrendLineSupportIndicator     │
                               │ - TrendLineResistanceIndicator  │
                               └─────────────────────────────────┘
```

### 2.2 新增 API

#### POST `/api/kline/trendline`

**请求体**：

```json
{
  "symbol": "BTCUSDT",
  "interval": "OKXMIN15",
  "size": 500,
  "indicators": ["support", "resistance"],
  "params": {
    "surroundingBars": 3,
    "barCount": 50
  }
}
```

**参数说明**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `indicators` | string[] | 必填 | 要计算的趋势线类型列表，可选 "support"、"resistance" |
| `params.surroundingBars` | int | 3 | 摆动点检测的对称窗口大小（TrendLineSupport/Resistance 使用） |
| `params.barCount` | int | 50 | 趋势线拟合的历史 K 线数量 |

**响应体**：

```json
{
  "success": true,
  "data": {
    "support": {
      "segment": {
        "firstIndex": 450,
        "secondIndex": 480,
        "slope": -0.0023,
        "intercept": 87450.0,
        "touchCount": 5,
        "outsideCount": 2,
        "score": 0.85
      },
      "linePoints": [
        { "time": 1716249600000, "value": 87500.0 },
        { "time": 1716336000000, "value": 87350.0 }
      ]
    },
    "resistance": {
      "segment": {
        "firstIndex": 440,
        "secondIndex": 470,
        "slope": 0.0015,
        "intercept": 87600.0,
        "touchCount": 4,
        "outsideCount": 1,
        "score": 0.78
      },
      "linePoints": [
        { "time": 1716246000000, "value": 87700.0 },
        { "time": 1716332400000, "value": 87850.0 }
      ]
    }
  }
}
```

### 2.3 后端核心类

#### 2.3.1 DTO（新增）

```java
// TrendlineRequest.java
public class TrendlineRequest {
    private String symbol;
    private String interval;
    private int size = 500;
    private List<String> indicators;
    private TrendlineParams params;
}

// TrendlineParams.java
public class TrendlineParams {
    private int surroundingBars = 3;
    private int barCount = 50;
}

// TrendlineResponse.java
public class TrendlineResponse {
    private boolean success;
    private TrendlineData data;
}

// TrendlineData.java
public class TrendlineData {
    private SupportResistanceData support;
    private SupportResistanceData resistance;
}

// SupportResistanceData.java
public class SupportResistanceData {
    private TrendSegment segment;
    private List<TimeValuePoint> linePoints;
}

// TrendSegment.java
public class TrendSegment {
    private int firstIndex;
    private int secondIndex;
    private BigDecimal slope;
    private BigDecimal intercept;
    private int touchCount;
    private int outsideCount;
    private double score;
}

// TimeValuePoint.java
public class TimeValuePoint {
    private long time;
    private double value;
}
```

#### 2.3.2 `TrendlineService`（新增接口 + 实现）

```java
public interface TrendlineService {
    TrendlineData calculateTrendlines(BarSeries series, List<String> indicators, TrendlineParams params);
}
```

`TrendlineServiceImpl` 内部逻辑：

```
for each requested indicator:
  case "support"     → TrendLineSupportIndicator(series, surroundingBars, barCount)
                        → 取 getCurrentSegment() → 转 SupportResistanceData
                        → 根据 segment 的 firstIndex/secondIndex 计算线段的起止时间点坐标
  case "resistance"  → TrendLineResistanceIndicator(series, surroundingBars, barCount)
                        → 同上
```

#### 2.3.3 `TrendlineController`（新增 Controller）

```java
@RestController
@RequestMapping("/api/kline")
public class TrendlineController {
    @PostMapping("/trendline")
    public ResponseEntity<TrendlineResponse> getTrendlines(@RequestBody TrendlineRequest request) {
        // 1. 查询 Candlestick
        // 2. build BarSeries
        // 3. 调用 TrendlineService
        // 4. 返回 TrendlineResponse
    }
}
```

**关键设计点**：

| 设计点 | 方案 |
|--------|------|
| 缓存复用 | 参考 `TrendAnalysisController` 的 Redis 缓存模式，加上 TTL 30s |
| 异步刷新 | 参考 `TrendAnalysisController`，命中缓存后异步刷新 |
| 参数校验 | indicators 列表校验，无效值忽略；size 限制 ≤2000 |
| 异常处理 | 单指标失败不影响其他指标（部分失败模式） |

---

## 三、ta4j 指标使用详解

### TrendLineSupportIndicator / TrendLineResistanceIndicator (ta4j 0.22.6)

**基本原理**：
1. 使用 `RecentSwingLowIndicator` / `RecentSwingHighIndicator` 检测摆动低点/高点
2. 基于窗口内的摆动点进行线性拟合，找到最佳支撑/阻力趋势线
3. `getCurrentSegment()` 返回当前最优的 `TrendLineSegment`

**构造参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `series` | BarSeries | K 线序列 |
| `surroundingBars` | int | 对称窗口大小（摆动点检测用），推荐 3-5 |
| `barCount` | int | 分析的 K 线数量，推荐 30-100 |

**返回的 `TrendLineSegment` 属性**：

| 属性 | 类型 | 说明 |
|------|------|------|
| `firstIndex` / `secondIndex` | int | 趋势线两个端点的 bar 索引 |
| `slope` | Num | 斜率（价格/bar） |
| `intercept` | Num | 截距 |
| `touchCount` | int | 触碰次数 |
| `outsideCount` | int | 突破次数 |
| `touchesExtreme` | int | 触碰极值次数 |
| `score` | double | 综合评分 |

**前端渲染方式**：根据 `firstIndex`/`secondIndex` 定位到具体的 `Candlestick` 获取对应时间戳，然后计算线段两端点的 y 值（`slope * index + intercept`），用 `LineSeries` 绘制两点连线。

---

## 四、前端方案

### 4.1 架构

```
┌─────────────────────────────────────────────────┐
│                MarketKlineV1.vue                  │
│                                                   │
│  ┌───────────────────────────────────────────┐    │
│  │  TrendlineService (前端模块)               │    │
│  │  - fetchTrendlineData() → POST /trendline  │    │
│  │  - trendlineCache (响应缓存)               │    │
│  └───────────────────────────────────────────┘    │
│                                                   │
│  ┌───────────────────────────────────────────┐    │
│  │  渲染函数                                   │    │
│  │  - renderSupportResistance()              │    │
│  └───────────────────────────────────────────┘    │
│                                                   │
│  ┌───────────────────────────────────────────┐    │
│  │  lightweight-charts Series                 │    │
│  │  - LineSeries × 2 (支撑线 + 阻力线)        │    │
│  └───────────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
```

### 4.2 指标配置

参考现有 `trendStrengthConfig`、`bollConfig` 的配置模式，新增趋势线配置：

```typescript
// 趋势线指标配置（新增）
const trendlineConfig = ref({
  enabled: false,
  support: { enabled: true },      // 支撑趋势线
  resistance: { enabled: true },   // 阻力趋势线
})
```

**渲染方式**：

| 指标 | lightweight-charts Series 类型 | 颜色 | 线型 |
|------|-------------------------------|------|------|
| 支撑趋势线 | `LineSeries`（2 点连线） | `#22ab94` (绿色) | 实线，宽度 1 |
| 阻力趋势线 | `LineSeries`（2 点连线） | `#ef5350` (红色) | 实线，宽度 1 |

### 4.3 数据流示例

```
用户勾选"趋势线"复选框
  → handleToggle('trendline', true)
  → 触发 fetchTrendlineData()
  → POST /api/kline/trendline (带当前配置参数)
  → 收到 TrendlineResponse
  → renderSupportResistance(data.support, data.resistance)
  → 在 lightweight-charts 上绘制支撑/阻力线
```

### 4.4 缓存策略

- **前端缓存**：`trendlineCache = ref<Map<string, TrendlineData>>(new Map())`，key = `symbol:interval:size`
- **失效条件**：切换交易对、切换时间周期、WebSocket 实时 K 线收盘（清空缓存）
- **请求合并**：500ms 防抖，避免参数频繁变化时重复请求

### 4.5 UI 交互

**菜单位置**：在 `IndicatorDropdownMenu.vue` 主图指标区域新增"趋势线"选项

```
┌─ 主图指标 ─────────────────────┐
│ ☐ BOLL                        │
│ ☐ 反转确认                    │
│ ☐ 趋势强度信号                │
│ ☑ 趋势线 ◀ NEW                │  ← 新增
│   ├ ☑ 支撑趋势线              │
│   └ ☑ 阻力趋势线              │
└────────────────────────────────┘
```

---

## 五、实现步骤

### Step 1：后端 DTO 定义 + Service 接口

**改动文件**（全部新增，不修改现有代码）：
- `TrendlineRequest.java`
- `TrendlineResponse.java`
- `TrendlineData.java`
- `SupportResistanceData.java`
- `TrendSegment.java`
- `TimeValuePoint.java`
- `TrendlineService.java`（接口）
- `TrendlineServiceImpl.java`（实现）
- `TrendlineController.java`

### Step 2：后端 Service 实现

**实现细节**：

```java
// TrendlineServiceImpl
private SupportResistanceData calcSupport(BarSeries series, TrendlineParams params) {
    if (series.getBarCount() < 2) return null;
    int safeBarCount = Math.min(params.getBarCount(), series.getBarCount());
    TrendLineSupportIndicator indicator = new TrendLineSupportIndicator(
            series, params.getSurroundingBars(), safeBarCount);
    AbstractTrendLineIndicator.TrendLineSegment segment = indicator.getCurrentSegment();
    if (segment == null) return null;

    SupportResistanceData data = new SupportResistanceData();
    data.setSegment(convertSegment(segment));

    int firstIdx = segment.firstIndex;
    int secondIdx = segment.secondIndex;
    if (firstIdx >= 0 && firstIdx < series.getBarCount()
            && secondIdx >= 0 && secondIdx < series.getBarCount()) {
        double priceAtFirst = segment.slope.doubleValue() * firstIdx + segment.intercept.doubleValue();
        double priceAtSecond = segment.slope.doubleValue() * secondIdx + segment.intercept.doubleValue();
        data.setLinePoints(List.of(
            new TimeValuePoint(series.getBar(firstIdx).getBeginTime().toEpochSecond() * 1000, priceAtFirst),
            new TimeValuePoint(series.getBar(secondIdx).getBeginTime().toEpochSecond() * 1000, priceAtSecond)
        ));
    }
    return data;
}

private SupportResistanceData calcResistance(BarSeries series, TrendlineParams params) {
    // 与 calcSupport 逻辑一致，使用 TrendLineResistanceIndicator
}
```

**验证**：对比 `TrendAnalysisController` 中支撑/阻力线的计算结果，确保一致。

### Step 3：前端 — IndicatorDropdownMenu 新增趋势线选项

**改动文件**：`IndicatorDropdownMenu.vue` + `MarketKlineV1.vue`

- 在 `IndicatorDropdownMenu.vue` 主图指标区新增"趋势线"checkbox
- 子选项：支撑趋势线、阻力趋势线
- 触发 `handleToggle('trendline', $event)`
- 在 `MarketKlineV1.vue` 新增 `trendlineConfig`、`handleTrendlineToggle()`

### Step 4：前端 — 数据获取模块

**改动文件**：`MarketKlineV1.vue`

```typescript
async function fetchTrendlineData() {
  const cfg = trendlineConfig.value;
  if (!cfg.enabled) return;

  const enabledIndicators: string[] = [];
  if (cfg.support.enabled) enabledIndicators.push("support");
  if (cfg.resistance.enabled) enabledIndicators.push("resistance");
  if (enabledIndicators.length === 0) return;

  const response = await request.post("/api/kline/trendline", {
    symbol: currentSymbol.value,
    interval: currentInterval.value,
    size: dataCache.value.length,
    indicators: enabledIndicators,
    params: {
      surroundingBars: 3,
      barCount: 50
    }
  });

  trendlineCache.value = response.data;
  renderTrendlines(response.data);
}
```

### Step 5：前端 — 渲染函数

**改动文件**：`MarketKlineV1.vue`

```typescript
// 趋势线 Series 引用
const trendlineSeriesRef = ref<{
  support?: any;
  resistance?: any;
}>({});

// 支撑/阻力趋势线渲染
function renderTrendlines(data: TrendlineData) {
  if (data.support?.linePoints) {
    if (!trendlineSeriesRef.value.support) {
      trendlineSeriesRef.value.support = chart.value.addSeries(LineSeries, {
        color: "#22ab94",
        lineWidth: 1,
        title: "支撑线",
        priceLineVisible: false,
        lastValueVisible: true,
      });
    }
    trendlineSeriesRef.value.support.setData(data.support.linePoints);
  }

  if (data.resistance?.linePoints) {
    if (!trendlineSeriesRef.value.resistance) {
      trendlineSeriesRef.value.resistance = chart.value.addSeries(LineSeries, {
        color: "#ef5350",
        lineWidth: 1,
        title: "阻力线",
        priceLineVisible: false,
        lastValueVisible: true,
      });
    }
    trendlineSeriesRef.value.resistance.setData(data.resistance.linePoints);
  }
}
```

### Step 6：数据联动 — 切换交易对/周期/实时更新

- **切换交易对或周期**：请求结束后清空 `trendlineCache`，重新 `fetchTrendlineData()`
- **WebSocket 实时 K 线收盘**：清空 `trendlineCache`，触发重新请求
- **缩放/平移**：不触发重新请求（已有数据仍然有效）
- **K 线追加（loadMore）**：暂不清缓存，但新数据到达后清空并重新请求

---

## 六、与已有 TrendAnalysisController 的关系

| 维度 | TrendAnalysisController（已有） | TrendlineController（新增） |
|------|-------------------------------|---------------------------|
| 用途 | 趋势分析概览（方向/强度/斜率） | K 线图趋势线指标绘制 |
| 调用方 | 前端趋势分析面板 | K 线图指标渲染系统 |
| 返回格式 | 分析型 JSON | 指标序列数据 + 线段坐标 |
| 缓存 key | `trend:analysis:...` | `trendline:...` |

**核心原则**：`TrendlineController` **不调用** `TrendAnalysisController`，两者是独立的。`TrendlineController` 直接使用 ta4j indicator 和 `IndicatorWrapHelper`，与 `TrendAnalysisController` 共享底层基础设施但不共享 Controller 层逻辑。

---

## 七、注意事项

### 7.1 ta4j 版本兼容性

| 指标 | ta4j 版本 | 包路径 |
|------|-----------|--------|
| `TrendLineSupportIndicator` | 0.22.6 | `org.ta4j.core.indicators.supportresistance` |
| `TrendLineResistanceIndicator` | 0.22.6 | 同上 |

项目使用 ta4j 0.22.6（`ta4j-master-0.22.6`），上述指标都可直接使用。

### 7.2 性能考虑

| 场景 | 优化策略 |
|------|---------|
| K 线数量 ≤500 | 直接计算，无缓存 |
| K 线数量 500-2000 | 启用 Redis 缓存，TTL 30s |
| WebSocket 实时更新 | 收到新 bar 后清缓存，下次请求重新计算 |
| 频繁切换指标 | 前端 500ms 防抖 |

### 7.3 回退策略

| 异常场景 | 处理方式 |
|---------|---------|
| 后端指标计算失败 | 前端静默降级，不显示该指标，console.warn |
| 网络超时/断开 | 前端保留上次缓存不更新，显示旧数据 |
| ta4j 返回 NaN/异常值 | 过滤无效数据点，不影响其他指标 |
| 数据不足（K 线 < 2） | 不请求后端，直接跳过 |

---

## 八、后续可扩展

1. **线性回归线**：基于 `SimpleLinearRegressionIndicator` 的回归线
2. **对数回归通道**：基于 `LogarithmicRegressionChannelIndicator` 的通道指标
3. **摆动点标注**：在 K 线图上标记 swing high / swing low
4. **趋势线突破信号**：当价格突破趋势线时产生预警信号
5. **多时间框架趋势线**：在低周期 K 线图上叠加高周期的趋势线

---

# 九、全前端计算 + 后端扩展框架

> **决策确认**（2026-05-26）
> - ✅ **EMA/SMA/WMA/HMA 保留前端** — `clipToVisibleRange()` 裁剪 setData
> - ✅ **MACD/RSI/BOLL 也保留前端** — 同样用 `clipToVisibleRange()` 裁剪
> - ✅ **clipToVisibleRange 通用裁剪** — 所有前端指标共用，解决 setData 渲染卡顿
> - ✅ **后端指标平台作为扩展框架** — 留给"特别要求的"复杂指标（如未来新增的特殊 ta4j 指标）
> - ✅ **独立 Controller 保留** — `KLineIndicatorController` 作为扩展框架骨架
> - ❌ **本次不迁移任何指标到后端** — MACD/RSI/BOLL 保留在前端 indicators.ts

## 9.1 重新认识问题

### 9.1.1 指标渲染的共性

所有指标（MA / MACD / RSI / BOLL）面对的是**同一个问题**：

| 指标 | 全量数据点数 | 实际可视点数 | 冗余比 |
|------|------------|------------|--------|
| MA（4 条） | 5000 × 4 = **20000** | ~200 × 4 = **800** | **96%** |
| MACD（3 条） | 5000 × 3 = **15000** | ~200 × 3 = **600** | **96%** |
| RSI（1 条） | 5000 × 1 = **5000** | ~200 × 1 = **200** | **96%** |
| BOLL（3 条） | 5000 × 3 = **15000** | ~200 × 3 = **600** | **96%** |

> 问题的关键在于：**无论什么指标，lightweight-charts 的 setData 传入 5000 点和传入 200 点，渲染开销差距巨大**。后端计算可以省掉前端计算时间，但省不掉 setData 渲染时间。

### 9.1.2 卡顿真凶是 setData 渲染

```
❌ 错误归因：计算太慢（5000 点 × N 次）
✅ 真正原因：setData(20000 点) → 每次 scroll/zoom 触发 heavyweight 重绘
```

| 环节 | 当前耗时 | 裁剪后耗时 | 说明 |
|------|---------|-----------|------|
| emaArray(5000) × 4 | ~5ms | ~5ms | 计算本身就不慢 |
| calculateMACD(5000) | ~15ms | ~15ms | 前端也算得过来 |
| calculateRSI(5000) | ~10ms | ~10ms |  |
| calculateBOLL(5000) | ~15ms | ~15ms |  |
| **setData(全量)** | **~100-150ms** | **~5-10ms** | **这才是卡顿元凶** |
| 总计 | **~130-180ms** | **~35-55ms** | 裁剪后流畅可感知 |

### 9.1.3 结论

**所有指标都保留前端计算**，统一用 `clipToVisibleRange()` 在 setData 前裁剪到可视范围即可解决卡顿。后端指标平台保留为**扩展框架**，留给未来真正需要走后端的情况。

```
┌──────────────────────────────────────────────────────────┐
│                      前端（全部）                          │
│                                                          │
│  EMA / SMA / WMA / HMA  ──→ emaArray() + clipToVisible   │
│  MACD                   ──→ calculateMACD() + clipToVisible│
│  RSI                    ──→ calculateRSI() + clipToVisible│
│  BOLL                   ──→ calculateBOLL() + clipToVisible│
│  VOLUME / OBV / Kalman  ──→ 前端计算 + clipToVisible      │
│                                                          │
│  setData(~200 点) → 流畅                                │
└──────────────────────────────────────────────────────────┘
                            │
                            ▼ (仅扩展)
┌──────────────────────────────────────────────────────────┐
│              后端扩展框架（本次不动）                      │
│                                                          │
│  KLineIndicatorController   ← 骨架已搭建，留给未来        │
│  KLineIndicatorService      ← 特别要求的复杂指标          │
└──────────────────────────────────────────────────────────┘
```

## 9.2 架构设计

```
┌─────────────────────────────────────┐
│         MarketKlineV1.vue            │
│                                      │
│  updateMA()    ────→ emaArray() + clipToVisible │
│  updateMACD()  ────→ calculateMACD + clipToVisible │
│  updateRSI()   ────→ calculateRSI + clipToVisible  │
│  updateBOLL()  ────→ calculateBOLL + clipToVisible │
│  updateVOLUME() ───→ 前端简单计算 + clipToVisible   │
│  updateSUP()   ────→ (复用 TrendlineController)    │
└─────────────────────────────────────┘
```

## 9.3 后端扩展框架（骨架）

> 后端 `KLineIndicatorController` + `KLineIndicatorService` 作为**扩展框架**，本次搭建骨架不动，留给未来"特别要求的"复杂指标。

### 9.3.1 DTO 骨架

遵循 `TrendlineRequest` / `TrendlineResponse` 模式：

```java
public class IndicatorRequest {
    private String symbol;
    private String interval;
    private int size = 500;
    private List<IndicatorConfig> indicators;   // 未来扩展用
    private Long visualFrom;
    private Long visualTo;
}

public class IndicatorConfig {
    private String type;                        // 未来扩展用
    private Map<String, Object> params;
}

public class IndicatorResponse {
    private boolean success;
    private IndicatorResult data;
}

public class IndicatorResult {
    private long startTime;
    private long endTime;
    private String symbol;
    private String interval;
    private Map<String, IndicatorSeries> series; // 未来扩展用
}

public class IndicatorSeries {
    private String type;                        // line / histogram
    private int decimal;
    private List<TimeValuePoint> points;
}
```

### 9.3.2 Controller + Service 骨架

```java
@RestController
@RequestMapping("/api/kline")
public class KLineIndicatorController {

    @Resource
    private KLineIndicatorService indicatorService;

    @PostMapping("/indicators")
    public ApiResponse<IndicatorResult> calculateIndicators(
            @RequestBody @Valid IndicatorRequest request) {
        IndicatorResult result = indicatorService.calculate(request);
        return ApiResponse.success(result);
    }
}
```

```java
@Service
public class KLineIndicatorServiceImpl implements KLineIndicatorService {

    @Resource
    private ICandlestickService candlestickService;

    @Override
    public IndicatorResult calculate(IndicatorRequest request) {
        List<Candlestick> klines = candlestickService.getLastKlines(...);
        BarSeries series = IndicatorWrapHelper.buildSeries(klines);
        Map<String, IndicatorSeries> resultSeries = new LinkedHashMap<>();

        for (IndicatorConfig config : request.getIndicators()) {
            // 未来按需扩展，如 "SUPER_TREND" → calcSuperTrend(series, config, ...)
            // switch (config.getType()) { ... }
        }

        IndicatorResult result = new IndicatorResult();
        result.setSymbol(request.getSymbol());
        result.setInterval(request.getInterval());
        result.setSeries(resultSeries);
        return result;
    }
}
```

## 9.4 前端集成（全部前端裁剪）

### 9.4.1 通用裁剪工具

所有前端指标共用同一个裁剪工具：

```typescript
// marketKlineBase.ts — 通用裁剪工具
function clipToVisibleRange<T extends { time: number }>(
  data: T[],
  visibleRange: { from: number; to: number } | null,
  padding = 5,
): T[] {
  if (!visibleRange || !data?.length) return data;
  const from = visibleRange.from - padding;
  const to = visibleRange.to + padding;
  return data.filter(p => p.time >= from && p.time <= to);
}
```

### 9.4.2 各指标改造

**updateMA()** — 前端计算 + 裁剪：

```typescript
function updateMA() {
  if (!chart.value) return;
  // 计算 emaArray 逻辑不变 ...
  const visibleRange = chart.value.timeScale().getVisibleRange();
  const clippedData = clipToVisibleRange(emaValues, visibleRange);
  maSeries.setData(clippedData);  // 5000 → ~200 点
}
```

**updateMACD()** — 前端计算 + 裁剪（不走后端，indicators.ts 中 calculateMACD 保留）：

```typescript
function updateMACD() {
  if (!chart.value) return;
  // calculateMACD() 保留，继续前端计算 ...
  const visibleRange = chart.value.timeScale().getVisibleRange();
  macdLineSeries.setData(clipToVisibleRange(macdValues, visibleRange));
  signalLineSeries.setData(clipToVisibleRange(signalValues, visibleRange));
  histogramSeries.setData(clipToVisibleRange(histValues, visibleRange));
}
```

**updateRSI()** — 前端计算 + 裁剪：

```typescript
function updateRSI() {
  if (!chart.value) return;
  // calculateRSI() 保留，继续前端计算 ...
  const visibleRange = chart.value.timeScale().getVisibleRange();
  rsiSeries.setData(clipToVisibleRange(rsiValues, visibleRange));
}
```

**updateBOLL()** — 前端计算 + 裁剪（indicators.ts 中 calculateBoll 保留）：

```typescript
function updateBOLL() {
  if (!chart.value) return;
  // calculateBoll() 保留，继续前端计算 ...
  const visibleRange = chart.value.timeScale().getVisibleRange();
  middleSeries.setData(clipToVisibleRange(middleValues, visibleRange));
  upperSeries.setData(clipToVisibleRange(upperValues, visibleRange));
  lowerSeries.setData(clipToVisibleRange(lowerValues, visibleRange));
}
```

### 9.4.3 可视范围联动（全前端）

```typescript
function initIndicatorRangeSync() {
  chart.value.timeScale().subscribeVisibleTimeRangeChange(() => {
    // 所有指标全部前端裁剪，不需要防抖请求后端
    if (indicators.value.ma) updateMA();
    if (indicators.value.macd?.enabled) updateMACD();
    if (indicators.value.rsi?.enabled) updateRSI();
    if (indicators.value.boll?.enabled) updateBOLL();
  });
}
```

> 因为全部是前端操作，没有网络请求，可视范围变化时瞬间完成裁剪 + setData，无需 debounce。

## 9.6 Trendline 适配

保持不变，趋势线仍然独立为 `TrendlineController`。

## 9.7 性能分析

### 9.7.1 全前端裁剪前后对比（所有指标统一）

| 维度 | 当前（5000 点全量 setData） | 前端裁剪后（~200 点 setData） |
|------|--------------------------|---------------------------|
| 计算位置 | 前端（均不变） | 前端（均不变） |
| 计算耗时 | ~5-15ms | ~5-15ms（不变） |
| setData 点数（MA × 4） | 5000 × 4 = **20000** | **~200 × 4 = 800** |
| setData 点数（MACD × 3） | 5000 × 3 = **15000** | **~200 × 3 = 600** |
| setData 点数（RSI × 1） | 5000 × 1 = **5000** | **~200 × 1 = 200** |
| setData 点数（BOLL × 3） | 5000 × 3 = **15000** | **~200 × 3 = 600** |
| 渲染耗时 | ~100-150ms | **~5-10ms** |
| 网络开销 | 0（全部前端） | 0（全部前端，不变） |
| 总耗时 | **~130-180ms（卡顿）** | **~10-25ms（流畅）** |

> **关键结论**：全部指标保留前端，只需加一行 `clipToVisibleRange()` 裁剪即可解决所有渲染卡顿，零网络开销、零后端改造成本。

## 9.8 缓存策略

全部前端计算，不需要缓存。

| 数据 | 缓存策略 |
|------|---------|
| dataCache（K 线原始数据） | 已有不变 |
| 前端指标计算结果 | **不缓存** — setData 时裁剪即可，计算量极小 |
| 后端扩展框架 | 未来按需设计 |

## 9.9 异常处理

| 异常场景 | 处理方式 |
|---------|---------|
| dataCache 为空 | 不计算指标 |
| chart 未初始化 | 跳过 setData |
| 前端指标计算异常（如除零） | 单条指标静默跳过，不影响其他 |
| 后端扩展框架 | 未来按需设计 |

> 全部前端计算不存在网络失败问题，异常处理极简。

## 9.10 实施计划

### Phase 1（1 天）：全指标 clipToVisibleRange 热修复

| 步骤 | 内容 | 文件 |
|------|------|------|
| 1.1 | 新增 `clipToVisibleRange()` 通用裁剪函数 | `marketKlineBase.ts` |
| 1.2 | updateMA() → setData 前裁剪 | `MarketKlineV1.vue` |
| 1.3 | updateMACD() → setData 前裁剪 | `MarketKlineV1.vue` |
| 1.4 | updateRSI() → setData 前裁剪 | `MarketKlineV1.vue` |
| 1.5 | updateBOLL() → setData 前裁剪 | `MarketKlineV1.vue` |
| 1.6 | updateVOLUME() → setData 前裁剪 | `MarketKlineV1.vue` |
| 1.7 | subscribeVisibleTimeRangeChange 联动裁剪 | `MarketKlineV1.vue` |

> 后端扩展框架（KLineIndicatorController + DTO + Service）单独排期，本次不开发。

### 实施前后对比

| 指标 | 改前 | 改后 |
|------|------|------|
| MA 选择 | ~130ms 卡顿 | ~15ms 丝滑 |
| MACD 选择 | ~130ms 卡顿 | ~20ms 丝滑 |
| RSI 选择 | ~120ms 卡顿 | ~15ms 丝滑 |
| BOLL 选择 | ~130ms 卡顿 | ~20ms 丝滑 |
| 所有指标全开 | ~300ms+ 严重卡顿 | ~40ms 丝滑 |

## 9.11 最终状态

### 9.11.1 指标计算位置总表

| 指标 | 计算位置 | 数据来源 | 裁剪方式 |
|------|---------|---------|---------|
| EMA / SMA / WMA / HMA | ✅ **前端** | dataCache | clipToVisibleRange |
| MACD | ✅ **前端** | dataCache | clipToVisibleRange |
| RSI | ✅ **前端** | dataCache | clipToVisibleRange |
| BOLL | ✅ **前端** | dataCache | clipToVisibleRange |
| VOLUME | ✅ **前端** | dataCache | clipToVisibleRange |
| OBV | ✅ **前端** | dataCache | clipToVisibleRange |
| Kalman | ✅ **前端** | dataCache | clipToVisibleRange |
| 趋势线/支撑压力 | ✅ **后端**（现有，独立） | ta4j | N/A（2 点线段） |

### 9.11.2 前端 indicators.ts 保留/删除清单

| 函数 | 操作 |
|------|------|
| `emaArray()` | **保留** — MA 继续使用 |
| `smaArray()` | **保留** |
| `wmaArray()` | **保留** |
| `hmaArray()` | **保留** |
| `maByType()` | **保留** |
| `atrArray()` | **保留** |
| `stdDevArray()` | **保留** |
| `calculateBoll()` | **保留** — MACD/RSI/BOLL 全部不走后端 |
| `calculateMACD()` | **保留** |
| `kalmanFilter()` | **保留** |

> indicators.ts **零删除**，所有函数保留不动。

## 9.12 与现有架构的关系

| 已有组件 | 关系 |
|---------|------|
| `TrendlineController` | 保持独立，不受影响 |
| `IndicatorWrapHelper` | 本次不动（留给未来后端扩展用） |
| `ICandlestickService` | 本次不动 |
| `indicators.ts`（前端） | **零删除**，所有函数保留 |
| `MarketKlineV1.vue` | 仅新增 `clipToVisibleRange()` 裁剪逻辑 |

## 9.13 后端扩展框架（未来）

后端 `KLineIndicatorController` 骨架单独维护，在以下场景考虑启用：

1. **引入复杂 ta4j 指标**：如 `IchimokuCloudIndicator`、`ParabolicSARIndicator` 等，前端实现成本极高
2. **需要精确精度**：依赖 ta4j 内部多层嵌套的指标链
3. **大数据量全量计算**：仅后台分析场景需要全量数据，与前端展示分离
