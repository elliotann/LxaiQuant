# AI Extension - TA4J 双向持仓扩展

## 概述

AI Extension是一个TA4J扩展库，提供了双向持仓支持，允许同一个策略同时持有多头和空头仓位。该扩展完全兼容TA4J核心API，不修改任何TA4J源码。

## 主要特性

- ✅ **零侵入设计**：不修改TA4J核心类
- ✅ **完全兼容**：实现标准TA4J接口
- ✅ **双向持仓**：同时持有多空仓位
- ✅ **灵活配置**：支持互斥模式和对冲模式
- ✅ **多种方向选择器**：基于趋势、RSI等指标的方向选择

## 核心组件

### 1. DirectionSelector - 方向选择器
决定应该开多还是开空的策略逻辑。

- `TrendBasedDirectionSelector`: 基于EMA趋势
- `RSIBasedDirectionSelector`: 基于RSI超买超卖
- `ExclusiveDirectionSelector`: 互斥模式包装器
- `HedgedDirectionSelector`: 对冲模式包装器

### 2. DualDirectionTradingRecord - 双向交易记录
实现`TradingRecord`接口，内部维护多头和空头两个独立的交易记录。

### 3. BidirectionalStrategyWrapper - 双向策略包装器
包装原始策略，根据方向选择器决定开仓方向。

### 4. BidirectionalExecutionManager - 执行管理器
协调双向持仓的开平仓逻辑。

### 5. BidirectionalExtensionFactory - 工厂类
提供便捷的创建和配置方法。

## 快速开始

### 基本使用

```java
import com.chain.ai.trade.extension.ta4j.bidirectional.*;

// 1. 创建原始策略
Strategy originalStrategy = createYourStrategy();

// 2. 创建方向选择器
DirectionSelector selector = new TrendBasedDirectionSelector(20);

// 3. 创建双向执行环境（互斥模式）
BidirectionalExecutionContext context =
    BidirectionalExtensionFactory.createExclusiveContext(
        barSeries, originalStrategy, selector);

// 4. 执行策略
TradingRecord result = BidirectionalExtensionFactory.runBidirectional(
    context,
    DecimalNum.valueOf(10), // 交易数量
    barSeries.getBeginIndex(),
    barSeries.getEndIndex()
);

// 5. 分析结果
DualDirectionTradingRecord dualRecord = (DualDirectionTradingRecord) result;
System.out.println("多头交易数: " + dualRecord.getLongRecord().getTrades().size());
System.out.println("空头交易数: " + dualRecord.getShortRecord().getTrades().size());
System.out.println("是否对冲: " + dualRecord.hasHedgedPosition());
```

### 对冲模式

```java
// 创建对冲模式的执行环境
BidirectionalExecutionContext hedgedContext =
    BidirectionalExtensionFactory.createHedgedContext(
        barSeries, originalStrategy, selector);

// 在对冲模式下，可以同时持有多空仓位
```

### 自定义方向选择器

```java
// 实现自定义的方向选择器
public class CustomDirectionSelector implements DirectionSelector {
    @Override
    public TradeType selectDirection(int index, BarSeries barSeries,
                                     Strategy strategy, TradingRecord tradingRecord) {
        // 你的自定义逻辑
        return TradeType.BUY; // 或 TradeType.SELL 或 null
    }
}
```

## 架构说明

```
┌─────────────────────────────────────────────────────────────┐
│                    扩展层 (Extension Layer)                    │
├─────────────────────────────────────────────────────────────┤
│  DualDirectionTradingRecord                                │
│  ┌────────────────┐  ┌────────────────┐                    │
│  │LongRecord      │  │ShortRecord    │                    │
│  │(BaseTrading    │  │(BaseTrading   │                    │
│  │ Record)         │  │ Record)       │                    │
│  └────────────────┘  └────────────────┘                    │
│                          │                                    │
│                          ▼                                    │
│  BidirectionalStrategyWrapper                              │
│  ┌────────────────┐  ┌────────────────┐                    │
│  │Direction       │  │Original       │                    │
│  │Selector        │  │Strategy       │                    │
│  └────────────────┘  └────────────────┘                    │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                  TA4J 核心层 (Core Layer)                     │
└─────────────────────────────────────────────────────────────┘
```

## 依赖关系

```xml
<dependency>
    <groupId>com.chain.ai</groupId>
    <artifactId>ai-extension</artifactId>
    <version>${project.version}</version>
</dependency>
```

## 许可证

本项目采用与主项目相同的许可证。