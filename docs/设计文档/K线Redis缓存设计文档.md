# K线Redis缓存设计文档

## 1. 背景

实盘中 K线查询压力大，每次图表加载、滑屏加载、实时刷新都直接查 MySQL。随着K线数据量增长，查询性能持续下降。目标是将最近 1200 条 K线数据缓存到 Redis，**实盘查 Redis，测试/历史数据查 MySQL**。

---

## 2. 总体架构

```
交易所
   │
   ▼
KlineCollectorTask (定时拉取)
   │
   ▼
CandlestickServiceImpl.batchSave()
   ├── 1. 写入 MySQL
   └── 2. 增量更新 Redis (ZADD + ZREMRANGEBYRANK)
                                                  ┌──────────────┐
          ┌──────────────┐                        │   Redis      │
          │  KLineV1     │ ── 查询K线数据 ──▶    │  ZSET        │
          │  Service     │ ◀── 返回 ────          │  1200条/key  │
          └──────────────┘                        └──────────────┘
                  │                                      │
                  │ 超过1200条或查询历史                   │
                  ▼                                      ▼
          ┌──────────────┐                        ┌──────────────┐
          │   MySQL      │                        │   未命中     │
          │  Candlestick │ ◀── 回填 ────────────── │              │
          └──────────────┘                        └──────────────┘
```

---

## 3. Redis 数据结构

### 3.1 Key 设计

```
kline:cache:{exchange}:{symbol}:{interval}
```

示例：

| Key | 说明 |
|-----|------|
| `kline:cache:OKX:BTC-USDT:OKXMIN3` | OKX 交易所 BTC-USDT 3分钟K线 |
| `kline:cache:BINANCE:BTC-USDT:OKXMIN15` | Binance 15分钟K线 |

### 3.2 Value 设计

**类型：Sorted Set (ZSET)**

| 字段 | 说明 |
|------|------|
| **Score** | K线时间戳（秒级，如 `1719129600`） |
| **Member** | JSON 序列化的 `Candlestick` 完整对象 |

### 3.3 容量控制

每个 Key 最多保留 **1200 条**，写入后执行 `ZREMRANGEBYRANK key 0 -1201` 删除最早的数据。

### 3.4 内存预估

- 单条 ~200 bytes
- 1200 条 ≈ 240KB / 每个 (exchange, symbol, interval) 组合
- 假设 50 交易对 × 5 周期 × 1 交易所 ≈ 250 个 Key
- 总内存 ≈ 250 × 240KB ≈ **60MB**

---

## 4. 核心流程

### 4.1 写入缓存（batchSave 触发）

```
batchSave(entityList):
  1. 写入 MySQL（原有逻辑不变）

  2. 按 (exchange, symbol, interval) 分组

  3. 每组循环:
     for candle in entityList:
         timestamp = extractTimestamp(candle)     // 从 timeStr 提取秒级时间戳
         key = buildKey(exchange, symbol, interval)
         redis.zAdd(key, timestamp, toJson(candle))   // ZADD，相同 score 自动覆盖

         // 清理旧数据，只保留最新1200条
         redis.zRemRangeByRank(key, 0, -1201)
```

### 4.2 读取缓存（5个查询方法）

```
query(方法参数):
  key = buildKey(exchange, symbol, interval)

  // 判断是否走缓存（同时满足才走 Redis）
  if !isTest && 查询范围 ≤ 1200条:
      total = redis.zCard(key)
      if total == 0:
          // 缓存未初始化 → 查 MySQL → 回填 Redis
          data = mysql.query(...)
          backfillCache(key, data)
          return data

      // 从 ZSET 取最新 N 条 (ZREVRANGE 降序)
      rawList = redis.zRevRange(key, 0, N - 1)
      return parseAndReverse(rawList)

  else:
      // 以下任一情况走 MySQL:
      //   1. isTest = true（回测/测试）
      //   2. 查询数量 > 1200（超出缓存范围）
      //   3. Redis 不可用（zCard 返回 0）
      return mysql.query(...)
```

### 4.3 实盘 vs 回测区分

通过 `KlineParam.isTest` 字段区分（已有字段，无需新增）：

| 来源 | isTest 值 | 缓存行为 |
|------|-----------|---------|
| 实盘查询（`KLineV1Service`） | `false`（默认） | 走 Redis 缓存 |
| 回测查询（`BacktestService`等） | `true` | 跳过 Redis，直接查 MySQL |

### 4.4 缓存预热

应用启动时不做自动预热，**首次查询触发回填**即可。

---

## 5. 数据一致性

### 5.1 重复数据

ZSET 的 `score` 是时间戳，同一根K线重复写入时 ZADD 会**覆盖更新**（同 score 的 member 被替换），天然去重。

### 5.2 连续性

Redis 不主动检测 gap。连续性依赖上游数据源：
- `KlineCollectorTask` 拉取的原始数据本身就是连续的
- 断线重连后，批量拉取的缺失数据通过 ZADD 自动插入到 ZSET 正确位置

### 5.3 缓存与 MySQL 不一致的场景

| 场景 | 影响 | 容忍度 |
|------|------|--------|
| MySQL 写入成功，Redis 写入失败 | Redis 下次查询回填 | 低，短暂不一致 |
| Redis 写入成功，MySQL 写入失败 | MySQL 事务回滚，Redis 多了一条 | 高，多一帧旧数据无影响 |

解决方案：`batchSave` 中 Redis 操作放在 MySQL 之后，MySQL 失败时 Redis 不执行。

---

## 6. 配置开关

```yaml
# application-prod.yml
kline:
  redis-cache:
    enabled: true          # 实盘开启

# application-test.yml / application-dev.yml
kline:
  redis-cache:
    enabled: false         # 测试环境关闭，全走 MySQL
```

通过 `@ConditionalOnProperty` 控制缓存逻辑是否生效，测试环境零侵入。

---

## 7. 变更范围

### 7.1 涉及的模块与文件

| 模块 | 文件 | 操作 | 说明 |
|------|------|------|------|
| **ai-data** | `CandlestickServiceImpl.java` | 修改 | 5 个查询方法加缓存逻辑（判 isTest）；batchSave 加写入逻辑 |
| **ai-data** | `application.yml` | 新增配置 | `kline.redis-cache.enabled` |
| **ai-common** | `RedisCache.java` | 不改 | 已有 zAdd / zRevRange / zRemRangeByRank / zCard 等方法 |

### 7.2 不涉及的模块

| 模块 | 说明 |
|------|------|
| ai-quant | 业务层无需改动，对 KLineV1ServiceImpl 透明 |
| ai-signal | 不涉及 |
| ai-member | 不涉及 |
| ai-xchange-extends | 不涉及 |
| ai-task | `KlineCollectorTask` 不改，它调的是 batchSave |

---

## 8. 风险与注意事项

1. **并行写入**：同一时刻多个线程调 batchSave 往同一个 Key ZADD，ZSET 是原子操作，无并发问题
2. **大 ZSET 性能**：1200 条的 ZREVRANGE / ZREMRANGEBYRANK 均是微秒级，无性能瓶颈
3. **JSON 序列化**：用已有的 Jackson ObjectMapper（项目已在用），无需新增依赖
4. **首次查询**：缓存未命中时自动回填，首次响应会比平时慢一次（多了一次 Redis 写入），可接受
5. **失效兜底**：Redis 宕机时 zCard 返回 0，自动穿透到 MySQL，不影响业务
