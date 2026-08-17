# SMC 多周期信号评估与动态仓位权重系统 V2.4 — 执行清单

> 依据：[SMC多周期信号评估与动态仓位权重系统 V2.3.md](../设计文档/权重引擎/SMC多周期信号评估与动态仓位权重系统%20V2.3.md)
>
> 设计原则：核心指标层（SmartMoneyConceptsIndicator）**不改动**，全部扩展计算由独立后处理器完成。

---

## 总览

| 阶段 | 内容 | 模块 | 依赖 |
|------|------|------|------|
| 一 | DTO 层（纯数据结构） | `ai-data` | 无 |
| 二 | 计算器层（纯函数） | `ai-data` / `ai-extension` | 阶段一 |
| 三 | 服务层（Spring Service） | `ai-quant` | 阶段一、二 |
| 四 | 权重规则引擎扩展 | `ai-signal` | 阶段三 |
| 五 | 行情看板与评估接口 | `ai-quant` | 阶段三、四 |
| 六 | PC 前端看板页面 | `ai-frontend-web` | 阶段五 |

---

## 阶段一：DTO 层 — 纯数据结构

> 目标模块：`ai-data`
> 包路径建议：`com.chain.ai.trade.engine.data.entity.dto.smc`

### 1.1 创建 `SmcStructureDTO`

文档 §10.2 定义。含三部分：

**基础标识**
- `symbol` / `period` / `timestamp`

**来自 SmartMoneyConceptsIndicator.Result 的原始字段**
- `swingTrend` / `internalTrend`
- `lastSwingHigh` / `lastSwingLow` / `prevSwingHigh` / `prevSwingLow`
- `lastHigherLow` / `lastLowerHigh`
- `lastSwingHighTime` / `lastSwingLowTime`

**BOS/CHOCH 信号**
- `swingBullishBOS` / `swingBearishBOS`
- `swingBullishCHOCH` / `swingBearishCHOCH`
- `lastSwingEventType`（0=无, 1=BOS, 2=CHOCH）

**扩展计算字段（由后处理器填充）**
- `waveIndex` / `wavePhase` / `positionRatio`
- `structureAge` / `flipCount`
- `riskRewardRatio` / `riskPercent` / `isChaosException`

**结构位**
- `swingOrderBlocks`（List<OrderBlockDTO>）
- FVG 字段（lastBullishFVGTop/Bottom, lastBearishFVGTop/Bottom）
- 溢价/折扣区域（premiumTop/Bottom, discountTop/Bottom, equilibriumCenter, currentZone）

**内部类 OrderBlockDTO**
- `high` / `low` / `time` / `bias`

### 1.2 创建 `MultiPeriodSmcData`

文档 §10.3 定义。4H/1H/15M 三层结构字段 + 盈亏比/混沌特例字段。

**4H 层：** `waveIndex4h` / `wavePhase4h` / `swingTrend4h` / `flipCount4h` / 结构位

**1H 层：** `positionRatio1h` / `structureAge1h` / `swingTrend1h` / BOS 事件

**15M 层：** `positionRatio15m` / `structureAge15m` / `swingTrend15m` / BOS 事件

**全局：** `riskRewardRatio` / `riskPercent` / `isChaosException` / `chaosForcedMultiplier`

### 1.3 创建 `ChaosExceptionResult`

文档 §8.6 中内嵌在 `ChaosExceptionEvaluator` 内的结果类。
- `triggered`（boolean）
- `reason`（String）
- `forcedMultiplier`（double，固定 0.2）

---

## 阶段二：计算器层 — 纯函数

> 推荐包路径：`com.chain.ai.trade.engine.data.utils.smc`
> 全部为静态方法，无 Spring 依赖，可单独单元测试

### 2.1 `WaveIndexCalculator` — 波次计算器

文档 §8.3

| 方法 | 签名 | 说明 |
|------|------|------|
| `calculate` | `(List<SmcBarResult>, int currentIdx, boolean isBuy) → int` | 遍历历史结果计算波次。多头 0~4+，空头 0~-4+。CHOCH 启动计数，Swing BOS 递增，趋势反转归零 |
| `getWavePhase` | `(int wave, boolean isBuy) → String` | 波次 → 中文阶段名：混沌/试盘/确认/加速/赶顶/赶底 |
| `isChaosExceptionApplicable` | `(int wave, double rr, double riskPercent, int flipCount) → boolean` | 四条件判断是否可触发混沌特例 |
| `calculateFlipCount` | `(List<SmcBarResult>, int currentIdx, int lookback) → int` | 过去 N 根 K 线内 swingTrend 翻转次数 |

### 2.2 `PositionRatioCalculator` — 位置比计算器

文档 §8.4

| 方法 | 签名 | 说明 |
|------|------|------|
| `calculate` | `(SmartMoneyConceptsIndicator.Result, boolean isBuy, double currentPrice) → double` | 做多：`(price - HL) / (HH - HL)`；做空：`(price - LL) / (LH - LL)`，返回 0.0~1.0 |

### 2.3 `SmcRRCalculator` — 盈亏比计算器（SMC 结构驱动）

文档 §8.5

| 方法 | 签名 | 说明 |
|------|------|------|
| `calculateNetRR` | `(SmcStructureDTO, double entryPrice, boolean isBuy, boolean isChaosMode) → double` | 基于结构位计算 SL/TP → 净盈亏比。做多：SL=HL下方0.1%~0.2%，TP=最近Bearish OB/FVG下沿；做空：SL=LH上方0.1%~0.2%，TP=最近Bullish OB/FVG上沿 |

### 2.4 `ChaosExceptionEvaluator` — 混沌特例判定器

文档 §8.6

| 方法 | 签名 | 说明 |
|------|------|------|
| `evaluate` | `(int waveIndex, double rr, double riskPercent, int flipCount) → ChaosExceptionResult` | 四条件 AND：Wave=0 + RR≥3:1 + 风险≤0.5% + flipCount<3 |

---

## 阶段三：服务层

> 目标模块：`ai-quant`
> 包路径：`com.chain.ai.trade.engine.service.smc`

### 3.1 创建 `SmcStructureService`

文档 §10.1 架构图的核心新增服务。

**职责：**
1. 调用 SmartMoneyConceptsIndicator 获取 Result
2. 调用 4 个计算器填充 SmcStructureDTO
3. 组装 MultiPeriodSmcData（4H/1H/15M）
4. 提供懒加载缓存（类似 SmcIndicatorService 的 ThreadLocal 模式）

**核心方法：**
- `getStructure(String symbol, String period, long signalTimeMs) → SmcStructureDTO` — 单周期结构数据
- `getMultiPeriodForWeightEngine(String symbol) → MultiPeriodSmcData` — 多周期数据（供权重引擎用）
- 内部：loadKlines → buildIndicator → runCalculators

---

## 阶段四：权重规则引擎扩展

> 目标模块：`ai-signal`

### 4.1 `IndicatorType` 新增枚举值

| 枚举值 | 文档 §9.4 说明 |
|--------|---------------|
| `SMC_WAVE_DIRECTIONAL` | 波次值（2,3,-2,-3 为确认/加速） |
| `SMC_FLIP_COUNT` | 翻转频率 |
| `SMC_POSITION_IDEAL` | 位置理想（1=理想，多支撑/空阻力） |
| `SMC_INTERNAL_BOS_ALIGNED` | 微观共振（1=共振） |
| `SMC_STRUCTURE_AGE` | 结构年龄（K线根数） |
| `SMC_DIRECTION_ALIGNED` | 方向一致性（1=一致, -1=背离） |
| `SMC_RISK_REWARD_RATIO` | 盈亏比 |

### 4.2 `WeightRuleContext` 新增字段

对应 7 个新指标的数据源字段。

### 4.3 `BuiltInIndicatorProvider` 注册新 handler

每个新指标注册一个 handler，数据从 `SmcStructureService` 获取。

### 4.4 权重规则 JSON 配置

文档 §9.2 完整配置，含：

**VETO 规则（第 1 层）：**
- SMC 混沌熔断（flipCount ≥ 3）
- SMC 方向一致性（directionAligned == -1，混沌特例可豁免）
- SMC 阶段否决（wave IN (1,4,-1,-4)）
- SMC 衰老结构否决（age > 15）

**SCORING 规则（第 2~4 层）：**
- 确认/加速段加分（wave IN (2,3,-2,-3) → +1.0）
- 理想位置加分（positionIdeal == 1 → +0.5）
- 趋势流畅加分（flipCount ≤ 1 → +0.5）
- 微观共振加分（internalBosAligned == 1 → +0.2）
- 新鲜结构加分（age ≤ 5 → +0.2）
- 混沌特例加分（wave=0 + RR≥3:1 + 风险≤0.5% → +0.2）

---

## 阶段五：行情看板与评估接口

> 目标模块：`ai-quant`
> 注意：**不创建独立 DashboardController**，评估能力集成到现有 `SmcController` + `GET /smc/multiPeriod` 接口中

### 5.1 `SmcMultiPeriodResponse` 扩展

在现有 Response 结构中扩展评估相关字段：

**MatrixItem 扩展：**
- `wavePhase`（混沌/试盘/确认/加速/赶顶/赶底 — 彩色标签）
- `positionRatio`（位置比数值 0.0~1.0）
- `structureAge`（结构年龄 K 线根数）
- `flipCount`（翻转频率）

**CoreData 扩展为 `SignalEvaluation` 评估面板：**
- `totalScore`（总评分）
- `finalWeight`（最终仓位乘数）
- `isChaosException`（混沌特例标记）
- `riskRewardRatio`（盈亏比）
- `entryPrice` / `stopLoss` / `takeProfit`（执行参数，可选）

### 5.2 `SmcMultiPeriodService.compute()` 扩展

在 `buildMatrix()` 和 `buildCoreData()` 中调用 `SmcStructureService`：
- `buildMatrix()` → 填充 wavePhase/positionRatio/structureAge/flipCount
- `buildCoreData()` → 调用 `SmcStructureService` 获取评估数据，填充评分/乘数

### 5.3 `SmcController` 扩展 — 新增 evaluate 接口

在现有 `SmcController` 中新增：

```
GET /smc/evaluate?symbol=ETH-USDT-SWAP&direction=LONG&entryPrice=2850.0
```

**处理流程：**
1. 调用 `SmcStructureService.getMultiPeriodForWeightEngine(symbol)` 获取多周期结构数据
2. 调用 `SmcRRCalculator.calculateNetRR()` 计算结构驱动盈亏比
3. 调用 `ChaosExceptionEvaluator.evaluate()` 判定混沌特例
4. 组装 `WeightRuleContext`，执行 `WeightRuleEngine.evaluateWithTrace()`
5. 混沌特例强制覆盖乘数
6. 返回评估结果 + 执行参数

**返回结构（复用现有 `SmcMultiPeriodResponse`，新增 evaluation 字段）：**

```json
{
  "symbol": "ETH-USDT-SWAP",
  "matrix": [
    { "period": "3M", "direction": "震荡" },
    { "period": "15M", "direction": "多头", "wavePhase": "加速", "positionRatio": 0.25, "structureAge": 3, "flipCount": 1 },
    { "period": "1H", "direction": "多头", "wavePhase": "确认", "positionRatio": 0.32, "structureAge": 8, "flipCount": 1 },
    { "period": "4H", "direction": "多头", "wavePhase": "加速", "positionRatio": 0.18, "structureAge": 4, "flipCount": 1 },
    { "period": "1D", "direction": "震荡" }
  ],
  "core": {
    "institutionResonance": "4H+1H+15M 多头共振",
    "marketGenre": "多头趋势",
    "trendState": "强上升·健康",
    "compositeState": "强上升·健康",
    "totalScore": 2.4,
    "finalWeight": 1.5,
    "isChaosException": false,
    "riskRewardRatio": 3.2
  },
  "criticalLevels": [ ... ],
  "evaluation": {                              // 新增：评估详情
    "status": "通过",
    "wavePhase4h": "加速",
    "positionRatio4h": 0.18,
    "flipCount4h": 1,
    "vetoResults": [ ... ],
    "scoringResults": [ ... ],
    "weightMapping": "≥2.0 → 1.5",
    "executionParams": {
      "entryPrice": 2850.0,
      "stopLoss": 2790.0,
      "takeProfit": 3100.0
    }
  }
}
```

---

## 阶段六：PC 前端 — 行情看板面板扩展

> 目标模块：`ai-frontend-web`
> 集成位置：`LingSheAiPanel.vue`（市场行情页面 MarketKlineV1.vue 右侧面板）

### 6.1 行情看板（LingSheAiPanel）扩展

在现有面板基础上新增以下展示区域：

**周期矩阵增强（MatrixItem 扩展展示）：**
- 每周期行增加：波次阶段名（颜色编码标签）、位置比进度条（0~100%）、结构年龄（数字+K线图标）、翻转频率（数字+警告色）
- 4H 行的翻转频率 ≥ 3 时高亮警告

**综合评估面板（新增区域）：**
- 评分分数 + 星级展示
- 最终仓位乘数（带颜色：0.2=灰, 0.5=黄, 1.0=绿, 1.5=紫）
- 混沌特例标记（特殊徽标）
- 盈亏比展示

**评估操作入口（新增区域）：**
- 输入框：入场价格（默认当前价）
- 方向选择：做多/做空（默认当前趋势方向）
- 按钮："执行评估"
- 调用 `GET /smc/evaluate` 后展示评估结果详情

**VETO/SCORING 轨迹（可折叠区域）：**
- 显示每条规则的触发状态和分数

### 6.2 API 调用

```typescript
// 扩展现有 smc API
export const evaluateSmc = (symbol: string, direction: string, entryPrice: number) =>
  api.get("/smc/evaluate", { params: { symbol, direction, entryPrice } });
```

---

## 依赖关系图

```
阶段一 ──→ 阶段二 ──→ 阶段三 ──┬──→ 阶段四
          DTO       计算器     服务   权重引擎
                                 │
                                 └──→ 阶段五 ──→ 阶段六
                                      看板接口    前端
```

## 建议执行顺序

1. **阶段一（DTO）** → 无依赖，可独立完成
2. **阶段二（计算器）** → 依赖 DTO，纯函数可单独测试
3. **阶段三（服务层）** → 依赖 DTO + 计算器
4. **阶段四（权重引擎）** → 依赖服务层提供数据
5. **阶段五（看板接口）** → 依赖服务层 + 权重引擎
6. **阶段六（前端）** → 依赖后端接口就绪

---

*文档版本：1.0*
*生成日期：2026-07-22*
*依据设计文档版本：V2.4*
