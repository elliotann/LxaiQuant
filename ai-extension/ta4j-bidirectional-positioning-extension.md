# TA4J 双向持仓扩展设计文档

## 快速开始

### 如何选择使用扩展？

**简单判断**：如果你的策略需要同时持有多头和空头仓位，或者需要根据市场条件动态选择开多或开空，那么应该使用双向持仓扩展。

**快速使用**（3步）：

```java
// 步骤1: 创建方向选择器（决定何时开多/开空）
DirectionSelector selector = new TrendBasedDirectionSelector(20.0);

// 步骤2: 创建双向持仓执行环境
BidirectionalExecutionContext context = 
    BidirectionalExtensionFactory.createExclusiveContext(
        barSeries, originalStrategy, selector);

// 步骤3: 执行策略
TradingRecord result = BidirectionalExtensionFactory.runBidirectional(
    context, DecimalNum.valueOf(10), 
    barSeries.getBeginIndex(), barSeries.getEndIndex());
```

**详细说明**：请参考第4章"如何启用和使用扩展"。

---

## 1. 概述

### 1.1 设计目标

本扩展旨在为TA4J框架提供双向持仓支持，允许同一个策略同时持有多头和空头仓位，而**不修改任何TA4J核心类**。扩展通过实现`TradingRecord`接口和策略包装器来实现这一功能。

### 1.2 核心约束

- ✅ **不修改TA4J核心类**：所有扩展代码独立于ta4j-core模块
- ✅ **完全兼容TA4J接口**：扩展类实现标准TA4J接口，可与现有代码无缝集成
- ✅ **保持向后兼容**：现有单方向策略可以继续正常工作
- ✅ **支持双向持仓**：同一策略可以同时持有多头和空头仓位

### 1.3 应用场景

- **对冲策略**：同时持有多空仓位以降低风险
- **市场中性策略**：通过多空配对实现市场中性收益
- **套利策略**：利用价差进行多空套利
- **动态方向选择**：根据市场条件动态选择开多或开空

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    扩展层 (Extension Layer)                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │      DualDirectionTradingRecord                      │   │
│  │  ┌────────────────┐  ┌────────────────┐              │   │
│  │  │LongRecord      │  │ShortRecord    │              │   │
│  │  │(BaseTrading    │  │(BaseTrading   │              │   │
│  │  │ Record)         │  │ Record)       │              │   │
│  │  └────────────────┘  └────────────────┘              │   │
│  └──────────────────────────────────────────────────────┘   │
│                          │                                    │
│                          ▼                                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │      BidirectionalStrategyWrapper                    │   │
│  │  ┌────────────────┐  ┌────────────────┐              │   │
│  │  │Direction       │  │Original       │              │   │
│  │  │Selector        │  │Strategy       │              │   │
│  │  └────────────────┘  └────────────────┘              │   │
│  └──────────────────────────────────────────────────────┘   │
│                          │                                    │
│                          ▼                                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │      BidirectionalExecutionManager                   │   │
│  │  (协调多空仓位的执行逻辑)                                │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                  TA4J 核心层 (Core Layer)                     │
│  (TradingRecord, Strategy, Position, Trade 等)              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件

#### 2.2.1 DualDirectionTradingRecord

**职责**：实现`TradingRecord`接口，内部维护两个`BaseTradingRecord`实例（多头和空头），提供统一的接口访问双向持仓。

**关键特性**：
- 内部维护`longRecord`（`BaseTradingRecord`，`TradeType.BUY`）和`shortRecord`（`BaseTradingRecord`，`TradeType.SELL`）
- `getCurrentPosition()`返回一个虚拟的合并Position或当前活跃的Position
- 所有交易操作根据方向路由到对应的内部记录
- 聚合所有交易、持仓和统计信息

#### 2.2.2 BidirectionalStrategyWrapper

**职责**：包装原始策略，将策略信号转换为双向持仓信号。

**关键特性**：
- 实现`Strategy`接口，内部持有原始策略
- 通过`DirectionSelector`决定开多还是开空
- 重写`shouldEnter`和`shouldExit`方法，根据方向选择逻辑处理信号

#### 2.2.3 DirectionSelector

**职责**：根据市场条件和策略状态决定应该开多还是开空。

**接口设计**：
```java
public interface DirectionSelector {
    /**
     * 决定应该开多还是开空
     * @param index 当前bar索引
     * @param barSeries 价格序列
     * @param strategy 原始策略
     * @param tradingRecord 交易记录
     * @return TradeType.BUY表示开多，TradeType.SELL表示开空，null表示不开仓
     */
    TradeType selectDirection(int index, BarSeries barSeries, 
                             Strategy strategy, TradingRecord tradingRecord);
}
```

#### 2.2.4 BidirectionalExecutionManager

**职责**：协调双向持仓的执行逻辑，处理多空仓位的开平仓。

**关键特性**：
- 管理多空仓位的独立开平仓逻辑
- 支持对冲模式（同时持有多空仓位）
- 支持互斥模式（同一时间只能持有一个方向的仓位）
- 处理仓位转换（从多转空或从空转多）

## 3. 详细设计

### 3.1 DualDirectionTradingRecord 实现

#### 3.1.1 类结构

```java
package org.ta4j.extension.bidirectional;

import org.ta4j.core.*;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;
import java.util.*;

/**
 * 双向持仓交易记录实现
 * 
 * 内部维护两个BaseTradingRecord实例：
 * - longRecord: 管理多头仓位（TradeType.BUY）
 * - shortRecord: 管理空头仓位（TradeType.SELL）
 */
public class DualDirectionTradingRecord implements TradingRecord {
    
    private final BaseTradingRecord longRecord;
    private final BaseTradingRecord shortRecord;
    private final String name;
    private final Integer startIndex;
    private final Integer endIndex;
    
    // 当前活跃方向（用于getCurrentPosition()）
    private TradeType activeDirection;
    
    public DualDirectionTradingRecord(String name, Integer startIndex, Integer endIndex,
                                     CostModel transactionCostModel, CostModel holdingCostModel) {
        this.name = name;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.longRecord = new BaseTradingRecord(TradeType.BUY, startIndex, endIndex, 
                                                transactionCostModel, holdingCostModel);
        this.shortRecord = new BaseTradingRecord(TradeType.SELL, startIndex, endIndex, 
                                                 transactionCostModel, holdingCostModel);
        this.activeDirection = null;
    }
    
    // ... 实现TradingRecord接口的所有方法
}
```

#### 3.1.2 关键方法实现

**operate方法**：
```java
@Override
public void operate(int index, Num price, Num amount) {
    // 根据activeDirection路由到对应的记录
    if (activeDirection == TradeType.BUY) {
        longRecord.operate(index, price, amount);
    } else if (activeDirection == TradeType.SELL) {
        shortRecord.operate(index, price, amount);
    } else {
        throw new IllegalStateException("No active direction set");
    }
}
```

**enter方法**：
```java
@Override
public boolean enter(int index, Num price, Num amount) {
    return enter(index, price, amount, determineDirection());
}

/**
 * 指定方向的开仓
 */
public boolean enter(int index, Num price, Num amount, TradeType direction) {
    if (direction == TradeType.BUY) {
        boolean entered = longRecord.enter(index, price, amount);
        if (entered) {
            activeDirection = TradeType.BUY;
        }
        return entered;
    } else if (direction == TradeType.SELL) {
        boolean entered = shortRecord.enter(index, price, amount);
        if (entered) {
            activeDirection = TradeType.SELL;
        }
        return entered;
    }
    return false;
}
```

**exit方法**：
```java
@Override
public boolean exit(int index, Num price, Num amount) {
    if (activeDirection == TradeType.BUY) {
        boolean exited = longRecord.exit(index, price, amount);
        if (exited && longRecord.getCurrentPosition().isNew()) {
            activeDirection = null;
        }
        return exited;
    } else if (activeDirection == TradeType.SELL) {
        boolean exited = shortRecord.exit(index, price, amount);
        if (exited && shortRecord.getCurrentPosition().isNew()) {
            activeDirection = null;
        }
        return exited;
    }
    return false;
}
```

**getCurrentPosition方法**：
```java
@Override
public Position getCurrentPosition() {
    // 返回当前活跃方向的position
    if (activeDirection == TradeType.BUY) {
        return longRecord.getCurrentPosition();
    } else if (activeDirection == TradeType.SELL) {
        return shortRecord.getCurrentPosition();
    }
    // 如果两个方向都没有持仓，返回一个new position
    return new Position(TradeType.BUY);
}
```

**聚合方法**：
```java
@Override
public List<Trade> getTrades() {
    List<Trade> allTrades = new ArrayList<>();
    allTrades.addAll(longRecord.getTrades());
    allTrades.addAll(shortRecord.getTrades());
    // 按索引排序
    allTrades.sort(Comparator.comparing(Trade::getIndex));
    return allTrades;
}

@Override
public List<Position> getPositions() {
    List<Position> allPositions = new ArrayList<>();
    allPositions.addAll(longRecord.getPositions());
    allPositions.addAll(shortRecord.getPositions());
    // 按关闭时间排序
    allPositions.sort(Comparator.comparing(p -> p.getExit().getIndex()));
    return allPositions;
}
```

#### 3.1.3 双向持仓查询方法

```java
/**
 * 获取多头交易记录
 */
public TradingRecord getLongRecord() {
    return longRecord;
}

/**
 * 获取空头交易记录
 */
public TradingRecord getShortRecord() {
    return shortRecord;
}

/**
 * 检查是否同时持有多空仓位（对冲模式）
 */
public boolean hasHedgedPosition() {
    return longRecord.getCurrentPosition().isOpened() 
        && shortRecord.getCurrentPosition().isOpened();
}

/**
 * 获取净持仓方向
 * @return BUY表示净多头，SELL表示净空头，null表示无持仓
 */
public TradeType getNetPositionDirection() {
    boolean longOpen = longRecord.getCurrentPosition().isOpened();
    boolean shortOpen = shortRecord.getCurrentPosition().isOpened();
    
    if (longOpen && !shortOpen) {
        return TradeType.BUY;
    } else if (shortOpen && !longOpen) {
        return TradeType.SELL;
    } else if (longOpen && shortOpen) {
        // 对冲状态，可以根据持仓量计算净方向
        Num longAmount = longRecord.getCurrentPosition().getEntry().getAmount();
        Num shortAmount = shortRecord.getCurrentPosition().getEntry().getAmount();
        if (longAmount.isGreaterThan(shortAmount)) {
            return TradeType.BUY;
        } else if (shortAmount.isGreaterThan(longAmount)) {
            return TradeType.SELL;
        }
        return null; // 完全对冲
    }
    return null;
}
```

### 3.2 BidirectionalStrategyWrapper 实现

#### 3.2.1 类结构

```java
package org.ta4j.extension.bidirectional;

import org.ta4j.core.*;

/**
 * 双向持仓策略包装器
 * 
 * 包装原始策略，通过DirectionSelector决定开多还是开空
 */
public class BidirectionalStrategyWrapper implements Strategy {
    
    private final Strategy originalStrategy;
    private final DirectionSelector directionSelector;
    private final BarSeries barSeries;
    
    public BidirectionalStrategyWrapper(Strategy originalStrategy, 
                                       DirectionSelector directionSelector,
                                       BarSeries barSeries) {
        this.originalStrategy = originalStrategy;
        this.directionSelector = directionSelector;
        this.barSeries = barSeries;
    }
    
    // 委托原始策略的方法
    @Override
    public String getName() {
        return "Bidirectional[" + originalStrategy.getName() + "]";
    }
    
    @Override
    public Rule getEntryRule() {
        return originalStrategy.getEntryRule();
    }
    
    @Override
    public Rule getExitRule() {
        return originalStrategy.getExitRule();
    }
    
    @Override
    public int getUnstableBars() {
        return originalStrategy.getUnstableBars();
    }
    
    @Override
    public boolean isUnstableAt(int index) {
        return originalStrategy.isUnstableAt(index);
    }
    
    // 重写关键方法
    @Override
    public boolean shouldOperate(int index, TradingRecord tradingRecord) {
        if (!(tradingRecord instanceof DualDirectionTradingRecord)) {
            // 如果不是双向记录，回退到原始策略
            return originalStrategy.shouldOperate(index, tradingRecord);
        }
        
        DualDirectionTradingRecord dualRecord = (DualDirectionTradingRecord) tradingRecord;
        Position currentPosition = tradingRecord.getCurrentPosition();
        
        if (currentPosition.isNew()) {
            // 无持仓，检查是否应该开仓
            return shouldEnter(index, tradingRecord);
        } else if (currentPosition.isOpened()) {
            // 有持仓，检查是否应该平仓
            return shouldExit(index, tradingRecord);
        }
        return false;
    }
    
    @Override
    public boolean shouldEnter(int index, TradingRecord tradingRecord) {
        if (!(tradingRecord instanceof DualDirectionTradingRecord)) {
            return originalStrategy.shouldEnter(index, tradingRecord);
        }
        
        // 检查原始策略是否建议开仓
        if (!originalStrategy.shouldEnter(index, tradingRecord)) {
            return false;
        }
        
        // 通过DirectionSelector决定方向
        DualDirectionTradingRecord dualRecord = (DualDirectionTradingRecord) tradingRecord;
        TradeType direction = directionSelector.selectDirection(index, barSeries, 
                                                                originalStrategy, tradingRecord);
        
        return direction != null;
    }
    
    @Override
    public boolean shouldExit(int index, TradingRecord tradingRecord) {
        if (!(tradingRecord instanceof DualDirectionTradingRecord)) {
            return originalStrategy.shouldExit(index, tradingRecord);
        }
        
        // 直接使用原始策略的退出逻辑
        return originalStrategy.shouldExit(index, tradingRecord);
    }
}
```

### 3.3 DirectionSelector 接口及实现

#### 3.3.1 接口定义

```java
package org.ta4j.extension.bidirectional;

import org.ta4j.core.*;

/**
 * 方向选择器接口
 * 
 * 根据市场条件和策略状态决定应该开多还是开空
 */
public interface DirectionSelector {
    
    /**
     * 决定应该开多还是开空
     * 
     * @param index 当前bar索引
     * @param barSeries 价格序列
     * @param strategy 原始策略
     * @param tradingRecord 交易记录
     * @return TradeType.BUY表示开多，TradeType.SELL表示开空，null表示不开仓
     */
    TradeType selectDirection(int index, BarSeries barSeries, 
                             Strategy strategy, TradingRecord tradingRecord);
}
```

#### 3.3.2 基于趋势的方向选择器

```java
package org.ta4j.extension.bidirectional;

import org.ta4j.core.*;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * 基于EMA趋势的方向选择器
 * 
 * 当价格在EMA之上时开多，在EMA之下时开空
 */
public class TrendBasedDirectionSelector implements DirectionSelector {
    
    private final int emaPeriod;
    
    public TrendBasedDirectionSelector(double emaPeriod) {
        this.emaPeriod = emaPeriod;
    }
    
    @Override
    public TradeType selectDirection(int index, BarSeries barSeries, 
                                     Strategy strategy, TradingRecord tradingRecord) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        EMAIndicator ema = new EMAIndicator(closePrice, emaPeriod);
        
        Num currentPrice = closePrice.getValue(index);
        Num emaValue = ema.getValue(index);
        
        if (currentPrice.isGreaterThan(emaValue)) {
            return TradeType.BUY; // 价格在EMA之上，开多
        } else if (currentPrice.isLessThan(emaValue)) {
            return TradeType.SELL; // 价格在EMA之下，开空
        }
        
        return null; // 价格等于EMA，不开仓
    }
}
```

#### 3.3.3 基于RSI的方向选择器

```java
package org.ta4j.extension.bidirectional;

import org.ta4j.core.*;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * 基于RSI的方向选择器
 * 
 * RSI < 30时开多（超卖），RSI > 70时开空（超买）
 */
public class RSIBasedDirectionSelector implements DirectionSelector {
    
    private final int rsiPeriod;
    private final Num oversoldThreshold;
    private final Num overboughtThreshold;
    
    public RSIBasedDirectionSelector(int rsiPeriod, double oversoldThreshold, double overboughtThreshold) {
        this.rsiPeriod = rsiPeriod;
        this.oversoldThreshold = oversoldThreshold;
        this.overboughtThreshold = overboughtThreshold;
    }
    
    @Override
    public TradeType selectDirection(int index, BarSeries barSeries, 
                                     Strategy strategy, TradingRecord tradingRecord) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        RSIIndicator rsi = new RSIIndicator(closePrice, rsiPeriod);
        
        Num rsiValue = rsi.getValue(index);
        
        if (rsiValue.isLessThan(oversoldThreshold)) {
            return TradeType.BUY; // 超卖，开多
        } else if (rsiValue.isGreaterThan(overboughtThreshold)) {
            return TradeType.SELL; // 超买，开空
        }
        
        return null; // RSI在正常范围，不开仓
    }
}
```

#### 3.3.4 互斥方向选择器

```java
package org.ta4j.extension.bidirectional;

import org.ta4j.core.*;

/**
 * 互斥方向选择器
 * 
 * 确保同一时间只能持有一个方向的仓位
 * 如果已有持仓，则不允许开相反方向的仓位
 */
public class ExclusiveDirectionSelector implements DirectionSelector {
    
    private final DirectionSelector baseSelector;
    
    public ExclusiveDirectionSelector(DirectionSelector baseSelector) {
        this.baseSelector = baseSelector;
    }
    
    @Override
    public TradeType selectDirection(int index, BarSeries barSeries, 
                                     Strategy strategy, TradingRecord tradingRecord) {
        if (!(tradingRecord instanceof DualDirectionTradingRecord)) {
            return baseSelector.selectDirection(index, barSeries, strategy, tradingRecord);
        }
        
        DualDirectionTradingRecord dualRecord = (DualDirectionTradingRecord) tradingRecord;
        
        // 如果已有持仓，不允许开相反方向的仓位
        if (dualRecord.getLongRecord().getCurrentPosition().isOpened()) {
            // 已有多头，只能开多或不开
            TradeType direction = baseSelector.selectDirection(index, barSeries, strategy, tradingRecord);
            return direction == TradeType.BUY ? TradeType.BUY : null;
        }
        
        if (dualRecord.getShortRecord().getCurrentPosition().isOpened()) {
            // 已有空头，只能开空或不开
            TradeType direction = baseSelector.selectDirection(index, barSeries, strategy, tradingRecord);
            return direction == TradeType.SELL ? TradeType.SELL : null;
        }
        
        // 无持仓，使用基础选择器
        return baseSelector.selectDirection(index, barSeries, strategy, tradingRecord);
    }
}
```

#### 3.3.5 对冲模式方向选择器

```java
package org.ta4j.extension.bidirectional;

import org.ta4j.core.*;

/**
 * 对冲模式方向选择器
 * 
 * 允许同时持有多空仓位，根据信号决定开多还是开空
 */
public class HedgedDirectionSelector implements DirectionSelector {
    
    private final DirectionSelector baseSelector;
    
    public HedgedDirectionSelector(DirectionSelector baseSelector) {
        this.baseSelector = baseSelector;
    }
    
    @Override
    public TradeType selectDirection(int index, BarSeries barSeries, 
                                     Strategy strategy, TradingRecord tradingRecord) {
        // 对冲模式下，允许同时持有多空仓位
        // 直接使用基础选择器的结果
        return baseSelector.selectDirection(index, barSeries, strategy, tradingRecord);
    }
}
```

### 3.4 BidirectionalExecutionManager 实现

#### 3.4.1 类结构

```java
package org.ta4j.extension.bidirectional;

import org.ta4j.core.*;
import org.ta4j.core.backtest.TradeExecutionModel;
import org.ta4j.core.num.Num;

/**
 * 双向持仓执行管理器
 * 
 * 协调双向持仓的执行逻辑，处理多空仓位的开平仓
 */
public class BidirectionalExecutionManager {
    
    private final TradeExecutionModel tradeExecutionModel;
    private final boolean allowHedging; // 是否允许对冲（同时持有多空）
    
    public BidirectionalExecutionManager(TradeExecutionModel tradeExecutionModel, 
                                        boolean allowHedging) {
        this.tradeExecutionModel = tradeExecutionModel;
        this.allowHedging = allowHedging;
    }
    
    /**
     * 执行双向持仓策略
     * 
     * @param index 当前bar索引
     * @param strategy 双向策略包装器
     * @param tradingRecord 双向交易记录
     * @param barSeries 价格序列
     * @param amount 交易数量
     */
    public void execute(int index, BidirectionalStrategyWrapper strategy,
                       DualDirectionTradingRecord tradingRecord, 
                       BarSeries barSeries, Num amount) {
        
        if (!strategy.shouldOperate(index, tradingRecord)) {
            return;
        }
        
        Position currentPosition = tradingRecord.getCurrentPosition();
        
        if (currentPosition.isNew()) {
            // 无持仓，尝试开仓
            handleEntry(index, strategy, tradingRecord, barSeries, amount);
        } else if (currentPosition.isOpened()) {
            // 有持仓，尝试平仓
            handleExit(index, strategy, tradingRecord, barSeries, amount);
        }
    }
    
    private void handleEntry(int index, BidirectionalStrategyWrapper strategy,
                            DualDirectionTradingRecord tradingRecord,
                            BarSeries barSeries, Num amount) {
        
        if (!strategy.shouldEnter(index, tradingRecord)) {
            return;
        }
        
        DirectionSelector directionSelector = strategy.getDirectionSelector();
        TradeType direction = directionSelector.selectDirection(index, barSeries, 
                                                                strategy.getOriginalStrategy(), 
                                                                tradingRecord);
        
        if (direction == null) {
            return;
        }
        
        // 检查是否允许开仓
        if (!allowHedging) {
            // 互斥模式：如果已有相反方向的持仓，先平仓
            if (direction == TradeType.BUY && 
                tradingRecord.getShortRecord().getCurrentPosition().isOpened()) {
                // 要开多，但已有空头，先平空
                closePosition(index, tradingRecord.getShortRecord(), barSeries, amount);
            } else if (direction == TradeType.SELL && 
                      tradingRecord.getLongRecord().getCurrentPosition().isOpened()) {
                // 要开空，但已有多头，先平多
                closePosition(index, tradingRecord.getLongRecord(), barSeries, amount);
            }
        }
        
        // 执行开仓
        TradingRecord targetRecord = direction == TradeType.BUY ? 
            tradingRecord.getLongRecord() : tradingRecord.getShortRecord();
        
        tradeExecutionModel.execute(index, targetRecord, barSeries, amount);
        
        // 更新活跃方向
        if (targetRecord.getCurrentPosition().isOpened()) {
            tradingRecord.setActiveDirection(direction);
        }
    }
    
    private void handleExit(int index, BidirectionalStrategyWrapper strategy,
                           DualDirectionTradingRecord tradingRecord,
                           BarSeries barSeries, Num amount) {
        
        if (!strategy.shouldExit(index, tradingRecord)) {
            return;
        }
        
        // 平当前活跃方向的仓位
        TradeType activeDirection = tradingRecord.getActiveDirection();
        if (activeDirection != null) {
            TradingRecord targetRecord = activeDirection == TradeType.BUY ? 
                tradingRecord.getLongRecord() : tradingRecord.getShortRecord();
            
            tradeExecutionModel.execute(index, targetRecord, barSeries, amount);
            
            // 如果仓位已平，清除活跃方向
            if (targetRecord.getCurrentPosition().isNew()) {
                tradingRecord.setActiveDirection(null);
            }
        }
    }
    
    private void closePosition(int index, TradingRecord record, 
                              BarSeries barSeries, Num amount) {
        if (record.getCurrentPosition().isOpened()) {
            tradeExecutionModel.execute(index, record, barSeries, amount);
        }
    }
}
```

## 4. 如何启用和使用扩展

### 4.1 使用场景判断

#### 决策流程图

```
开始
  │
  ▼
是否需要同时持有多空仓位？
  │
  ├─ 是 ──→ 使用双向持仓扩展
  │         │
  │         ├─ 需要对冲？ ──→ 使用对冲模式 (allowHedging=true)
  │         │
  │         └─ 互斥持仓？ ──→ 使用互斥模式 (allowHedging=false)
  │
  └─ 否 ──→ 是否需要动态选择开多/开空？
            │
            ├─ 是 ──→ 使用双向持仓扩展 + 方向选择器
            │
            └─ 否 ──→ 使用标准TA4J模式
                      (BarSeriesManager.run())
```

#### 判断标准

在决定是否使用双向持仓扩展时，需要考虑以下因素：

#### 4.1.1 适合使用双向持仓的场景

- ✅ **需要对冲风险**：希望同时持有多空仓位以降低市场方向性风险
- ✅ **市场中性策略**：通过多空配对实现市场中性收益
- ✅ **动态方向选择**：策略需要根据市场条件动态选择开多或开空
- ✅ **套利策略**：利用价差进行多空套利
- ✅ **波动率交易**：在波动市场中通过双向持仓获利

#### 4.1.2 不适合使用双向持仓的场景

- ❌ **简单趋势跟踪**：只需要单向持仓的趋势策略
- ❌ **长期持有策略**：买入并持有的长期投资策略
- ❌ **资源受限环境**：对内存和计算资源有严格限制的场景

#### 4.1.3 标准模式 vs 双向持仓模式对比

| 特性 | 标准模式 | 双向持仓模式 |
|------|---------|-------------|
| **持仓方向** | 单一方向（BUY或SELL） | 可同时持有多空 |
| **方向选择** | 固定（构造时指定） | 动态（通过DirectionSelector） |
| **使用场景** | 单向趋势策略 | 对冲、套利、市场中性策略 |
| **代码复杂度** | 简单 | 中等（需要配置方向选择器） |
| **内存开销** | 低 | 中等（约为标准模式的2倍） |
| **执行方式** | `BarSeriesManager.run()` | `BidirectionalExtensionFactory.runBidirectional()` |
| **兼容性** | TA4J原生支持 | 扩展实现，完全兼容TA4J接口 |
| **适用策略** | 趋势跟踪、买入持有 | 对冲、套利、动态方向选择 |

### 4.2 执行模式选择

扩展提供了两种使用方式：

#### 方式一：直接使用扩展组件（推荐用于自定义执行逻辑）

```java
// 1. 创建双向持仓组件
DualDirectionTradingRecord tradingRecord = new DualDirectionTradingRecord(...);
BidirectionalStrategyWrapper strategy = new BidirectionalStrategyWrapper(...);
BidirectionalExecutionManager executionManager = new BidirectionalExecutionManager(...);

// 2. 手动执行循环
for (int i = startIndex; i <= endIndex; i++) {
    executionManager.execute(i, strategy, tradingRecord, barSeries, amount);
}
```

#### 方式二：通过扩展的BarSeriesManager（推荐用于标准回测）

```java
// 使用扩展的BarSeriesManager，自动处理双向持仓逻辑
BidirectionalBarSeriesManager manager = new BidirectionalBarSeriesManager(...);
TradingRecord record = manager.runBidirectional(amount, startIndex, endIndex);
```

### 4.3 配置和工厂类

为了简化使用，可以创建一个工厂类来统一管理扩展的创建和选择：

```java
package org.ta4j.extension.bidirectional;

import org.ta4j.core.*;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.backtest.TradeExecutionModel;
import org.ta4j.core.num.Num;

/**
 * 双向持仓扩展工厂类
 * 
 * 提供便捷的方法来创建和配置双向持仓组件
 */
public class BidirectionalExtensionFactory {
    
    /**
     * 创建双向持仓执行环境
     * 
     * @param barSeries 价格序列
     * @param originalStrategy 原始策略
     * @param directionSelector 方向选择器
     * @param transactionCostModel 交易成本模型
     * @param holdingCostModel 持仓成本模型
     * @param tradeExecutionModel 交易执行模型
     * @param allowHedging 是否允许对冲
     * @return 双向持仓执行环境
     */
    public static BidirectionalExecutionContext createExecutionContext(
            BarSeries barSeries,
            Strategy originalStrategy,
            DirectionSelector directionSelector,
            CostModel transactionCostModel,
            CostModel holdingCostModel,
            TradeExecutionModel tradeExecutionModel,
            boolean allowHedging) {
        
        // 创建双向策略包装器
        BidirectionalStrategyWrapper bidirectionalStrategy = 
            new BidirectionalStrategyWrapper(originalStrategy, directionSelector, barSeries);
        
        // 创建双向交易记录
        DualDirectionTradingRecord tradingRecord = new DualDirectionTradingRecord(
            bidirectionalStrategy.getName(),
            barSeries.getBeginIndex(),
            barSeries.getEndIndex(),
            transactionCostModel,
            holdingCostModel
        );
        
        // 创建执行管理器
        BidirectionalExecutionManager executionManager = 
            new BidirectionalExecutionManager(tradeExecutionModel, allowHedging);
        
        return new BidirectionalExecutionContext(
            bidirectionalStrategy,
            tradingRecord,
            executionManager,
            barSeries
        );
    }
    
    /**
     * 创建标准双向持仓环境（互斥模式）
     */
    public static BidirectionalExecutionContext createExclusiveContext(
            BarSeries barSeries,
            Strategy originalStrategy,
            DirectionSelector directionSelector) {
        
        return createExecutionContext(
            barSeries,
            originalStrategy,
            new ExclusiveDirectionSelector(directionSelector),
            new ZeroCostModel(),
            new ZeroCostModel(),
            new TradeOnCurrentCloseModel(),
            false // 互斥模式
        );
    }
    
    /**
     * 创建对冲双向持仓环境
     */
    public static BidirectionalExecutionContext createHedgedContext(
            BarSeries barSeries,
            Strategy originalStrategy,
            DirectionSelector directionSelector) {
        
        return createExecutionContext(
            barSeries,
            originalStrategy,
            new HedgedDirectionSelector(directionSelector),
            new ZeroCostModel(),
            new ZeroCostModel(),
            new TradeOnCurrentCloseModel(),
            true // 允许对冲
        );
    }
    
    /**
     * 执行双向持仓策略
     */
    public static TradingRecord runBidirectional(
            BidirectionalExecutionContext context,
            Num amount,
            int startIndex,
            int endIndex) {
        
        int runBeginIndex = Math.max(startIndex, context.getBarSeries().getBeginIndex());
        int runEndIndex = Math.min(endIndex, context.getBarSeries().getEndIndex());
        
        for (int i = runBeginIndex; i <= runEndIndex; i++) {
            context.getExecutionManager().execute(
                i,
                context.getStrategy(),
                context.getTradingRecord(),
                context.getBarSeries(),
                amount
            );
        }
        
        return context.getTradingRecord();
    }
}

/**
 * 双向持仓执行上下文
 * 封装所有双向持仓相关的组件
 */
class BidirectionalExecutionContext {
    private final BidirectionalStrategyWrapper strategy;
    private final DualDirectionTradingRecord tradingRecord;
    private final BidirectionalExecutionManager executionManager;
    private final BarSeries barSeries;
    
    public BidirectionalExecutionContext(
            BidirectionalStrategyWrapper strategy,
            DualDirectionTradingRecord tradingRecord,
            BidirectionalExecutionManager executionManager,
            BarSeries barSeries) {
        this.strategy = strategy;
        this.tradingRecord = tradingRecord;
        this.executionManager = executionManager;
        this.barSeries = barSeries;
    }
    
    // Getters
    public BidirectionalStrategyWrapper getStrategy() { return strategy; }
    public DualDirectionTradingRecord getTradingRecord() { return tradingRecord; }
    public BidirectionalExecutionManager getExecutionManager() { return executionManager; }
    public BarSeries getBarSeries() { return barSeries; }
}
```

### 4.4 实际执行示例

#### 示例1：标准回测场景（使用工厂类）

```java
import org.ta4j.core.*;
import org.ta4j.extension.bidirectional.*;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

public class BidirectionalBacktestExample {
    
    public static void main(String[] args) {
        // 1. 加载数据
        BarSeries series = loadBarSeries();
        
        // 2. 创建原始策略
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator shortEma = new EMAIndicator(closePrice, 10);
        EMAIndicator longEma = new EMAIndicator(closePrice, 20);
        
        Rule entryRule = new CrossedUpIndicatorRule(shortEma, longEma);
        Rule exitRule = new CrossedDownIndicatorRule(shortEma, longEma);
        Strategy originalStrategy = new BaseStrategy(entryRule, exitRule);
        
        // 3. 创建方向选择器（基于趋势）
        DirectionSelector directionSelector = new TrendBasedDirectionSelector(20.0);
        
        // 4. 选择执行模式
        boolean useBidirectional = shouldUseBidirectional(); // 你的判断逻辑
        
        TradingRecord tradingRecord;
        
        if (useBidirectional) {
            // 使用双向持仓扩展
            BidirectionalExecutionContext context = 
                BidirectionalExtensionFactory.createExclusiveContext(
                    series, originalStrategy, directionSelector);
            
            tradingRecord = BidirectionalExtensionFactory.runBidirectional(
                context,
                DecimalNum.valueOf(10),
                series.getBeginIndex(),
                series.getEndIndex()
            );
            
            // 分析双向持仓结果
            analyzeBidirectionalResults((DualDirectionTradingRecord) tradingRecord);
            
        } else {
            // 使用标准TA4J方式
            BarSeriesManager manager = new BarSeriesManager(series);
            tradingRecord = manager.run(originalStrategy, TradeType.BUY);
            
            // 分析标准结果
            analyzeStandardResults(tradingRecord);
        }
        
        // 5. 输出结果
        System.out.println("Total trades: " + tradingRecord.getTrades().size());
        System.out.println("Total positions: " + tradingRecord.getPositions().size());
    }
    
    private static boolean shouldUseBidirectional() {
        // 根据你的业务逻辑判断
        // 例如：配置文件、策略类型、市场条件等
        return true; // 或 false
    }
    
    private static void analyzeBidirectionalResults(DualDirectionTradingRecord record) {
        System.out.println("Long positions: " + record.getLongRecord().getPositions().size());
        System.out.println("Short positions: " + record.getShortRecord().getPositions().size());
        System.out.println("Has hedged position: " + record.hasHedgedPosition());
        System.out.println("Net direction: " + record.getNetPositionDirection());
    }
    
    private static void analyzeStandardResults(TradingRecord record) {
        System.out.println("Standard trading record analysis");
    }
}
```

#### 示例2：与现有代码集成（适配器模式）

```java
/**
 * 双向持仓适配器
 * 允许在现有代码中无缝切换标准模式和双向模式
 */
public class TradingRecordAdapter {
    
    private final boolean useBidirectional;
    private final BidirectionalExecutionContext bidirectionalContext;
    private final BarSeriesManager standardManager;
    private final Strategy standardStrategy;
    
    public TradingRecordAdapter(BarSeries barSeries, Strategy strategy, 
                               boolean useBidirectional) {
        this.useBidirectional = useBidirectional;
        
        if (useBidirectional) {
            // 创建双向持仓环境
            DirectionSelector selector = new TrendBasedDirectionSelector(20.0);
            this.bidirectionalContext = 
                BidirectionalExtensionFactory.createExclusiveContext(
                    barSeries, strategy, selector);
            this.standardManager = null;
            this.standardStrategy = null;
        } else {
            // 使用标准模式
            this.standardManager = new BarSeriesManager(barSeries);
            this.standardStrategy = strategy;
            this.bidirectionalContext = null;
        }
    }
    
    /**
     * 执行策略（自动选择模式）
     */
    public TradingRecord run(Num amount, int startIndex, int endIndex) {
        if (useBidirectional) {
            return BidirectionalExtensionFactory.runBidirectional(
                bidirectionalContext, amount, startIndex, endIndex);
        } else {
            return standardManager.run(standardStrategy, TradeType.BUY, 
                                     amount, startIndex, endIndex);
        }
    }
    
    /**
     * 检查是否使用双向持仓
     */
    public boolean isBidirectional() {
        return useBidirectional;
    }
}
```

#### 示例3：配置驱动的选择

```java
/**
 * 基于配置的执行器
 * 通过配置文件或环境变量控制是否使用双向持仓
 */
public class ConfigurableTradingExecutor {
    
    public static TradingRecord execute(BarSeries series, Strategy strategy) {
        // 从配置读取
        boolean enableBidirectional = getConfig("enable.bidirectional", false);
        String directionSelectorType = getConfig("direction.selector.type", "trend");
        boolean allowHedging = getConfig("allow.hedging", false);
        
        if (enableBidirectional) {
            // 创建方向选择器
            DirectionSelector selector = createDirectionSelector(
                directionSelectorType, series);
            
            // 创建执行上下文
            BidirectionalExecutionContext context = 
                BidirectionalExtensionFactory.createExecutionContext(
                    series, strategy, selector,
                    new ZeroCostModel(), new ZeroCostModel(),
                    new TradeOnCurrentCloseModel(), allowHedging);
            
            // 执行
            return BidirectionalExtensionFactory.runBidirectional(
                context,
                DecimalNum.valueOf(getConfig("trade.amount", 10)),
                series.getBeginIndex(),
                series.getEndIndex()
            );
        } else {
            // 标准执行
            BarSeriesManager manager = new BarSeriesManager(series);
            return manager.run(strategy);
        }
    }
    
    private static DirectionSelector createDirectionSelector(
            String type, BarSeries series) {
        switch (type) {
            case "trend":
                return new TrendBasedDirectionSelector(20.0);
            case "rsi":
                return new RSIBasedDirectionSelector(14, 30.0, 70.0);
            default:
                return new TrendBasedDirectionSelector(20.0);
        }
    }
    
    private static boolean getConfig(String key, boolean defaultValue) {
        // 从配置文件、环境变量或系统属性读取
        String value = System.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    private static int getConfig(String key, int defaultValue) {
        String value = System.getProperty(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }
}
```

### 4.5 运行时选择策略

#### 策略1：基于策略类型

```java
public static boolean shouldUseBidirectional(Strategy strategy) {
    // 根据策略名称或类型判断
    String strategyName = strategy.getName().toLowerCase();
    return strategyName.contains("hedge") || 
           strategyName.contains("arbitrage") ||
           strategyName.contains("market-neutral");
}
```

#### 策略2：基于市场条件

```java
public static boolean shouldUseBidirectional(BarSeries series) {
    // 计算市场波动率
    Num volatility = calculateVolatility(series);
    Num threshold = DecimalNum.valueOf(0.02); // 2%波动率阈值
    
    // 高波动率市场使用双向持仓
    return volatility.isGreaterThan(threshold);
}
```

#### 策略3：基于性能指标

```java
public static boolean shouldUseBidirectional(
        Strategy strategy, BarSeries series) {
    
    // 先用标准模式回测
    BarSeriesManager manager = new BarSeriesManager(series);
    TradingRecord standardRecord = manager.run(strategy);
    Num standardReturn = new GrossReturnCriterion().calculate(series, standardRecord);
    
    // 如果标准模式收益不佳，尝试双向持仓
    if (standardReturn.isLessThan(DecimalNum.valueOf(1.0))) {
        return true; // 尝试双向持仓
    }
    
    return false;
}
```

### 4.6 实际项目集成示例

以下示例展示如何在实际项目中根据配置和条件选择使用扩展：

```java
/**
 * 实际项目中的策略执行器
 * 根据配置和策略类型自动选择使用标准模式或双向持仓模式
 */
public class ProjectStrategyExecutor {
    
    private final Properties config;
    private final BarSeries barSeries;
    
    public ProjectStrategyExecutor(Properties config, BarSeries barSeries) {
        this.config = config;
        this.barSeries = barSeries;
    }
    
    /**
     * 执行策略（自动选择模式）
     */
    public TradingRecord executeStrategy(Strategy strategy) {
        // 1. 检查配置是否强制启用双向持仓
        boolean forceBidirectional = Boolean.parseBoolean(
            config.getProperty("strategy.bidirectional.enabled", "false"));
        
        // 2. 检查策略类型是否适合双向持仓
        boolean strategySupportsBidirectional = 
            checkStrategyType(strategy);
        
        // 3. 检查市场条件是否适合双向持仓
        boolean marketConditionSuitable = 
            checkMarketCondition(barSeries);
        
        // 4. 决定使用哪种模式
        boolean useBidirectional = forceBidirectional || 
                                 (strategySupportsBidirectional && marketConditionSuitable);
        
        if (useBidirectional) {
            return executeBidirectional(strategy);
        } else {
            return executeStandard(strategy);
        }
    }
    
    /**
     * 执行双向持仓策略
     */
    private TradingRecord executeBidirectional(Strategy strategy) {
        // 读取配置
        String selectorType = config.getProperty(
            "strategy.bidirectional.selector", "trend");
        boolean allowHedging = Boolean.parseBoolean(
            config.getProperty("strategy.bidirectional.hedging", "false"));
        Num amount = DecimalNum.valueOf(
            Double.parseDouble(config.getProperty("strategy.amount", "10")));
        
        // 创建方向选择器
        DirectionSelector selector = createDirectionSelector(selectorType);
        
        // 创建执行上下文
        BidirectionalExecutionContext context;
        if (allowHedging) {
            context = BidirectionalExtensionFactory.createHedgedContext(
                barSeries, strategy, selector);
        } else {
            context = BidirectionalExtensionFactory.createExclusiveContext(
                barSeries, strategy, selector);
        }
        
        // 执行策略
        return BidirectionalExtensionFactory.runBidirectional(
            context, amount, 
            barSeries.getBeginIndex(), 
            barSeries.getEndIndex());
    }
    
    /**
     * 执行标准策略
     */
    private TradingRecord executeStandard(Strategy strategy) {
        BarSeriesManager manager = new BarSeriesManager(barSeries);
        TradeType tradeType = TradeType.valueOf(
            config.getProperty("strategy.trade.type", "BUY"));
        Num amount = DecimalNum.valueOf(
            Double.parseDouble(config.getProperty("strategy.amount", "10")));
        
        return manager.run(strategy, tradeType, amount);
    }
    
    /**
     * 检查策略类型
     */
    private boolean checkStrategyType(Strategy strategy) {
        String name = strategy.getName().toLowerCase();
        // 这些类型的策略通常适合双向持仓
        return name.contains("hedge") || 
               name.contains("arbitrage") ||
               name.contains("market-neutral") ||
               name.contains("pairs");
    }
    
    /**
     * 检查市场条件
     */
    private boolean checkMarketCondition(BarSeries series) {
        // 计算市场波动率
        Num volatility = calculateVolatility(series);
        Num threshold = DecimalNum.valueOf(0.02);
        
        // 高波动率市场更适合双向持仓
        return volatility.isGreaterThan(threshold);
    }
    
    /**
     * 创建方向选择器
     */
    private DirectionSelector createDirectionSelector(String type) {
        switch (type.toLowerCase()) {
            case "trend":
                int emaPeriod = Integer.parseInt(
                    config.getProperty("strategy.bidirectional.ema.period", "20"));
                return new TrendBasedDirectionSelector(emaPeriod);
                
            case "rsi":
                int rsiPeriod = Integer.parseInt(
                    config.getProperty("strategy.bidirectional.rsi.period", "14"));
                Num oversold = DecimalNum.valueOf(
                    Double.parseDouble(config.getProperty(
                        "strategy.bidirectional.rsi.oversold", "30")));
                Num overbought = DecimalNum.valueOf(
                    Double.parseDouble(config.getProperty(
                        "strategy.bidirectional.rsi.overbought", "70")));
                return new RSIBasedDirectionSelector(rsiPeriod, oversold.doubleValue(), overbought.doubleValue());
                
            default:
                return new TrendBasedDirectionSelector(20.0);
        }
    }
    
    private Num calculateVolatility(BarSeries series) {
        // 简化的波动率计算
        // 实际项目中可以使用更复杂的计算方法
        return DecimalNum.valueOf(0.01); // 示例值
    }
}

/**
 * 使用示例
 */
public class ProjectExample {
    public static void main(String[] args) {
        // 加载配置
        Properties config = loadConfig("strategy.properties");
        
        // 加载数据
        BarSeries series = loadBarSeries();
        
        // 创建策略
        Strategy strategy = createStrategy(series);
        
        // 创建执行器
        ProjectStrategyExecutor executor = 
            new ProjectStrategyExecutor(config, series);
        
        // 执行策略（自动选择模式）
        TradingRecord result = executor.executeStrategy(strategy);
        
        // 分析结果
        analyzeResults(result);
    }
}
```

**配置文件示例 (strategy.properties)**：

```properties
# 是否启用双向持仓
strategy.bidirectional.enabled=true

# 方向选择器类型: trend, rsi
strategy.bidirectional.selector=trend

# 是否允许对冲（同时持有多空）
strategy.bidirectional.hedging=false

# EMA参数（当selector=trend时使用）
strategy.bidirectional.ema.period=20

# RSI参数（当selector=rsi时使用）
strategy.bidirectional.rsi.period=14
strategy.bidirectional.rsi.oversold=30
strategy.bidirectional.rsi.overbought=70

# 交易数量
strategy.amount=10

# 标准模式交易类型（当不使用双向持仓时）
strategy.trade.type=BUY
```

### 4.7 完整执行流程

```java
/**
 * 完整的双向持仓执行流程
 */
public class CompleteBidirectionalExecution {
    
    public static void main(String[] args) {
        // ========== 步骤1: 准备数据 ==========
        BarSeries series = loadBarSeries();
        
        // ========== 步骤2: 创建策略 ==========
        Strategy originalStrategy = createStrategy(series);
        
        // ========== 步骤3: 决定是否使用双向持仓 ==========
        boolean useBidirectional = decideIfUseBidirectional(series, originalStrategy);
        
        TradingRecord result;
        
        if (useBidirectional) {
            // ========== 步骤4a: 配置双向持仓 ==========
            DirectionSelector directionSelector = selectDirectionSelector(series);
            boolean allowHedging = shouldAllowHedging();
            
            // ========== 步骤5a: 创建双向持仓环境 ==========
            BidirectionalExecutionContext context = 
                BidirectionalExtensionFactory.createExecutionContext(
                    series,
                    originalStrategy,
                    directionSelector,
                    new ZeroCostModel(),
                    new ZeroCostModel(),
                    new TradeOnCurrentCloseModel(),
                    allowHedging
                );
            
            // ========== 步骤6a: 执行双向持仓策略 ==========
            result = BidirectionalExtensionFactory.runBidirectional(
                context,
                DecimalNum.valueOf(10),
                series.getBeginIndex(),
                series.getEndIndex()
            );
            
            // ========== 步骤7a: 分析双向持仓结果 ==========
            analyzeBidirectionalResult((DualDirectionTradingRecord) result);
            
        } else {
            // ========== 步骤4b: 使用标准模式 ==========
            BarSeriesManager manager = new BarSeriesManager(series);
            
            // ========== 步骤5b: 执行标准策略 ==========
            result = manager.run(originalStrategy, TradeType.BUY);
            
            // ========== 步骤6b: 分析标准结果 ==========
            analyzeStandardResult(result);
        }
        
        // ========== 步骤8: 输出最终结果 ==========
        printResults(result);
    }
    
    private static boolean decideIfUseBidirectional(
            BarSeries series, Strategy strategy) {
        // 综合判断逻辑
        return shouldUseBidirectional(strategy) ||
               shouldUseBidirectional(series) ||
               shouldUseBidirectional(strategy, series);
    }
    
    private static DirectionSelector selectDirectionSelector(BarSeries series) {
        // 根据策略或配置选择方向选择器
        return new TrendBasedDirectionSelector(20.0);
    }
    
    private static boolean shouldAllowHedging() {
        // 根据策略类型决定是否允许对冲
        return false; // 互斥模式
    }
    
    private static void analyzeBidirectionalResult(DualDirectionTradingRecord record) {
        System.out.println("=== 双向持仓分析 ===");
        System.out.println("多头交易数: " + record.getLongRecord().getTrades().size());
        System.out.println("空头交易数: " + record.getShortRecord().getTrades().size());
        System.out.println("多头持仓数: " + record.getLongRecord().getPositions().size());
        System.out.println("空头持仓数: " + record.getShortRecord().getPositions().size());
        System.out.println("是否对冲: " + record.hasHedgedPosition());
        System.out.println("净方向: " + record.getNetPositionDirection());
    }
    
    private static void analyzeStandardResult(TradingRecord record) {
        System.out.println("=== 标准模式分析 ===");
        System.out.println("交易数: " + record.getTrades().size());
        System.out.println("持仓数: " + record.getPositions().size());
    }
    
    private static void printResults(TradingRecord record) {
        // 输出最终结果
        AnalysisCriterion returnCriterion = new GrossReturnCriterion();
        // ... 计算和输出各种指标
    }
}
```

## 5. 使用示例

### 5.1 基本使用

```java
import org.ta4j.core.*;
import org.ta4j.extension.bidirectional.*;

// 1. 创建原始策略
Rule entryRule = new CrossedUpIndicatorRule(shortSma, longSma);
Rule exitRule = new CrossedDownIndicatorRule(shortSma, longSma);
Strategy originalStrategy = new BaseStrategy(entryRule, exitRule);

// 2. 创建方向选择器
DirectionSelector directionSelector = new TrendBasedDirectionSelector(20.0);

// 3. 创建双向策略包装器
BidirectionalStrategyWrapper bidirectionalStrategy = 
    new BidirectionalStrategyWrapper(originalStrategy, directionSelector, barSeries);

// 4. 创建双向交易记录
DualDirectionTradingRecord tradingRecord = new DualDirectionTradingRecord(
    "Bidirectional Strategy",
    barSeries.getBeginIndex(),
    barSeries.getEndIndex(),
    new ZeroCostModel(),
    new ZeroCostModel()
);

// 5. 创建执行管理器
BidirectionalExecutionManager executionManager = 
    new BidirectionalExecutionManager(new TradeOnCurrentCloseModel(), false);

// 6. 运行策略
for (int i = barSeries.getBeginIndex(); i <= barSeries.getEndIndex(); i++) {
    executionManager.execute(i, bidirectionalStrategy, tradingRecord, 
                            barSeries, DecimalNum.valueOf(10));
}
```

### 4.2 对冲模式使用

```java
// 允许同时持有多空仓位
BidirectionalExecutionManager executionManager = 
    new BidirectionalExecutionManager(new TradeOnCurrentCloseModel(), true);

// 使用对冲方向选择器
DirectionSelector hedgedSelector = new HedgedDirectionSelector(
    new TrendBasedDirectionSelector(20.0)
);

BidirectionalStrategyWrapper hedgedStrategy = 
    new BidirectionalStrategyWrapper(originalStrategy, hedgedSelector, barSeries);
```

### 4.3 互斥模式使用

```java
// 互斥模式：同一时间只能持有一个方向的仓位
DirectionSelector exclusiveSelector = new ExclusiveDirectionSelector(
    new RSIBasedDirectionSelector(14, 30.0, 70.0)
);

BidirectionalStrategyWrapper exclusiveStrategy = 
    new BidirectionalStrategyWrapper(originalStrategy, exclusiveSelector, barSeries);
```

### 4.4 与BarSeriesManager集成

```java
// 创建自定义的BarSeriesManager扩展
public class BidirectionalBarSeriesManager extends BarSeriesManager {
    
    private final BidirectionalExecutionManager executionManager;
    private final BidirectionalStrategyWrapper bidirectionalStrategy;
    
    public BidirectionalBarSeriesManager(BarSeries barSeries, 
                                        BidirectionalStrategyWrapper strategy,
                                        CostModel transactionCostModel,
                                        CostModel holdingCostModel) {
        super(barSeries, transactionCostModel, holdingCostModel);
        this.bidirectionalStrategy = strategy;
        this.executionManager = new BidirectionalExecutionManager(
            getTradeExecutionModel(), false);
    }
    
    public TradingRecord runBidirectional(Num amount, int startIndex, int finishIndex) {
        DualDirectionTradingRecord tradingRecord = new DualDirectionTradingRecord(
            bidirectionalStrategy.getName(),
            startIndex,
            finishIndex,
            getTransactionCostModel(),
            getHoldingCostModel()
        );
        
        int runBeginIndex = Math.max(startIndex, getBarSeries().getBeginIndex());
        int runEndIndex = Math.min(finishIndex, getBarSeries().getEndIndex());
        
        for (int i = runBeginIndex; i <= runEndIndex; i++) {
            executionManager.execute(i, bidirectionalStrategy, tradingRecord, 
                                    getBarSeries(), amount);
        }
        
        return tradingRecord;
    }
}
```

## 5. 性能分析

### 5.1 内存开销

- **DualDirectionTradingRecord**：维护两个`BaseTradingRecord`实例，内存开销约为单方向的2倍
- **BidirectionalStrategyWrapper**：仅持有引用，内存开销可忽略
- **DirectionSelector**：根据实现不同，可能需要额外的指标计算，但通常开销较小

### 5.2 计算开销

- **方向选择**：每次`shouldEnter`调用时执行，需要计算方向选择逻辑
- **聚合操作**：`getTrades()`和`getPositions()`需要合并两个记录，时间复杂度O(n)
- **建议优化**：缓存聚合结果，仅在交易发生时更新

### 5.3 优化建议

1. **延迟聚合**：只在需要时合并交易和持仓列表
2. **缓存方向选择结果**：在同一bar内复用方向选择结果
3. **并行计算**：如果方向选择器计算复杂，可以考虑并行计算

## 6. 测试策略

### 6.1 单元测试

```java
@Test
public void testDualDirectionTradingRecord() {
    DualDirectionTradingRecord record = new DualDirectionTradingRecord(
        "Test", 0, 100, new ZeroCostModel(), new ZeroCostModel());
    
    // 测试开多
    assertTrue(record.enter(10, DecimalNum.valueOf(100), DecimalNum.valueOf(10), TradeType.BUY));
    assertTrue(record.getLongRecord().getCurrentPosition().isOpened());
    
    // 测试开空（对冲模式）
    assertTrue(record.enter(20, DecimalNum.valueOf(110), DecimalNum.valueOf(10), TradeType.SELL));
    assertTrue(record.hasHedgedPosition());
    
    // 测试平多
    assertTrue(record.exit(30, DecimalNum.valueOf(120), DecimalNum.valueOf(10)));
    assertFalse(record.getLongRecord().getCurrentPosition().isOpened());
    assertTrue(record.getShortRecord().getCurrentPosition().isOpened());
}
```

### 6.2 集成测试

```java
@Test
public void testBidirectionalStrategy() {
    // 创建测试数据
    BarSeries series = generateTestSeries();
    
    // 创建策略和方向选择器
    Strategy strategy = createTestStrategy();
    DirectionSelector selector = new TrendBasedDirectionSelector(20.0);
    BidirectionalStrategyWrapper wrapper = 
        new BidirectionalStrategyWrapper(strategy, selector, series);
    
    // 运行策略
    DualDirectionTradingRecord record = runStrategy(wrapper, series);
    
    // 验证结果
    assertTrue(record.getTrades().size() > 0);
    assertTrue(record.getPositions().size() > 0);
}
```

## 7. 扩展点

### 7.1 自定义方向选择器

用户可以轻松实现自定义的`DirectionSelector`接口：

```java
public class CustomDirectionSelector implements DirectionSelector {
    @Override
    public TradeType selectDirection(int index, BarSeries barSeries, 
                                     Strategy strategy, TradingRecord tradingRecord) {
        // 自定义逻辑
        return TradeType.BUY; // 或 TradeType.SELL 或 null
    }
}
```

### 7.2 自定义执行逻辑

可以扩展`BidirectionalExecutionManager`以支持更复杂的执行逻辑：

- 部分平仓
- 动态调整仓位大小
- 风险控制逻辑
- 仓位管理规则

### 7.3 统计分析扩展

可以添加专门的双向持仓分析工具：

```java
public class BidirectionalAnalysis {
    public Num calculateNetExposure(DualDirectionTradingRecord record) {
        // 计算净敞口
    }
    
    public Num calculateHedgeRatio(DualDirectionTradingRecord record) {
        // 计算对冲比例
    }
    
    public Num calculateCorrelation(DualDirectionTradingRecord record) {
        // 计算多空收益相关性
    }
}
```

## 8. 限制和注意事项

### 8.1 已知限制

1. **getCurrentPosition()的语义**：在双向持仓模式下，`getCurrentPosition()`返回的是当前活跃方向的position，而不是合并的position。如果需要合并的position，需要额外实现。

2. **策略兼容性**：某些策略可能依赖于单方向持仓的假设，在使用双向持仓时需要验证策略的兼容性。

3. **性能考虑**：聚合操作（如`getTrades()`）在交易数量很大时可能有性能影响。

### 8.2 使用建议

1. **明确使用场景**：根据实际需求选择对冲模式或互斥模式
2. **测试方向选择器**：确保方向选择逻辑符合预期
3. **监控持仓状态**：定期检查`hasHedgedPosition()`和`getNetPositionDirection()`
4. **性能优化**：对于高频交易场景，考虑优化聚合操作

## 9. 总结

本扩展设计通过实现`TradingRecord`接口和策略包装器，为TA4J提供了双向持仓支持，同时保持了与现有代码的完全兼容性。扩展采用组合模式，内部使用两个`BaseTradingRecord`实例分别管理多空仓位，通过方向选择器和执行管理器协调双向持仓逻辑。

### 9.1 核心优势

- ✅ **零侵入**：不修改TA4J核心代码
- ✅ **完全兼容**：实现标准接口，可与现有代码无缝集成
- ✅ **灵活扩展**：支持自定义方向选择器和执行逻辑
- ✅ **多种模式**：支持对冲模式和互斥模式

### 9.2 适用场景

- 对冲策略
- 市场中性策略
- 套利策略
- 动态方向选择策略

### 9.3 未来改进方向

1. 支持部分平仓
2. 动态仓位管理
3. 更丰富的统计分析工具
4. 性能优化（缓存、并行计算等）
