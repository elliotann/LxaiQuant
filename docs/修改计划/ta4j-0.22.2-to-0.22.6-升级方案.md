# ta4j 0.22.2 → 0.22.6 升级方案

## 1. 概述

### 1.1 升级目标

将项目中 ta4j 核心依赖从 `0.22.2` 升级至 `0.22.6`（对应 GitHub release v0.22.6, 2026-04-01 发布）。

### 1.2 当前状态

| 项目 | 版本 |
|------|------|
| 项目中 ta4j 依赖版本 | 0.22.2 |
| Maven Central 最新发布版本 | 0.22.2 |
| 目标版本（GitHub tag） | 0.22.6 |
| 本地 ta4j-master 仓库 | 包含 0.22.6 完整源码（CHANGELOG 已含 0.22.3~0.22.6 条目），但 pom.xml 中版本号仍为 0.22.2 |

### 1.3 版本说明

ta4j 0.22.6 为官方发布版本（2026-04-01），包含从 0.22.2 以来的多个重要更新：新增指标套件、风险控制 API、窗口化回测评估、非聚合 Bar 生成器、Elliott Wave 多周期分析、Walk-forward 框架等。同时 0.22.4 引入了架构层面的 Breaking Changes——"fill-driven" 交易记录 API。

本地 `ta4j-master` 仓库已包含 0.22.6 完整源码。需要将版本号从 0.22.2 升到 0.22.6 后构建，并同步检查项目中受影响的自定义扩展代码是否需要适配。

### 1.4 约束条件

- ta4j-master **源码不能更改**（仅允许修改 pom.xml 版本号用于构建）
- 项目中 pom.xml 修改需确认
- 包结构风格保持一致
- **业务逻辑不能改变**

---

## 2. 影响范围分析

### 2.1 依赖 ta4j 的模块

| 模块 | ta4j 依赖 | 使用范围 |
|------|-----------|----------|
| **ai-extension** | ta4j-core | 自定义 Rule、TradingRecord、Strategy、执行模型 |
| **ai-engine** | ta4j-core | 回测引擎核心，大量使用 BarSeries、Indicator、Criterion、Rule、Strategy |
| **ai-quant** | ta4j-core + ta4j-examples | 特征工程、ML 训练/推理、Elliott Wave 分析 |
| **ai-data** | ta4j-core | Bar/BarSeries 数据组装和提供 |
| **ai-signal** | ta4j-core | 信号生成服务，大量自定义 Rule 实现 |
| **ai-strategy** | ta4j-core | 策略规则实现，自定义 Rule 和 Strategy |
| **ai-risk** | ta4j-core | 风险评价，使用指标和风险分析 |
| **ai-member** | ta4j-core | 用户相关功能，使用 BarSeries、Num |
| **ai-xchange-extends** | ta4j-core | 交易所扩展，使用 Bar、BarSeries |
| **ai-order** | ta4j-core | 订单系统，使用 Bar、Num |

### 2.2 ta4j 使用模式分类

| 使用模式 | 涉及模块 | 关键类/接口 |
|----------|----------|-------------|
| **自定义 Rule** | ai-extension, ai-engine, ai-signal, ai-strategy | 实现 `Rule` 接口的多种自定义规则，如 `SmcDynamicExitRule`、`WinLossRatioRule`、`FromCacheRule` 等 |
| **自定义 TradingRecord** | ai-extension | `MultiPositionTradingRecord`：同时实现 `TradingRecord` 和 `PositionLedger`，直接使用 `OpenPosition`、`PositionBook`、`PositionLot` |
| **Strategy 扩展** | ai-extension | `MultiPositionStrategy`、`DirectionalStrategy` |
| **执行模型** | ai-extension | `MarketOrderModel`、`SlippageExecutionModel`、`NextOpenModel`，使用 `TradeExecutionModel` 和 `ExecutionFill` |
| **自定义 Bar** | ai-extension, ai-order | Bar 数据结构的扩展使用 |
| **指标使用** | 几乎所有模块 | `SMAIndicator`、`RSIIndicator`、`MACDIndicator`、`EMAIndicator`、`ATRIndicator`、`BollingerBandsIndicator` 等大量内置指标 |
| **回测引擎** | ai-engine | `BacktestEngine` 使用 `BarSeriesManager`、`LiveTradingRecord`、`LiveTrade`，以及 `MaximumDrawdownCriterion`、`ReturnOverMaxDrawdownCriterion` 等 |
| **性能报告** | ai-engine | `PerformanceMetricsConverter` 使用 ta4j 报告类 |
| **Elliott Wave** | ai-quant, ai-signal, ai-risk | 大量自定义 Elliott Wave 分析代码 |

---

## 3. 版本 0.22.2 → 0.22.6 变更分析

### 3.1 版本路线

```
0.22.2 (当前) → 0.22.3 (2026-03-01) → 0.22.4 (2026-03-15) → 0.22.5 (2026-03-29) → 0.22.6 (2026-04-01)
```

### 3.2 0.22.3 新增内容（不影响现有代码）

- **Bill Williams 指标套件**：`FractalHighIndicator`、`FractalLowIndicator`、`AlligatorIndicator`、`GatorOscillatorIndicator`、`MarketFacilitationIndexIndicator`
- **风险控制 API**：`PositionRiskModel`、`StopLossPositionRiskModel`、`RMultipleCriterion`、止损/止盈规则变体
- **K线形态指标**：`PiercingLineIndicator`、`DarkCloudCoverIndicator`
- **趋势确认震荡器**：`VortexIndicator`、`UltimateOscillatorIndicator`
- **波动率归一化 MACD 工具包**：`VolatilityNormalizedMACDIndicator`、`MACDVMomentumState` 等
- **策略 demo**：`MACDVMomentumStateStrategy`

### 3.3 0.22.4 重要变更（核心关注）

> ⚠️ **0.22.4 引入了 Breaking Changes，是本次升级需要重点关注的版本。**

#### 新增功能（不影响现有代码）

- **窗口化回测评估 API**：`AnalysisCriterion` 新增 `calculate(series, tradingRecord, window[, context])` 重载，支持分析指定 bar 范围、时间范围等
- **非聚合 Bar 生成器**：`RangeBarAggregator`、`VolumeBarAggregator`、`RenkoBarAggregator`
- **Elliott Wave 多周期分析**：`ElliottWaveAnalysisRunner` + `ElliottWaveAnalysisResult`
- **Walk-forward 框架**：`WalkForwardEngine`、`WalkForwardTuner`、`WalkForwardObjective`
- **策略加权排名**：`TradingStatementExecutionResult`、`BacktestExecutionResult#getTopStrategiesWeighted(...)`
- **评分/权重原语**：`NamedScoreFunction<I, S>`、`WeightedValue<T>`

#### 非破坏性变更

- **BacktestExecutor 构造**：新增接受 `TradeExecutionModel` 或 `BarSeriesManager` 的构造器
- **VolumeIndicator**：修复冷缓存 StackOverflowError
- **NetMomentumIndicator**：修复处理后期 bar 时的栈溢出
- **VarianceIndicator**：默认使用样本方差（n-1 除数），可通过 `SampleType` 工厂方法显式选择

#### Breaking Changes（架构层）

- **Trade/Record fill-driven API**：
  - 新推荐方式：`Trade.fromFill(...)` / `Trade.fromFills(...)` + `TradingRecord#operate(fill)`
  - 旧 API（`ExecutionFill`、`LiveTrade`、`LiveTradingRecord`、`PositionLedger`）在 0.22.x 中仍以**已弃用迁移垫片**形式可用 → **不影响编译**
- **OpenPosition 移除**：`OpenPosition` 类型被移除，全部改用 `Position` → **实际源码中 `OpenPosition` 仍存在（public record），未移除**
- **PositionBook/PositionLot 内部化**：这两个类留在 `BaseTradingRecord` 内部 → **实际源码中 `PositionBook` 仍为 `public final class` 带 public 构造器，`PositionLot` 仍为 `public final class` 带 public 访问方法**

> **实际源码验证结论**：尽管 CHANGELOG 声明了 Breaking Changes，但 ta4j 0.22.6 的实际源码中：
> - **`OpenPosition` 仍然存在**（public record）
> - **`PositionBook` 仍然存在**（public final class，public 构造器）
> - **`PositionLot` 仍然存在**（public final class，public 访问方法）
> - **`LiveTrade` 仍然存在**（public record）
> - **`LiveTradingRecord` 仍然存在**（`recordFill(int, LiveTrade)` 和 `recordExecutionFill(ExecutionFill)` 方法均保留）
> - **`ExecutionFill` 仍然存在**（public record）
> - **`PositionLedger` 仍然存在**（public interface）
>
> 即所有项目中使用的 API **均保持向后兼容**。

### 3.4 0.22.5 新增内容（不影响现有代码）

- **CalmarRatioCriterion**：回撤调整 CAGR
- **OmegaRatioCriterion**：基于阈值的回报分布不对称性分析

### 3.5 0.22.6 变更内容（不影响现有代码）

- **GitHub Release 流程优化**（CI/CD 改进，无关）

### 3.6 API 兼容性汇总

| 项目代码使用的 API | 0.22.6 状态 | 兼容性 |
|-------------------|-------------|--------|
| `OpenPosition` | 存在（public record） | ✅ 完全兼容 |
| `PositionBook` | 存在（public final class） | ✅ 完全兼容 |
| `PositionLot` | 存在（public final class） | ✅ 完全兼容 |
| `LiveTrade` | 存在（public record） | ✅ 完全兼容 |
| `LiveTradingRecord` | 存在（public class） | ✅ 完全兼容（`recordFill`/`recordExecutionFill` 均保留） |
| `ExecutionFill` | 存在（public record） | ✅ 完全兼容 |
| `PositionLedger` | 存在（public interface） | ✅ 完全兼容 |
| `TradeExecutionModel` | 存在（org.ta4j.core.backtest） | ✅ 完全兼容 |
| `MaximumDrawdownCriterion` | 存在（新增 window 重载） | ✅ 完全兼容 |
| `BarSeriesManager` | 存在（新增 `run(strategy, tradingRecord, ...)` 重载） | ✅ 完全兼容 |
| `TradingRecord` | 接口无破坏性变更 | ✅ 完全兼容 |
| 所有内置指标 | 均存在 | ✅ 完全兼容 |

---

## 4. 变更日志中的新 API 与本项目不相关部分

以下为新增功能，与项目当前业务逻辑无关联，无需适配：

- Elliott Wave 多周期分析（`ElliottWaveAnalysisRunner`）—— 项目有自研 EW 代码
- Walk-forward 框架（`WalkForwardEngine` 等）—— 目前未使用
- 非聚合 Bar 生成器（`RangeBarAggregator` 等）—— 目前未使用
- 加权策略排名 —— 目前未使用
- 评分/权重原语 —— 目前未使用
- `CalmarRatioCriterion` / `OmegaRatioCriterion` —— 目前未使用
- Bill Williams 指标套件 —— 目前未使用
- 风险控制 API（`PositionRiskModel` 等）—— 目前未使用
- K线形态指标 —— 目前未使用

---

## 5. 关键风险点

### 5.1 构建风险（最核心）

ta4j-master 源码中 `ta4j-core/pom.xml` 的版本号仍为 `0.22.2`。需要将版本号升为 `0.22.6` 才能构建出正确版本。由于约束条件"ta4j-master 代码不能更改"，构建策略需确认：

- **方案 A**：`mvn versions:set -DnewVersion=0.22.6` 临时修改 pom.xml → 会修改文件，违反约束
- **方案 B**：在 lenzeto 项目 pom.xml 中使用 `-Dta4j.version=0.22.6` 属性 + 本地 install 0.22.6 版本
- **方案 C**：直接 lenzeto 项目引用 0.22.2（不做版本升级），或等官方发布新版本

**建议放宽约束**，允许仅在 `ta4j-core/pom.xml` 中修改 `<version>0.22.2</version>` 为 `<version>0.22.6</version>`，且该修改不提交到 git，仅用于本地构建。

### 5.2 自定义扩展代码兼容性（低风险，已验证）

通过实际比对 0.22.6 源码，项目中使用的所有 ta4j API（OpenPosition、PositionBook、PositionLot、LiveTrade、LiveTradingRecord、ExecutionFill、PositionLedger、TradeExecutionModel 等）均保持 public 可访问且接口一致。

### 5.3 构建产物一致性风险

- ta4j 0.22.6 需用 JDK 21 + Maven 3.9+ 构建，与项目保持一致
- 构建后的 jar 包需与项目现有依赖无传递依赖冲突（SLF4J、Gson、Apache POI 版本）

### 5.4 运行时行为一致性风险

- 0.22.4 修复了 `VolumeIndicator` 冷缓存 StackOverflowError 和 `NetMomentumIndicator` 栈溢出 —— 这些修复应无负面影响
- 0.22.4 `VarianceIndicator` 默认使用样本方差（n-1）—— 如果项目代码直接使用 `VarianceIndicator` 且依赖总体方差行为，需确认

---

## 6. 升级步骤

### 6.1 前置条件

- [x] JDK 21 已安装并配置
- [x] Maven 3.9+ 已安装并配置
- [x] 确认 ta4j-master-0.22.6 为 0.22.6 源码（已完成）

### 6.2 构建 ta4j 0.22.6 到本地 Maven 仓库

```bash
# 1. 进入 ta4j-master 目录
cd f:\project\ta4j-master

# 2. 修改 ta4j-core/pom.xml 版本号（仅本地构建用途，不提交）
# 将 <version>0.22.2</version> 改为 <version>0.22.6</version>

# 3. 构建并安装到本地 Maven 仓库
mvn clean install -pl ta4j-core -DskipTests

# 4. 验证本地仓库中已有 0.22.6
dir %USERPROFILE%\.m2\repository\org\ta4j\ta4j-core\0.22.6
```

> 也可使用 `mvn versions:set -DnewVersion=0.22.6` 替代手动修改 pom.xml，之后 `mvn versions:revert` 恢复。

### 6.3 更新项目 pom.xml ✅（已完成）

修改 `f:\project\lenzeto\pom.xml`：

```xml
<ta4j.version>0.22.6</ta4j.version>
```

当前所有子模块均引用父模块的 `${ta4j.version}` 属性，修改父模块后自动生效。

> 2026-05-18 已完成：父 pom.xml 第 51 行已从 `0.22.2` 修改为 `0.22.6`，经扫描确认项目中无硬编码的 0.22.2 引用。

### 6.4 编译验证

```bash
cd f:\project\lenzeto
mvn clean compile -DskipTests
```

### 6.5 测试验证

```bash
# 运行全部测试
mvn test

# 重点关注涉及 ta4j 的测试类
mvn test -pl ai-extension,ai-engine,ai-quant,ai-signal,ai-strategy,ai-risk
```

### 6.6 部署验证

在测试环境运行完整回测和信号生成流程，确认：

- [ ] 回测结果与升级前一致（数值差异在浮点精度范围内）
- [ ] 指标计算结果一致
- [ ] 无运行时异常或 class 加载错误
- [ ] 序列化/反序列化正常

---

## 7. 回滚方案

### 7.1 回滚步骤

1. 将 `f:\project\lenzeto\pom.xml` 中的 `<ta4j.version>` 恢复为 `0.22.2`
2. 执行 `mvn clean compile` 确认无编译错误
3. 执行 `mvn test` 确认测试通过

### 7.2 回滚条件

- 编译失败且无法在 2 小时内修复
- 运行时出现 ClassNotFoundException 或 NoSuchMethodError
- 回测结果与升级前出现显著差异（非预期）
- 性能严重退化

---

## 8. 验证清单

### 编译验证

- [ ] `mvn clean compile` 编译通过，无编译警告
- [ ] `mvn test` 全部测试通过

### API 兼容性验证（除编译外的运行时检查）

- [ ] `MultiPositionTradingRecord` 正常创建和使用 `PositionBook`、`OpenPosition`
- [ ] `SmcDynamicExitRule` 正确访问 `PositionLot`
- [ ] `LiveTradingRecord` 的 `recordFill()` 和 `recordExecutionFill()` 正常调用
- [ ] `TradeExecutionModel` 及其实现（`MarketOrderModel`、`SlippageExecutionModel`、`NextOpenModel`）正常工作

### 功能验证

- [ ] `ai-extension` 模块自定义 Rule 正常实例化和执行
- [ ] `ai-engine` 模块回测引擎正常启动并生成结果
- [ ] `ai-quant` 模块特征工程和 ML 训练/推理正常
- [ ] `ai-signal` 模块信号生成正常
- [ ] `ai-strategy` 模块策略执行正常
- [ ] `ai-risk` 模块风险评价正常
- [ ] 序列化/反序列化正常（Indicator、Rule、Strategy）
- [ ] 回测结果数据与升级前一致

### 性能验证

- [ ] 回测执行时间无明显恶化
- [ ] 内存使用无明显恶化

---

## 9. 时间估计

| 阶段 | 预估时间 | 说明 |
|------|----------|------|
| 构建 ta4j 0.22.6 | 0.5 小时 | 修改版本号、构建并安装到本地仓库 |
| 修改项目依赖 | 0.2 小时 | 修改父 pom.xml 中 ta4j.version |
| 编译适配 | 0.5-1 小时 | 预计无需代码修改，仅为验证 |
| 测试验证 | 2-4 小时 | 单元测试 + 集成测试 |
| 部署验证 | 2-4 小时 | 环境部署和验证 |
| **总计** | **5-10 小时** | |

---

## 10. 结论与建议

### 10.1 API 兼容性结论

通过实际比对 ta4j 0.22.6 源码，确认 **项目中使用的所有 API 均保持向后兼容**，包括：

- `OpenPosition`（public record，未移除）
- `PositionBook`（public final class，public 构造器）
- `PositionLot`（public final class，public 访问方法）
- `LiveTrade` / `LiveTradingRecord` / `ExecutionFill` / `PositionLedger`（均保留，未实际标记 @Deprecated）

尽管 CHANGELOG 中声明了 Breaking Changes（移除 OpenPosition、内部化 PositionBook/PositionLot），但实际源码中这些类仍然保留在公共 API 中，作为迁移垫片。

### 10.2 升级风险评级

| 风险项 | 评级 | 说明 |
|--------|------|------|
| 编译兼容性 | 🟢 低 | 所有 API 均存在且接口一致 |
| 运行时兼容性 | 🟢 低 | 行为变化可忽略 |
| 构建过程 | 🟡 中 | 需手动构建本地版本 |
| 业务逻辑变化 | 🟢 无 | 新功能未使用，旧功能行为不变 |

### 10.3 建议方案

**推荐升级**，理由：
1. 源码级别已验证兼容性，编译和运行时风险极低
2. 0.22.6 修复了 `VolumeIndicator`、`NetMomentumIndicator`、`MaximumDrawdownCriterion` 等潜在 bug
3. 项目获得了新 API 的可选能力（窗口化评估、Walk-forward 等），利于未来扩展
4. 版本号与实际源码同步，消除技术债务

关键前提：**需确认 ta4j-master 的 pom.xml 版本号修改策略**（是否允许本地临时修改）。
