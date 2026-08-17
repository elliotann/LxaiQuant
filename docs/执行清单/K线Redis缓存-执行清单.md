# K线Redis缓存 - 执行清单

---

## 一、配置准备

### 1.1 添加配置项

**文件：** `ai-data/src/main/resources/application.yml`

```yaml
kline:
  redis-cache:
    enabled: false   # 默认关闭，生产环境 application-prod.yml 覆盖为 true
```

生产配置 `application-prod.yml`：

```yaml
kline:
  redis-cache:
    enabled: true
```

---

## 二、代码实现（ai-data模块）

### 2.1 实现缓存辅助类

| 任务 | 说明 | 优先级 |
|------|------|--------|
| 2.1.1 新增 `KlineCacheHelper` | 封装 ZSET 读写、Key 构建、JSON 序列化 | P0 |

```java
// 待实现的核心方法
- buildKey(exchange, symbol, interval) → String
- put(key, timestamp, candlestick)      → ZADD + 清理旧数据
- getLatest(key, size)                  → ZREVRANGE
- getByRange(key, from, to)            → ZRANGEBYSCORE
- size(key)                             → ZCARD
- clear(key)                            → 删除Key
```

### 2.2 修改 CandlestickServiceImpl

| 任务 | 说明 | 优先级 |
|------|------|--------|
| 2.2.1 注入 `RedisCache` 和配置开关 | `@Autowired RedisCache` + `@Value` 读 enabled 开关 | P0 |
| 2.2.2 `getKlines4KChart()` 加缓存 | 最新 1200 条范围内走 Redis | P0 |
| 2.2.3 `getByQry()` 加缓存 | 同上 | P0 |
| 2.2.4 `getLastKlines()` 加缓存 | 同上 | P0 |
| 2.2.5 `getLastKline()` 加缓存 | 同 symbol+interval 的最新1条 | P0 |
| 2.2.6 `getKlines()` 加缓存 | 同上（按参数过滤） | P1 |
| 2.2.7 `batchSave()` 加 Redis 写入 | MySQL 写入成功后增量更新 ZSET | P0 |

### 2.3 判断条件（所有查询方法通用）

```
走缓存条件（同时满足）:
  1. `kline.redis-cache.enabled = true`
  2. `param.isTest = false`（实盘查询，默认就是 false）
  3. 查询数量 ≤ 1200
  4. 不是查询历史数据（时间范围在最近1200条内）

走 MySQL 条件（任一满足）:
  1. `kline.redis-cache.enabled = false`
  2. `param.isTest = true`（回测/测试查询）
  3. 查询数量 > 1200
  4. 查询的是历史数据（早于缓存中最旧的时间戳）
```

---

## 三、测试验证

### 3.1 单元测试

| 任务 | 说明 | 优先级 |
|------|------|--------|
| 3.1.1 测试写入后能正确读取 | batchSave → getKlines4KChart 验证数量 | P0 |
| 3.1.2 测试超过1200条自动清除 | 写入1300条 → 验证 Redis 只保留1200条 | P0 |
| 3.1.3 测试相同时间戳覆盖更新 | 同时间戳写入两次 → 验证无重复 | P0 |
| 3.1.4 测试开关关闭时走 MySQL | enabled=false → 验证不走 Redis | P0 |
| 3.1.5 测试 Redis 宕机自动降级 | 模拟 Redis 不可用 → 穿透 MySQL | P1 |

### 3.2 集成测试

| 任务 | 说明 | 优先级 |
|------|------|--------|
| 3.2.1 测试环境开启缓存验证 | 启动 test 环境，打开开关，验证 K线图表正常 | P0 |
| 3.2.2 验证查询返回数据格式一致 | MySQL 直查 vs Redis 缓存返回的字段值一致 | P0 |
| 3.2.3 验证 KlineCollectorTask 写入后 Redis 正确更新 | 跑一轮定时任务后检查 Redis 数据 | P1 |

### 3.3 生产验证

| 任务 | 说明 | 优先级 |
|------|------|--------|
| 3.3.1 先在预发布环境灰度开启 | 观察日志、监控，确认无异常 | P0 |
| 3.3.2 监控 Redis 内存增长 | 确认在预期范围内（约 60MB） | P0 |
| 3.3.3 监控 K线接口响应时间 | 对比缓存前后 P99 延迟下降 | P0 |

---

## 四、回滚方案

| 步骤 | 操作 |
|------|------|
| 1 | 将 `kline.redis-cache.enabled` 设为 `false` |
| 2 | 重启应用（或动态刷新配置） |
| 3 | 确认所有查询回到 MySQL |
| 4 | 可选：`redis-cli KEYS "kline:cache:*" | xargs redis-cli DEL` 清理缓存 |

---

## 五、时间预估

| 阶段 | 内容 | 预估 |
|------|------|------|
| 设计 | 已完成 | — |
| 编码 | 辅助类 + 修改 CandlestickServiceImpl | 约 2 小时 |
| 单元测试 | 5 个测试用例 | 约 1 小时 |
| 集成测试 | 环境部署 + 验证 | 约 1 小时 |
| 上线 | 灰度 + 全量 | 约 0.5 小时 |
| **合计** | | **约 4.5 小时** |

---

## 六、依赖

- [x] Redis 已在生产环境部署
- [x] ai-common 模块已有 `RedisCache`（支持 ZSET 操作）
- [x] ai-data 模块已有 `spring-boot-starter-data-redis` 依赖
- [ ] 无新增 Maven/Gradle 依赖
