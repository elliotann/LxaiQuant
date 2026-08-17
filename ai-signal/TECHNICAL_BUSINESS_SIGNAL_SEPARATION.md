# 技术信号与业务信号分离设计文档

## 概述

本设计将原有的混合信号表分离为两个独立的表：技术信号表（Technical Signal）和业务信号表（Trade Signal）。实现职责分离，提高系统的可维护性和扩展性。

## 表结构

### 1. 技术信号表（TechnicalSignal）

存储纯粹由技术指标产生的信号，不涉及业务逻辑。

```java
@TableName("technical_signal")
public class TechnicalSignal {
    private Long id;
    private Date createTime;
    private String dataSource;
    private String timeframe;  // 1m, 5m, 1h, 4h, 1d
    private Long klineTimestamp;
    private String klineTime;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal volume;
    private String symbol;  // BTCUSDT, ETHUSDT
    private String indicator;  // MACD, RSI, BOLL, MA
    private String strategyName;
    private String technicalDirection;  // STRONG_BULLISH, BULLISH, NEUTRAL, BEARISH, STRONG_BEARISH
    private BigDecimal signalStrength;  // 0-1 或 0-100
    private BigDecimal confidence;
    private BigDecimal indicatorValue;
    private BigDecimal threshold;
    private String extraParams;  // JSON存储额外指标参数
    private String signalHash;  // MD5(symbol+timeframe+klineTime+indicator+strategy)
}
```

### 2. 业务信号表（TradeSignal）

结合技术信号和业务逻辑后，产生的实际交易指令。

```java
@TableName("trade_signal")
public class TradeSignal {
    private Long id;
    private String creator;
    private Date createTime;
    private String updater;
    private Date updateTime;
    private Boolean deleted;

    // 关联技术信号
    private Long technicalSignalId;
    private String technicalSignalHash;
    private String technicalSignalBrief;

    // 业务决策信息
    private String symbol;
    private String timeframe;
    private String klineTime;
    private String decisionReason;
    private String riskLevel;
    private BigDecimal positionRatio;
    private Integer priority;

    // 订单执行信息
    private OrderAction orderAction;
    private TradeStatus status;
    private String orderSn;
    private String orderItemSn;
    private BigDecimal expectedPrice;
    private BigDecimal expectedAmount;
    private BigDecimal stopLossPrice;
    private BigDecimal takeProfitPrice;
    private Integer leverage;
    private BigDecimal feeRate;

    // 执行结果
    private BigDecimal executedPrice;
    private BigDecimal executedAmount;
    private BigDecimal actualFee;
    private Date executedTime;
    private String executionNote;

    // 绩效统计
    private BigDecimal pnlAmount;
    private BigDecimal pnlPercentage;
    private Date closeTime;
    private Long holdingSeconds;
    private BigDecimal sharpeRatio;
}
```

## 枚举类

### TechnicalDirection - 技术信号方向

```java
public enum TechnicalDirection {
    STRONG_BULLISH("强势看多"),
    BULLISH("看多"),
    NEUTRAL("中性"),
    BEARISH("看空"),
    STRONG_BEARISH("强势看空");
}
```

### OrderAction - 订单操作（业务层面）

```java
public enum OrderAction {
    OPEN_LONG("开多"),
    OPEN_SHORT("开空"),
    CLOSE_LONG("平多"),
    CLOSE_SHORT("平空"),
    ADJUST_LEVERAGE("调整杠杆"),
    CANCEL_ORDER("取消订单");
}
```

### TradeStatus - 交易状态

```java
public enum TradeStatus {
    PENDING("待处理"),
    VALIDATING("验证中"),
    APPROVED("已批准"),
    REJECTED("已拒绝"),
    EXECUTING("执行中"),
    PARTIALLY_FILLED("部分成交"),
    FILLED("完全成交"),
    CANCELLED("已取消"),
    FAILED("执行失败"),
    SETTLED("已结算");
}
```

## DTO类（数据传输对象）

### TechnicalSignalDTO - 技术信号传输对象

```java
public class TechnicalSignalDTO {
    private String symbol;
    private String timeframe;
    private String klineTime;
    private String indicator;
    private String strategyName;
    private String technicalDirection;
    private BigDecimal signalStrength;
    private BigDecimal confidence;
    private BigDecimal currentPrice;
    private Map<String, Object> indicatorValues;
    private String signalHash;
}
```

### GenerateTradeSignalRequest - 交易信号生成请求

```java
public class GenerateTradeSignalRequest {
    @NotNull
    private Long technicalSignalId;

    @NotNull
    private String symbol;

    private String accountId;
    private String strategyId;

    @NotNull
    private BigDecimal maxPositionRatio = BigDecimal.valueOf(0.1);

    private BigDecimal stopLossRatio;
    private BigDecimal takeProfitRatio;
    private Integer leverage;
    private String riskLevel;
}
```

### GenerateTradeSignalResponse - 交易信号生成响应

```java
public class GenerateTradeSignalResponse {
    private boolean success;
    private String message;
    private Long tradeSignalId;
    private String orderSn;
    private BigDecimal calculatedPrice;
    private BigDecimal calculatedAmount;
    private BigDecimal suggestedStopLoss;
    private BigDecimal suggestedTakeProfit;
    private String riskAssessment;
    private String decisionReason;
    private String warningMessage;
}
```

### TradeSignalDTO - 交易信号传输对象

```java
public class TradeSignalDTO {
    private Long id;
    private Long technicalSignalId;
    private String technicalSignalBrief;
    private String symbol;
    private String timeframe;
    private String klineTime;
    private String decisionReason;
    private String riskLevel;
    private BigDecimal positionRatio;
    private Integer priority;
    private OrderAction orderAction;
    private TradeStatus status;
    private String orderSn;
    private BigDecimal expectedPrice;
    private BigDecimal executedPrice;
    private BigDecimal expectedAmount;
    private BigDecimal executedAmount;
    private BigDecimal stopLossPrice;
    private BigDecimal takeProfitPrice;
    private Integer leverage;
    private BigDecimal feeRate;
    private BigDecimal actualFee;
    private Date createTime;
    private Date executedTime;
    private BigDecimal pnlAmount;
    private BigDecimal pnlPercentage;
    private String executionNote;
}
```

### SignalQueryDTO - 信号查询对象

```java
public class SignalQueryDTO {
    private String symbol;
    private String timeframe;
    private String indicator;
    private String strategyName;
    private String technicalDirection;
    private OrderAction orderAction;
    private List<TradeStatus> statuses;
    private String riskLevel;
    private Date startTime;
    private Date endTime;
    private Double minSignalStrength;
    private Double maxSignalStrength;
    private Boolean isProfitable;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private String orderBy = "createTime";
    private String orderDirection = "desc";
}
```

## VO类（视图对象）

### TechnicalSignalVO - 技术信号视图对象

用于前端展示技术信号信息，包含格式化的描述信息。

### TradeSignalVO - 交易信号视图对象

用于前端展示交易信号的完整信息，包含状态描述和格式化的时间信息。

### SignalDashboardVO - 信号仪表板统计对象

用于前端仪表板展示信号统计信息，包括技术信号统计、交易信号统计、绩效指标等。

## 服务接口

### ITechnicalSignalService - 技术信号服务

- `createTechnicalSignal()` - 创建技术信号
- `queryTechnicalSignals()` - 查询技术信号
- `queryLatestTechnicalSignal()` - 查询最新技术信号
- `batchSaveTechnicalSignals()` - 批量保存技术信号

### ITradeSignalService - 业务信号服务

- `createTradeSignal()` - 创建交易信号
- `queryTradeSignalsByTechnicalSignalId()` - 根据技术信号ID查询
- `updateTradeSignalStatus()` - 更新信号状态
- `executeTradeSignal()` - 执行交易信号
- `queryPendingTradeSignals()` - 查询待执行信号

### ISignalCoordinatorService - 信号协调服务

负责技术信号到业务信号的转换：

- `processTechnicalSignal()` - 处理技术信号生成业务信号
- `shouldGenerateTradeSignal()` - 判断是否需要生成交易信号
- `applyRiskControl()` - 应用风控规则
- `applyPositionManagement()` - 应用仓位管理
- `calculateOrderAmount()` - 计算订单数量

## 业务逻辑处理流程

### 1. 技术信号生成

技术指标分析模块分析K线数据，产生技术信号：

```java
// 示例：创建技术信号
TechnicalSignal signal = new TechnicalSignal();
signal.setSymbol("ETH-USDT-SWAP");
signal.setIndicatorType("RSI");
signal.setTechSignal(TechSignal.LONG);
signal.setStrength(new BigDecimal("0.8"));
signal.setKlineTime("2025-01-01 12:00:00");

technicalSignalService.createTechnicalSignal(signal);
```

### 2. 业务信号生成

信号协调服务读取技术信号，结合业务逻辑生成交易信号：

```java
// 信号协调处理
TradeSignal tradeSignal = signalCoordinatorService.processTechnicalSignal(technicalSignal);
if (tradeSignal != null) {
    tradeSignalService.createTradeSignal(tradeSignal);
}
```

### 3. 订单执行

订单执行模块读取业务信号，执行交易：

```java
// 查询待执行的信号
List<TradeSignal> pendingSignals = tradeSignalService.queryPendingTradeSignals();

for (TradeSignal signal : pendingSignals) {
    boolean success = tradeSignalService.executeTradeSignal(signal.getId());
    if (success) {
        // 更新状态为已执行
        tradeSignalService.updateTradeSignalStatus(signal.getId(), TradeSignalStatus.EXECUTED);
    } else {
        // 更新状态为失败
        tradeSignalService.updateTradeSignalStatus(signal.getId(), TradeSignalStatus.FAILED);
    }
}
```

## 优势

1. **职责分离**：技术信号和业务信号各自独立，便于维护和扩展
2. **灵活性**：业务逻辑可以基于多个技术信号进行决策，也可以考虑其他因素
3. **可追溯**：通过technicalSignalId可以追溯到原始技术信号，便于回测和复盘
4. **降低耦合**：技术指标模块无需关心业务逻辑，只需产生技术信号

## 索引建议

### 技术信号表
- `IDX_TECHNICAL_SIGNAL_SYMBOL_TIME` - (symbol, kline_time, indicator_type)
- `IDX_TECHNICAL_SIGNAL_TYPE` - (tech_signal)

### 业务信号表
- `IDX_TRADE_SIGNAL_TECHNICAL_ID` - (technical_signal_id)
- `IDX_TRADE_SIGNAL_ORDER_SN` - (order_sn)
- `IDX_TRADE_SIGNAL_STATUS` - (status)
- `IDX_TRADE_SIGNAL_TIME` - (create_time)

## 使用示例

### 创建技术信号

```java
@Autowired
private ITechnicalSignalService technicalSignalService;

public void generateSignal() {
    TechnicalSignal signal = new TechnicalSignal();
    signal.setSymbol("BTC-USDT");
    signal.setIndicatorType("MACD");
    signal.setTechSignal(TechSignal.LONG);
    signal.setStrength(BigDecimal.valueOf(0.75));

    Long signalId = technicalSignalService.createTechnicalSignal(signal);
}
```

### 查询和处理信号

```java
@Autowired
private ITradeSignalService tradeSignalService;
@Autowired
private ISignalCoordinatorService signalCoordinatorService;

public void processSignals() {
    // 查询待处理的业务信号
    List<TradeSignal> pendingSignals = tradeSignalService.queryPendingTradeSignals();

    for (TradeSignal signal : pendingSignals) {
        // 执行信号
        tradeSignalService.executeTradeSignal(signal.getId());
    }
}
```

## 注意事项

1. **数据一致性**：确保technicalSignalId正确关联到对应的技术信号
2. **状态管理**：正确更新TradeSignal的状态，避免状态不一致
3. **异常处理**：对信号处理过程中的异常进行适当处理和记录
4. **性能优化**：对频繁查询的字段建立适当的索引
