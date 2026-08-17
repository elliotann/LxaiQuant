# 双向持仓扩展集成指南

## 概述

本指南介绍如何在现有的量化交易系统中集成和使用双向持仓扩展能力。该扩展允许策略同时持有多头和空头仓位，实现更灵活的交易策略。

## 目录

1. [项目集成](#项目集成)
2. [回测环境使用](#回测环境使用)
3. [实盘环境使用](#实盘环境使用)
4. [策略开发](#策略开发)
5. [配置说明](#配置说明)
6. [注意事项](#注意事项)

---

## 项目集成

### 1. Maven依赖配置

在需要使用双向持仓的模块中添加依赖：

```xml
<!-- ai-quant/pom.xml 或其他需要使用的模块 -->
<dependencies>
    <!-- 添加双向持仓扩展依赖 -->
    <dependency>
        <groupId>com.chain.ai.trade</groupId>
        <artifactId>ai-extension</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### 2. 导入必要的类

```java
import com.chain.ai.trade.extension.ta4j.bidirectional.*;
import org.ta4j.core.*;
import org.ta4j.core.num.DecimalNum;
```

---

## 回测环境使用

### 基本使用流程

```java
public class BacktestWithBidirectionalExample {

    public void runBacktest() {
        // 1. 准备数据
        BarSeries barSeries = loadBarSeries();

        // 2. 创建原始策略（单向策略）
        Strategy originalStrategy = createOriginalStrategy(barSeries);

        // 3. 创建方向选择器
        DirectionSelector directionSelector = new TrendBasedDirectionSelector(20.0);

        // 4. 创建双向持仓执行上下文
        BidirectionalExecutionContext context =
            BidirectionalExtensionFactory.createExclusiveContext(
                barSeries, originalStrategy, directionSelector);

        // 5. 执行回测
        TradingRecord result = BidirectionalExtensionFactory.runBidirectional(
            context,
            DecimalNum.valueOf(1.0), // 交易数量
            20, // 起始索引（跳过前20个bar用于指标计算）
            barSeries.getEndIndex() // 结束索引
        );

        // 6. 分析结果
        analyzeResults(result);
    }

    private Strategy createOriginalStrategy(BarSeries barSeries) {
        // 创建基础策略（RSI超买超卖策略）
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        Rule entryRule = new OverIndicatorRule(rsi, DecimalNum.valueOf(30))
            .or(new UnderIndicatorRule(rsi, DecimalNum.valueOf(70)));

        Rule exitRule = new UnderIndicatorRule(rsi, DecimalNum.valueOf(50))
            .and(new OverIndicatorRule(rsi, DecimalNum.valueOf(50)));

        return new BaseStrategy(entryRule, exitRule);
    }
}
```

### 高级配置

#### 对冲模式（允许同时持有多空仓位）

```java
// 创建对冲模式的双向策略
BidirectionalExecutionContext hedgedContext =
    BidirectionalExtensionFactory.createHedgedContext(
        barSeries, originalStrategy, directionSelector);

// 或直接创建执行上下文
BidirectionalExecutionContext customContext =
    BidirectionalExtensionFactory.createExecutionContext(
        barSeries,
        originalStrategy,
        new HedgedDirectionSelector(directionSelector), // 对冲方向选择器
        new ZeroCostModel(),  // 交易成本模型
        new ZeroCostModel(),  // 持仓成本模型
        new TradeOnCurrentCloseModel(), // 交易执行模型
        true // 允许对冲
    );
```

#### 自定义方向选择器

```java
// RSI-based方向选择器
DirectionSelector rsiSelector = new RSIBasedDirectionSelector(14, 30.0, 70.0);

// 互斥方向选择器（确保同一时间只能持有一个方向）
DirectionSelector exclusiveSelector = new ExclusiveDirectionSelector(rsiSelector);

// 创建策略
BidirectionalExecutionContext context =
    BidirectionalExtensionFactory.createExclusiveContext(
        barSeries, originalStrategy, exclusiveSelector);
```

---

## 实盘环境使用

### 适配现有交易系统

#### 1. 修改策略执行器

在 `ai-engine` 或 `ai-quant` 模块中修改策略执行逻辑：

```java
public class LiveTradingStrategyExecutor {

    private final DirectionSelector directionSelector;
    private final BidirectionalExecutionManager executionManager;
    private final DualDirectionTradingRecord tradingRecord;

    public LiveTradingStrategyExecutor(Strategy originalStrategy,
                                     DirectionSelector directionSelector,
                                     BarSeries barSeries) {
        this.directionSelector = directionSelector;

        // 创建双向执行上下文
        BidirectionalExecutionContext context =
            BidirectionalExtensionFactory.createExclusiveContext(
                barSeries, originalStrategy, directionSelector);

        this.executionManager = context.getExecutionManager();
        this.tradingRecord = context.getTradingRecord();
    }

    public void onNewBar(Bar newBar, int index) {
        // 执行双向策略逻辑
        executionManager.execute(
            index,
            new BidirectionalStrategyWrapper(
                originalStrategy, directionSelector, barSeries),
            tradingRecord,
            barSeries,
            DecimalNum.valueOf(1.0) // 交易数量
        );

        // 检查是否有新的交易信号
        Position currentPosition = tradingRecord.getCurrentPosition();
        if (currentPosition.isOpened() && !currentPosition.isNew()) {
            // 有持仓，检查是否需要调整
            Trade.TradeType netDirection = tradingRecord.getNetPositionDirection();
            executePositionAdjustment(netDirection);
        }
    }

    private void executePositionAdjustment(Trade.TradeType direction) {
        if (direction == TradeType.BUY) {
            // 执行多头操作：平空仓，开多仓
            closeShortPosition();
            openLongPosition();
        } else if (direction == TradeType.SELL) {
            // 执行空头操作：平多仓，开空仓
            closeLongPosition();
            openShortPosition();
        }
        // 如果direction为null，表示完全对冲，不需要操作
    }
}
```

#### 2. 集成到订单系统

在 `ai-order` 模块中添加双向持仓支持：

```java
@Service
public class BidirectionalOrderService {

    @Autowired
    private OrderRepository orderRepository;

    public void executeBidirectionalSignal(DualDirectionTradingRecord tradingRecord,
                                         BarSeries barSeries) {
        // 检查当前持仓状态
        boolean hasLong = tradingRecord.getLongRecord().getCurrentPosition().isOpened();
        boolean hasShort = tradingRecord.getShortRecord().getCurrentPosition().isOpened();

        Trade.TradeType netDirection = tradingRecord.getNetPositionDirection();

        if (netDirection == TradeType.BUY && hasShort) {
            // 需要转为多头：平空，开多
            createCloseOrder(TradeType.SELL, getShortPositionAmount());
            createOpenOrder(TradeType.BUY, getTradeAmount());
        } else if (netDirection == TradeType.SELL && hasLong) {
            // 需要转为空头：平多，开空
            createCloseOrder(TradeType.BUY, getLongPositionAmount());
            createOpenOrder(TradeType.SELL, getTradeAmount());
        }
        // 对冲状态不需要额外操作
    }

    private BigDecimal getTradeAmount() {
        // 根据风险管理策略计算交易数量
        return BigDecimal.valueOf(1.0);
    }
}
```

#### 3. 修改信号处理

在 `ai-signal` 模块中集成双向持仓逻辑：

```java
@Service
public class BidirectionalSignalProcessor {

    public Signal processBidirectionalSignal(BarSeries barSeries,
                                           Strategy originalStrategy,
                                           DirectionSelector directionSelector) {

        // 创建双向执行上下文
        BidirectionalExecutionContext context =
            BidirectionalExtensionFactory.createExclusiveContext(
                barSeries, originalStrategy, directionSelector);

        // 执行策略
        TradingRecord result = BidirectionalExtensionFactory.runBidirectional(
            context, DecimalNum.valueOf(1.0), 20, barSeries.getEndIndex());

        // 生成信号
        Position currentPosition = result.getCurrentPosition();
        if (currentPosition.isOpened()) {
            Trade.TradeType direction = result.getNetPositionDirection();
            return new Signal(direction, getTradeAmount(), SignalType.BIDIRECTIONAL);
        }

        return Signal.NO_SIGNAL;
    }
}
```

---

## 策略开发

### 自定义方向选择器

```java
public class CustomDirectionSelector implements DirectionSelector {

    private final int fastPeriod;
    private final int slowPeriod;

    public CustomDirectionSelector(int fastPeriod, int slowPeriod) {
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
    }

    @Override
    public Trade.TradeType selectDirection(int index, BarSeries barSeries,
                                         Strategy strategy, TradingRecord tradingRecord) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);

        // 双均线策略
        SMAIndicator fastSMA = new SMAIndicator(closePrice, fastPeriod);
        SMAIndicator slowSMA = new SMAIndicator(closePrice, slowPeriod);

        Num fastValue = fastSMA.getValue(index);
        Num slowValue = slowSMA.getValue(index);

        if (fastValue.isGreaterThan(slowValue)) {
            return TradeType.BUY;  // 快线上穿慢线，开多
        } else if (fastValue.isLessThan(slowValue)) {
            return TradeType.SELL; // 快线下穿慢线，开空
        }

        return null; // 均线粘合，不开仓
    }
}
```

### 组合方向选择器

```java
public class CompositeDirectionSelector implements DirectionSelector {

    private final List<DirectionSelector> selectors;
    private final boolean requireAllAgree; // 是否要求所有选择器都同意

    @Override
    public Trade.TradeType selectDirection(int index, BarSeries barSeries,
                                         Strategy strategy, TradingRecord tradingRecord) {
        List<Trade.TradeType> votes = new ArrayList<>();

        for (DirectionSelector selector : selectors) {
            Trade.TradeType vote = selector.selectDirection(index, barSeries, strategy, tradingRecord);
            if (vote != null) {
                votes.add(vote);
            }
        }

        if (requireAllAgree) {
            // 需要所有选择器都同意
            if (votes.stream().allMatch(v -> v == TradeType.BUY)) {
                return TradeType.BUY;
            } else if (votes.stream().allMatch(v -> v == TradeType.SELL)) {
                return TradeType.SELL;
            }
        } else {
            // 多数决定
            long buyVotes = votes.stream().filter(v -> v == TradeType.BUY).count();
            long sellVotes = votes.stream().filter(v -> v == TradeType.SELL).count();

            if (buyVotes > sellVotes) {
                return TradeType.BUY;
            } else if (sellVotes > buyVotes) {
                return TradeType.SELL;
            }
        }

        return null;
    }
}
```

---

## 配置说明

### 应用配置

```yaml
# application.yml
trading:
  bidirectional:
    # 模式：EXCLUSIVE（互斥）或 HEDGED（对冲）
    mode: EXCLUSIVE

    # 方向选择器配置
    direction-selector:
      type: TREND_BASED  # TREND_BASED, RSI_BASED, CUSTOM
      trend-based:
        ema-period: 20
      rsi-based:
        rsi-period: 14
        oversold-threshold: 30
        overbought-threshold: 70

    # 风险管理
    risk-management:
      max-position-size: 1.0
      allow-hedging: false
      transaction-cost-model: ZERO
      holding-cost-model: ZERO
```

### 策略配置示例

```java
@Configuration
public class BidirectionalStrategyConfig {

    @Bean
    public DirectionSelector directionSelector() {
        return new TrendBasedDirectionSelector(20.0);
    }

    @Bean
    public BidirectionalExecutionManager executionManager() {
        return new BidirectionalExecutionManager(
            new TradeOnCurrentCloseModel(),
            false // 不允许对冲
        );
    }
}
```

---

## 注意事项

### 1. 性能考虑

- 双向持仓会增加计算复杂度
- 在回测大量数据时注意内存使用
- 实盘环境中及时清理历史数据

### 2. 风险管理

- 注意对冲模式的资金占用
- 设置合理的止损机制
- 监控净持仓风险

### 3. 数据一致性

- 确保BarSeries数据完整性
- 同步更新多头和空头仓位
- 处理交易执行延迟

### 4. 兼容性

- 检查现有策略是否支持双向持仓
- 逐步迁移，避免大范围重构
- 保留原有单向策略作为备选

### 5. 监控和调试

- 记录双向持仓的详细状态
- 监控净持仓变化
- 分析多空仓位表现差异

### 6. 扩展点

- 自定义DirectionSelector实现复杂逻辑
- 扩展BidirectionalExecutionManager支持更多交易类型
- 集成第三方风险管理系统

---

## 故障排除

### 常见问题

1. **编译错误**：检查ai-extension依赖是否正确添加
2. **运行时异常**：确认DirectionSelector实现是否完整
3. **信号不正确**：检查原始策略和方向选择器的逻辑
4. **性能问题**：优化大数据集的处理方式

### 调试技巧

```java
// 启用详细日志
@Slf4j
public class DebugBidirectionalStrategy {

    public void debugExecution(BidirectionalExecutionContext context, int index) {
        log.info("=== 双向策略执行调试 ===");
        log.info("索引: {}", index);
        log.info("多头持仓: {}", context.getTradingRecord().getLongRecord().getCurrentPosition());
        log.info("空头持仓: {}", context.getTradingRecord().getShortRecord().getCurrentPosition());
        log.info("净方向: {}", context.getTradingRecord().getNetPositionDirection());

        // 执行前状态
        DirectionSelector selector = ((BidirectionalStrategyWrapper)context.getStrategy()).getDirectionSelector();
        Trade.TradeType direction = selector.selectDirection(
            index, context.getBarSeries(), context.getStrategy().getOriginalStrategy(),
            context.getTradingRecord());
        log.info("方向选择: {}", direction);
    }
}
```

---

*本指南基于双向持仓扩展的设计文档，如有疑问请参考源码实现或联系开发团队。*