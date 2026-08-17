# getWeightAndConfidence SMC 计算结果复用优化方案

## 问题背景

在 `DefaultSignService.getWeightAndConfidence()` 方法中，同一次调用内对相同 `(symbol, interval, signalTime)` 的 SMC 指标存在重复计算。

## 重复计算现状

```
getWeightAndConfidence()
  signalTime = kLines.get(size-1).getId()   ← 统一的时间基点
  │
  ├── [行128] computeSmcSnapshot(OKXMIN15, signalTime)     ① 计算一次
  ├── [行129] computeSmcSnapshot(OKXMIN60, signalTime)     ② 计算一次
  │
  └── evaluateWeightRuleEngine()
        ├── computeSmcSnapshot(OKX4HOUR, signalTime)        ③ 唯一
        ├── computeSmcSnapshot(OKX1D, signalTime)           ④ 唯一
        ├── computeSmcResult(OKX4HOUR, signalTime)          ← ⑤ 与③重复
        ├── computeSmcResult(OKXMIN60, signalTime)          ← ⑥ 与②重复
        └── computeSmcResult(OKXMIN15, signalTime)          ← ⑦ 与①重复
```

**每次 SMC 计算的代价**：
- `loadKlines(limit=1000)` → DB 查询 + MyBatis 映射
- `buildSeries(...)` → BarSeries 构造
- `new SmartMoneyConceptsIndicator(...)` → 完整 SMC 指标计算（swings、OB、FVG、BOS/CHOCH 等）

**当前最坏情况**：单次 `getWeightAndConfidence` → 7 次 SMC 计算 → 7000 条 K 线 DB 查询。

## 优化方案：方法级别 SMC 结果缓存

### 核心思路

在 `getWeightAndConfidence` 方法内部，通过 `ThreadLocal<Map<String, SmartMoneyConceptsIndicator.Result>>` 缓存 SMC 计算结果，`computeSmcSnapshot` 和 `computeSmcResult` 统一从缓存取结果，避免重复计算。

### 缓存 Key

缓存 key = `symbol + ":" + interval.name() + ":" + signalTimeMs`

包含 `signalTimeMs` 的原因是：同一 `(symbol, interval)` 在不同时间点调用，`findIndexById` 找到的 `targetIndex` 不同，SMC 指标计算结果也可能不同。

### 缓存作用域

`ThreadLocal` 的 `Map` 绑定到当前线程，在单次 `getWeightAndConfidence` 方法入口处 `.get().clear()`，方法结束自动回收。与请求级生命周期相同，无并发问题。

### 改动范围

只改 `DefaultSignService.java`，只涉及 3 处改动：
1. 新增 `ThreadLocal` 字段
2. `getWeightAndConfidence` 入口清缓存
3. 新增 `getCachedSmcResult` 方法，`computeSmcSnapshot` / `computeSmcResult` 统一委托

子类无感知。

### 实现代码

```java
// ==================== DefaultSignService.java ====================

/** 字段：请求级 SMC 结果缓存，key=symbol:interval:signalTimeMs */
private final ThreadLocal<Map<String, SmartMoneyConceptsIndicator.Result>> smcResultCache =
        ThreadLocal.withInitial(HashMap::new);
```

```java
// 改动1：getWeightAndConfidence 入口清理缓存
@Override
public WeightAndConfidenceDto getWeightAndConfidence(IndicatorCalcDto calcDto) {
    applyConfiguredParams();
    applyOverrideParams(calcDto.getParameterOverrides());
    smcResultCache.get().clear();   // ← 清空上次调用的残余
    // ... 后续逻辑不变 ...
}
```

```java
// 改动2：新增 getCachedSmcResult 统一缓存读取 + 计算
private SmartMoneyConceptsIndicator.Result getCachedSmcResult(
        String symbol, CandlestickIntervalEnum intervalEnum, long signalTimeMs) {
    String key = symbol + ":" + intervalEnum.name() + ":" + signalTimeMs;
    Map<String, SmartMoneyConceptsIndicator.Result> cache = smcResultCache.get();
    SmartMoneyConceptsIndicator.Result result = cache.get(key);
    if (result == null) {
        result = computeSmcIndicatorResult(symbol, intervalEnum, signalTimeMs);
        if (result != null) {
            cache.put(key, result);
        }
    }
    return result;
}
```

```java
// 改动3：computeSmcSnapshot 和 computeSmcResult 委托给缓存
protected SmcBarResult computeSmcSnapshot(String symbol, CandlestickIntervalEnum intervalEnum, long signalTimeMs) {
    SmartMoneyConceptsIndicator.Result result = getCachedSmcResult(symbol, intervalEnum, signalTimeMs);
    // ... DTO 转换逻辑不变 ...
}

protected SmartMoneyConceptsIndicator.Result computeSmcResult(String symbol, CandlestickIntervalEnum intervalEnum, long signalTimeMs) {
    return getCachedSmcResult(symbol, intervalEnum, signalTimeMs);
}
```

### 线程安全说明

- `computeSmcIndicatorResult` 内部每次 new 出 `SmartMoneyConceptsIndicator` 等对象，无共享可变状态
- `HashMap` 仅在线程内单次方法调用中使用，不存在跨线程读写
- `getWeightAndConfidence` 入口 `clear()` 确保每次调用从上一次结果中解耦

### 收益

| 指标 | 优化前 | 优化后 | 收益 |
|------|--------|--------|------|
| SMC 计算次数 | 7 次 | 4 次 | **-43%** |
| DB K 线查询量 | 7000 条 | 4000 条 | **-43%** |
| 行级代码变更 | — | ~10 行新增，~2 行修改 | 极小影响面 |

### 实施前提

- `computeSmcIndicatorResult` 是纯函数：相同 `(symbol, interval, signalTimeMs)` 输入，返回相同结果
- 当前 `computeSmcIndicatorResult` 内部 `loadKlines` 使用 `endTime=signalTimeMs, size=1000`，输入参数已可唯一确定输出

### 风险与注意事项

1. **不改变任何业务逻辑**：缓存只是消除重复计算，不改变计算方式或结果
2. **请求级缓存无并发问题**：`ThreadLocal` 绑定线程，`HashMap` 仅在线程内单次方法调用中使用
3. **入口 clear() 确保隔离**：每次 `getWeightAndConfidence` 先清空缓存，避免线程复用导致的上次调用残留数据被本次误命中
4. **仅共享 `SmartMoneyConceptsIndicator.Result`**：`SmcBarResult` 仍由 `computeSmcSnapshot` 独自构建（DTO 转换），不缓存
5. **`ThreadLocal.withInitial(HashMap::new)` 惰性初始化**：只有真正用到缓存的方法调用才会创建 Map，零额外开销
