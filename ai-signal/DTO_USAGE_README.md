# 信号系统DTO使用指南

## 概述

本文档介绍技术信号和业务信号分离设计中的DTO（数据传输对象）使用方法。

## DTO分类

### 1. 技术信号相关DTO

#### TechnicalSignalDTO
**用途**: 创建和传输技术信号数据

**关键字段**:
- `symbol`: 交易对（如"BTCUSDT"）
- `timeframe`: 时间周期（如"1m", "5m", "1h"）
- `indicator`: 指标类型（如"MACD", "RSI"）
- `technicalDirection`: 信号方向（如"STRONG_BULLISH"）
- `signalStrength`: 信号强度（0-1或0-100）
- `indicatorValues`: 各指标数值Map

**使用示例**:
```java
TechnicalSignalDTO dto = new TechnicalSignalDTO();
dto.setSymbol("BTCUSDT");
dto.setTimeframe("1h");
dto.setIndicator("MACD");
dto.setTechnicalDirection("BULLISH");
dto.setSignalStrength(BigDecimal.valueOf(0.85));
dto.setCurrentPrice(BigDecimal.valueOf(45000));

// 调用API创建技术信号
Long signalId = technicalSignalService.createTechnicalSignal(dto);
```

### 2. 交易信号相关DTO

#### GenerateTradeSignalRequest
**用途**: 请求生成交易信号

**关键字段**:
- `technicalSignalId`: 关联的技术信号ID（必填）
- `symbol`: 交易对（必填）
- `maxPositionRatio`: 最大仓位比例（默认0.1，即10%）
- `stopLossRatio`: 止损比例
- `takeProfitRatio`: 止盈比例
- `leverage`: 杠杆倍数
- `riskLevel`: 风控等级

**使用示例**:
```java
GenerateTradeSignalRequest request = new GenerateTradeSignalRequest();
request.setTechnicalSignalId(12345L);
request.setSymbol("BTCUSDT");
request.setMaxPositionRatio(BigDecimal.valueOf(0.05)); // 5%仓位
request.setStopLossRatio(BigDecimal.valueOf(0.02));    // 2%止损
request.setTakeProfitRatio(BigDecimal.valueOf(0.06));  // 6%止盈
request.setLeverage(2);                                 // 2倍杠杆

GenerateTradeSignalResponse response = signalCoordinatorService.generateTradeSignal(request);
```

#### GenerateTradeSignalResponse
**用途**: 返回交易信号生成结果

**关键字段**:
- `success`: 是否成功
- `tradeSignalId`: 生成的交易信号ID
- `calculatedPrice`: 计算出的开仓价格
- `calculatedAmount`: 计算出的开仓数量
- `suggestedStopLoss`: 建议的止损价
- `suggestedTakeProfit`: 建议的止盈价

**静态工厂方法**:
```java
// 成功响应
GenerateTradeSignalResponse response = GenerateTradeSignalResponse.success(signalId, orderSn);

// 失败响应
GenerateTradeSignalResponse response = GenerateTradeSignalResponse.failure("风控检查失败");

// 拒绝响应
GenerateTradeSignalResponse response = GenerateTradeSignalResponse.rejected("仓位不足");
```

#### TradeSignalDTO
**用途**: 传输完整的交易信号信息

**包含字段**:
- 关联信息：`technicalSignalId`, `technicalSignalBrief`
- 业务决策：`decisionReason`, `riskLevel`, `positionRatio`
- 订单信息：`orderAction`, `status`, `orderSn`
- 价格数量：`expectedPrice`, `executedPrice`, `expectedAmount`, `executedAmount`
- 风控信息：`stopLossPrice`, `takeProfitPrice`, `leverage`
- 绩效信息：`pnlAmount`, `pnlPercentage`

### 3. 查询相关DTO

#### SignalQueryDTO
**用途**: 统一的信号查询条件

**查询条件**:
- 基础筛选：`symbol`, `timeframe`, `indicator`
- 信号筛选：`technicalDirection`, `orderAction`, `statuses`
- 时间范围：`startTime`, `endTime`
- 数值筛选：`minSignalStrength`, `maxSignalStrength`
- 分页参数：`pageNum`, `pageSize`, `orderBy`, `orderDirection`

**使用示例**:
```java
SignalQueryDTO query = new SignalQueryDTO();
query.setSymbol("BTCUSDT");
query.setStatuses(Arrays.asList(TradeStatus.PENDING, TradeStatus.EXECUTING));
query.setStartTime(DateUtils.parseDate("2025-01-01"));
query.setEndTime(new Date());
query.setPageSize(50);
query.setOrderBy("createTime");
query.setOrderDirection("desc");

// 查询交易信号
Page<TradeSignalVO> result = tradeSignalService.queryTradeSignals(query);
```

## VO类说明

### TechnicalSignalVO
**用途**: 前端展示技术信号，包含格式化的描述信息

**额外字段**:
- `technicalDirectionDesc`: 信号方向描述（如"强势看多"）
- `hasTradeSignal`: 是否已生成交易信号

### TradeSignalVO
**用途**: 前端展示交易信号的完整信息

**额外字段**:
- `orderActionDesc`: 订单操作描述
- `statusDesc`: 状态描述
- `isProfitable`: 是否盈利
- 格式化的时间字段

### SignalDashboardVO
**用途**: 仪表板统计数据

**统计维度**:
- 技术信号统计（今日、本周、成功率）
- 交易信号统计（待执行、执行中、完成数量）
- 绩效统计（盈亏、胜率、夏普比率等）
- 风险指标（活跃仓位、风控警告等）

## API使用示例

### 1. 创建技术信号
```java
@PostMapping("/api/signal/technical")
public Result<Long> createTechnicalSignal(@Valid @RequestBody TechnicalSignalDTO dto) {
    Long signalId = technicalSignalService.createTechnicalSignal(dto);
    return Result.success(signalId);
}
```

### 2. 生成交易信号
```java
@PostMapping("/api/signal/trade/generate")
public Result<GenerateTradeSignalResponse> generateTradeSignal(@Valid @RequestBody GenerateTradeSignalRequest request) {
    GenerateTradeSignalResponse response = signalCoordinatorService.generateTradeSignal(request);
    return Result.success(response);
}
```

### 3. 查询信号列表
```java
@GetMapping("/api/signal/trade")
public Result<Page<TradeSignalVO>> queryTradeSignals(SignalQueryDTO query) {
    Page<TradeSignalVO> page = tradeSignalService.queryTradeSignals(query);
    return Result.success(page);
}
```

### 4. 执行交易信号
```java
@PostMapping("/api/signal/trade/{id}/execute")
public Result<Void> executeTradeSignal(@PathVariable Long id) {
    boolean success = tradeSignalService.executeTradeSignal(id);
    return success ? Result.success(null) : Result.failure("执行失败");
}
```

## 注意事项

1. **数据验证**: 所有DTO都使用了JSR-303验证注解，确保数据完整性
2. **分页查询**: 查询DTO包含完整的分页和排序参数
3. **状态管理**: 交易信号状态流转需要严格按照枚举定义
4. **关联关系**: 交易信号必须关联到技术信号，确保可追溯性
5. **风控检查**: 生成交易信号时会进行风控验证，失败会返回具体原因

## 扩展建议

1. 可以根据业务需要添加更多的DTO子类
2. VO类可以根据前端展示需求进行定制
3. 可以添加DTO之间的转换工具类
4. 建议使用MapStruct等工具进行DTO/Entity转换
