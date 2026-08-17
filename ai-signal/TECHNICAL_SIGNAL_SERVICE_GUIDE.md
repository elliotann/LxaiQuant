# 技术信号服务接口使用指南

## 概述

`ITechnicalSignalService` 是专门为技术信号表设计的服务接口，专注于技术指标信号的生成、存储、查询和分析。该接口严格遵循职责分离原则，不涉及任何业务逻辑（如下单、风控等）。

## 核心功能分类

### 1. 信号生成与保存

#### `saveTechnicalSignal(TechnicalSignalDTO signalDTO)`
生成并保存单个技术信号
```java
TechnicalSignalDTO dto = new TechnicalSignalDTO();
dto.setSymbol("BTCUSDT");
dto.setIndicator("MACD");
dto.setTechnicalDirection("BULLISH");
dto.setSignalStrength(BigDecimal.valueOf(0.85));

Long signalId = technicalSignalService.saveTechnicalSignal(dto);
```

#### `batchSaveTechnicalSignals(List<TechnicalSignalDTO> signalDTOList)`
批量保存技术信号，支持高性能批量插入
```java
List<TechnicalSignalDTO> signals = Arrays.asList(dto1, dto2, dto3);
Integer savedCount = technicalSignalService.batchSaveTechnicalSignals(signals);
```

#### 信号去重机制
- `generateSignalHash()`: 生成唯一信号哈希
- `existsByHash()`: 检查信号是否已存在
- 自动跳过重复信号，确保数据不冗余

### 2. 增强查询功能

#### 基础查询
```java
// 分页查询
IPage<TechnicalSignal> page = technicalSignalService.pageTechnicalSignals(query);

// 条件查询
List<TechnicalSignal> signals = technicalSignalService.listTechnicalSignals(query);

// 按时间范围查询
List<TechnicalSignal> timeRangeSignals = technicalSignalService.getSignalsByTimeRange(
    "BTCUSDT", startTime, endTime);
```

#### 高级查询
```java
// 获取最新N个信号
List<TechnicalSignal> latest = technicalSignalService.getLatestSignals("BTCUSDT", "MACD", 10);

// 按方向查询信号
List<TechnicalSignal> bullishSignals = technicalSignalService.getSignalsByDirection(
    "BTCUSDT", "MACD", "BULLISH", 20);

// 根据哈希查询
TechnicalSignal signal = technicalSignalService.getByHash(signalHash);
```

### 3. 信号统计与分析

#### `getSignalStatistics(TechnicalSignalQuery query)`
获取全面的信号统计信息
```java
SignalStatisticsVO stats = technicalSignalService.getSignalStatistics(query);
System.out.println("总信号数: " + stats.getTotalSignals());
System.out.println("信号有效率: " + stats.getSignalValidityRate());
```

#### 性能分析
```java
// 计算指标命中率
BigDecimal hitRate = technicalSignalService.calculateIndicatorHitRate("BTCUSDT", "MACD", 24);

// 获取指标性能分析
List<IndicatorPerformanceVO> performances = technicalSignalService.analyzeIndicatorPerformance(
    "BTCUSDT", startTime, endTime);

// 计算信号相关性
BigDecimal correlation = technicalSignalService.calculateSignalCorrelation("MACD", "RSI", "BTCUSDT");
```

#### 分布统计
```java
// 信号强度分布
Map<String, Integer> strengthDist = technicalSignalService.getSignalStrengthDistribution("BTCUSDT", "MACD");

// 分组统计
Map<String, Long> timeGroups = technicalSignalService.groupSignalsByTime("BTCUSDT", "1h", startTime, endTime);
Map<String, Long> indicatorGroups = technicalSignalService.groupSignalsByIndicator("BTCUSDT", startTime, endTime);
```

### 4. 信号处理与转换

#### 业务信号转换判断
```java
// 检查是否可以生成业务信号
Boolean canGenerate = technicalSignalService.canGenerateTradeSignal(technicalSignalId);

// 获取信号摘要
String brief = technicalSignalService.getSignalBrief(technicalSignalId);

// 提取信号特征
Map<String, Object> features = technicalSignalService.extractSignalFeatures(technicalSignalId);
```

#### 信号验证
```java
// 验证信号有效性
Boolean isValid = technicalSignalService.validateSignal(technicalSignalId);

// 验证信号一致性
Map<String, Boolean> consistency = technicalSignalService.validateSignalConsistency(signal);
```

### 5. 缓存管理

#### 缓存操作
```java
// 缓存信号
technicalSignalService.cacheTechnicalSignal(signal, 3600L); // 缓存1小时

// 从缓存获取
TechnicalSignal cached = technicalSignalService.getTechnicalSignalFromCache(signalHash);

// 清除缓存
technicalSignalService.evictTechnicalSignalFromCache(signalHash);

// 获取缓存中的最新信号
TechnicalSignal latest = technicalSignalService.getLatestSignalFromCache("BTCUSDT", "MACD");
```

### 6. 监控与告警

#### 信号监控
```java
// 监控信号生成频率
Long frequency = technicalSignalService.monitorSignalFrequency("MACD", 60); // 60分钟内的信号数量

// 检测异常信号
List<TechnicalSignal> anomalies = technicalSignalService.detectAnomalousSignals("BTCUSDT", BigDecimal.valueOf(0.95));

// 触发告警
technicalSignalService.triggerSignalAlarm(signal, "HIGH_FREQUENCY");
```

### 7. 批量操作

#### 数据维护
```java
// 批量删除过期信号
Integer deleted = technicalSignalService.batchDeleteExpiredSignals(30); // 删除30天前的数据

// 批量更新状态
Integer updated = technicalSignalService.batchUpdateSignalStatus(signalIds, "ARCHIVED");

// 批量导出
String filePath = technicalSignalService.batchExportSignals(query, "CSV");
```

### 8. 数据质量管理

#### 质量检查
```java
// 检查数据完整性
Map<String, Object> integrity = technicalSignalService.checkDataIntegrity(startTime, endTime);

// 修复异常数据
Boolean repaired = technicalSignalService.repairAbnormalData(signalId);
```

## 使用注意事项

### 1. 性能优化

- **批量操作**: 对于大量信号，优先使用批量保存方法
- **索引优化**: 查询条件应充分利用数据库索引
- **缓存策略**: 热点数据应合理使用缓存
- **分页查询**: 大数据量查询必须使用分页

### 2. 数据一致性

- **哈希去重**: 确保信号哈希的唯一性
- **时间戳管理**: 正确处理时区和时间戳转换
- **状态同步**: 缓存与数据库状态保持一致

### 3. 异常处理

- **网络异常**: 重试机制和降级处理
- **数据异常**: 完整性验证和修复机制
- **并发控制**: 避免信号重复生成

### 4. 监控告警

- **频率监控**: 防止信号生成过频
- **质量监控**: 实时监控信号质量
- **异常检测**: 及时发现和处理异常信号

## 架构优势

### 1. 职责分离
- **纯技术层面**: 只处理技术指标逻辑
- **业务无关**: 不涉及交易决策和执行
- **接口清晰**: 功能划分明确，易于维护

### 2. 高性能设计
- **批量处理**: 支持高并发批量操作
- **缓存优化**: 多层缓存策略
- **索引优化**: 合理的数据库索引设计

### 3. 可扩展性
- **模块化设计**: 功能独立，便于扩展
- **接口标准化**: 统一的调用规范
- **配置灵活**: 支持多种配置选项

## 最佳实践

### 1. 信号生成
```java
// 推荐的信号生成流程
TechnicalSignalDTO dto = buildSignalDTO(indicatorData);
String hash = technicalSignalService.generateSignalHash(dto);

if (!technicalSignalService.existsByHash(hash)) {
    Long signalId = technicalSignalService.saveTechnicalSignal(dto);
    // 后续业务处理
}
```

### 2. 查询优化
```java
// 使用查询对象进行复杂查询
TechnicalSignalQuery query = TechnicalSignalQuery.builder()
    .symbol("BTCUSDT")
    .indicator("MACD")
    .startTime(startTime)
    .endTime(endTime)
    .minSignalStrength(BigDecimal.valueOf(0.7))
    .pageNum(1)
    .pageSize(50)
    .build();

IPage<TechnicalSignal> result = technicalSignalService.pageTechnicalSignals(query);
```

### 3. 统计分析
```java
// 定期进行信号质量分析
SignalStatisticsVO stats = technicalSignalService.getSignalStatistics(query);
if (stats.getSignalValidityRate().compareTo(BigDecimal.valueOf(0.8)) < 0) {
    // 触发质量告警
    technicalSignalService.triggerSignalAlarm(null, "QUALITY_DEGRADATION");
}
```

## 扩展建议

1. **实时计算**: 集成流处理框架进行实时信号计算
2. **机器学习**: 添加信号质量预测和优化建议
3. **多数据源**: 支持多种数据源的信号聚合
4. **智能缓存**: 基于访问模式的智能缓存策略
5. **监控面板**: 集成可视化监控面板

这个服务接口为技术信号管理提供了完整的功能集合，既保证了性能和可靠性，又保持了清晰的职责分离。
