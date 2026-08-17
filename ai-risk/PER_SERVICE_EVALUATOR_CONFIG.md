# 按服务类配置评估器指南

## 概述

系统支持为不同的信号服务类配置不同的评估器列表，而不是全局统一配置。这样可以让不同的策略使用最适合的评估器组合。

## 实现方案

### 1. SignalOrchestrator 支持指定评估器列表

`SignalOrchestrator.evaluateSignal()` 方法支持传入评估器ID列表参数：

```java
/**
 * 评估单个信号（指定评估器列表）
 * 
 * @param signal 交易信号
 * @param analysisData K线数据和分析数据
 * @param evaluatorIds 要使用的评估器ID列表，如果为null则使用默认配置
 * @return 信号评估结果
 */
public SignalEvaluation evaluateSignal(TradingSignal signal, AnalysisData analysisData, List<String> evaluatorIds);
```

### 2. 信号服务类配置评估器

在每个信号服务类中，通过 `@Value` 注解配置要使用的评估器列表。

## 配置方法

### 在 application.yml 中配置

```yaml
risk:
  orchestrator:
    # 全局默认评估器列表（当服务类未指定时使用）
    default-evaluators: trend-strength,volume-confirmation,risk-reward,elliott-wave
    default-adjusters: quality-based,risk-based
    min-composite-score: 0.5
    base-position-size: 1.0
    max-recommended-signals: 10

  evaluator:
    # 所有评估器配置...
    elliott-wave:
      weight: 1.5
      min-bars: 200
      fib-tolerance: 0.25

strategy:
  bollinger-rsi:
    # 启用风险模块
    use-risk-module: true
    # 指定要使用的评估器列表（逗号分隔，如果为空则使用全局默认配置）
    risk-module-evaluators: elliott-wave  # BollingerRsiSignService 只使用艾略特波浪评估器
  
  # 其他策略配置
  # another-strategy:
  #   use-risk-module: true
  #   risk-module-evaluators: trend-strength,volume-confirmation  # 使用不同的评估器组合
```

### 在服务类中读取配置

在 `BollingerRsiSignService` 中：

```java
@Value("${strategy.bollinger-rsi.use-risk-module:true}")
private boolean useRiskModule;

@Value("${strategy.bollinger-rsi.risk-module-evaluators:}")
private String riskModuleEvaluators; // 指定要使用的评估器列表，逗号分隔

private List<String> getEvaluatorIds() {
    if (riskModuleEvaluators != null && !riskModuleEvaluators.trim().isEmpty()) {
        return Arrays.asList(riskModuleEvaluators.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
    return null; // 返回null表示使用全局默认配置
}

private BigDecimal calculateConfidenceWithRiskModule(...) {
    // ...
    List<String> evaluatorIds = getEvaluatorIds();
    SignalEvaluation evaluation = signalOrchestrator.evaluateSignal(tradingSignalDto, analysisData, evaluatorIds);
    // ...
}
```

## 配置示例

### 示例1：BollingerRsiSignService 只使用 ElliottWaveEvaluator

```yaml
strategy:
  bollinger-rsi:
    use-risk-module: true
    risk-module-evaluators: elliott-wave
```

### 示例2：使用多个评估器

```yaml
strategy:
  bollinger-rsi:
    use-risk-module: true
    risk-module-evaluators: elliott-wave,trend-strength,volume-confirmation
```

### 示例3：使用全局默认配置

```yaml
strategy:
  bollinger-rsi:
    use-risk-module: true
    # risk-module-evaluators 不配置或配置为空，则使用全局默认配置
    # risk-module-evaluators:
```

### 示例4：不同策略使用不同评估器

```yaml
risk:
  orchestrator:
    default-evaluators: trend-strength,volume-confirmation,risk-reward,elliott-wave

strategy:
  bollinger-rsi:
    use-risk-module: true
    risk-module-evaluators: elliott-wave  # 只使用艾略特波浪
  
  macd-strategy:
    use-risk-module: true
    risk-module-evaluators: trend-strength,volume-confirmation  # 使用趋势和成交量
  
  rsi-strategy:
    use-risk-module: true
    # 不配置 risk-module-evaluators，使用全局默认配置
```

## 评估器ID列表

可用的评估器ID（通过 `getId()` 方法返回）：

- `trend-strength` - 趋势强度评估器
- `elliott-wave` - 艾略特波浪分析评估器
- `volume-confirmation` - 成交量确认评估器
- `risk-reward` - 风险收益比评估器
- `market-condition` - 市场环境评估器
- `support-resistance` - 支撑阻力评估器
- `multi-timeframe` - 多周期一致性评估器（需要 MultiTimeFrameAnalyzer 支持）

## 最佳实践

### 1. 根据策略特点选择评估器

- **趋势策略**：优先使用 `trend-strength`, `volume-confirmation`
- **波浪分析策略**：优先使用 `elliott-wave`
- **震荡策略**：优先使用 `support-resistance`, `market-condition`

### 2. 评估器组合建议

- **保守组合**：`trend-strength,volume-confirmation,risk-reward`（3个评估器，综合评估）
- **专注波浪**：`elliott-wave`（单一评估器，专注于波浪分析）
- **完整评估**：`trend-strength,volume-confirmation,risk-reward,elliott-wave`（4个评估器，全面评估）

### 3. 配置管理

- 全局默认配置：适用于大多数策略的通用评估器组合
- 策略特定配置：为特定策略优化评估器组合
- 避免过度配置：每个策略使用2-4个评估器即可，过多会增加计算开销

## 注意事项

### 1. 评估器ID必须正确

配置中的评估器ID必须与评估器的 `getId()` 方法返回值完全一致（区分大小写）。

### 2. 配置为空时的行为

如果 `risk-module-evaluators` 配置为空或未配置，系统会使用全局默认配置（`risk.orchestrator.default-evaluators`）。

### 3. 评估器不存在

如果配置的评估器ID不存在，系统会在日志中记录警告，但不会中断评估过程。该评估器会被跳过。

### 4. K线数据要求

不同的评估器对K线数据的要求不同：
- `elliott-wave`：需要至少200根K线
- `trend-strength`：需要至少50根K线
- `volume-confirmation`：需要至少20根K线

确保传入的K线数据满足所有使用的评估器的最低要求。

## 调试和验证

### 1. 检查配置是否生效

启用调试日志：

```yaml
logging:
  level:
    com.chain.ai.trade.engine.risk: DEBUG
    com.chain.ai.trade.engine.signal.service.support.BollingerRsiSignService: DEBUG
```

查看日志输出：

```
交易对: ETH-USDT-SWAP, 风险模块置信度 - 信号: BUY, 评估器: [elliott-wave], 综合得分: 0.85
```

### 2. 验证评估器列表

在代码中添加日志：

```java
List<String> evaluatorIds = getEvaluatorIds();
log.info("使用的评估器列表: {}", evaluatorIds);
SignalEvaluation evaluation = signalOrchestrator.evaluateSignal(tradingSignalDto, analysisData, evaluatorIds);
```

### 3. 检查评估结果

```java
SignalEvaluation evaluation = signalOrchestrator.evaluateSignal(tradingSignalDto, analysisData, evaluatorIds);
Map<String, QualityEvaluationResult> results = evaluation.getEvaluatorResults();

// 应该只包含配置的评估器
log.info("评估器结果数量: {}", results.size());
results.keySet().forEach(id -> log.info("评估器ID: {}", id));
```

## 迁移指南

如果要从全局配置迁移到按服务配置：

1. **保持全局默认配置**：作为后备配置
2. **为每个服务类添加配置项**：`risk-module-evaluators`
3. **逐步迁移**：先迁移一个服务类，验证无误后再迁移其他服务类
4. **测试验证**：确保评估结果符合预期

## 相关文档

- [开发使用手册](./DEVELOPMENT_GUIDE.md)
- [实现状态文档](./IMPLEMENTATION_STATUS.md)

