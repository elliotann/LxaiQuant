# SmcDynamicExitRule 出场规则

## 概述

基于多周期（15分钟、60分钟）SMC（Smart Money Concepts）结构的动态出场规则。支持：

- 动态止损（基于订单块、折价/溢价区）
- 动态止盈（多级目标价，根据趋势强度、信号质量、加仓次数自适应选择）
- 多订单项管理（超过3个未平项时，自动识别最佳盈利补仓项精准退出）

---

## 一、核心数据流

```
每根K线触发 getSignal(index, tradingRecord)
    │
    ├─ 0. 新K线或新时间 → updateDynamicLevels() 重新计算止损止盈
    │       │
    │       ├─ 取15m/60m SMC缓存结果（60s过期）
    │       ├─ 计算趋势类型（trendType）
    │       ├─ 计算信号质量评分（qualityScore）
    │       ├─ 1. 基础止损（折价区底部/溢价区顶部）
    │       ├─ 2. 订单块测试后调整止损
    │       ├─ 3. 低质量信号收紧止损
    │       ├─ 4. 初始止损偏移
    │       ├─ 5. 构建目标价列表 → 选最近目标
    │       └─ 6. 加仓≥3次且趋势不强时收紧目标
    │
    ├─ 1. 多订单项独立退出（openOrderItemCount > 3）
    │       └─ 整体亏损且有多个未平项时 → 遍历非头仓，找盈利≥0.5%且触价的最佳项 → ORDER_ITEM_TAKE_PROFIT
    │
    ├─ 2. 止损检查
    │       └─ K线穿破 currentStopPrice → ORDER_BLOCK
    │
    ├─ 3. 主动止盈检查（activeTakeProfit 启用时走此）
    │       └─ 三级对立OB检测（15m → 1h → higher）→ ACTIVE_TAKE_PROFIT_OB15M / OB1H / HIGHER
    │
    └─ 4. 常规止盈检查（activeTakeProfit 禁用时走此）
            └─ K线达到 currentTargetPrice → PROFIT_TARGET
```

---

## 二、止损逻辑

### 2.1 基础止损（calculateBaseStop）

| 方向 | BUY（做多） | SELL（做空） |
|------|------------|-------------|
| 止损位 | `折价区底部 × (1 - stopLossOffset)` | `溢价区顶部 × (1 + stopLossOffset)` |
| 兜底 | `trailingLow` | `trailingHigh` |
| 说明 | 折价区底部未定义时用trailingLow兜底；必须低于入场均价 | 溢价区顶部未定义时用trailingHigh兜底；必须高于入场均价 |

### 2.2 订单块测试后调整止损（adjustStopForOrderBlockTest）

入场价落在订单块区间内时，认为该订单块已被测试（`orderBlockTested`缓存命中），将止损收紧到`入场均价 × (1 ∓ stopLossOffset × 0.5)`。

### 2.3 低质量信号收紧止损（tightenStopByQuality）

当 qualityScore < 0.5 时：

| 方向 | BUY | SELL |
|------|-----|------|
| 找最近需求区上沿（high < avgEntry） | ✅ | - |
| 找最近供应区下沿（low > avgEntry） | - | ✅ |
| 动作 | 若找到且高于当前止损，则替换 | 若找到且低于当前止损，则替换 |

### 2.4 初始止损偏移（applyInitialStopOffset）

启用 `initialStopOffsetEnabled` 时：

| 模式 | 偏移量 | BUY | SELL |
|------|--------|-----|------|
| PERCENT | `base × (initialStopOffsetPercent / 100)` | `base - 偏移量` | `base + 偏移量` |
| POINTS | `initialStopOffsetPoints` | `base - 偏移量` | `base + 偏移量` |

base 取值为：最近订单块的另一侧价 → 若无则用 trailingLow/trailingHigh。

---

## 三、止盈逻辑

### 3.1 目标等级选择（selectTargetLevel）

根据趋势强度、加仓次数、信号质量自动选择止盈挡位：

| qualityScore | 趋势 | 加仓次数 | 目标等级 |
|-------------|------|---------|---------|
| > 1.5 | 强趋势 | < 2 | ULTRA_FAR（最远） |
| > 1.5 | 强趋势 | ≥ 2 | FAR（远） |
| > 1.5 | 普通 | 任意 | MID（中） |
| < 0.5 | 任意 | 任意 | NEAR（近） |
| 其他 | 强趋势 | < 2 | ULTRA_FAR |
| 其他 | 强趋势 | ≥ 2 | FAR |
| 其他 | 回调趋势 | 任意 | MID |
| 其他 | 其他 | 任意 | NEAR |

### 3.2 目标价构建（buildTargetList）

根据选定的目标等级，从以下来源收集候选目标价（去重、过滤、按距离排序）：

| 来源 | 条件 | BUY候选 | SELL候选 |
|------|------|---------|---------|
| 60m订单块 | FAR及以上 | 反向订单块低点、中点（>avgEntry） | 反向订单块高点、中点（<avgEntry） |
| 15m订单块 | MID及以上 | 同上 | 同上 |

> **注意**：`addZoneTargets()`（折溢价区目标价）已在代码中**被注释掉**（`SmcDynamicExitRule.java` 第637行 `//addZoneTargets(...)`），折溢价区不再参与候选目标列表。

最终选 **距入场均价最近** 的目标作为 `currentTargetPrice`。

### 3.3 加仓频繁时收紧目标（tightenTargetForFrequentAdds）

条件：`addPosCount >= 3` 且 非强趋势时，从反向订单块中取最近的一个作为唯一目标。

### 3.4 目标与入场价的最小间距

- BUY：`target ≥ avgEntry × (1 + 0.002)`
- SELL：`target ≤ avgEntry × (1 - 0.002)`

不满足间距的目标被过滤。

---

## 四、多订单项独立退出

当 `openOrderItemCount > 3` 且**整体亏损**（BUY时当前价<均价，SELL时当前价>均价）时：

1. 遍历所有**非头仓**的未平订单项
2. 计算每项的浮动盈亏百分比（基于各自的入场价）
3. 筛选已盈利 ≥ 0.5% 且**已触价**（K线已达到 `入场价 × 1.005/0.995`）的项
4. 选其中**盈利百分比最高**的订单项 → 生成 `ORDER_ITEM_TAKE_PROFIT`

该逻辑的目的是：整体亏损时，如果有补仓项已经盈利，先把这个盈利的项退出来，降低整体风险。

---

## 五、趋势类型评估

基于 SmcTrendUtils.identifyTrendType，合并60m和15m的摆动趋势+内部趋势：

| 趋势类型 | 含义 |
|---------|------|
| STRONG_BULLISH | 强多头 |
| STRONG_BEARISH | 强空头 |
| BULLISH_PULLBACK | 多头回调 |
| BEARISH_PULLBACK | 空头回调 |
| POTENTIAL_BOTTOM | 潜在底部 |
| POTENTIAL_TOP | 潜在顶部 |
| RANGING | 震荡 |

---

## 六、信号质量评分

### 6.1 趋势分

| 趋势 | BUY得分 | SELL得分 |
|------|--------|---------|
| STRONG_BULLISH | 2.0 | 0 |
| STRONG_BEARISH | 0 | 2.0 |
| BULLISH_PULLBACK | 0.5 | 0 |
| BEARISH_PULLBACK | 0 | 0.5 |
| POTENTIAL_BOTTOM | 1.0 | 0 |
| POTENTIAL_TOP | 0 | 1.0 |
| 其他 | 0 | 0 |

### 6.2 位置分

基于入场价与15m订单块的位置关系：

| 位置 | BUY得分 | SELL得分 |
|------|--------|---------|
| 在订单块内部 | 1.0 ~ 2.0 | 1.0 ~ 2.0 |
| 在订单块下方 | 0 ~ 1.0 | -0.5 ~ 0 |
| 在订单块上方 | -0.5 ~ 0 | 0 ~ 1.0 |

### 6.3 总分

`qualityScore = 趋势分 + max(位置分, 0)`

- **> 1.5**：高质量信号，追求更远目标
- **0.5 ~ 1.5**：普通质量，按原逻辑
- **< 0.5**：低质量信号，收紧止损+只取近目标

---

## 七、可配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| useStructureBreak | boolean | true | 是否使用结构突破出场（字段已声明但 `getSignal()` 中从未检查，实际不生效） |
| usePremiumDiscountExit | boolean | true | 是否使用折溢价区出场（StrategyFactory 中强置为 false，且 DTO 无配置入口） |
| useTargets | boolean | true | 是否使用目标价出场 |
| stopLossOffset | double | 0.005 | 止损偏移量(0.5%) |
| initialStopOffsetEnabled | boolean | false | 是否启用初始止损偏移 |
| initialStopOffsetPercent | double | 0.0 | 初始止损偏移百分比 |
| initialStopOffsetPoints | double | 0.0 | 初始止损偏移点数 |
| targetPeriod | String | "15" | 目标价周期(15/60) |
| stopLossPeriod | String | "15" | 止损周期(15/60) |
| structureBreakPeriod | String | "15" | 结构突破周期(15/60) |
| smcRangeThresholdPercent | double | 2 | SMC范围阈值 |
| primaryTargetMaxDistancePercent | double | 0.015 | 主目标最大距离 |
| MIN_TARGET_SPACE_RATIO | double | 0.002 | 最小目标间距 |

---

## 八、出场类型对照

| ExitType | 触发条件 | 平仓方式 |
|----------|---------|---------|
| PROFIT_TARGET | K线达到 currentTargetPrice | 分批退出（closeOrderByVolume） |
| ORDER_BLOCK | K线突破 currentStopPrice | 分批退出（closeOrderByVolume） |
| ORDER_ITEM_TAKE_PROFIT | 多订单项模式，选中最佳盈利补仓项 | 精确量退出（closeOrderByVolumeExact） |

---

## 九、前端配置方式

**入口**：策略列表 → 编辑策略 → 支撑与压力出场设置

### 9.1 整体开关

| 配置项 | 前端标签 | 默认值 |
|--------|---------|--------|
| smcExitEnabled | 启用支撑/压力出场 | false |

---

### 9.2 主动止盈（ActiveTakeProfit）

到达订单块等关键结构时按比例平仓。

| 配置路径 | 前端标签 | 默认值 |
|---------|---------|--------|
| activeTakeProfit.enabled | 按订单块（总开关） | true |
| activeTakeProfit.ob15m.enabled | 到达15m对立订单块时平仓 | true |
| activeTakeProfit.ob15m.closePercent | 平仓比例 | 50% |
| activeTakeProfit.ob1h.enabled | 到达1h对立订单块时平仓 | true |
| activeTakeProfit.ob1h.closePercent | 平仓比例 | 100% |
| activeTakeProfit.higher.enabled | 到达更高时间框架时平仓 | false |
| activeTakeProfit.higher.period | 周期（4小时/1天） | 240(4h) |
| activeTakeProfit.higher.closePercent | 平仓比例 | 100% |

---

### 9.3 被动离场（PassiveExit）

结构破坏时无条件全平。

| 配置路径 | 前端标签 | 默认值 |
|---------|---------|--------|
| passiveExit.enabled | 结构破坏（总开关） | true |
| passiveExit.reverseChoch | 出现反向CHOCH时立即平仓全部 | true |
| passiveExit.reverseBos | 出现反向BOS时立即平仓全部 | false |

---

### 9.4 移动止损（TrailingStop）

| 配置路径 | 前端标签 | 默认值 |
|---------|---------|--------|
| trailingStop.enabled | 启用（总开关） | true |
| trailingStop.moveToBreakeven.enabled | 盈利达到风险倍数后移动止损至成本价 | true |
| trailingStop.moveToBreakeven.triggerR | 触发倍数 | 1.5 倍风险 |
| trailingStop.trackStructure.enabled | 跟踪结构低点/高点移动止损 | true |
| trailingStop.trackStructure.period | 跟踪周期 | 15分钟 |
| trailingStop.trackStructure.point | 跟踪点类型 | internal（内部结构点） |

**跟踪点类型说明**：
- **swing**（摆动点）：跟踪更大级别的摆动高/低点，止损较宽松
- **internal**（内部结构点）：跟踪内部结构高/低点，止损较紧凑

---

### 9.5 初始止损偏移（InitialStopOffset）

在顺势订单块外侧增加偏移作为止损，避免毛刺扫损。

| 配置路径 | 前端标签 | 默认值 |
|---------|---------|--------|
| initialStopOffset.enabled | 启用（总开关） | true |
| initialStopOffset.mode | 方式（百分比/固定点数） | points |
| initialStopOffset.percent | 百分比值 | 0.05% |
| initialStopOffset.points | 固定点数 | 0.01 |
| initialStopOffset.stopLossObSameAsTarget | 止损订单块与止盈目标周期相同 | true |
| initialStopOffset.fixed15mOrderBlock | 固定使用15分钟订单块 | false |

---

### 9.6 参考时间框架（Reference）

| 配置路径 | 前端标签 | 默认值 |
|---------|---------|--------|
| reference.stopStructurePeriod | 止损结构周期 | 15分钟 |
| reference.targetPeriod | 止盈目标周期 | 60分钟(1h) |

**影响说明**：
- **止损结构周期** → 被动离场、移动止损（跟踪结构点）使用的时间周期
- **止盈目标周期** → 主动止盈（1h/更高）、初始止损偏移（若勾选"止损订单块与止盈相同"）使用的时间周期
- 初始止损偏移的订单块来源可独立选择（与止盈周期相同 或 固定15分钟）

---

### 9.7 默认配置（JSON结构）

```json
{
  "smcExitEnabled": false,
  "smcExit": {
    "activeTakeProfit": {
      "enabled": true,
      "ob15m": { "enabled": true, "closePercent": 50 },
      "ob1h": { "enabled": true, "closePercent": 100 },
      "higher": { "enabled": false, "period": "240", "closePercent": 100 }
    },
    "passiveExit": {
      "enabled": true,
      "reverseChoch": true,
      "reverseBos": false
    },
    "trailingStop": {
      "enabled": true,
      "moveToBreakeven": { "enabled": true, "triggerR": 1.5 },
      "trackStructure": { "enabled": true, "period": "15", "point": "internal" }
    },
    "initialStopOffset": {
      "enabled": true,
      "mode": "points",
      "percent": 0.05,
      "points": 0.01,
      "stopLossObSameAsTarget": true,
      "fixed15mOrderBlock": false
    },
    "reference": {
      "stopStructurePeriod": "15",
      "targetPeriod": "60"
    }
  }
}
```

---

## 十、实现状态总览（待逐步完善）

### 10.1 整体架构

```
前端配置 (RiskControlForm.vue → EditStrategy.vue)
    ↓ JSON 保存到 DB
StrategyFactory.java 读取 → configureSmcDynamicRule() + addSmcTrailingStopRules()
    ↓
SmcDynamicExitRule  (止损 + 止盈主规则)
SmcTrailingStopRule (移动止损辅助规则，独立执行)
```

---

### 10.2 已实现 ✅

#### SmcDynamicExitRule 已实现

| 功能 | 章节 | 说明 |
|------|:----:|------|
| 基础止损（折价区底部/溢价区顶部） | 二 | `calculateBaseStop()` |
| 订单块测试后调整止损（收紧） | 二 | `adjustStopForOrderBlockTest()` |
| 低质量信号收紧止损（qualityScore<0.5） | 二 | `tightenStopByQuality()` |
| 初始止损偏移（百分比/点数模式） | 二 | `applyInitialStopOffset()`，仅支持 percent/points 偏移量计算，配置走的 `initialStopOffset.mode` + `percent/points` |
| 四档目标等级自适应选择 | 三 | `selectTargetLevel()`，按 qualityScore+趋势+加仓次数 |
| 多周期订单块构建目标价（15m/60m） | 三 | `buildTargetList()` → `addTargetsFromPeriod()`，从 15m 和 60m SMC 结果提取 |
| 加仓≥3次收紧目标 | 三 | `tightenTargetForFrequentAdds()`，含标准关键点位对比（取更易触发的目标） |
| 多订单项独立退出（>3未平项、整体亏损时选最佳盈利补仓项） | 四 | `getSignal()` 中的 `checkOrderItemTakeProfit` 逻辑 |
| 趋势类型评估 | 五 | `SmcTrendUtils.identifyTrendType()` |
| 信号质量评分（趋势分+位置分） | 六 | `evaluateQualityScore()` |
| 止损结构周期/止盈目标周期配置 | 九 | `stopLossPeriod` / `targetPeriod` 字段已读取使用 |

#### SmcTrailingStopRule 已实现

| 功能 | 说明 |
|------|------|
| 基于 R 值的保本逻辑（1.5R 归成本、2.0R 收紧） | **硬编码** 1.5R/2.0R，非前端可配置的 `triggerR` |
| AUTO/MANUAL 模式切换 | MANUAL 模式按齿轮挡位（CONSERVATIVE/MODERATE/AGGRESSIVE）运行，AUTO 模式为兜底行为 |
| 加仓后重新调整跟踪参数 | 在 `SmcTrailingStopRule.getSignal()` 中处理 |

---

### 10.3 未实现 ❌

| # | 功能 | 所属规则 | 问题描述 | 优先级 |
|:-:|------|---------|---------|:------:|
| 1 | **主动止盈：按比例分批平仓**（15m/1h/Higher 独立开关+百分比） | SmcDynamicExitRule | 前端已配置 `ob15m.closePercent(50%)`、`ob1h.closePercent(100%)`、`higher.closePercent(100%)`，但后端仅计算单目标价 `currentTargetPrice`，到价后统一调用 `closeOrderByVolume()` 平仓。**未实现**：按不同周期订单块分别计算目标价 + 按各自百分比分批平仓。需将 `getSignal()` 中的止盈检查改造为多目标分级检查 | ⭐ |
| 2 | **主动止盈：higher 周期无 SMC 数据加载** | SmcDynamicExitRule | 前端可配置 `higher.period` 为 240(4h) 或 1440(1d)，但后端在 `buildTargetList()` 中仅从 15m 和 60m 周期的 SMC 缓存提取订单块，**从未加载** 4h/1d 的 SMC 数据。即使实现分级平仓，higher 周期也无法获得有效目标价 | ⭐ |
| 3 | **被动离场：结构破坏（reverseChoch/reverseBos）** | SmcDynamicExitRule | `useStructureBreak` 字段在类中声明（第59行），`StrategyFactory` 中已通过 `rule.setUseStructureBreak(smc.getPassiveExit().isEnabled())` 赋值（第357行），但 `getSignal()` 中**从未检查**该字段，结构破坏逻辑完全缺失 | ⭐ |
| 4 | **移动止损：保本 triggerR 可配置** | SmcTrailingStopRule | 前端配置 `moveToBreakeven.enabled` + `moveToBreakeven.triggerR(1.5)`，但 `StrategyFactory.addSmcTrailingStopRules()` 中**未读取**该配置传入 `SmcTrailingStopRule`。当前规则内部硬编码 1.5R/2.0R 两个挡位，`triggerR` 参数被忽略 | ⭐⭐ |
| 5 | **移动止损：跟踪结构点（trackStructure）** | SmcTrailingStopRule | 前端配置 `trackStructure.enabled` + `period` + `point`（swing/internal），但 `StrategyFactory.addSmcTrailingStopRules()` 中**未读取**该配置。`SmcTrailingStopRule` 内部也无基于结构性高低点推损的逻辑 | ⭐⭐ |
| 6 | **移动止损：AUTO 模式缺少自适应齿轮逻辑** | SmcTrailingStopRule | `trailingStop.mode` 设为 AUTO 时，齿轮挡位配置未生效，仅退化为兜底行为。前端配置的 `gear(CONSERVATIVE/MODERATE/AGGRESSIVE)` 仅 MANUAL 模式使用 | ⭐⭐ |
| 7 | **折溢价区出场（usePremiumDiscountExit）被强置关闭** | SmcDynamicExitRule | `configureSmcDynamicRule()` 中写死 `rule.setUsePremiumDiscountExit(false)`（第358行），DTO 中无对应配置入口，且 `addZoneTargets()` 在 `buildTargetList()` 中被注释（第637行）。折溢价区的出场和止盈均已无效 | ⭐⭐⭐ |
| 8 | **初始止损偏移：stopLossObSameAsTarget / fixed15mOrderBlock** | SmcDynamicExitRule | DTO 中已声明 `stopLossObSameAsTarget`、`fixed15mOrderBlock` 字段，但 `StrategyFactory.configureSmcDynamicRule()` 中**未读取**，`SmcDynamicExitRule.applyInitialStopOffset()` 中也未使用这两个参数控制订单块来源 | ⭐⭐⭐ |
| 9 | **折溢价区目标价（addZoneTargets）被注释** | SmcDynamicExitRule | `addZoneTargets()` 方法体代码完整，但 `buildTargetList()` 中调用被**注释掉**（第637行 `//addZoneTargets(...)`），折溢价区价格不再参与目标列表构建。该方法仅在 `addZoneTargets()` 方法体中保留，但从未被调用 | ⭐⭐⭐ |

---

### 10.4 各模块实现关系矩阵

| 前端配置项 | 后端 DTO 字段 | StrategyFactory 处理 | 规则层读取/使用 | 实现状态 |
|-----------|:-------------:|:-------------------:|:--------------:|:--------:|
| `activeTakeProfit.enabled` | `smcExit.activeTakeProfit.enabled` | `rule.setUseTargets(...)` | `useTargets` → 控制是否启用止盈检查 | ✅ 已实现 |
| `activeTakeProfit.ob15m.enabled/.closePercent` | `ObConfig` | 未读取 | 未使用 | ❌ 未实现 |
| `activeTakeProfit.ob1h.enabled/.closePercent` | `ObConfig` | 未读取 | 未使用 | ❌ 未实现 |
| `activeTakeProfit.higher.enabled/.period/.closePercent` | `HigherConfig` | 未读取 | 未使用+无对应SMC数据 | ❌ 未实现 |
| `passiveExit.enabled/.reverseChoch/.reverseBos` | `smcExit.passiveExit` | `rule.setUseStructureBreak(...)` | `useStructureBreak`声明但never checked | ❌ 未实现 |
| `trailingStop.enabled` | `smcExit.trailingStop` | `addSmcTrailingStopRules()` | 创建 `SmcTrailingStopRule` | ✅ 已实现 |
| `trailingStop.mode/.gear` | `mode/gear` | 读取并传入 SmcTrailingStopRule | AUTO模式未使用gear | ⚠️ 部分实现 |
| `trailingStop.moveToBreakeven.enabled/.triggerR` | `MoveToBreakevenConfig` | 未读取 | 未传入规则，硬编码1.5R/2.0R | ❌ 未实现 |
| `trailingStop.trackStructure.enabled/.period/.point` | `TrackStructureConfig` | 未读取 | 未使用 | ❌ 未实现 |
| `initialStopOffset.enabled/.mode/.percent/.points` | `InitialStopOffsetConfig` | 读取mode/percent/points | `applyInitialStopOffset()` | ✅ 已实现 |
| `initialStopOffset.stopLossObSameAsTarget` | `boolean` | 未读取 | 未使用 | ❌ 未实现 |
| `initialStopOffset.fixed15mOrderBlock` | `boolean` | 未读取 | 未使用 | ❌ 未实现 |
| `reference.stopStructurePeriod` | `String` | `rule.setStopLossPeriod()` | 止损结构周期 | ✅ 已实现 |
| `reference.targetPeriod` | `String` | `rule.setTargetPeriod()` | 止盈目标周期 | ✅ 已实现 |

> **备注**：前端默认配置与 DTO 默认值存在不一致（前端 `ob15m.enabled` 默认 true、`closePercent` 默认 50；DTO 中 `ObConfig` 的 `closePercent` 默认 100，`enabled` 默认 true），需注意后续实现时对齐。

---

## 十一、主动止盈实现方案

### 11.1 整体流程

```
getSignal() 中的分支选择（优先级从上到下）：

  ┌──────────────────────────────────────────────────┐
  │     updateDynamicLevels()：每根K线都执行，只为计算价位 │
  │                                                    │
  │  ├── 止损计算（不变）                                │
  │  ├── 目标价计算 buildTargetList()                    │
  │  │     ← 始终计算 currentTargetPrice                 │
  │  │       即使 activeTakeProfit 启用也要算             │
  │  │       因为下方 tightenForFrequentAdds 会用到       │
  │  ├── tightenForFrequentAdds (addPosCount ≥ 3)       │
  │  │     加仓过多时收紧 targetPrice                     │
  │  │    （此逻辑独立于主动止盈，始终运行）               │
  │  └── standardLevels 对比（取更易触发的目标）          │
  │                                                      │
  └──────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────┐
  │     getSignal()：检查是否要退出（优先级从上到下）    │
  │                                                    │
  │  #1  多订单项出场 (openOrderItemCount > 3)          │
  │      条件：有盈利的单个子订单，平掉最赚的那个         │
  │      返回：ExitType.ORDER_ITEM_TAKE_PROFIT          │
  │                                                    │
  │  #2  止损检查                                       │
  │      条件：价格触及 currentStopPrice（订单块止损）   │
  │      返回：ExitType.ORDER_BLOCK                     │
  │                                                    │
  │  #3  主动止盈检查（activeTakeProfit 启用时走此）     │
  │      条件：价格触及对立OB，且该级未触发过             │
  │      返回：ACTIVE_TAKE_PROFIT_OB15M / OB1H / HIGHER │
  │      说明：每级独立触发，按 closePercent 分批平仓     │
  │      注：此分支不读 currentTargetPrice               │
  │                                                    │
  │  #4  常规止盈（activeTakeProfit 禁用时走此）         │
  │      条件：价格触及 currentTargetPrice               │
  │      返回：ExitType.PROFIT_TARGET                   │
  │      注：currentTargetPrice 始终被计算，但仅在此使用 │
  └──────────────────────────────────────────────────┘

主动止盈分支内部：
  │
  ├─ ob15m 检查：是否已触发？价格触及15m对立OB？
  │    └─ 是 → 平原总仓×ob15m.closePercent，标记已触发
  │
  ├─ ob1h 检查：是否已触发？价格触及1h对立OB？
  │    └─ 是 → 平原总仓×ob1h.closePercent，标记已触发
  │
  └─ higher 检查：是否已触发？价格触及higher对立OB？
       └─ 是 → 平原总仓×higher.closePercent，标记已触发
```

### 11.2 分批计算方式

各级 `closePercent` 均基于 **原总仓量**（开仓时快照），独立计算：

```
示例配置：ob15m=30%, ob1h=50%, 4h=20%
触发顺序：ob15m → ob1h → 4h

         原总仓 100 张
              │
ob15m触发 ────┼──→ 平 100×30% = 30 张（closePartialPosition）
              │    剩余 70 张
              │
ob1h触发 ─────┼──→ 平 100×50% = 50 张（closePartialPosition）
              │    剩余 20 张
              │
4h触发 ───────┼──→ 平 100×20% = 20 张（closePartialPosition）
              │    剩余 0 张 → 仓位归零 ✅
```

关键规则：
- 每级按 **原总仓 × closePercent** 计算绝对数量，独立于当前剩余
- 若当前剩余 < 需平数量，则平剩余全部
- 各级独立触发，价格从低到高自然顺序（ob15m → ob1h → higher）
- 不会出现"后级因前级已平导致量不够"的问题（因为每级只平自己的固定比例，累计到100%刚好出完）

### 11.3 对立体订单块价格检测

```
对于 BUY（做多）：
  对立OB =  bias == -1（sell-side order blocks）
  触发条件 = highPrice >= oppositeObPrice（K线最高价触及或超过）

对于 SELL（做空）：
  对立OB =  bias == 1（buy-side order blocks）
  触发条件 = lowPrice <= oppositeObPrice（K线最低价触及或低于）
```

各级周期的对立OB来源：

| 级别 | 来源 |
|------|------|
| ob15m | 15m 周期的 SMC 缓存（getCachedSmcResult("15")）→ internalOrderBlocks 中 bias 相反的订单块 |
| ob1h | 60m 周期的 SMC 缓存（getCachedSmcResult("60")）→ 同上 |
| higher | 根据配置的 `period`（240/1440）取对应周期 SMC 缓存 |

**取最近的一个对立OB价格**作为该级的触发价。

### 11.4 防重复触发机制

在 `SmcDynamicExitRule` 中新增 **线程级状态缓存**：

```java
// 记录每个级别是否已触发主动止盈
private final Map<String, Boolean> activeTakeProfitFired = new HashMap<>();

// getSignal() 中：
if (activeTakeProfitEnabled) {
    // ob15m 检查
    if (checkObLevel("ob15m", ob15mConfig, highPrice, lowPrice, oppositeObPrice15m)) {
        activeTakeProfitFired.put("ob15m", true);
    }
    // ob1h 检查
    if (checkObLevel("ob1h", ob1hConfig, highPrice, lowPrice, oppositeObPrice1h)) {
        activeTakeProfitFired.put("ob1h", true);
    }
    // higher 检查
    if (checkObLevel("higher", higherConfig, highPrice, lowPrice, oppositeObPriceHigher)) {
        activeTakeProfitFired.put("higher", true);
    }
}
```

重置时机：
- 新开仓时调用 `reset()` 清空所有 `activeTakeProfitFired`
- 全平后 `reset()` 自动调用

### 11.5 与 PositionExitHandler 现有分批机制的衔接

主动止盈 **不复用** 现有的 BATCH_TAKE_PROFIT 分批逻辑，原因：

| 对比项 | 现有分批（BATCH_TAKE_PROFIT） | 主动止盈 |
|--------|------------------------------|---------|
| 触发方式 | 固定价格档位，按序触发 | 对立OB价格K线触及触发 |
| 批次索引 | Redis 维护 index 递增 | 不需要，每级独立标记 |
| 平仓量 | 按 levels[] 档位 + ratios[] 比例 | 按原总仓 × closePercent |
| 依赖 orderSn | 是，走 `getBatchIndex(type, orderSn)` | 不需要，Rule 内状态管理 |

主动止盈方案：
1. SmcDynamicExitRule 内维护 `activeTakeProfitFired` Map + `originalTotalVolume` 快照
2. 条件满足时，在 `getSignal()` 中计算本次平仓量，通过 `ExitSignal` 传递
3. ExitSignal 需扩展支持 `closePercent` 或 `closeVolume` 字段
4. PositionExitHandler 收到信号后直接调 `closePartialPosition(orderSn, volume)`，无需走 Redis 批次索引

```java
// ExitSignal 扩展（实际实现）
public class ExitSignal {
    private final Trade.TradeType direction;
    private final ExitType exitType;
    private final Double price;
    private final String orderItemSn;
    private final Integer closePercent;       // 新增：分批出场百分比（主动止盈用，0~100）

    // 主动止盈构造
    public ExitSignal(Trade.TradeType direction, ExitType exitType, Double price, Integer closePercent) {
        this(direction, exitType, price, null, closePercent);
    }

    public Integer getClosePercent() { return closePercent; }
}
```

### 11.6 需要加载 higher 周期的 SMC 数据

当前 `getCachedSmcResult()` 仅按需缓存，已有 15/60/240/1440 周期支持。
`updateDynamicLevels()` 第318-324行 已尝试加载240/1440的SMC缓存。
需确保在主动止盈启用时，higher 周期的 SMC 数据在缓存中可用。

### 11.7 改动清单

| # | 文件 | 改动内容 | 状态 |
|:-:|------|---------|:----:|
| 1 | `SmcDynamicExitRule.java` | 新增 `activeTakeProfitEnabled/atpOb15mPercent/atpOb1hPercent/atpHigherPercent/atpHigherPeriod` 配置字段 + setter；`activeTakeProfitFired` 状态 Map；`checkActiveTakeProfit()` 三级检测方法；`checkOppositeObHit()` 对立OB价格命中检测；`getSignal()` 中 `activeTakeProfitEnabled` 互斥分支 | ✅ |
| 2 | `ExitSignal.java` | 新增 `closePercent` (Integer, 0~100) 字段 + 配套构造器 | ✅ |
| 3 | `ExitType.java` | 新增 `ACTIVE_TAKE_PROFIT_OB15M/OB1H/HIGHER` 三个枚举值，加入 `shouldClearPosition()` 排除列表；新增 `isActiveTakeProfitExit()` 辅助方法 | ✅ |
| 4 | `PositionExitHandler.java` | 价格计算分支改用 `exitType.isActiveTakeProfitExit()` 统配三个 ATP 类型；数量计算增加比例逻辑（原总仓 × closePercent / 100）并 `setScale(0, ROUND_DOWN)` 取整；执行分支调用 `closePartialPosition()` 直接平仓 | ✅ |
| 5 | `StrategyFactory.java` | 读取 `smc.getActiveTakeProfit()` 配置，传递完整 ATP 参数到 `SmcDynamicExitRule` 的 setter | ✅ |
| 6 | `SmcDynamicExitRule` (构建目标) | higher 周期 SMC 数据由 `getCachedSmcResult()` 按需加载，无需额外改动 | ✅ |

### 11.8 数据库表现

主动止盈**不需新增任何数据表**，复用已有的 4 张表：

#### 11.8.1 涉及的表

| 表名 | 实体类 | 用途 |
|------|--------|------|
| `ai_trade_position` | `TradeOrder` | 仓位主表，记录总持仓状态，`volume` 留存原总仓量不变以作分批计算基数 |
| `ai_trade_entry` | `TradeOrderItem` | 入场明细，每条记录有 volume、closedVolume，分批时按比例摊减 closedVolume |
| `ai_trade_order_close` | `TradeOrderClose` | 平仓主表，每批触发新增一条记录（记录总平仓量） |
| `ai_trade_exit_item` | `TradeOrderCloseItem` | **平仓明细表**，记录本次平仓对每个 entry 子项的精确摊减明细 |

#### 11.8.2 每级触发的数据变化

以 `ob15m → ob1h → 4h` 三级触发为例，配置 `30%+50%+20%`，原总仓 100 张：

```
触发前：ai_trade_position: status=DEAL, volume=100
        ai_trade_entry: sum(volume - closedVolume) = 100 (可平总量)

★ ob15m 触发 (平 30 张)
  → closePartialPosition(orderSn, 30)
  → ai_trade_entry: closedVolume += 30（按各 entry 的可平量比例摊减）
  → ai_trade_order_close: INSERT { id=batch1, orderSn, closedVolume=30, closeMethod=AUTO, closeOrderType=ACTIVE_TAKE_PROFIT_OB15M }
  → ai_trade_exit_item: 每个摊减到的 entry 都 INSERT 一条明细
       { batchId=batch1, entrySn=xxx, closedVolume=15 }
       { batchId=batch1, entrySn=yyy, closedVolume=15 }
  → ai_trade_position: status 不变（仍 DEAL，剩余可平 70 张）

★ ob1h 触发 (平 50 张)
  → closePartialPosition(orderSn, 50)
  → ai_trade_entry: closedVolume += 50
  → ai_trade_order_close: INSERT { id=batch2, closedVolume=50, closeOrderType=ACTIVE_TAKE_PROFIT_OB1H }
  → ai_trade_exit_item: 每个 entry 摊减明细
  → ai_trade_position: status 不变（仍 DEAL，剩余可平 20 张）

★ 4h 触发 (平 20 张)
  → closePartialPosition(orderSn, 20)
  → ai_trade_entry: closedVolume += 20
  → ai_trade_order_close: INSERT { id=batch3, closedVolume=20, closeOrderType=ACTIVE_TAKE_PROFIT_HIGHER }
  → ai_trade_exit_item: 每个 entry 摊减明细
  → ai_trade_position: totalPosition = 100-30-50-20 = 0 → status = GAIN（全部出完）
```

#### 11.8.3 `closeOrderType` 标识

`closeOrderType` 字段可区分不同级别的主动止盈：

| 触发级别 | closeOrderType 值 |
|----------|------------------|
| 15m 对立OB | `ACTIVE_TAKE_PROFIT_OB15M` |
| 1h 对立OB | `ACTIVE_TAKE_PROFIT_OB1H` |
| higher 对立OB | `ACTIVE_TAKE_PROFIT_HIGHER` |

#### 11.8.4 原总仓量来源

通过 `calculateTotalPosition(positionId)` 或直接读 `ai_trade_position.volume` 获取开仓时的原总仓量。在匹配入场（matchEntry）后，`volume` 字段反映的是初始总仓位大小，后续 `closePartialPosition` 只减子项的 `closedVolume`，不改主表 `volume`。

```
ai_trade_position:
  position_id  |  volume  |  status
  P20250101    |  100     |  DEAL     ← 原总仓 100 张，全平前不变

ai_trade_entry:
  id  |  order_sn      |  volume  |  closed_volume
  1   |  P20250101      |  50      |  50  (已全平)
  2   |  P20250101      |  50      |  0   (未动)

剩余可平 = (50-50) + (50-0) = 50 张

ai_trade_order_close:  ← 每次主动止盈触发 INSERT 一条记录
  id  |  order_sn    |  closed_volume  |  close_order_type
  1   |  P20250101   |  30             |  ACTIVE_TAKE_PROFIT_OB15M   ← ob15m
  2   |  P20250101   |  50             |  ACTIVE_TAKE_PROFIT_OB1H    ← ob1h
  3   |  P20250101   |  20             |  ACTIVE_TAKE_PROFIT_HIGHER  ← 4h

ai_trade_exit_item:  ← 每条平仓记录对每个 entry 的摊减明细
  batch_id  |  position_id  |  entry_sn  |  closed_volume
  1         |  P20250101    |  ENTRY_01  |  15    (30张中摊了15给entry1)
  1         |  P20250101    |  ENTRY_02  |  15    (30张中摊了15给entry2)
  2         |  P20250101    |  ENTRY_02  |  50    (剩下entry2剩50张，ob1h平50全部摊给它)
  3         |  P20250101    |  ENTRY_02  |  20    (4h触发时，entry2剩20张，全平)
```
