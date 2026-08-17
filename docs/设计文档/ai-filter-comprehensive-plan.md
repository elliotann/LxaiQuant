# AI 智能决策过滤 — 完整方案文档

> 本文档整合了以下3份文档的全部内容：
> 1. `docs/auto-signal-move-plan.md` — 自动信号从 MarketKlineV1 迁移到 EditStrategy
> 2. `docs/ai-filter-integration-plan.md` — AI 过滤整合到 Java 后端的方案
> 3. `docs/ai-filter-architecture-design.md` — 信号驱动型 + 策略驱动型的统一架构设计
>
> 文档中标记 **[待确认]** 的项需要团队讨论确认后才能进入实施。**全部 16 项均已确认，方案就绪，可进入 Phase 1 实施。**

---

## 目录

- [第1章 背景与动机](#第1章-背景与动机)
- [第2章 前端变更 — 自动信号迁移到策略配置](#第2章-前端变更--自动信号迁移到策略配置)
- [第3章 后端 AI 过滤引擎设计](#第3章-后端-ai-过滤引擎设计)
- [第4章 两套接入方式（信号驱动 + 策略驱动）](#第4章-两套接入方式信号驱动--策略驱动)
- [第5章 实施路线图](#第5章-实施路线图)
- [第6章 待确认项汇总](#第6章-待确认项汇总)

---

## 第1章 背景与动机

### 1.1 当前自动信号的问题

**现状：** 「自动信号」功能位于 [MarketKlineV1.vue](file:///f:/project/lenzeto/ai-frontend-web/src/views/market/MarketKlineV1.vue) 的「小灵宝」聊天面板底部，独立于策略配置存在。

**主要问题：**

| 问题 | 说明 |
|------|------|
| **全局配置** | 所有策略共用一套自动信号配置，无法按策略独立控制 |
| **位置不当** | 自动信号本质上是策略配置，不应放在行情页面的小灵宝中 |
| **功能单一** | 当前仅为开关 + 信号类型选择，没有 AI 智能判断 |
| **与策略割裂** | 配置存储在小灵宝组件中，与策略管理完全分离 |

### 1.2 目标

1. **前端**：将自动信号从 MarketKlineV1.vue 迁移到 EditStrategy.vue，作为策略级配置
2. **后端**：新增 AI 智能决策过滤引擎，在信号/决策产生时进行智能拦截
3. **架构**：同时支持**信号驱动型**（现有）和**策略指标驱动型**（未来，如网格交易）

### 1.3 参考设计：QuantDinger 的 AI 智能决策过滤

QuantDinger-Vue-main 中的 AI 过滤设计作为主要参考：

- **位置**：策略创建/编辑表单中，独立的 🤖 区块
- **数据**：`trading_config.enable_ai_filter`（布尔值，策略级别）
- **UI**：Ant Design Vue 的 a-switch + 提示文字
- **后端**：Python FastAnalysisService，5 阶段分析（数据采集 → 客观评分 → LLM 分析 → 共识校准 → 最终决策）

---

## 第2章 前端变更 — 自动信号迁移到策略配置

### 2.1 当前实现分析

#### 2.1.1 MarketKlineV1.vue 中的自动信号

**界面位置：** 小灵宝聊天面板最底部，聊天输入框上方

```
┌─────────────────────────────────────┐
│ 自动信号  [已开启/已关闭] [限价/市价] [全部] │
│ [业务快捷入口: 分析/复盘/...]              │
│ [输入框                    ] [发送]       │
└─────────────────────────────────────┘
```

**数据模型：**
```typescript
const autoSignalEnabled = ref(false);
const autoSignalAllowedActions = ref<string[]>([]);
const autoSignalActions = [
  { key: "limit_signal", label: "限价/市价" },
  { key: "all_signals", label: "全部" },
];
```

**API 接口：**
- `GET /api/signal/auto-config` → 返回 `{ enabled: boolean, allowedActions: string[] }`
- `POST /api/signal/auto-config` → 发送 `{ enabled: boolean, allowedActions: string[] }`

> ⚠ 当前配置是**全局级别**的（所有策略共用），并未绑定到具体策略。

**关联函数：**
- `loadAutoSignalConfig()` — 初始化时加载自动信号配置
- `saveAutoSignalConfig()` — 切换开关/点击 chip 时保存
- `toggleAutoSignalEnabled()` — 切换开关
- `toggleAutoSignalAction(key)` — 切换信号类型

#### 2.1.2 EditStrategy.vue 现有结构

**表单卡片布局：**

| 卡片区块 | 内容 |
|---------|------|
| 基本信息 | 策略名称、类型、类、时间框架、信号ID、描述 |
| 策略参数 | 参数名/类型/默认值/最小值/最大值 |
| 策略代码 | 代码编辑区 |
| 风控设置 | 仓位控制、止损规则、止盈规则、支撑与压力出场、时间条件止盈止损、分批止盈 |

**保存 payload 关键字段：**
```typescript
const updateData = {
  name, strategyId, strategyType, description,
  className, timeFrame, codeContent, defaultParameters,
  riskControl: JSON.stringify(riskControlMerged),
  positionRisk: JSON.stringify(positionRisk),
  exitRules: JSON.stringify(exitRules),
};
```

### 2.2 参考：QuantDinger-Vue-main 的 AI 过滤 UI

**界面：**
```
┌─────────────────────────────────────────┐
│  🤖  启用AI智能决策过滤          [开关]  │
│  启用后，指标信号将经过AI智能过滤，提高交易质量 │
└─────────────────────────────────────────┘
```

**关键设计要点：**
1. 开关绑定到**策略级别** — 每个策略独立配置
2. **纯开关设计** — 简洁明了，无需额外下拉或芯片
3. 显示在**策略表单**中 — 与风控配置同级

### 2.3 迁移方案

#### 2.3.1 方案对比

| 方案 | 描述 | 复杂度 | 推荐 |
|-----|------|--------|:---:|
| **A：策略级配置（推荐）** | 将自动信号作为策略的一个属性，每个策略独立配置开关 | 中 | ⭐ |
| B：全局配置 + 策略引用 | 保留全局配置，在策略中引用 | 低 | ❌ |
| C：风控子项 | 在风控设置中增加「自动信号」子卡 | 中 | ❌ |

**推荐方案 A**，理由：
- 与 QuantDinger 的 AI 智能决策过滤设计一致
- 不同策略可能有不同的自动信号需求
- 策略表单已有独立卡片布局，新增卡片符合现有模式

#### 2.3.2 前端 UI 设计

**选项 1 — 独立卡片（推荐）：** 在风控设置卡片之后新增独立的 el-card

```
┌────────────────────────────────────────────┐
│  📡  AI 智能决策过滤                         │
├────────────────────────────────────────────┤
│                                            │
│  启用 AI 智能过滤                    [开关]  │
│                                            │
│  启用后，开仓信号将经过 AI 大模型分析，         │
│  结合市场数据综合判断后决定是否执行开仓         │
│                                            │
│  > 信号类型由 LLM 决策自动确定，无需手动配置     │
│                                            │
└────────────────────────────────────────────┘
```

> **已确认 ✓**：采用 **选项1 — 独立卡片** 布局。

#### 2.3.3 数据模型变更

在 `strategyForm` 中新增 `autoSignal` 字段：

```typescript
const strategyForm = reactive({
  // ... existing fields
  autoSignal: {
    enabled: false,
  },
});
```

#### 2.3.4 API 设计

**方式一（推荐）：合并到策略保存 API**

将 `autoSignal` 合并到 `updateData` 中：

```typescript
const updateData: any = {
  // ... existing fields
  autoSignal: JSON.stringify(strategyForm.autoSignal),
};
```

或合入 `exitRules`：

```typescript
const exitRules = {
  // ... existing
  autoSignal: {
    enabled: strategyForm.autoSignal.enabled,
  },
};
```

> **已确认 ✓**：采用**方式一（合并到策略保存 API）**。

#### 2.3.5 数据加载（fillFormFromData）

```typescript
if (strategy.autoSignal) {
  try {
    const config = typeof strategy.autoSignal === 'string'
      ? JSON.parse(strategy.autoSignal)
      : strategy.autoSignal;
    strategyForm.autoSignal.enabled = !!config.enabled;
  } catch (e) {
    console.warn("解析自动信号配置失败", e);
  }
}
```

#### 2.3.6 旧代码清理

从 [MarketKlineV1.vue](file:///f:/project/lenzeto/ai-frontend-web/src/views/market/MarketKlineV1.vue) 中移除：

1. **模板**：`<div class="auto-signal-row">` 区块（约 L1301-L1304）
2. **脚本**：`autoSignalEnabled`、`autoSignalAllowedActions`、`autoSignalSaving`、`autoSignalActions`、`loadAutoSignalConfig()`、`saveAutoSignalConfig()`、`toggleAutoSignalEnabled()`、`toggleAutoSignalAction()`（约 L11295-L11350）
3. **样式**：`.auto-signal-row`、`.auto-signal-label`、`.auto-signal-toggle`、`.auto-signal-chip`（约 L14131-L14185）

---

## 第3章 后端 AI 过滤引擎设计

### 3.1 两种交易模式分析

#### 模式 A：信号驱动型（当前系统实现）

```
SignTaskExecute (XXL-JOB 定时触发)
  │
  ▼
技术指标计算 (RSI/MACD/BOLL等)
  │
  ▼
BuyAndSellWeightDto (buyType: LONG/SHORT/NONE)
  │
  ▼
technical_signal DB 持久化 ← 信号落库
  │
  ▼ (另条时间线)
DefaultDealStrategyTrade → SignalBasedStrategyImpl
  → 读取信号 → 开仓
```

**特点：**
- 信号生产（SignTaskExecute）与消费（trading engine）解耦
- 信号持久化到 DB，可回溯、可复用、可被多个机器人消费
- 核心数据：`technical_signal` 表（signalType: LB/SB, 时间戳, 品种等）

#### 模式 B：策略指标驱动型（未来要支持，如网格交易）

```
GridTradingStrategy (策略内部逻辑)
  │
  ├── 价格是否到达网格层？
  ├── 波动率是否在安全范围？
  ├── 是否触发加仓/减仓条件？
  │
  ▼
shouldEnterDirection() → BUY/SELL/null  ← 决策即执行
  │
  ▼
DefaultDealStrategyTrade → 开仓
```

**特点：**
- 策略内部自带完整的入场/出场逻辑
- 没有单独的「信号落库」步骤
- 核心数据：策略内部状态（网格层数、持仓均价等）

#### 两种模式核心差异

| 维度 | 信号驱动型 | 策略指标驱动型 |
|------|-----------|--------------|
| **决策来源** | 外部定时器 + 技术指标计算 | 策略内部逻辑 |
| **数据持久化** | 信号先落库，再被消费 | 策略状态在内存中，订单落库 |
| **AI 过滤时机** | 信号生成后、落库前 | 策略决策后、下单前 |
| **代表策略** | BOLL_RSI, MACD 信号策略 | 网格交易、马丁格尔、趋势跟踪 |

### 3.2 AI 过滤时机

> **已确认 ✓**：采用**信号生成时过滤（方案A）** — 在 SignTaskExecute 中信号计算后、DB 落库前执行 AI 过滤。

**方案说明（信号生成时过滤）：**

```
SignTaskExecute
  │
  ├── calculateSignal() → { buyType=LONG }
  ├── getWeightAndConfidence() → { weight=1.2 }  ← 仓位乘数 (范围 0~2)
  │
  ├── ★ AI Filter ← 只在这里调一次 LLM
  │      ├── 允许 → weight 不变，正常落库:
  │      │     buyType=LONG, signalStrength=1.2, ai_filter_decision=ALLOW
  │      │
  │      └── 拦截 → weight 置 0，BuyType 不变:
  │            buyType=LONG, signalStrength=0, ai_filter_decision=REJECT
  │
  ├── 下游 SignalBasedStrategyImpl 按 signalStrength 计算仓位：
  │      PositionAdjuster.adjust(capital, leverage, signalStrength, price)
  │      → signalStrength=0 时 adjustedAmount=0，不开仓
  │
  └── 对比分析层面按 ai_filter_decision + signalStrength 统计分析

优势：
  ✅ LLM 调用次数 = 信号生成次数（最低）
  ✅ BuyType 不变，数据结构最简单，无新增冗余字段
  ✅ 下游零改动（SignalBasedStrategyImpl 默认 weight=1.0，weight=0 即不开仓）
  ✅ 回测时可选择是否纳入 AI 过滤后的信号
```

**不采纳的方案 B（下单前过滤）原因：** 同一个信号可能被多个机器人读取导致 LLM 重复调用，DB 中存在已拦截的脏数据需要额外标记，回测处理也更复杂。

### 3.3 统一架构设计

两种模式共用同一个核心服务 [AiSignalFilterService](file:///f:/project/lenzeto/ai-frontend-web/docs/ai-filter-architecture-design.md)：

```
                     ┌──────────────────────────────┐
                     │     AiSignalFilterService     │  ← 核心服务，两者共用
                     │                              │
                     │  Phase 1: 客观评分（规则引擎）  │
                     │  Phase 2: LLM 分析（可选）     │
                     │  Phase 3: 共识校准（融合决策）  │
                     └──────────────────────────────┘
                                ▲
              ┌─────────────────┴─────────────────┐
              │                                    │
  信号驱动型使用                         策略指标驱动型使用
              │                                    │
  ┌────────────────────┐      ┌────────────────────────────┐
  │ SignTaskExecute     │      │ GridTradingStrategy        │
  │  → 信号生成后过滤   │      │  → shouldEnter 时过滤      │
  │  → 过滤信号本身     │      │  → 过滤开仓决策           │
  │  → 仓位乘数设为0后落库  │      │  → 控制是否下单           │
  └────────────────────┘      └────────────────────────────┘
```

#### 整体架构图

```
╔══════════════════════════════════════════════════════════════╗
║                   统一 AI Filter 架构                        ║
╠══════════════════════════════════════════════════════════════╣
║                                                              ║
║  ┌──────────────────────────────────────────────────────┐   ║
║  │               AiSignalFilterService                   │   ║
║  │                                                      │   ║
║  │  ┌─────────────────┐   ┌─────────────────────────┐   │   ║
║  │  │  客观评分引擎     │   │   LLM 分析引擎           │   │   ║
║  │  │  ObjectiveScorer │   │  LlmAnalyzer             │   │   ║
║  │  │                  │   │                         │   │   ║
║  │  │  - 趋势评分      │   │  - 构造 Prompt           │   │   ║
║  │  │  - 波动率评分    │   │  - 调用 LLM API          │   │   ║
║  │  │  - 量价关系评分  │   │  - 解析 JSON 决策        │   │   ║
║  │  │  - 支撑阻力评分  │   │  - 超时/重试处理          │   │   ║
║  │  └─────────────────┘   └─────────────────────────┘   │   ║
║  │                                                      │   ║
║  │  ┌──────────────────────────────────────────────┐   │   ║
║  │  │          共识校准器 ConsensusCalibrator        │   │   ║
║  │  │  - 客观分 vs LLM 决策融合                      │   │   ║
║  │  │  - 置信度计算                                  │   │   ║
║  │  │  - 最终决策: ALLOW / REJECT / AMBIGUOUS        │   │   ║
║  │  └──────────────────────────────────────────────┘   │   ║
║  └──────────────────────────────────────────────────────┘   ║
║                                                              ║
║  ┌──────────────────────┐   ┌──────────────────────────┐   ║
║  │  MarketDataCollector  │   │   AiFilterConfigLoader   │   ║
║  │  - 多周期K线采集      │   │   - 读取策略配置         │   ║
║  │  - 技术指标计算       │   │   - 读取LLM模型配置      │   ║
║  └──────────────────────┘   └──────────────────────────┘   ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
```

### 3.4 核心接口设计

```java
/**
 * AI 过滤结果
 */
public enum AiFilterDecision {
    ALLOW,      // 允许通过（信号质量好）
    REJECT,     // 拦截（信号质量差）
    AMBIGUOUS   // 不确定，由调用方决定（LLM 超时等降级场景）
}

/**
 * AI 过滤请求 — 统一入参
 * 无论信号驱动还是策略驱动，都使用这个请求对象
 */
public class AiFilterRequest {
    private String symbol;                    // 交易品种
    private String direction;                 // 信号方向: LONG / SHORT
    private String strategyId;                // 策略ID
    private String robotId;                   // 机器人ID
    private List<Candlestick> candlesticks;   // 多周期K线（可选，不传则内部采集）
    private Map<String, Double> indicators;   // 技术指标预计算值（可选）
    private String source;                    // "SIGNAL_DRIVEN" / "STRATEGY_DRIVEN"
    private String signalType;                // 信号类型（如哪个指标产生的）
}

/**
 * AI 过滤结果
 */
public class AiFilterResult {
    private AiFilterDecision decision;
    private double confidence;                // 置信度 0-100
    private double objectiveScore;            // 客观评分 -100 ~ +100
    private String llmDecision;               // LLM 原始决策: BUY/SELL/HOLD
    private String summary;                   // AI 分析摘要
    private String rejectionReason;           // 拦截原因
    private long processTimeMs;               // 处理耗时
    private boolean llmInvoked;               // 是否调用了LLM
}

/**
 * AI 过滤服务 — 统一入口
 */
public interface AiSignalFilterService {
    AiFilterResult filter(AiFilterRequest request);
    ObjectiveScoreResult quickScore(AiFilterRequest request);
}
```

### 3.5 客观评分引擎设计（纯规则，不调 LLM）

这是**性能关键**。先走规则引擎，大部分决策不需要调 LLM。

**[待确认 3.5-A]** 以下评分权重和阈值是否合理？后续可根据回测数据调整。

```java
public class ObjectiveScorer {

    /**
     * 计算客观评分
     * @return -100 (强烈看空) ~ +100 (强烈看多)，0 = 中性
     */
    public ObjectiveScoreResult calculate(AiFilterRequest request) {
        double trendScore = scoreTrend(request.getCandlesticks());          // 趋势评分 × 0.35
        double volatilityScore = scoreVolatility(request.getCandlesticks());// 波动率评分 × 0.20
        double supportResistanceScore = scoreSR(request.getCandlesticks()); // 支撑阻力评分 × 0.25
        double volumeScore = scoreVolume(request.getCandlesticks());        // 量价关系评分 × 0.20

        double totalScore = trendScore * 0.35
                          + volatilityScore * 0.20
                          + supportResistanceScore * 0.25
                          + volumeScore * 0.20;

        double confidence = calculateConfidence(totalScore);
        return new ObjectiveScoreResult(totalScore, confidence, Map.of(
            "trend", trendScore,
            "volatility", volatilityScore,
            "supportResistance", supportResistanceScore,
            "volume", volumeScore
        ));
    }

    /**
     * 三级决策策略（性能优化核心）
     *
     * 客观分 > +50 → 强烈看多，无需 LLM，直接 ALLOW（如果信号方向=LONG）
     * 客观分 < -50 → 强烈看空，无需 LLM，直接 ALLOW（如果信号方向=SHORT）
     * 客观分 -15 ~ +15 → 高度不确定，调 LLM 分析
     * 其他区间 → 看配置，可以调 LLM 也可以走规则
     */
    public ProcessingLevel determineLevel(double objectiveScore) {
        if (Math.abs(objectiveScore) > 50) return ProcessingLevel.SKIP_LLM;
        if (Math.abs(objectiveScore) < 15) return ProcessingLevel.MUST_LLM;
        return ProcessingLevel.OPTIONAL_LLM;
    }
}
```

#### 性能估算

| 场景 | 耗时 | LLM 调用频率 |
|------|------|-------------|
| 客观分 > 50（强烈趋势） | < 1ms | 0%（直接决策） |
| 客观分 < 15（高度不确定） | < 1ms + LLM | 约 20-30% 信号需调 LLM |
| 中间区间 | < 1ms + (可选 LLM) | 可配置，0-100% |

> 按此设计，约 **70-80% 的信号无需调用 LLM**，仅靠客观评分即可决策。

#### 决策树

```
                  信号方向 = LONG (或 SHORT)
                         │
                         ▼
              客观评分引擎 (耗时 < 1ms)
                         │
           ┌─────────────┼─────────────┐
           │             │             │
      |分数|>50     15<|分数|<50    |分数|<15
           │             │             │
           ▼             ▼             ▼
     方向一致?        可选调LLM      必须调LLM
           │             │             │
     ┌─────┴─────┐   ┌────┴────┐      ▼
     │           │   │         │      LLM 分析
     是          否  调LLM   不调LLM    │
     │           │   │         │      │
     ▼           ▼   ▼         ▼      ▼
   ALLOW     REJECT    校准    按客观分  LLM决策
                              决策    +客观分校准
```

### 3.6 LLM Prompt 设计 — 统一模板资源库

Prompt 不硬编码在 Java 中，统一存放在 `ai-quant/src/main/resources/prompts/` 目录，作为统一的 Prompt 资源库管理。

#### 资源库目录结构

```
ai-quant/src/main/resources/prompts/
├── live_advice_v1.md                # 小灵宝实时建议（已有）
├── live_advice_non_openclaw_v1.md   # 小灵宝实时建议-非OpenClaw版（已有）
└── ai_filter_signal_v1.md           # ★ 新增: AI信号过滤
```

#### 模板格式规范

沿用现有 `MarkdownPromptTemplateService` 的模板格式，每个 `.md` 文件包含两个区块：

- `<!--SYSTEM-->...<!--/SYSTEM-->`：系统角色定义 + 规则 + 输出格式约束
- `<!--USER-->...<!--/USER-->`：用户输入数据，使用 `{{变量名}}` 占位符

#### AI 过滤 Prompt 模板（已创建）

模板文件：[ai_filter_signal_v1.md](file:///f:/project/lenzeto/ai-quant/src/main/resources/prompts/ai_filter_signal_v1.md)

**System 层设计：**
- 角色：资深量化风控分析师
- 任务：对技术信号进行多时间框架审核
- 评分规则：4 个维度加权（趋势一致性 35% / 波动率 20% / 关键价位 25% / 量价验证 20%）
- 阈值逻辑：≥50 ALLOW / <30 REJECT / 30~50 AMBIGUOUS
- 禁止调用外部 API

**User 层输入数据（由 Java 组装传入）：**
- 信号信息：交易对、方向、原始仓位乘数、时间
- 多时间框架行情：周线 → 4H → 1H → 15min
- 每个周期的趋势方向、RSI、布林带/MACD 位置
- 关键价位：支撑/阻力列表、价格距最近支撑阻力的百分比
- 量价数据：ATR、均量、成交量比率
- 近 5 根 K 线明细

**Prompt 输出 JSON 格式（由 System 约束 LLM 按此格式输出）：**

```json
{
  "decision": "ALLOW|REJECT|AMBIGUOUS",
  "confidence": 0.8,
  "score": 72,
  "key_reasons": ["1H 趋势与信号方向一致", "RSI 未超买"],
  "risks": ["接近阻力位", "成交量偏低"],
  "suggestedStrength": 1.0,
  "summary": "趋势向上，允许开多"
}
```

#### 加载机制（复用现有服务）

使用已有的 `MarkdownPromptTemplateService`，按照与 `loadLiveAdviceTemplate()` 一致的风格新增方法：

```java
// 在 MarkdownPromptTemplateService 中新增（与 loadLiveAdviceTemplate 风格一致）
public LoadedTemplate loadAiFilterSignalTemplate(Environment environment) {
    String name = environment != null
        ? environment.getProperty("advice.filter.signal.template") : null;
    if (name == null || name.isBlank()) {
        name = "ai_filter_signal_v1";
    }
    String configuredPath = environment != null
        ? environment.getProperty("advice.filter.signal.templatePath") : null;
    return loadTemplate(name, configuredPath);
}

// 调用方使用
LoadedTemplate tpl = markdownPromptTemplateService.loadAiFilterSignalTemplate(environment);

Map<String, String> vars = new HashMap<>();
vars.put("symbol", request.getSymbol());
vars.put("signalDirection", request.getDirection());
vars.put("signalStrength", String.valueOf(wc.getWeight()));
vars.put("trend4h", analysisResult.getTrend4h());
// ... 更多变量

String systemPrompt = markdownPromptTemplateService.render(tpl.system, vars);
String userPrompt = markdownPromptTemplateService.render(tpl.user, vars);
```

**优势：**
- ✅ Prompt 与代码分离，产品/运营人员可直接修改 `.md` 文件
- ✅ 版本化管理（`_v1`/`_v2`），支持 A/B 测试
- ✅ 复用现有加载机制，零新增依赖
- ✅ 支持通过配置中心动态切换模板版本

### 3.7 共识校准设计

```java
public class ConsensusCalibrator {

    /**
     * 共识校准：融合客观评分 + LLM 决策
     *
     * 规则：
     * 1. 客观分绝对值 > 50 → 以客观分为准（LLM 仅参考）
     * 2. LLM 决策为 HOLD → 以 HOLD 为准（安全优先）
     * 3. 客观分与 LLM 方向一致 → 高置信度 ALLOW
     * 4. 客观分与 LLM 方向相反 → 低置信度 REJECT
     * 5. LLM 超时/失败 → 按客观分降级（可配置）
     */
    public AiFilterResult calibrate(AiFilterRequest request,
                                     ObjectiveScoreResult objectiveScore,
                                     LlmResult llmResult) {

        if (llmResult == null || llmResult.isTimeout()) {
            return degradeToObjectiveScore(request, objectiveScore);
        }

        double objScore = objectiveScore.getTotalScore();
        String llmDecision = llmResult.getDecision(); // BUY/SELL/HOLD

        // LLM 决策为 HOLD → 安全拦截
        if ("HOLD".equalsIgnoreCase(llmDecision)) {
            return AiFilterResult.reject("LLM建议观望",
                        calculateConfidence(objScore, llmResult.getConfidence()));
        }

        // 客观分强烈 → 以客观分为主
        if (Math.abs(objScore) > 50) {
            boolean directionMatch = isDirectionMatch(request.getDirection(), objScore);
            return directionMatch
                ? AiFilterResult.allow("客观评分强信号", 85 + Math.abs(objScore) * 0.1)
                : AiFilterResult.reject("客观评分与信号方向相反", 90);
        }

        // LLM 与客观分方向一致 → ALLOW
        boolean llmMatchesObjective = isLlmMatchObjective(llmDecision, objScore);
        boolean llmMatchesSignal = isLlmMatchSignal(llmDecision, request.getDirection());

        if (llmMatchesObjective && llmMatchesSignal) {
            double confidence = 50 + Math.abs(objScore) * 0.3 + llmResult.getConfidence() * 0.2;
            return AiFilterResult.allow("LLM与客观评分一致", Math.min(confidence, 100));
        }

        // LLM 与客观分或信号方向不一致 → REJECT
        return AiFilterResult.reject(
            llmMatchesObjective ? "LLM与信号方向不一致" : "LLM与客观评分不一致",
            70
        );
    }
}
```

### 3.8 原始信号 vs AI 过滤对比分析

#### 3.8.1 信号表新增字段

在 `technical_signal` 表中新增以下字段，记录 AI 决策信息：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `ai_filter_decision` | VARCHAR(20) | AI决策：ALLOW / REJECT / AMBIGUOUS / NOT_APPLIED |
| `ai_filter_score` | DECIMAL(5,2) | 客观评分 -100~100 |
| `ai_filter_llm_decision` | VARCHAR(10) | LLM返回的原始决策：BUY / SELL / HOLD / NULL |
| `ai_filter_summary` | VARCHAR(500) | AI分析摘要 |

> `buy_type` 和 `signal_strength` 使用现有字段，AI 拦截时仅将 `signal_strength` 置为 0，`buy_type` 保持不变。

#### 3.8.2 数据存储示例

| buy_type | signal_strength | signal_time | ai_filter_decision | ai_filter_score |
|----------|----------------|-------------|-------------------|-----------------|
| LONG     | 1.2            | 2026-05-11  | ALLOW             | 72              |
| LONG     | 0              | 2026-05-11  | REJECT            | 23              |
| SHORT    | 0.8            | 2026-05-11  | ALLOW             | 65              |

> `signal_strength` 是 **仓位乘数**（范围 0~2），对应 `WeightAndConfidenceDto.weight` 字段：
> - `0` = 不开仓（AI 拦截）
> - `0.5` = 半仓
> - `1.0` = 标准仓位（默认值）
> - `2.0` = 双倍仓位
>
> `ai_filter_score` 是客观评分（范围 -100~+100），是 AI 过滤引擎内部评估信号质量的分数，**不等于**数据库中的 `signal_strength` 字段（仓位乘数 0~2）。

#### 3.8.3 对比分析查询

**① 拦截率统计：**

```sql
SELECT
  COUNT(*) AS 总信号数,
  SUM(CASE WHEN ai_filter_decision = 'REJECT' THEN 1 ELSE 0 END) AS 拦截数,
  ROUND(AVG(CASE WHEN ai_filter_decision = 'REJECT' THEN 1 ELSE 0 END) * 100, 2) AS 拦截率,
  SUM(CASE WHEN ai_filter_decision = 'ALLOW' THEN 1 ELSE 0 END) AS 允许数
FROM technical_signal
WHERE ai_filter_decision != 'NOT_APPLIED'
  AND signal_date >= NOW() - INTERVAL 7 DAY;
```

**② 各方向拦截明细：**

```sql
SELECT
  buy_type AS 信号方向,
  ai_filter_decision AS AI决策,
  COUNT(*) AS 信号数
FROM technical_signal
WHERE ai_filter_decision != 'NOT_APPLIED'
GROUP BY buy_type, ai_filter_decision
ORDER BY buy_type, ai_filter_decision;
```

结果示例：

| 信号方向 | AI决策 | 信号数 | 说明 |
|---------|--------|-------|------|
| LONG | ALLOW | 42 | 看多信号，AI同意 |
| LONG | REJECT | 18 | 看多信号，AI否决（仓位乘数置0） |
| SHORT | ALLOW | 35 | 看空信号，AI同意 |
| SHORT | REJECT | 12 | 看空信号，AI否决（仓位乘数置0） |

**③ 拦截原因分析：**

```sql
SELECT
  ai_filter_summary AS 拦截原因,
  COUNT(*) AS 次数
FROM technical_signal
WHERE ai_filter_decision = 'REJECT'
GROUP BY ai_filter_summary
ORDER BY 次数 DESC;
```

#### 3.8.4 数据看板建议

建议在管理后台（或小灵宝）增加一个分析面板，展示：

```
┌─────────────────────────────────────────────────┐
│  📊 AI 过滤效果分析        📅 [本周 ▼]           │
├─────────────────────────────────────────────────┤
│                                                   │
│  总信号: 107    拦截: 30 (28%)    允许: 77 (72%)  │
│                                                   │
│  ┌──────────────┬────────┬────────┬────────┐     │
│  │  原始方向    │  总数  │  拦截  │  拦截率 │     │
│  ├──────────────┼────────┼────────┼────────┤     │
│  │  📈 LONG     │   60   │   18   │  30%   │     │
│  │  📉 SHORT    │   47   │   12   │  26%   │     │
│  └──────────────┴────────┴────────┴────────┘     │
│                                                   │
│  🔍 拦截TOP原因:                                  │
│  1. 趋势不明朗 (12次)                              │
│  2. 接近阻力位 (8次)                               │
│  3. 波动率过高 (6次)                               │
│                                                   │
└─────────────────────────────────────────────────┘
```

#### 3.8.5 回测如何处理被拦截信号

| 回测模式 | 行为 | 推荐场景 |
|---------|------|---------|
| **保守模式（默认）** | 只使用 `signal_strength > 0` 的信号（即AI过滤后的信号），回测结果接近实盘 | 验证AI上线后的实际效果 |
| **对照模式** | 以 AI 上线前的历史信号为基准，对比 AI 上线后的胜率变化 | 评估AI带来的收益提升 |

> **数据完备性保障**：拦截信号也落库（`signal_strength=0` 但 `buy_type=LONG`），AI 上线前后都可在同一张表中查询对比，这是「设仓位乘数为0仍落库」方案的核心优势。

### 3.9 时序约束分析 — AI 过滤完成后交易引擎再读取

**[待确认 3.9-A]** 用户指出了一个关键时序问题：信号生成 → AI 过滤 → 交易引擎执行，三者必须按顺序进行，不能让交易引擎读到未过滤的信号。

#### 3.9.1 当前架构时序链路

```
[ai-task 模块]                                 [ai-quant 模块]
SignTaskExecute (XXL-Job)                      LiveTradingServiceImpl
       │                                              │
       │  ① 生成信号 (calculateSignal)                  │
       │  ② 保存到 DB (sendSignMq)                     │
       │  ③ 完成                                      │
       │                                              │  ④ 触发执行 (execStrategy)
       │                                              │  ⑤ 创建 SignalBasedStrategyImpl
       │                                              │  ⑥ loadSignalCache() ← 从 DB 读信号
       │                                              │  ⑦ shouldEnterDirection() ← 查缓存
       │                                              │  ⑧ 开仓/不开仓
```

**关键风险**：如果 ④ 发生在 ② 之后但 AI 过滤还没执行完，交易引擎会读到 `signal_strength > 0` 的未过滤信号，导致不该开的仓位被打开。

#### 3.9.2 三种可选方案

| 方案 | 描述 | 复杂度 | 风险 |
|------|------|--------|------|
| **A. 同步过滤（推荐）** | AI 过滤在 SignTaskExecute 内同步执行，过滤完才落库 | ⭐ 低 | ✅ 零风险 |
| **B. 状态标志（两阶段）** | 先落库（status=PENDING），异步 AI 过滤后更新 status，引擎跳过 PENDING | ⭐⭐⭐ 高 | ⚠️ 需处理超时/失败 |
| **C. 事件驱动** | SignTaskExecute 只生成信号 + 触发 AI 过滤，过滤完成后发 MQ 通知引擎执行 | ⭐⭐⭐⭐ 很高 | ⚠️ MQ 可靠性 |

**推荐方案 A（同步过滤）：**

```
SignTaskExecute (XXL-Job)
  │
  ├── calculateSignal()                       ← 生成原始信号
  ├── getWeightAndConfidence()                 ← 计算仓位乘数
  │
  ├── ★ AI Filter (同步阻塞)
  │     ├── MarketDataCollector.collect()      ← 采集多周期数据
  │     ├── ObjectiveScorer.score()            ← 客观评分
  │     ├── LlmAnalyzer.analyze() (可跳过)     ← LLM 分析
  │     └── 返回 decision + score
  │
  ├── 根据 decision 决定 signalStrength:
  │     ├── ALLOW  → signalStrength 不变
  │     ├── REJECT → signalStrength = 0
  │     └── AMBIGUOUS → 按配置降级
  │
  ├── sendSignMq() ← ★ 此时 DB 中 signal_strength 已经是最终值
  │
  └── 完成 ──→ 交易引擎任何时候读取，都是已过滤的最终值
```

**为什么同步方案可行？**
- LLM 调用延迟通常在 **1~5 秒**，信号生成任务（XXL-Job）可以接受这个延迟
- 信号生成频率通常 **15 分钟/次**（或每根 K 线），1~5 秒延迟影响极小
- 若启用三级策略（客观评分 >50 跳过 LLM），~80% 的信号无需 LLM 调用，延迟仅 ~50ms
- 同步方案零额外复杂度，零竞态风险

**方案 B 的应用场景（如果未来需要）：**
- 当 LLM 延迟变得不可接受（>10 秒）
- 当信号生成频率变高（每分钟生成）
- 此时需要引入状态标志机制

#### 3.9.3 同步过滤的时序保证

```
时间轴:
│
├── T:   SignTaskExecute 开始
├── T+1: calculateSignal() 完成 → buyType=LONG, weight=1.2
├── T+2: AI Filter 开始
│         ├── MarketDataCollector.collect()   ~300ms
│         ├── ObjectiveScorer.score()         ~50ms
│         ├── (可选) LLM analyze              ~1~5s
│         └── ConsensusCalibrator.calibrate() ~20ms
├── T+7: AI Filter 完成 → decision=REJECT
├── T+8: wc.setWeight(BigDecimal.ZERO)
├── T+9: sendSignMq() 落库 → signal_strength=0
└── T+10: 完成

交易引擎在 T+10 之后的任何触发都能读到正确的 signal_strength=0
```

#### 3.9.4 伪代码体现时序保证

```java
@XxlJob("signTaskExecute")
public void execute() {
    // ... 现有逻辑: 参数解析、获取K线数据 ...

    // ① 执行信号计算（现有）
    BuyAndSellWeightDto result = calculateSignal(strategyType, candlesticks, symbol, robotId);

    // ② 获取权重和置信度（现有）
    WeightAndConfidenceDto wc = getWeightAndConfidence(result);

    // ★ ③ AI 过滤（同步阻塞，必须在④落库前完成）
    if (aiFilterService.isAiFilterEnabled(robotId)) {
        AiFilterDecision decision = aiFilterService.evaluateSignal(/*...*/);
        if (decision.shouldReject()) {
            wc.setWeight(BigDecimal.ZERO);  // 仓位乘数置0
        }
        // 保存 AI 决策结果，随信号一起落库
        result.setAiFilterDecision(decision.getDecision().name());
        result.setAiFilterScore(decision.getObjectiveScore());
        result.setAiFilterSummary(decision.getSummary());
    }

    // ④ 落库（★ 此时 signal_strength 已经是 AI 过滤后的最终值）
    //    交易引擎读取时必然拿到正确值，无竞态风险
    saveSignalToDb(result, wc, strategyId, robotId);
}
```

---

## 第4章 两套接入方式（信号驱动 + 策略驱动）

### 4.1 方式 A：信号驱动型接入（SignTaskExecute）

在信号生成后、DB **落库前**同步插入 AI 过滤。这是保证时序正确的前提（详见 [3.9 节](file:///f:/project/lenzeto/ai-frontend-web/docs/ai-filter-comprehensive-plan.md#39-%E6%97%B6%E5%BA%8F%E7%BA%A6%E6%9D%9F%E5%88%86%E6%9E%90)）：

```java
// ★ SignTaskExecute.java
// 时序保证: ①计算信号 → ②AI过滤(同步) → ③落库
// 交易引擎在任何时候读取DB，都能拿到已经过滤的最终值
@XxlJob("signTaskExecute")
public void execute() {
    // ... 现有逻辑: 参数解析、获取K线数据 ...

    // ① 执行信号计算（现有）
    BuyAndSellWeightDto result = calculateSignal(strategyType, candlesticks, symbol, robotId);

    // ② 获取权重和置信度（现有逻辑，通常在 DefaultSignService 中完成）
    WeightAndConfidenceDto wc = getWeightAndConfidence(result);

    // ★ ③ AI 过滤（同步阻塞，保证在④落库前完成）
    String aiDecision = "NOT_APPLIED";
    BigDecimal originalStrength = wc.getWeight(); // 记录原始仓位乘数，用于对比分析
    if (isAiFilterEnabled(strategyId) && result.getBuyType() != BuySellType.NONE) {
        AiFilterRequest filterReq = AiFilterRequest.builder()
                .symbol(symbol)
                .direction(mapDirection(result.getBuyType()))
                .strategyId(strategyId)
                .robotId(robotId)
                .candlesticks(candlesticks)
                .source("SIGNAL_DRIVEN")
                .signalType(strategyType.name())
                .build();

        AiFilterResult filterResult = aiSignalFilterService.filter(filterReq);
        aiDecision = filterResult.getDecision().name();

        if (filterResult.getDecision() == AiFilterDecision.REJECT) {
            log.info("AI过滤拦截信号: symbol={}, direction={}, reason={}",
                    symbol, result.getBuyType(), filterResult.getRejectionReason());
            wc.setWeight(BigDecimal.ZERO);  // 仓位乘数置0，BuyType 不变
        }

        if (filterResult.getDecision() == AiFilterDecision.AMBIGUOUS) {
            log.warn("AI过滤不确定，按配置降级处理: symbol={}", symbol);
            if (!shouldAllowOnAmbiguous(strategyId)) {
                wc.setWeight(BigDecimal.ZERO);
            }
        }

        // 将 AI 决策信息写入信号对象，伴随落库
        result.setAiFilterDecision(aiDecision);
        result.setAiFilterScore(filterResult.getObjectiveScore());
        result.setAiFilterSummary(filterResult.getSummary());
    }

    // ④ 落库（★ 此时 signal_strength 已经是 AI 过滤后的最终值，交易引擎读取无竞态风险）
    // DefaultSignService.sendSignMq() 会将 WeightAndConfidenceDto.weight → TechnicalSignal.signalStrength
    saveSignalToDb(result, wc, strategyId, robotId);
}
```

### 4.2 方式 B：策略指标驱动型接入（如 GridTradingStrategy）

在策略的 `shouldEnterDirection()` 中插入 AI 过滤：

```java
@Component
@Scope("prototype")
public class GridTradingStrategy implements MultiPositionStrategy {

    @Autowired(required = false)
    private AiSignalFilterService aiSignalFilterService;

    @Override
    public Trade.TradeType shouldEnterDirection(int index, TradingRecord tradingRecord) {
        // 1. 网格逻辑判断是否应该入场
        Trade.TradeType gridDecision = evaluateGridEntry(index);
        if (gridDecision == null) return null;

        // 2. ★ AI 做二次确认
        if (aiSignalFilterService != null && isAiFilterEnabled()) {
            AiFilterRequest request = buildFilterRequest(index, gridDecision);
            AiFilterResult result = aiSignalFilterService.filter(request);

            if (result.getDecision() == AiFilterDecision.REJECT) {
                log.info("AI过滤拦截网格入场: index={}, direction={}, reason={}",
                        index, gridDecision, result.getRejectionReason());
                return null;
            }
        }

        return gridDecision;
    }
}
```

### 4.3 整合方案对比

| 方案 | 描述 | 复杂度 | 维护成本 | 延迟 |
|------|------|--------|---------|------|
| **A：Java 原生实现（推荐）** | 在 Java 后端直接实现 AI 过滤全流程 | 中 | 低（统一项目） | 低 |
| B：Python 微服务 | 将 QuantDinger 的 Python 代码部署为独立微服务 | 中 | 高（多服务） | 中（网络开销） |

**[待确认 4.3-A]** 采用方案 A（Java 原生实现）还是方案 B（Python 微服务）？

#### 方案 A 详述：后端新模块清单

| 新增类/接口 | 职责 | 参考来源 |
|------------|------|---------|
| `AiSignalFilterService` | AI 过滤入口，编排数据采集→评分→LLM→共识 | QuantDinger FastAnalysisService |
| `MarketDataCollector` | 采集多周期行情和技术指标 | QuantDinger Phase 1 |
| `ObjectiveScorer` | 纯规则引擎客观评分 | QuantDinger Phase 2 |
| `LlmAnalyzer` / `AiLlmClient` | LLM API 调用（OpenAI 协议） | 新开发 |
| `ConsensusCalibrator` | 客观评分 + LLM 共识校准 | QuantDinger Phase 4 |
| `AiFilterConfigLoader` | 读取策略的 AI 过滤配置 | 新开发 |

#### 方案 A 数据流

```
策略信号触发 (open_long / open_short)
  │
  ▼
检查策略配置 auto_signal.enabled == true?
  ├── 否 → 正常下单/落库
  └── 是 →
        ▼
  AiSignalFilterService.filter(request)
        │
        ▼
  1. MarketDataCollector.collect()
     - 当前K线 (1H/4H/1D)
     - RSI/MACD/MA/ATR
     - 支撑阻力位
        │
        ▼
  2. ObjectiveScorer.calculate()
     - 技术面评分 + 趋势评分 + 波动率评分
     - 综合客观分数 (-100 ~ +100)
        │
        ▼
  3. 三级决策:
     |分数|>50 → 跳过 LLM，直接决策
     |分数|<15 → 必须调 LLM
     其他 → 可选调 LLM
        │
        ▼
  4. (如需) LlmAnalyzer.analyze()
     - 构造 Prompt（含市场数据+客观评分）
     - 调用大模型 API
     - 解析 JSON 决策 (BUY/SELL/HOLD)
        │
        ▼
  5. ConsensusCalibrator.calibrate()
     - 客观分 vs LLM 决策融合
     - 最终决策: ALLOW / REJECT / AMBIGUOUS
        │
        ▼
  决策 == ALLOW? → 允许通过 ✅
  决策 == REJECT? → 拦截 + 日志 + 通知 ❌
  决策 == AMBIGUOUS? → 按配置降级（放行/拦截）
```

---

## 第5章 实施路线图

### Phase 1：数据模型与前端 UI（1-2 天）

| 任务 | 涉及文件 | 说明 |
|------|---------|------|
| 策略表新增 `auto_signal` JSON/TEXT 字段 | 数据库迁移 | **[待确认 5.1-A]** 字段类型：JSON 还是 TEXT？ |
| EditStrategy.vue 新增 AI 过滤配置 UI | [EditStrategy.vue](file:///f:/project/lenzeto/ai-frontend-web/src/views/strategy/EditStrategy.vue) | 独立卡片或风控子区 |
| strategyForm 增加 autoSignal 数据模型 | 同上 | 见 2.3.3 节 |
| fillFormFromData 增加回填逻辑 | 同上 | 见 2.3.5 节 |
| saveStrategy 中增加字段保存 | 同上 | 见 2.3.4 节 |
| MarketKlineV1.vue 清理旧代码 | [MarketKlineV1.vue](file:///f:/project/lenzeto/ai-frontend-web/src/views/market/MarketKlineV1.vue) | 移除模板/脚本/样式 |

### Phase 2：后端核心 AI 引擎（3-5 天）

| 任务 | 说明 | 依赖 |
|------|------|------|
| `MarketDataCollector` — 行情数据采集 | 接入已有行情 API | 行情数据源 |
| `AiLlmClient` — LLM API 集成 | 对接 OpenAI/DeepSeek 协议 | **[待确认 5.2-A]** LLM 服务地址和 API Key |
| `ObjectiveScorer` — 规则评分 | 实现 4 个评分维度 | 无 |
| `ConsensusCalibrator` — 共识校准 | 融合客观分 + LLM 决策 | ObjectiveScorer + LlmAnalyzer |
| `AiSignalFilterService` — 编排服务 | 串联 1-4 | 以上全部 |
| SignTaskExecute 接入 AI 过滤 | 信号生成后插入过滤 | AiSignalFilterService |

### Phase 3：通知与监控（1 天）

| 任务 | 说明 |
|------|------|
| 被 AI 拦截的开仓通知推送到前端 | WebSocket 推送 |
| 拦截日志记录 | 写入策略日志表 |
| 前端拦截记录展示 | 在策略日志页面展示拦截记录 |

### Phase 4：优化与灰度（1 天）

| 任务 | 说明 |
|------|------|
| LLM Prompt 调优 | 适配我们的交易品种和策略 |
| 超时/降级处理 | LLM 超时时走客观评分兜底 |
| 灰度开关 | 允许按策略比例放量（先 10% 策略启用，逐步扩大） |
| 策略指标驱动型接入 | GridTradingStrategy 等未来策略集成 |

### 总工作量估算

| Phase | 人天 | 前置条件 |
|-------|------|---------|
| Phase 1：前端 UI + 数据模型 | 1-2 天 | 方案确认 |
| Phase 2：后端 AI 引擎 | 3-5 天 | LLM 服务就绪 |
| Phase 3：通知与监控 | 1 天 | Phase 2 完成 |
| Phase 4：优化与灰度 | 1 天 | Phase 2-3 完成 |
| **合计** | **6-9 天** | |

---

## 第6章 待确认项汇总

### 6.1 前端设计确认

| 编号 | 已确认项 | 说明 |
|------|---------|------|
| ~~[2.3.2-A]~~ | **✅ 已确认**：独立卡片布局 |  |
| ~~[2.3.4-A]~~ | **✅ 已确认**：合并到策略保存 API |  |
| ~~[5.1-A]~~ | **✅ 已确认**：数据库字段使用 JSON 类型 | 策略表 `auto_signal` 字段使用 MySQL JSON 类型 |

### 6.2 后端技术确认

| 编号 | 已确认项 | 说明 |
|------|---------|------|
| ~~[3.2-A]~~ | **✅ 已确认**：信号生成时过滤（SignTaskExecute 中） |  |
| ~~[3.9-A]~~ | **✅ 已确认**：同步过滤方案 | AI 过滤在 SignTaskExecute 内同步阻塞执行，过滤完再落库（方案A），交易引擎任何时候读取都正确 |
| ~~[4.3-A]~~ | **✅ 已确认**：Java 原生实现 | 与现有系统统一部署，无跨进程开销 |
| ~~[5.2-A]~~ | **✅ 已确认**：复用现有 LLM 配置 | 小灵宝中已对接好 LLM 服务，直接复用其 API Key 和 endpoint 配置 |
| ~~[6.2-A]~~ | **✅ 已确认**：使用现有技术栈 | 系统现有的 Spring Boot + MyBatis 技术栈 |

### 6.3 业务逻辑确认

| 编号 | 已确认项 | 说明 |
|------|---------|------|
| ~~[3.5-A]~~ | **✅ 已确认**：客观评分权重和阈值 | 趋势0.35/波动率0.20/支撑阻力0.25/量价0.20。三级阈值：>50跳过LLM / <15必须LLM。后续可根据回测调整 |
| ~~[3.6-A]~~ | **✅ 已确认**：Prompt 统一模板资源库管理 | 存放在 `ai-quant/src/main/resources/prompts/ai_filter_signal_v1.md`，不硬编码在 Java 中 |
| ~~[6.3-A]~~ | **✅ 已确认**：Phase 1 只做技术面 | 宏观/新闻数据放在后续迭代 |
| ~~[6.3-B]~~ | **✅ 已确认**：灰度上线策略 | 先 10% 策略启用 AI 过滤，观察 1-2 周后逐步扩大 |

### 6.4 兼容性确认

| 编号 | 已确认项 | 说明 |
|------|---------|------|
| ~~[6.4-A]~~ | **✅ 已确认**：直接丢弃旧配置 | 旧的小灵宝全局自动信号配置直接清空，减少维护成本 |
| ~~[6.4-B]~~ | **✅ 已确认**：回测不启用 AI 过滤 | 回测速度快，LLM 零调用成本 |

---

## 附录：涉及文件清单

### 前端文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| `src/views/market/MarketKlineV1.vue` | 删除 | 移除自动信号模板、逻辑、样式 |
| `src/views/strategy/EditStrategy.vue` | 修改 | 新增 AI 过滤 UI、数据模型、加载/保存逻辑 |

### 后端文件 / 新模块

| 模块/类 | 操作 | 说明 |
|---------|------|------|
| `AiSignalFilterService` | 新增 | AI 过滤入口服务 |
| `MarketDataCollector` | 新增 | 多周期行情数据采集 |
| `ObjectiveScorer` | 新增 | 规则引擎客观评分 |
| `LlmAnalyzer` / `AiLlmClient` | 新增 | LLM API 调用 |
| `ConsensusCalibrator` | 新增 | 共识校准 |
| `AiFilterConfigLoader` | 新增 | AI 过滤配置加载 |
| `AiFilterAnalysisService` | 新增 | 对比分析服务（拦截率统计、Dashboard 数据） |
| `MarkdownPromptTemplateService` | 修改 | 新增 `loadAiFilterSignalTemplate()` 方法 |
| `SignTaskExecute.java` | 修改 | 插入 AI 过滤调用 + 写入 AI 决策字段 |
| `BuyAndSellWeightDto` / 信号实体 | 修改 | 新增 `ai_filter_*` 字段 |

### 资源文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| `ai-quant/src/main/resources/prompts/ai_filter_signal_v1.md` | 新增 | AI 信号过滤 Prompt 模板 |

### 数据库变更

| 表名 | 操作 | 说明 |
|------|------|------|
| 策略表 | 修改 | 新增 `auto_signal` 字段（JSON 或 TEXT） |
| `technical_signal` | 修改 | 新增 `ai_filter_decision`, `ai_filter_score`, `ai_filter_llm_decision`, `ai_filter_summary` 字段（`buy_type` 和 `signal_strength` 使用已有字段） |

---

> **文档状态**：**全部 16 项已确认，方案就绪，可进入 Phase 1 实施。** 已确认项包括：独立卡片、合并到策略API、信号生成时过滤、移除信号类型配置、拦截后设仓位乘数为0仍落库、客观评分权重和阈值、Prompt 统一模板资源库、同步过滤时序方案、JSON 字段类型、Java 原生实现、复用现有LLM配置、使用现有技术栈、Phase 1只做技术面、灰度上线策略、直接丢弃旧配置、回测不启用AI过滤。
