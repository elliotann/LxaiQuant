# AI-Risk 模块开发使用手册

## 目录

1. [系统概述](#系统概述)
2. [快速开始](#快速开始)
3. [核心概念](#核心概念)
4. [API 使用指南](#api-使用指南)
5. [配置说明](#配置说明)
6. [评估器开发](#评估器开发)
7. [调节器开发](#调节器开发)
8. [最佳实践](#最佳实践)
9. [常见问题](#常见问题)

---

## 系统概述

`ai-risk` 模块是一个通用的信号质量评估与仓位调节系统，用于对交易信号进行质量评估、风险评估和仓位调节。

### 核心功能

- **信号质量评估**：通过多个评估器对交易信号进行多维度质量评估
- **多周期分析**：支持不同时间框架下的信号一致性分析
- **仓位调节**：根据信号质量和风险评估自动调整仓位大小
- **风险控制**：多层风险控制机制，确保交易安全
- **熔断机制**：自适应熔断器，防止系统异常时的损失扩大
- **监控与可观测性**：提供系统运行指标和监控数据

### 架构设计

```
SignalOrchestrator (协调引擎)
    ├── QualityEvaluator (质量评估器)
    │   ├── TrendStrengthEvaluator
    │   ├── ElliottWaveEvaluator
    │   ├── VolumeConfirmationEvaluator
    │   └── ...
    ├── MultiTimeFrameAnalyzer (多周期分析)
    └── PositionAdjuster (仓位调节器)
        ├── QualityBasedAdjuster
        ├── RiskBasedAdjuster
        └── ...
```

---

## 快速开始

### 1. 基本使用

在 `BollingerRsiSignService` 中集成风险模块的示例：

```java
@Autowired(required = false)
@Lazy
private SignalOrchestrator signalOrchestrator;

private BigDecimal calculateConfidenceWithRiskModule(
        List<Candlestick> kLines, String signalType, String symbol, IndicatorCalcDto calcDto) {
    
    // 1. 构建 TradingSignal
    TradingSignal tradingSignalDto = buildTradingSignal(kLines, signalType, symbol, calcDto);
    
    // 2. 构建 AnalysisData（从K线数据）
    AnalysisData analysisData = AnalysisData.builder()
            .bars(kLines)
            .build();
    
    // 3. 调用 SignalOrchestrator 评估信号（传入K线数据）
    SignalEvaluation evaluation = signalOrchestrator.evaluateSignal(tradingSignalDto, analysisData);
    
    if (evaluation == null) {
        return BigDecimal.valueOf(0.5); // 默认置信度
    }
    
    // 4. 从评估结果中提取综合得分作为置信度
    double compositeScore = evaluation.getCompositeScore();
    return BigDecimal.valueOf(compositeScore);
}
```

### 2. 构建 TradingSignal

```java
private TradingSignal buildTradingSignal(
        List<Candlestick> kLines, String signalType, String symbol, IndicatorCalcDto calcDto) {
    
    double triggerPrice = kLines.get(kLines.size() - 1).getClosePrice().doubleValue();
    SignalType type = "BUY".equals(signalType) 
            ? SignalType.BUY 
            : ("SELL".equals(signalType) ? SignalType.SELL : SignalType.HOLD);
    TimeFrame timeFrame = convertTimeFrame(calcDto.getCandlestickIntervalEnum());
    
    return TradingSignal.builder()
            .id(UUID.randomUUID().toString())
            .symbol(symbol)
            .type(type)
            .timeFrame(timeFrame)
            .triggerPrice(triggerPrice)
            .timestamp(LocalDateTime.now())
            .initialConfidence(0.5)
            .build();
}
```

### 3. 时间框架转换

```java
private TimeFrame convertTimeFrame(CandlestickIntervalEnum intervalEnum) {
    if (intervalEnum == null) return TimeFrame.M15;
    switch (intervalEnum.name()) {
        case "OKXMIN1": return TimeFrame.M1;
        case "OKXMIN3": return TimeFrame.M5;
        case "OKXMIN5": return TimeFrame.M5;
        case "OKXMIN15": return TimeFrame.M15;
        case "OKXMIN30": return TimeFrame.M30;
        case "OKXMIN60": return TimeFrame.H1;
        case "OKXMIN240": return TimeFrame.H4;
        case "OKXMIN1440": return TimeFrame.D1;
        default: return TimeFrame.M15;
    }
}
```

---

## 核心概念

### TradingSignal (交易信号)

交易信号是系统的核心输入，包含以下信息：

```java
TradingSignal {
    String id;              // 信号ID（唯一标识）
    String symbol;          // 交易对（如 "ETH-USDT-SWAP"）
    SignalType type;        // 信号类型（BUY/SELL/HOLD）
    TimeFrame timeFrame;    // 时间框架（M15/H1/D1等）
    double triggerPrice;    // 触发价格
    LocalDateTime timestamp;// 信号时间
    double initialConfidence;// 初始置信度（0-1）
    Map<String, Object> metadata; // 元数据（如止损价、止盈价等）
}
```

### AnalysisData (分析数据)

分析数据包含评估器所需的市场数据：

```java
AnalysisData {
    List<Candlestick> bars;           // K线数据
    Map<String, Object> indicators;   // 技术指标数据
    Map<String, Object> marketData;   // 市场数据（成交量、持仓量等）
}
```

### SignalEvaluation (信号评估结果)

评估结果包含综合得分和详细评估信息：

```java
SignalEvaluation {
    TradingSignal signal;                                    // 原始信号
    double compositeScore;                                   // 综合得分（0-1）
    ConfidenceLevel confidenceLevel;                        // 置信度等级
    Map<String, QualityEvaluationResult> evaluatorResults;  // 各评估器的详细结果
    String summary;                                          // 评估摘要
}
```

---

## API 使用指南

### SignalOrchestrator 主要方法

#### 1. 评估单个信号（推荐）

```java
/**
 * 评估单个信号（带K线数据）
 * @param signal 交易信号
 * @param analysisData K线数据和分析数据
 * @return 信号评估结果
 */
SignalEvaluation evaluateSignal(TradingSignal signal, AnalysisData analysisData);
```

**使用示例**：

```java
// 构建信号
TradingSignal signal = TradingSignal.builder()
        .id("signal-001")
        .symbol("ETH-USDT-SWAP")
        .type(SignalType.BUY)
        .timeFrame(TimeFrame.M15)
        .triggerPrice(2500.0)
        .timestamp(LocalDateTime.now())
        .initialConfidence(0.6)
        .build();

// 构建分析数据
AnalysisData analysisData = AnalysisData.builder()
        .bars(kLines)  // 传入K线数据
        .build();

// 评估信号
SignalEvaluation evaluation = signalOrchestrator.evaluateSignal(signal, analysisData);

// 获取综合得分（0-1）
double confidence = evaluation.getCompositeScore();

// 获取各评估器的详细结果
evaluation.getEvaluatorResults().forEach((evaluatorId, result) -> {
    log.info("评估器 {} 得分: {}", evaluatorId, result.getScore());
});
```

#### 2. 评估单个信号（无K线数据，不推荐）

```java
/**
 * 评估单个信号（不提供K线数据，使用空数据）
 * 注意：此方法评估器无法获取真实K线数据，可能导致评估不准确
 */
SignalEvaluation evaluateSignal(TradingSignal signal);
```

#### 3. 批量协调处理信号

```java
/**
 * 协调处理信号列表（批量评估、排序、过滤、仓位调节）
 * @param signals 信号列表
 * @return 协调结果
 */
OrchestrationResult orchestrate(List<TradingSignal> signals);
```

**使用示例**：

```java
List<TradingSignal> signals = Arrays.asList(signal1, signal2, signal3);
OrchestrationResult result = signalOrchestrator.orchestrate(signals);

// 获取推荐信号
List<RankedSignal> recommendedSignals = result.getRankedSignals().stream()
        .filter(RankedSignal::isRecommended)
        .collect(Collectors.toList());

recommendedSignals.forEach(rankedSignal -> {
    log.info("推荐信号: {}, 最终得分: {}", 
            rankedSignal.getSignal().getId(), 
            rankedSignal.getFinalScore());
});
```

---

## 配置说明

### application.yml 配置

在 `application.yml` 中配置风险模块：

```yaml
risk:
  orchestrator:
    # 默认评估器列表（使用评估器的getId()返回值）
    default-evaluators: trend-strength,volume-confirmation,risk-reward,elliott-wave
    # 默认调节器列表
    default-adjusters: quality-based,risk-based,portfolio-aware
    # 最低综合得分阈值（低于此值的信号将被过滤）
    min-composite-score: 0.5
    # 基础仓位大小
    base-position-size: 1.0
    # 最大推荐信号数
    max-recommended-signals: 10

  # 评估器配置
  evaluator:
    # 趋势强度评估器
    trend-strength:
      weight: 1.2
      period: 50
    
    # 艾略特波浪评估器
    elliott-wave:
      weight: 1.5
      min-bars: 200
      fib-tolerance: 0.25
    
    # 成交量确认评估器
    volume-confirmation:
      weight: 1.0
      volume-avg-period: 20
      volume-threshold-factor: 1.5
    
    # 风险收益比评估器
    risk-reward:
      weight: 1.3
      min-rr-ratio: 1.5
      max-rr-ratio: 3.0
    
    # 市场环境评估器
    market-condition:
      weight: 0.8
      volatility-period: 30
      trend-period: 50
    
    # 多周期一致性评估器
    multi-timeframe:
      weight: 1.2
      consistency-threshold: 0.7

  # 调节器配置
  adjuster:
    # 基于质量得分调节
    quality-based:
      weight: 1.0
      min-score-threshold: 0.6
      max-score-threshold: 0.8
    
    # 基于风险评估调节
    risk-based:
      weight: 1.2
      max-risk-per-trade-ratio: 0.02
      min-rr-ratio-threshold: 1.0
    
    # 组合感知调节
    portfolio-aware:
      weight: 1.1
      max-position-per-symbol: 0.3
      correlation-penalty-threshold: 0.8

  # 多周期分析配置
  multitimeframe:
    default-data-limit: 500
    timeframe-weights:
      M15: 0.1
      H1: 0.3
      H4: 0.4
      D1: 0.2
```

### 在策略中启用风险模块

```yaml
strategy:
  bollinger-rsi:
    # 启用风险模块
    use-risk-module: true
    # 其他策略配置...
```

---

## 评估器开发

### 实现 QualityEvaluator 接口

创建一个新的评估器需要实现 `QualityEvaluator` 接口：

```java
@Component
@Slf4j
public class MyCustomEvaluator implements QualityEvaluator {
    
    @Value("${risk.evaluator.my-custom.weight:1.0}")
    private double weight;
    
    @Override
    public String getId() {
        return "my-custom";  // 必须唯一，用于配置中的引用
    }
    
    @Override
    public String getName() {
        return "我的自定义评估器";
    }
    
    @Override
    public String getDescription() {
        return "评估器功能描述";
    }
    
    @Override
    public double getWeight() {
        return weight;
    }
    
    @Override
    public QualityEvaluationResult evaluate(TradingSignal signal, EvaluationContext context) {
        AnalysisData data = context.getAnalysisData();
        List<Candlestick> bars = data.getBars();
        
        if (bars == null || bars.size() < 10) {
            return QualityEvaluationResult.builder()
                    .evaluatorId(getId())
                    .signalId(signal.getId())
                    .score(0.5)
                    .weight(getWeight())
                    .confidence(ConfidenceLevel.MEDIUM)
                    .summary("数据不足")
                    .warnings(List.of("K线数据不足"))
                    .build();
        }
        
        // 实现评估逻辑
        double score = calculateScore(bars, signal);
        
        return QualityEvaluationResult.builder()
                .evaluatorId(getId())
                .signalId(signal.getId())
                .score(score)
                .weight(getWeight())
                .confidence(ConfidenceLevel.fromScore(score))
                .summary("评估完成")
                .factors(Map.of("customFactor", score))
                .build();
    }
    
    private double calculateScore(List<Candlestick> bars, TradingSignal signal) {
        // 实现具体的评估逻辑
        return 0.8; // 返回0-1之间的得分
    }
}
```

### 评估器注册

评估器通过 Spring 的 `@Component` 注解自动注册。系统会在启动时通过 `@PostConstruct` 方法将所有评估器注册到 `SignalOrchestrator` 中，使用 `getId()` 返回值作为 key。

### 在配置中启用评估器

```yaml
risk:
  orchestrator:
    default-evaluators: trend-strength,volume-confirmation,my-custom  # 添加自定义评估器
  evaluator:
    my-custom:
      weight: 1.2
```

---

## 调节器开发

### 实现 PositionAdjuster 接口

创建一个新的调节器需要实现 `PositionAdjuster` 接口：

```java
@Component
@Slf4j
public class MyCustomAdjuster implements PositionAdjuster {
    
    @Value("${risk.adjuster.my-custom.weight:1.0}")
    private double weight;
    
    @Override
    public String getId() {
        return "my-custom";
    }
    
    @Override
    public AdjustmentResult adjust(
            TradingSignal signal,
            double qualityScore,
            double basePosition,
            AdjustmentContext context) {
        
        // 实现调节逻辑
        double adjustedWeight = calculateAdjustedWeight(qualityScore, context);
        double positionSize = basePosition * adjustedWeight;
        
        return AdjustmentResult.builder()
                .adjustedWeight(adjustedWeight)
                .positionSize(positionSize)
                .factors(Map.of("adjustmentFactor", adjustedWeight))
                .adjustments(List.of("根据自定义逻辑调整仓位"))
                .riskAssessment(createRiskAssessment(signal, positionSize))
                .build();
    }
    
    private double calculateAdjustedWeight(double qualityScore, AdjustmentContext context) {
        // 实现具体的调节逻辑
        return 1.0;
    }
    
    private RiskAssessment createRiskAssessment(TradingSignal signal, double positionSize) {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setMaxPositionSize(positionSize * 1.5);
        assessment.setRiskLevel("MEDIUM");
        return assessment;
    }
}
```

### 在配置中启用调节器

```yaml
risk:
  orchestrator:
    default-adjusters: quality-based,risk-based,my-custom  # 添加自定义调节器
  adjuster:
    my-custom:
      weight: 1.1
```

---

## 最佳实践

### 1. 始终传入K线数据

**推荐**：使用 `evaluateSignal(signal, analysisData)` 并传入真实的K线数据

```java
AnalysisData analysisData = AnalysisData.builder()
        .bars(kLines)  // 传入真实K线数据
        .build();
SignalEvaluation evaluation = signalOrchestrator.evaluateSignal(signal, analysisData);
```

**不推荐**：使用无参数的 `evaluateSignal(signal)`，评估器无法获取真实数据

### 2. 处理评估失败

```java
try {
    SignalEvaluation evaluation = signalOrchestrator.evaluateSignal(signal, analysisData);
    if (evaluation == null) {
        // 使用默认置信度
        return BigDecimal.valueOf(0.5);
    }
    return BigDecimal.valueOf(evaluation.getCompositeScore());
} catch (Exception e) {
    log.error("信号评估失败", e);
    // 回退到默认值或其他评估方法
    return BigDecimal.valueOf(0.5);
}
```

### 3. 使用评估器详细结果

```java
SignalEvaluation evaluation = signalOrchestrator.evaluateSignal(signal, analysisData);
evaluation.getEvaluatorResults().forEach((evaluatorId, result) -> {
    log.debug("评估器 {}: 得分={}, 摘要={}, 警告={}", 
            evaluatorId, 
            result.getScore(),
            result.getSummary(),
            result.getWarnings());
});
```

### 4. 配置评估器权重

根据实际需求调整评估器权重，权重越高，该评估器对最终得分的影响越大：

```yaml
risk:
  evaluator:
    elliott-wave:
      weight: 1.5  # 较高的权重，艾略特波浪分析很重要
    volume-confirmation:
      weight: 1.0  # 标准权重
    market-condition:
      weight: 0.8  # 较低的权重
```

### 5. 合理设置最低综合得分阈值

```yaml
risk:
  orchestrator:
    min-composite-score: 0.5  # 低于0.5的信号将被过滤
```

根据实际交易策略的风险承受能力调整此阈值。

### 6. 评估器中的错误处理

在自定义评估器中，始终进行数据校验和错误处理：

```java
@Override
public QualityEvaluationResult evaluate(TradingSignal signal, EvaluationContext context) {
    AnalysisData data = context.getAnalysisData();
    if (data == null || data.getBars().size() < minBars) {
        // 返回默认结果，而不是抛出异常
        return QualityEvaluationResult.builder()
                .evaluatorId(getId())
                .signalId(signal.getId())
                .score(0.5)
                .confidence(ConfidenceLevel.MEDIUM)
                .summary("数据不足")
                .warnings(List.of("K线数据不足"))
                .build();
    }
    
    try {
        // 评估逻辑
    } catch (Exception e) {
        log.error("评估失败", e);
        // 返回默认结果
        return QualityEvaluationResult.builder()
                .evaluatorId(getId())
                .signalId(signal.getId())
                .score(0.5)
                .confidence(ConfidenceLevel.MEDIUM)
                .summary("评估失败")
                .warnings(List.of("评估过程中发生错误"))
                .build();
    }
}
```

---

## 常见问题

### Q1: 评估器没有被调用？

**A**: 检查以下几点：

1. 评估器是否添加了 `@Component` 注解
2. 评估器的 `getId()` 返回值是否在 `default-evaluators` 配置中
3. 检查 Spring 组件扫描是否包含 `com.chain.ai.trade.engine.risk` 包

### Q2: 评估结果得分总是0.5？

**A**: 可能的原因：

1. 没有传入K线数据，评估器使用空数据返回默认值
2. 评估器内部发生异常，返回了默认值
3. 检查日志中的警告和错误信息

### Q3: 如何调试评估过程？

**A**: 启用调试日志：

```yaml
logging:
  level:
    com.chain.ai.trade.engine.risk: DEBUG
```

查看评估器注册、评估过程、得分计算等详细日志。

### Q4: 如何自定义评估器的权重？

**A**: 在 `application.yml` 中配置：

```yaml
risk:
  evaluator:
    my-evaluator:
      weight: 1.5  # 调整权重
```

### Q5: 评估器需要多周期数据怎么办？

**A**: 实现 `requiresMultiTimeFrame()` 方法返回 `true`，系统会自动调用 `MultiTimeFrameAnalyzer` 进行多周期分析。

```java
@Override
public boolean requiresMultiTimeFrame() {
    return true;  // 需要多周期分析
}
```

### Q6: 如何获取评估器的详细因子数据？

**A**: 从 `QualityEvaluationResult` 的 `factors` 字段获取：

```java
QualityEvaluationResult result = evaluation.getEvaluatorResults().get("elliott-wave");
Map<String, Object> factors = result.getFactors();
Double baseConfidence = (Double) factors.get("baseConfidence");
Double directionScore = (Double) factors.get("directionScore");
```

### Q7: 批量评估时如何传入K线数据？

**A**: 目前 `orchestrate` 方法不支持传入K线数据。如果需要批量评估，建议：

1. 使用 `evaluateSignal(signal, analysisData)` 逐个评估
2. 或者等待未来版本的 `orchestrate` 重载方法

---

## 相关文档

- [实现状态文档](./IMPLEMENTATION_STATUS.md)
- [设计文档](./DESIGN.md)（如果存在）

---

## 更新日志

- **2025-01-20**: 初始版本
  - 添加了基本使用指南
  - 添加了配置说明
  - 添加了评估器和调节器开发指南

