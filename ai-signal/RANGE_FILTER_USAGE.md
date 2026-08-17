# Range Filter 信号服务使用指南

## 概述

RangeFilterSignService 是一个完整的信号生成服务，实现了TradingView Range Filter算法，可以直接集成到现有的信号处理流程中。

## 集成方式

### 1. Spring 依赖注入

```java
@Service
public class YourSignalProcessor {

    @Autowired
    private RangeFilterSignService rangeFilterSignService;

    public void processSignal(IndicatorCalcDto calcDto) {
        BuyAndSellWeightDto signal = rangeFilterSignService.execute(calcDto);
        if (signal != null) {
            // 处理信号
            handleSignal(signal, calcDto);
        }
    }
}
```

### 2. 信号工厂集成

如果使用信号工厂模式，将RangeFilterSignService注册到SignFactory中：

```java
@Component
public class RangeFilterSignFactory implements SignFactory {

    @Autowired
    private RangeFilterSignService rangeFilterSignService;

    @Override
    public ISignService getSignService(String strategyType) {
        if ("RANGE_FILTER".equals(strategyType)) {
            return rangeFilterSignService;
        }
        return null;
    }
}
```

## API 使用

### 主要方法

#### execute(IndicatorCalcDto calcDto)
执行信号计算并返回买卖信号。

```java
BuyAndSellWeightDto signal = rangeFilterSignService.execute(calcDto);
if (signal != null) {
    BuyAndSellWeightDto.BuyType signalType = signal.getBuyType(); // BUY 或 SELL
    BigDecimal weight = signal.getWeight(); // 信号权重 0.1-1.0
}
```

#### getWeight(IndicatorCalcDto calcDto)
仅计算信号权重，不生成完整信号。

```java
Double weight = rangeFilterSignService.getWeight(calcDto);
```

## 数据要求

### IndicatorCalcDto 结构
```java
IndicatorCalcDto calcDto = new IndicatorCalcDto();
calcDto.setSymbol("BTCUSDT"); // 交易对
calcDto.setKLines(candlestickList); // K线数据列表，至少需要 range-period + 1 根K线
calcDto.setOpenSide(null); // 初始为null，由服务设置
```

### K线数据要求
- 必须包含：openPrice, closePrice, highPrice, lowPrice
- 时间顺序：按时间升序排列
- 数量：建议至少50根K线以获得稳定的计算结果

## 信号解释

### 信号类型
- **BUY**: 多头信号，建议开多或平空
- **SELL**: 空头信号，建议开空或平多

### 权重含义
- **0.1-0.3**: 弱信号，可能为噪音
- **0.3-0.7**: 中等强度信号
- **0.7-1.0**: 强信号，高置信度

## 实际应用示例

### 集成到 DefaultSignService.sendSignMq

```java
@Service
public class EnhancedDefaultSignService extends DefaultSignService {

    @Autowired
    private RangeFilterSignService rangeFilterSignService;

    @Autowired
    private ITechnicalSignalService technicalSignalService;

    @Override
    public void sendSignMq(IndicatorCalcDto calcDto, BuyAndSellWeightDto.BuyType buyType) {
        // 先执行Range Filter信号计算
        BuyAndSellWeightDto rangeSignal = rangeFilterSignService.execute(calcDto);

        if (rangeSignal != null && rangeSignal.getBuyType() == buyType) {
            // Range Filter确认信号方向，增强信号强度
            BigDecimal enhancedWeight = calcDto.getWeight().multiply(rangeSignal.getWeight());
            calcDto.setWeight(enhancedWeight);

            log.info("Range Filter增强信号 - 原始权重: {}, 增强权重: {}",
                    calcDto.getWeight(), enhancedWeight);
        }

        // 创建技术信号DTO
        TechnicalSignalDTO technicalSignalDTO = createTechnicalSignalDTO(calcDto, buyType);

        // 保存技术信号
        Long signalId = technicalSignalService.saveTechnicalSignal(technicalSignalDTO);

        // 继续原有逻辑...
        super.sendSignMq(calcDto, buyType);
    }

    private TechnicalSignalDTO createTechnicalSignalDTO(IndicatorCalcDto calcDto, BuyAndSellWeightDto.BuyType buyType) {
        // 实现技术信号DTO创建逻辑
        // ... 省略实现细节
    }
}
```

### 风控集成

```java
@Service
public class RiskControlledRangeFilterService extends RangeFilterSignService {

    @Autowired
    private RiskManagementService riskService;

    @Override
    public BuyAndSellWeightDto execute(IndicatorCalcDto calcDto) {
        // 先获取基础信号
        BuyAndSellWeightDto signal = super.execute(calcDto);

        if (signal != null) {
            // 检查风控条件
            boolean riskApproved = riskService.checkRiskLimits(calcDto, signal);

            if (!riskApproved) {
                log.warn("Range Filter信号被风控拒绝 - 交易对: {}, 信号: {}",
                        calcDto.getSymbol(), signal.getBuyType());
                return null;
            }

            // 调整权重基于风险水平
            BigDecimal riskAdjustedWeight = riskService.adjustWeightByRisk(signal.getWeight(), calcDto);
            signal.setWeight(riskAdjustedWeight);
        }

        return signal;
    }
}
```

## 性能优化建议

### 1. 缓存配置
```yaml
strategy:
  range-filter:
    # 启用缓存以提高性能
    cache-enabled: true
    cache-size: 1000
```

### 2. 批量处理
```java
public List<BuyAndSellWeightDto> batchProcessSignals(List<IndicatorCalcDto> calcDtos) {
    return calcDtos.stream()
            .map(this::execute)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
}
```

### 3. 异步处理
```java
@Async
public CompletableFuture<BuyAndSellWeightDto> executeAsync(IndicatorCalcDto calcDto) {
    return CompletableFuture.completedFuture(execute(calcDto));
}
```

## 监控和调试

### 日志配置
```yaml
logging:
  level:
    com.chain.ai.trade.engine.signal.service.support.RangeFilterSignService: DEBUG
```

### 关键指标监控
- 信号生成频率
- 信号准确率
- 平均权重分布
- 计算耗时

## 故障排除

### 常见问题

1. **信号为null**
   - 检查K线数据是否足够（至少需要range-period根K线）
   - 确认配置参数是否合理

2. **权重始终为0.5**
   - 检查范围大小计算是否正常
   - 确认价格数据有效性

3. **性能问题**
   - 减少范围周期参数
   - 启用缓存机制
   - 考虑异步处理

### 调试技巧

```java
// 启用详细日志
log.debug("Range Filter计算详情 - 价格: {}, 过滤器: {}, 方向: {}, 范围: {}",
         currentPrice, currentFilter, filterDirection, range);
```

## 扩展开发

### 自定义范围计算
```java
public class CustomRangeFilterSignService extends RangeFilterSignService {

    @Override
    protected double calculateRangeSize(IndicatorCalcDto calcDto) {
        // 实现自定义范围计算逻辑
        return super.calculateRangeSize(calcDto) * getCustomMultiplier(calcDto);
    }

    private double getCustomMultiplier(IndicatorCalcDto calcDto) {
        // 基于市场条件的动态乘数
        return marketVolatility > 0.8 ? 1.5 : 1.0;
    }
}
```

### 多时间框架集成
```java
public BuyAndSellWeightDto executeMultiTimeframe(IndicatorCalcDto calcDto) {
    // 1分钟信号
    BuyAndSellWeightDto m1Signal = execute(calcDto);

    // 5分钟信号确认
    IndicatorCalcDto m5CalcDto = convertToM5(calcDto);
    BuyAndSellWeightDto m5Signal = execute(m5CalcDto);

    // 多时间框架确认
    if (m1Signal != null && m5Signal != null &&
        m1Signal.getBuyType() == m5Signal.getBuyType()) {
        return m1Signal;
    }

    return null;
}
```
