# 仅使用 ElliottWaveEvaluator 配置指南

## 配置方法

如果只想在 `BollingerRsiSignService` 中使用 `ElliottWaveEvaluator`，需要修改 `application.yml` 配置。

### 1. 修改评估器配置

在 `application.yml` 中，将 `risk.orchestrator.default-evaluators` 配置为只包含 `elliott-wave`：

```yaml
risk:
  orchestrator:
    # 只使用艾略特波浪评估器
    default-evaluators: elliott-wave
    # 默认调节器列表（可选，根据需要配置）
    default-adjusters: quality-based,risk-based
    # 最低综合得分
    min-composite-score: 0.5
    # 基础仓位大小
    base-position-size: 1.0
    # 最大推荐信号数
    max-recommended-signals: 10

  evaluator:
    # 艾略特波浪评估器配置
    elliott-wave:
      weight: 1.5  # 评估器权重（由于只有一个评估器，权重不影响结果，但建议保留）
      min-bars: 200  # 最少需要的K线数量
      fib-tolerance: 0.25  # 斐波那契容差（25%）

  # 其他评估器配置可以保留，但不会被使用
  # trend-strength:
  #   weight: 1.2
  # volume-confirmation:
  #   weight: 1.0
```

### 2. 完整配置示例

```yaml
risk:
  orchestrator:
    default-evaluators: elliott-wave
    default-adjusters: quality-based,risk-based
    min-composite-score: 0.5
    base-position-size: 1.0
    max-recommended-signals: 10

  evaluator:
    elliott-wave:
      weight: 1.5
      min-bars: 200
      fib-tolerance: 0.25

strategy:
  bollinger-rsi:
    # 启用风险模块
    use-risk-module: true
```

## 注意事项

### 1. 评估器ID

`ElliottWaveEvaluator` 的 ID 是 `"elliott-wave"`（通过 `getId()` 方法返回）。确保配置中使用的是这个ID，不要使用类名或其他名称。

### 2. 权重的影响

当只有一个评估器时，权重不会影响最终得分（因为只有一个得分，没有加权平均）。但为了代码的一致性，建议保留权重配置。

### 3. K线数据要求

`ElliottWaveEvaluator` 需要至少 200 根K线（默认配置 `min-bars: 200`）。如果K线数据不足，评估器会返回默认得分 0.5。

### 4. 评估结果

使用单一评估器时：
- `SignalEvaluation.getCompositeScore()` 直接返回 `ElliottWaveEvaluator` 的得分
- `SignalEvaluation.getEvaluatorResults()` 只包含一个键值对：`"elliott-wave" -> QualityEvaluationResult`

### 5. 调试

可以通过日志查看评估器是否被正确调用：

```yaml
logging:
  level:
    com.chain.ai.trade.engine.risk: DEBUG
```

在日志中会看到：
```
注册评估器: id=elliott-wave, name=艾略特波浪分析评估器
评估器初始化完成，共注册 1 个评估器
```

## 验证配置

### 1. 检查评估器注册

启动应用后，查看日志中是否有：
```
评估器初始化完成，共注册 1 个评估器
```

如果有多个评估器注册，说明配置没有生效，需要检查：
- `default-evaluators` 配置是否正确
- 是否使用了正确的评估器ID（`elliott-wave`）

### 2. 检查评估结果

在代码中检查评估结果：

```java
SignalEvaluation evaluation = signalOrchestrator.evaluateSignal(tradingSignalDto, analysisData);
Map<String, QualityEvaluationResult> results = evaluation.getEvaluatorResults();

// 应该只有一个评估器结果
assert results.size() == 1;
assert results.containsKey("elliott-wave");

QualityEvaluationResult elliottResult = results.get("elliott-wave");
log.info("艾略特波浪评估得分: {}", elliottResult.getScore());
log.info("评估摘要: {}", elliottResult.getSummary());
```

## 性能考虑

使用单一评估器（ElliottWaveEvaluator）的优势：
- ✅ 评估速度更快（只有一个评估器执行）
- ✅ 资源消耗更少
- ✅ 结果更聚焦（专注于艾略特波浪分析）

注意事项：
- ⚠️ 缺少其他评估器的交叉验证
- ⚠️ 如果艾略特波浪分析失败，没有其他评估器作为备份
- ⚠️ 评估结果的维度单一

## 恢复多评估器配置

如果需要恢复使用多个评估器，修改配置：

```yaml
risk:
  orchestrator:
    default-evaluators: trend-strength,volume-confirmation,risk-reward,elliott-wave
```

然后重启应用即可。

