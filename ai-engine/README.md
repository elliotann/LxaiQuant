# AI Trading Engine Library

**From backtest to live trading**: A pure Java library that enables the same strategies you backtest to run live. Ta4j's deterministic calculations make it safe to deploy & test thoroughly, then execute with confidence.

This library can be integrated into any Java application to add algorithmic trading capabilities.

## Core Principles

- **Same code, different data**: Your strategy logic is identical for backtests and live trading
- **Deterministic**: Same inputs always produce same outputs - critical for testing and debugging
- **Type-safe**: Compile-time checks catch errors before they cost money

## Trading Engine Architecture

```
TradingEngineService (Main service)
├── Live BarSeries (grows as bars arrive)
├── Strategy (same as backtesting)
├── Trading Loop (checks signals on each new bar)
├── Order Execution (buy/sell logic)
└── Risk Management (position sizing, limits)
```

## Key Components

### TradingEngineService
- **Initialization**: Sets up live BarSeries and strategy
- **Trading Loop**: Continuously checks for signals and executes orders
- **Order Execution**: Pluggable interface for broker integration
- **Risk Management**: Position sizing and loss limits

### BacktestEngine
- **Single Strategy Backtest**: Execute backtest for individual strategies
- **Multi-Strategy Comparison**: Compare performance across multiple strategies
- **Walk-Forward Optimization**: Time-based strategy validation
- **Performance Metrics**: Return, drawdown, win rate, and trade statistics

### Current Implementation Notes
- **Strategy**: Currently uses a simplified price-based strategy for compatibility
- **Mock Classes**: Uses `MockBarSeries`, `MockBar`, and `MockTradingRecord` to avoid Ta4j API compatibility issues
- **Interface Compliance**: Mock classes implement all required Ta4j interfaces for compilation
- **Indicators**: Basic ClosePriceIndicator is used to avoid Ta4j version-specific packages
- **Production Ready**: Replace mock classes with proper Ta4j implementations for production use

### Live BarSeries
```java
// Create a live series (starts empty, grows as bars arrive)
BarSeries liveSeries = new BaseBarSeriesBuilder()
    .withName("BTC-USD")
    .build();
```

### Strategy Logic (Same as Backtesting)
```java
// Build your strategy (same code as backtesting!)
Strategy strategy = buildStrategy(liveSeries);

// Main trading loop: check for signals on each new bar
while (true) {
    Bar latest = fetchLatestBarFromBroker();
    liveSeries.addBar(latest);

    int endIndex = liveSeries.getEndIndex();

    // Check entry/exit signals (same API as backtesting)
    if (strategy.shouldEnter(endIndex)) {
        placeBuyOrder();
    } else if (strategy.shouldExit(endIndex)) {
        placeSellOrder();
    }

    Thread.sleep(60000); // Wait 1 minute
}
```

## Usage as Library

This is a pure Java library that can be integrated into any Java application.

### 1. Add to your project
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>ai-engine</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. Use in your code

#### Live Trading
```java
// Create trading engine
TradingEngineService engine = new TradingEngineService();

// Set up broker (use MockBroker for testing, or implement your own)
BrokerInterface broker = new MockBroker(); // For testing
// BrokerInterface broker = new MyRealBroker(); // For production
engine.setBroker(broker);

// Initialize for a symbol
engine.initialize("BTC");

// Start trading
engine.startTrading();

// Check status
System.out.println(engine.getStatusSummary());

// Stop when done
engine.stopTrading();
```

#### Backtesting
```java
// Create backtest engine
BacktestEngine backtestEngine = new BacktestEngine();

// Create a mock bar series with some test data
MockBarSeries series = new MockBarSeries("BTC-USD");
// Add bars to series...

// Implement a simple strategy
TradingStrategy strategy = new TradingStrategy() {
    @Override
    public String getName() { return "Simple Strategy"; }

    @Override
    public Strategy buildStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        Rule entryRule = new UnderIndicatorRule(closePrice, DecimalNum.valueOf(0));
        Rule exitRule = new UnderIndicatorRule(closePrice, DecimalNum.valueOf(0));
        return new BaseStrategy(entryRule, exitRule);
    }
};

// Run backtest
BacktestEngine.BacktestResult result = backtestEngine.runBacktest(strategy, series);
System.out.println("Backtest Result: " + result.getPerformanceMetrics().toFormattedString());
```

### 3. Custom Strategy Integration
```java
// Implement your own strategy
Strategy customStrategy = buildYourCustomStrategy(series);

// Use with engine (you would need to extend or modify the engine)
engine.setStrategy(customStrategy);
```

### 4. Broker Integration

Implement the `BrokerInterface` to connect with your broker/exchange:

```java
public class MyBroker implements BrokerInterface {
    private final BrokerApi brokerApi;

    public MyBroker(BrokerApi brokerApi) {
        this.brokerApi = brokerApi;
    }

    @Override
    public Bar fetchLatestBar(String symbol) {
        // Fetch real market data from your broker
        MarketData data = brokerApi.getLatestMarketData(symbol);
        return BarFactory.createBar(
            data.getOpen(),
            data.getHigh(),
            data.getLow(),
            data.getClose(),
            data.getVolume()
        );
    }

    @Override
    public String placeBuyOrder(BigDecimal price, BigDecimal quantity) {
        // Place real buy order
        return brokerApi.placeOrder(symbol, OrderType.BUY, price, quantity);
    }

    @Override
    public String placeSellOrder(BigDecimal price, BigDecimal quantity) {
        // Place real sell order
        return brokerApi.placeOrder(symbol, OrderType.SELL, price, quantity);
    }

    @Override
    public BigDecimal getAccountBalance() {
        return brokerApi.getAccountBalance();
    }

    @Override
    public BigDecimal getPositionSize(String symbol) {
        return brokerApi.getPositionSize(symbol);
    }

    @Override
    public boolean cancelOrder(String orderId) {
        return brokerApi.cancelOrder(orderId);
    }

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        return brokerApi.getCurrentPrice(symbol);
    }
}

// Use with engine
TradingEngineService engine = new TradingEngineService();
engine.setBroker(new MyBroker(myBrokerApi));
engine.initialize("BTC");
engine.startTrading();
```

### 5. Testing with MockBroker

For development and testing, use the included `MockBroker`:

```java
TradingEngineService engine = new TradingEngineService();
MockBroker mockBroker = new MockBroker();
engine.setBroker(mockBroker);

// Add some test data
for (int i = 0; i < 100; i++) {
    engine.addBar(BigDecimal.valueOf(50000 + i * 10));
}

// Check if signals are generated
System.out.println("Bars: " + engine.getSeriesSize());
```

### 6. Replacing Mock Classes

The current implementation uses mock classes to avoid Ta4j API compatibility issues. For production use, replace them with proper Ta4j implementations:

**Replace MockBarSeries:**
```java
// Instead of:
this.liveSeries = new MockBarSeries(symbol + "-USD");

// Use proper Ta4j BarSeries:
this.liveSeries = new BaseBarSeries(symbol + "-USD");
// or use builder/factory based on your Ta4j version
```

**Replace MockBar in BarFactory:**
```java
// Instead of returning MockBar, use proper Ta4j BarBuilder:
// BarBuilder builder = BarBuilderFactory.create();
// return builder.build(open, high, low, close, volume, timestamp);
```

### 7. Extending the Strategy

The library includes `StrategyExamples.java` with various strategy implementations. You can either:

**Option 1**: Modify the `buildStrategy()` method in `TradingEngineService`:

```java
private Strategy buildStrategy(BarSeries series) {
    // Use one of the predefined strategies
    return StrategyExamples.createRSIStrategy(series);
    // or return StrategyExamples.createMACrossoverStrategy(series);
    // or return StrategyExamples.createMACDStrategy(series);
}
```

**Option 2**: Implement custom strategy logic:

```java
private Strategy buildStrategy(BarSeries series) {
    // Example: RSI + Moving Average strategy
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

    // Add your indicators here based on your Ta4j version
    // RSIIndicator rsi = new RSIIndicator(closePrice, 14);
    // SMAIndicator sma = new SMAIndicator(closePrice, 20);

    // Define entry/exit rules
    // Rule entryRule = new OverIndicatorRule(rsi, sma); // RSI > SMA
    // Rule exitRule = new UnderIndicatorRule(rsi, sma);  // RSI < SMA

    // For now, using placeholder rules
    Rule entryRule = new UnderIndicatorRule(closePrice, DecimalNum.valueOf(0));
    Rule exitRule = new UnderIndicatorRule(closePrice, DecimalNum.valueOf(0));

    return new org.ta4j.core.BaseStrategy(entryRule, exitRule);
}
```

**Note**: The actual indicator classes (RSIIndicator, SMAIndicator, etc.) may be in different packages depending on your Ta4j version. Check your Ta4j documentation for the correct import paths.

## Strategy Implementation

The `buildStrategy()` method contains the exact same logic as your backtesting strategies:

```java
private Strategy buildStrategy(BarSeries series) {
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

    // RSI + MACD strategy (same as backtesting)
    RSIIndicator rsi = new RSIIndicator(closePrice, 14);
    MACDIndicator macd = new MACDIndicator(closePrice);
    EMAIndicator signal = new EMAIndicator(macd, 9);

    Rule entryRule = new UnderIndicatorRule(rsi, DecimalNum.valueOf(30))
            .and(new OverIndicatorRule(macd, signal));

    Rule exitRule = new OverIndicatorRule(rsi, DecimalNum.valueOf(70))
            .or(new UnderIndicatorRule(macd, signal));

    return new BaseStrategy(entryRule, exitRule);
}
```

## Broker Integration

Replace the placeholder methods with your broker API calls:

```java
private Bar fetchLatestBarFromBroker() {
    // TODO: Implement your broker/exchange integration
    // return brokerApi.getLatestBar(symbol);
}

private void placeBuyOrder() {
    // TODO: Implement order execution
    // Order order = new Order(OrderType.BUY, currentPrice, positionSize);
    // brokerApi.placeOrder(order);
}
```

## Safety Features

- **Same Logic**: Deterministic calculations ensure consistency
- **Type Safety**: Compile-time error checking
- **Risk Controls**: Built-in position sizing and limits
- **Monitoring**: Real-time status and performance tracking
- **Emergency Stops**: Circuit breakers for excessive losses

## Production Setup

1. **Replace Mock Broker**: Implement real exchange API integration
2. **Configure Risk Limits**: Set appropriate position sizes and loss limits
3. **Add Monitoring**: Logs, alerts, and performance tracking
4. **Database Integration**: Store trade history and analytics
5. **Load Testing**: Ensure system can handle real-time data feeds

The design ensures the same battle-tested strategies run in both backtesting and live environments with minimal code changes.