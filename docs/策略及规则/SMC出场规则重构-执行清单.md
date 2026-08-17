# SMC 出场规则重构 — 执行清单

> 基于 [SMC出场规则重构-SmcTrailingStopRule设计文档](./SMC出场规则重构-SmcTrailingStopRule.md) 拆解的可执行任务清单。
> 按阶段顺序执行，每项完成后勾选并验证。

---

## 总进度

- [x] **Phase 0：前置准备** — 熟悉现有代码
- [x] **Phase 1：新建 SmcTrailingStopRule** — 核心规则编写
- [x] **Phase 2：重构 SmcDynamicExitRule** — 移除 trailing stop 相关字段
- [x] **Phase 3：集成到 execStrategy** — 改造 DefaultDealStrategyTrade
- [x] **Phase 4：前端配置 UI** — 新增模式/挡位选择
- [x] **Phase 5：smcExitJson 配置扩展** — 后端 JSON 解析
- [x] **Phase 6：完整化 DirectionalTrailingStopRule** — 补齐 ta4j 规范的5项缺失
- [ ] **Phase 7：测试** — 单元+集成测试（7.3 回测已通过 ✅，7.4 编译已验证 ✅）

---

## Phase 0：前置准备工作

- [ ] **0.1 阅读 SmcDynamicExitRule 源码** ✅
  - [x] 确认 `useTrailingStop` / `trailingStopPercent` 字段位置（第 30-40 行）
  - [x] 确认 `getSignal()` 中无 trailing stop 触发逻辑（第 85-145 行仅有止损/止盈检查）
  - [x] 确认 `updateDynamicLevels()` 调用链路（第 150-200 行）

- [ ] **0.2 阅读 DirectionalTrailingStopRule 源码** ✅
  - [x] 理解其基于价格极值的百分比回撤模式（与 SMC 结构跟踪不同）

- [ ] **0.3 阅读 DefaultDealStrategyTrade.execStrategy SMC 相关代码** ✅
  - [x] 确认第 457-529 行中 SmcDynamicExitRule 的创建和配置
  - [x] 确认第 2770-2840 行 smcExitJson 的解析逻辑

- [ ] **0.4 阅读 CompositeState 枚举** ✅
  - [x] 确认 21 种枚举定义在 `ai-common` 模块

- [ ] **0.5 阅读 ExitType 枚举** ✅
  - [x] 确认现有 `TRAILING_STOP_LOSS` 类型可用
  - [x] 确认需新增 `SMC_TRAILING_STOP` 类型区分 SMC 结构跟踪止损

- [ ] **0.6 阅读 ta4j TrailingStopLossRule / TrailingStopGainRule 源码** ✅
  - [x] 确认 ta4j 规范：NaN/Null 防护、输入校验、Trace 日志、PriceModel 接口
  - [x] 确认入参单位差异：ta4j 传 5（5%），我们传 0.05
  - [x] 对比结果记录在 [设计文档第10章](./SMC出场规则重构-SmcTrailingStopRule.md#10-directionaltrailingstoprule-完整性补全)

---

## Phase 1：新建 SmcTrailingStopRule

> 目标：在 `ai-extension` 模块中新建独立规则，实现 SMC 结构跟踪移动止损

### 1.1 枚举和配置类

- [x] **1.1.0 在 ExitType 中注册 `SMC_TRAILING_STOP`**
  - 文件：`ai-extension/.../core/constants/ExitType.java`
  - 枚举值：`SMC_TRAILING_STOP("SMC结构跟踪止损")`
  - `shouldClearPosition()` 返回 true（清仓）
  - 与 `TRAILING_STOP_LOSS` 语义区分：后者是普通百分比移动止损，SMC_TRAILING_STOP 是 SMC 结构点跟踪止损

- [x] **1.1.1 新建 `TrailingMode` 枚举**（AUTO / MANUAL）
  - 文件：`ai-extension/.../core/rule/TrailingMode.java`
  - 包：`com.chain.ai.trade.extension.ta4j.core.rule`

- [x] **1.1.2 新建 `ManualGear` 枚举**（CONSERVATIVE / MODERATE / AGGRESSIVE）
  - 文件：`ai-extension/.../core/rule/ManualGear.java`
  - 包：`com.chain.ai.trade.extension.ta4j.core.rule`

- [x] **1.1.3 新建 `SmcTrailingStopConfig` 配置类**（或内嵌为内部类）
  - 含 `enabled`、`mode`、`manualGear`、`autoTrailingPercent`
  - 文件：`ai-extension/.../core/rule/SmcTrailingStopConfig.java`

### 1.2 核心规则类

- [x] **1.2.1 新建 `SmcTrailingStopRule` 骨架**
  - 实现 `DirectionalRule` 接口
  - 构造函数：接收 `Map<String, SmartMoneyConceptsIndicator>` + `TradeType` + `BarSeries`
  - 文件：`ai-extension/.../core/rule/SmcTrailingStopRule.java`

- [x] **1.2.2 实现 `autoSelectParams(CompositeState)` 方法**
  - 21 种状态 → `TrailingParams` 映射表
  - `TrailingParams` 内含 `enabled`、`period`、`pointType`、`offsetPercent`

- [x] **1.2.3 实现 `manualSelectParams(ManualGear)` 方法**
  - 三挡位 → `(周期, 点类型, 偏移量)` 映射

- [x] **1.2.4 实现 `getStructuralPoint(Result, PointType, TradeType)` 方法**
  - 摆动点：`result.getTrailingLow()` / `result.getTrailingHigh()`
  - 内部结构点：从 `result.getInternalOrderBlocks()` 中找最近同向订单块

- [x] **1.2.5 实现 `isPointValid(OrderBlock, double currentPrice, TradeType)` 方法**
  - 实体穿透判定（收盘价越过边界 → 失效）
  - 中度穿透判定（影线刺破 > 30% 块高 → 削弱标记）

- [x] **1.2.6 实现 `getSignal(int index, TradingRecord)` 核心逻辑**
  - 无持仓 → 重置状态，返回 null
  - 获取当前 CompositeState（或 TrendType 降级）
  - 选择跟踪参数（自动/手动）
  - 若复合状态不允许跟踪 → 返回 null
  - 获取结构点价格
  - 计算理论止损价 = 结构点 × (1 ± 偏移量)
  - 更新实际止损价（只向有利方向移动）
  - 当前价格触发止损 → 返回 `ExitSignal(direction, ExitType.SMC_TRAILING_STOP, stopPrice)`

- [x] **1.2.7 添加 SMC 指标缓存或复用 SmcDynamicExitRule 的缓存**
  - 复用静态 `smcCache` 或单独缓存

### 1.3 单元测试

- [ ] **1.3.1 测试手动模式三挡位映射**
  - CONSERVATIVE → 4H + 摆动点 + 0.3%
  - MODERATE → 1H + 内部点 + 0.2%
  - AGGRESSIVE → 15M + 内部点 + 0.1%

- [ ] **1.3.2 测试自动模式状态映射**
  - `STRONG_BULLISH_HEALTHY` → 15M + 摆动点 + 启用
  - `STRONG_BULLISH_CONFIRMED_PULLBACK` → 禁用
  - `RANGING_NO_DIRECTION` → 禁用

- [ ] **1.3.3 测试止损价只向有利方向移动**
  - 多头：连续两次 `updateStopPrice()`，第二次更高才更新，更低的不更新
  - 空头：连续两次 `updateStopPrice()`，第二次更低才更新，更高的不更新

- [ ] **1.3.4 测试结构点失效**
  - 实体穿透后 `isPointValid()` 返回 false
  - getSignal() 返回 null（停止跟踪）

---

## Phase 2：重构 SmcDynamicExitRule

> 目标：移除 trailing stop 相关字段和方法，精简类

- [x] **2.1 删除 `useTrailingStop` 字段**（第 20 行附近）

- [x] **2.2 删除 `trailingStopPercent` 字段**（第 21 行附近）

- [x] **2.3 删除 `setUseTrailingStop()` / `setTrailingStopPercent()` setter 方法**

- [x] **2.4 删除 `TrailingStopMode` 相关（如果有）**

- [x] **2.5 确认无其他代码在 `getSignal()` 中引用已删除字段**
  - 当前确认 `getSignal()` 中无 trailing stop 逻辑，直接删除安全

- [x] **2.6 保留 `tightenStopByQuality()`**（与信号质量相关，仍有用）

- [x] **2.7 编译验证**：`mvn -pl ai-extension compile -q`

---

## Phase 3：集成到 DefaultDealStrategyTrade.execStrategy

> 目标：在退出规则组合中同时添加 SmcDynamicExitRule 和 SmcTrailingStopRule

- [x] **3.1 在 `execStrategy` 中创建 `SmcTrailingStopRule` 实例**（第 457-529 行附近）
  ```java
  // 在 smcMap 不为空时，创建 SmcTrailingStopRule
  if (Boolean.TRUE.equals(config.smcUseTrailingStop)) {
      SmcTrailingStopRule longTrailing = new SmcTrailingStopRule(smcMap, TradeType.BUY, series);
      // 配置模式...
      exitRules.add(longTrailing);
  }
  ```

- [x] **3.2 解析 trailing stop 模式配置**
  - 从 `smcExitJson` 中读取 `trailingStop.mode` 和 `trailingStop.manualLevel`
  - 兼容旧配置：若 mode 不存在但 `smcUseTrailingStop = true`，默认使用自动模式

- [x] **3.3 移除 `SmcDynamicExitRule` 的 trailing stop 相关配置**
  - 删除 `smcExitLong.setUseTrailingStop()` 调用
  - 删除 `smcExitLong.setTrailingStopPercent()` 调用
  - 确认 `smcExitShort` 同理

- [x] **3.4 编译验证**：`mvn -pl ai-engine compile -q`

---

## Phase 4：前端配置 UI

> 目标：在 EditStrategy.vue 的 SMC 出场配置中增加模式选择和挡位选择

- [x] **4.1 在 SMC 出场规则配置区域增加"模式"单选组**
  - 选项：自动 / 手动
  - v-model 绑定到 `formData.smcTrailingStopMode`

- [x] **4.2 手动模式时显示"挡位"单选组**
  - 选项：保守 / 中等 / 激进
  - v-model 绑定到 `formData.smcTrailingStopGear`
  - 同时隐藏自动模式的提示信息

- [x] **4.3 自动模式时显示提示文字**
  - "根据市场状态自动调整跟踪周期和点类型"

- [x] **4.4 提交表单时包含新字段**
  ```typescript
  trailingStop: {
    enabled: formData.smcUseTrailingStop,
    mode: formData.smcTrailingStopMode,      // "AUTO" | "MANUAL"
    gear: formData.smcTrailingStopGear        // "CONSERVATIVE" | "MODERATE" | "AGGRESSIVE"
  }
  ```

---

## Phase 5：smcExitJson 配置解析扩展

> 目标：后端解析新的 trailingStop 配置结构

- [x] **5.1 在 `TradingStrategyConfig` 中增加字段**
  ```java
  private String smcTrailingStopMode;        // "auto" | "manual"
  private String smcTrailingStopGear;         // "conservative" | "moderate" | "aggressive"
  ```

- [x] **5.2 在 `DefaultDealStrategyTrade` 的配置解析中增加读取**（第 2770-2840 行）
  ```java
  // 从 smcExitJson 读取 trailingStop 子对象
  JSONObject trailing = smc.optJSONObject("trailingStop");
  if (trailing != null) {
      config.smcTrailingStopMode = trailing.optString("mode", "auto");
      config.smcTrailingStopGear = trailing.optString("manualLevel", "moderate");
  }
  ```

---

## Phase 6：完整化 DirectionalTrailingStopRule

> 目标：补齐 ta4j 规范的5项缺失，相关设计参见 [设计文档第10章](./SMC出场规则重构-SmcTrailingStopRule.md#10-directionaltrailingstoprule-完整性补全)

### 6.1 入参单位统一

- [x] **6.1.1 修改构造函数**：`lossPercentage` 改用百分比整数语义（传5表示5%），内部使用 `hundred()` 计算
- [x] **6.1.2 修改 StrategyFactory 调用方**：`config.trailingStopLossPercent * 100` 后传入
  - 文件：[StrategyFactory.java#L519-L524](file:///f:/project/lenzeto/ai-engine/src/main/java/com/chain/ai/trade/engine/strategy/StrategyFactory.java#L519-L524)
- [x] **6.1.3 编译验证**：`mvn -pl ai-extension,ai-engine compile -q`

### 6.2 NaN/Null 防护

- [x] **6.2.1 在 `getSignal()` 入口处增加 null/NaN 检查**
  - `Num.isNaNOrNull(entryPrice)` → return null
  - `Num.isNaNOrNull(currentPrice)` → return null
- [x] **6.2.2 在每个 `priceIndicator.getValue(i)` 后增加检查**
  - `Num.isNaNOrNull(value)` → continue/return null

### 6.3 输入校验

- [x] **6.3.1 构造函数增加 `Objects.requireNonNull(priceIndicator)`**
- [x] **6.3.2 校验 `lossPercentage` 不为 NaN 且 >= 0**
- [x] **6.3.3 校验 `barCount` 为正数**

### 6.4 Trace 日志

- [x] **6.4.1 添加 SLF4J Logger**（与项目现有风格一致）
- [x] **6.4.2 在极值更新点记录 trace 日志**：`极值=xxx，旧止损=xxx，新止损=xxx`
- [x] **6.4.3 在触发点记录 trace 日志**：`当前价=xxx，止损价=xxx，触发`

### 6.5 StopLossPriceModel 接口实现

- [x] **6.5.1 实现 `stopPrice()` 方法**（基于入场价+损失百分比的固定止损价）
- [x] **6.5.2 编译验证**：`mvn -pl ai-extension compile -q`

### 6.6 单元测试

- [ ] **6.6.1 测试 NaN/Null 输入不抛异常，返回 false/null**
- [ ] **6.6.2 测试非法入参抛 IllegalArgumentException**
- [ ] **6.6.3 测试入参单位转换后计算正确（传5表示5% trailing）**
- [ ] **6.6.4 测试止损价只向有利方向移动**

---

## Phase 7：测试

- [ ] **7.1 单元测试：SmcTrailingStopRule**
  - [ ] 手动模式三挡位映射正确
  - [ ] 自动模式 21 种状态映射正确
  - [ ] 止损价单向移动限制
  - [ ] 结构点失效逻辑
  - [ ] 无持仓时返回 null

- [ ] **7.2 集成测试：execStrategy**
  - [ ] 配置 `smcUseTrailingStop=true` 时，退出规则组合中包含 SmcTrailingStopRule
  - [ ] 配置 `smcUseTrailingStop=false` 时，退出规则组合中不包含 SmcTrailingStopRule
  - [ ] 两种规则通过 OrDirectionalRule 组合后不冲突

- [x] **7.3 回测验证**
  - [x] 使用已有策略参数跑回测，确认出场行为不变（auto 模式兼容旧配置）
  - [x] 手动模式三个挡位分别跑回测，确认行为差异

- [ ] **7.4 编译全量通过** ✅
  ```bash
  mvn -pl ai-extension,ai-engine,ai-quant clean compile -q
  ```

---

## 产出物清单

| # | 文件 | 操作 |
|---|------|------|
| 0 | `ai-extension/.../core/constants/ExitType.java` | 修改（新增 SMC_TRAILING_STOP 枚举） |
| 1 | `ai-extension/.../core/rule/TrailingMode.java` | 新建 |
| 2 | `ai-extension/.../core/rule/ManualGear.java` | 新建 |
| 3 | `ai-extension/.../core/rule/SmcTrailingStopConfig.java` | 新建 |
| 4 | `ai-extension/.../core/rule/SmcTrailingStopRule.java` | 新建 |
| 5 | `ai-extension/.../core/rule/SmcDynamicExitRule.java` | 修改（删除 trailing 字段） |
| 6 | `ai-engine/.../core/impl/DefaultDealStrategyTrade.java` | 修改（集成新规则） |
| 7 | `ai-engine/.../core/impl/DefaultDealStrategyTrade.java` (TradingStrategyConfig) | 修改（新增配置字段） |
| 8 | `ai-frontend-web/src/views/strategy/EditStrategy.vue` | 修改（新增UI配置） |
| 9 | `ai-extension/.../core/rule/DirectionalTrailingStopRule.java` | 修改（补齐5项ta4j规范） |
| 10 | `ai-engine/.../strategy/StrategyFactory.java` | 修改（统一入参单位） |
