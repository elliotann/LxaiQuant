# 5.7 AI 分析模块 & 5.8 AI 雷达模块

> 本文档为从 `完整架构设计文档` 抽取的独立设计文档，原位置保留引用注解。
> 分析日期：2026-06-03

---

## 5.7 AI 分析模块（已实现）

### 5.7.1 核心能力

- 自然语言生成策略（输出为策略模板）
- 策略代码解释
- 市场问答
- 批量定时分析：用户可对多个自选标的创建定时分析任务，按固定间隔自动执行 AI 分析并生成报告

### 5.7.2 批量定时任务设计

- **MonitorTask（监控任务）**：用户创建的一个定时分析配置，包含目标标的列表、分析间隔、通知渠道、启用状态
- 分析间隔支持 30 分钟、1 小时、4 小时、12 小时、24 小时
- 前端状态管理：批量模式、勾选标的、任务抽屉
- 后端通过 XXL-JOB 实现定时执行，生成报告存入 `analysis_reports` 表

### 5.7.3 数据库表设计

```sql
CREATE TABLE `ai_analysis_tasks` (
  `id` CHAR(36) PRIMARY KEY,
  `user_id` VARCHAR(32) NOT NULL,
  `symbols` JSON NOT NULL,
  `interval_min` INT NOT NULL DEFAULT 60,
  `notify_channels` JSON DEFAULT NULL,
  `enabled` TINYINT(1) DEFAULT 1,
  `last_run_at` TIMESTAMP NULL,
  `next_run_at` TIMESTAMP NULL,
  `xxl_job_id` INT DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE `analysis_reports` (
  `id` CHAR(36) PRIMARY KEY,
  `task_id` CHAR(36) NOT NULL,
  `symbol` VARCHAR(50) NOT NULL,
  `decision` VARCHAR(10),
  `confidence` INT,
  `summary` TEXT,
  `analysis` TEXT,
  `risks` TEXT,
  `trigger_type` VARCHAR(20) DEFAULT 'SCHEDULED',
  `report_json` JSON,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 5.8 AI 雷达模块（已实现）

### 5.8.1 模块概述

AI 雷达是一个多市场交易机会扫描与 AI 智能分析的前端页面，用于展示多市场（加密货币、美股、A股、港股、外汇）的潜在交易信号。

### 5.8.2 页面结构

- **顶部轮播区**：展示交易机会卡片（标的、价格、涨跌幅、信号类型）
- **工作区**：
  - Tab 1 即时分析：指标栏、市场热力图、分析工具栏、AI分析占位区
  - Tab 2 预测市场（Polymarket）– 开发中
- **自选股面板**：自选标的列表，支持添加/移除

### 5.8.3 后端架构

- **AiRadarController**：REST API 入口
- **AiRadarService**：扫描机会、技术指标计算、排序
- **MarketAnalysisService**：技术指标计算引擎（EMA、RSI、ATR、布林带、趋势、情绪、支撑阻力）
- **信号生成规则**：超买/超卖、看涨/看跌动能、盘整
- **信号强度**：strong/medium/weak

---


## 5.10 统一标的字典与自选股设计方案

> 设计日期：2026-06-03
> 状态：✅ 已实现（DDL + 后端 + 前端 API 完成）

### 5.10.1 设计目标

建立 `symbols`（统一标的字典）+ `user_favorites`（用户自选股）两张表，解决以下问题：

1. **雷达扫描范围**：替代当前 `SELECT DISTINCT symbol FROM candlestick` 扫到冷门标的的问题，改为热门标的 + 用户自选股双源
2. **前端选标硬编码**：前端多处（策略创建、回测配置、AI 分析、交易机器人）选标列表写死，统一从数据库读取
3. **自选股不跨设备**：当前自选股存 `localStorage`，后端不可读、手机/多设备不同步
4. **市场分类不准确**：当前 `classifyMarket()` 正则推断不可靠，已改为从 `symbols` 表的 `market` 字段查询

### 5.10.2 表结构设计

#### symbols（统一标的字典）

```sql
CREATE TABLE `symbols` (
  `id`          INT AUTO_INCREMENT PRIMARY KEY,
  `market`      VARCHAR(20)  NOT NULL COMMENT '市场：Crypto/USStock/CNStock/HKStock/Forex',
  `symbol`      VARCHAR(50)  NOT NULL COMMENT '标的代码，如 BTCUSDT、AAPL',
  `name`        VARCHAR(100) DEFAULT NULL COMMENT '中文名称，如 比特币、苹果',
  `exchange`    VARCHAR(20)  DEFAULT NULL COMMENT '交易所，如 OKX、BINANCE、NYSE',
  `is_hot`      TINYINT(1)   DEFAULT 0 COMMENT '是否热门标的，供雷达/推荐使用',
  `sort_order`  INT          DEFAULT 0 COMMENT '排序优先级，同 market 内排序',
  `active`      TINYINT(1)   DEFAULT 1 COMMENT '是否启用',
  `created_at`  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_market_symbol` (`market`, `symbol`),
  KEY `idx_market_hot` (`market`, `is_hot`, `sort_order`)
);
```

#### user_favorites（用户自选股）

```sql
CREATE TABLE `user_favorites` (
  `id`         INT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    VARCHAR(32) NOT NULL COMMENT '用户ID',
  `symbol_id`  INT NOT NULL COMMENT '关联 symbols.id',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_symbol` (`user_id`, `symbol_id`),
  KEY `idx_user_id` (`user_id`),
  FOREIGN KEY (`symbol_id`) REFERENCES `symbols`(`id`) ON DELETE CASCADE
);
```

**设计要点**：
- `user_favorites` 只存 `symbol_id` 不冗余存 `symbol`/`market`，保证数据一致性
- 联合唯一键 `(user_id, symbol_id)` 防止重复添加
- 外键级联删除，移除标的时自动清理关联自选

### 5.10.3 数据初始化

```sql
-- 1) 从现有 candlestick 表导入所有已知交易对
INSERT INTO symbols (market, symbol, exchange)
SELECT DISTINCT
  COALESCE(market_type,
    CASE
      WHEN symbol LIKE '%USDT' THEN 'Crypto'
      WHEN symbol REGEXP '^[A-Z]{1,4}$' THEN 'USStock'
      ELSE 'Other'
    END
  ) AS market,
  symbol,
  exchange
FROM candlestick
WHERE symbol IS NOT NULL;

-- 2) 手动标记热门标的
UPDATE symbols SET is_hot = 1, sort_order = 10
WHERE (market, symbol) IN (
  ('Crypto', 'BTCUSDT'), ('Crypto', 'ETHUSDT'), ('Crypto', 'SOLUSDT'),
  ('Crypto', 'BNBUSDT'), ('Crypto', 'XRPUSDT'), ('Crypto', 'DOGEUSDT'),
  ('Crypto', 'ADAUSDT'), ('USStock', 'AAPL'), ('USStock', 'MSFT'),
  ('USStock', 'GOOGL'), ('USStock', 'AMZN'), ('USStock', 'TSLA'),
  ('USStock', 'NVDA'), ('USStock', 'META')
  -- 后续按需持续补充
);
```

### 5.10.4 后端改动（模块分布）

**模块分布约定**：
- `ai-data` 模块：Entity + Service + Mapper（基础数据层）
- `ai-quant` 模块：Controller 层（`controller/market/` 包）

**ai-data 模块新增**：

| 类 | 包路径 | 功能 |
|------|--------|------|
| `entity/dos/Symbol.java` | `com.chain.ai.trade.engine.data.entity.dos` | symbols 表实体 |
| `entity/dos/UserFavorite.java` | `com.chain.ai.trade.engine.data.entity.dos` | user_favorites 表实体 |
| `service/ISymbolsService.java` | `com.chain.ai.trade.engine.data.service` | 标的查询接口 |
| `service/impl/SymbolsServiceImpl.java` | `com.chain.ai.trade.engine.data.service.impl` | 标的查询实现（getHotSymbols / search / listByMarket） |
| `service/IUserFavoriteService.java` | `com.chain.ai.trade.engine.data.service` | 自选股接口 |
| `service/impl/UserFavoriteServiceImpl.java` | `com.chain.ai.trade.engine.data.service.impl` | 自选股实现（add / remove / getByUserId） |
| `mapper/SymbolsMapper.java` | `com.chain.ai.trade.engine.mapper` | MyBatis-Plus Mapper |
| `mapper/UserFavoriteMapper.java` | `com.chain.ai.trade.engine.mapper` | MyBatis-Plus Mapper |

**ai-quant 模块新增**（`controller/market/` 包）：

| 类 | 功能 |
|------|------|
| `SymbolsController` | `GET /api/symbols?market=&is_hot=&keyword=` 搜索/筛选标的 |
| `UserFavoriteController` | `GET /api/user/favorites` 获取自选列表 |
| | `POST /api/user/favorites` 添加自选（body: `{symbol_id}`） |
| | `DELETE /api/user/favorites/{symbolId}` 移除自选 |

**修改**：

| 类 | 模块 | 改动内容 |
|------|------|---------|
| `AiRadarService.scanOpportunities()` | ai-quant | 原 `getLatestTickers()` → `symbolsService.getHotSymbols()` + `userFavoritesService.getByUserId(userId)` 合并去重 |
| `AiRadarController` | ai-quant | 接收 `userId` 参数传递到 Service |

### 5.10.5 前端改动

**新增 API 文件 `src/api/symbols.ts`**：
```typescript
export function getSymbols(params: { market?: string; is_hot?: boolean; keyword?: string }): Promise<SymbolDTO[]>
export function addFavorite(symbolId: number): Promise<void>
export function removeFavorite(symbolId: number): Promise<void>
export function getFavorites(): Promise<FavoriteDTO[]>
```

**替换硬编码选标列表的文件**：

| 文件 | 当前 | 改后 |
|------|------|------|
| `StrategyManagement.vue` | 硬编码 symbol 选项 | `getSymbols({market: 'Crypto'})` |
| `BacktestConfigDialog.vue` | 硬编码列表 | `getSymbols()` |
| `AiRadar.vue` 自选股面板 | `localStorage` | `getFavorites()` + localStorage 本地缓存 |
| 其他涉及选标组件 | 硬编码 | 逐步替换 |

**自选股管理**：
- 保留 `localStorage` 作为**本地缓存**，页面打开秒级展示
- 后台异步请求 `getFavorites()` 拉取服务端数据做同步
- 增删操作同时更新 localStorage 和调用后端 API

### 5.10.6 影响范围总览

| 层 | SQL | ai-data 模块 | ai-quant 模块 | 前端 | 数据 |
|----|-----|-------------|-------------|------|------|
| 新建 `symbols` 表 | ✅ DDL | Entity + Service + Mapper | SymbolsController | symbols.ts API | 从 candlestick 导入 |
| 新建 `user_favorites` 表 | ✅ DDL | Entity + Service + Mapper | UserFavoriteController | favorites API | 从 localStorage 迁移 |
| 修改雷达扫描 | — | — | AiRadarService + Controller | — | 热门+自选双源 |
| 替换前端选标 | — | — | — | 逐步替换各组件 | — |

### 5.10.7 与 QuantDinger 对比

| 维度 | QuantDinger | Lenzeto（改后） |
|------|-------------|----------------|
| 标的表 | `qd_market_symbols` | `symbols` |
| 自选表 | `qd_user_favorites` | `user_favorites` |
| 热门控制 | `is_hot` 字段 | `is_hot` + `sort_order` 字段 |
| 覆盖范围 | 热门 + 搜索 | ✅ 全系统统一标的字典（不仅是雷达） |
| 前端选标 | 从 API 获取 | ✅ 从 API 获取（统一替换硬编码） |
| 雷达范围 | 热门标的 + 自选股 | 热门标的 + 自选股 |

---

## 5.9 Lenzeto vs QuantDinger 对比分析（AI 雷达模块）

> 对比日期：2026-06-03
> 进度说明：✅ 已完成 / 🔧 需优化 / ❌ 缺失待补

### 5.9.1 对比总表

| 维度 | Lenzeto 实现 | QuantDinger 实现 | 差异分析 | 进度 |
|------|-------------|-----------------|---------|------|
| **信号生成方式** | RSI(14) + EMA9/21趋势 + Bollinger带 + ATR 复合评分 | 纯 24h 涨跌幅阈值 | Lenzeto 多指标交叉验证更专业，QuantDinger 简单但直观 | ✅ 保留 |
| **扫描范围** | 数据库拉取活跃交易对（最多60个） | 固定热门标的列表（每市场12-20个） | Lenzeto 可能混入冷门标的，QuantDinger 可控性更好 | ✅ 已改为热门标的+自选股双源 |
| **市场扫描方式** | 一次全量扫描，按 symbol 名正则推断市场 | 分市场独立 scanner + `is_market_visible()` 控制 | Lenzeto 一个异常可能影响全部；QuantDinger 各市场独立容错 | 🔧 建议改为分市场扫描 |
| **容错性** | 单个 symbol 异常 try/catch 跳过 | 每个市场 scanner 独立 try/catch | QuantDinger 更健壮（市场级隔离） | 🔧 需加强 |
| **缓存策略** | Controller 接收 force 参数，但 Service 无缓存实现 | `cached_or_compute` 统一缓存层，TTL 可配，支持 force 刷新 | Lenzeto 无实质缓存，每次请求都全量计算 | ❌ 需补充缓存层 |
| **数据源** | 仅依赖本地数据库 KLine | Yahoo API → Stooq API → KLine 三级降级 | Lenzeto 无降级策略，依赖本地数据完整性 | ❌ 需加降级策略 |
| **并发获取** | 串行逐个 analyze → 已改为 CompletableFuture + 8线程池 | `ThreadPoolExecutor(8)` 并行获取价格 | 已改为并发模式，与 QuantDinger 一致 | ✅ 已完成 |
| **市场分类** | `classifyMarket()` 按 symbol 名字正则推断 | 预定义数组（TOP_CRYPTO_SYMBOLS / FOREX_PAIRS 等） | Lenzeto 正则匹配不可靠（如 AAPL 可能匹配 [A-Z]{1,4} 误判） | ✅ 已改为从 symbols 表 market 字段查询 |
| **排序规则** | 强度降序 → \|change24h\| 降序，取前30 | \|change24h\| 降序 | Lenzeto 排序更合理 | ✅ 保留 |
| **前端轮播** | CSS 无限滚动 + hover 暂停 | CSS 无限滚动 + hover 暂停 | 功能类似 | ✅ 保留 |
| **前端指数栏** | F&G / VIX / DXY 指数栏 + 热力图 | 无指数栏 | Lenzeto 更丰富 | ✅ 优势 |
| **自选股面板** | 自选标的列表（添加/移除） | 深度集成（hover 创建任务/持仓/QuickTrade） | QuantDinger 交互更丰富 | ❌ 需加强联动 |
| **信号强度** | 复合打分（RSI+Trend+Bollinger）→ strong/medium/weak | 阈值简单分档（>15% strong, >5% medium 等） | Lenzeto 更精细 | ✅ 保留 |
| **AI 分析联动** | 占位区 | 点击机会卡片触发 AI 分析 | 两者均需完善 | ❌ 需实现 |

### 5.9.2 核心结论

**Lenzeto 优势**（保留）：
- 复合技术指标信号生成（RSI+EMA+Bollinger+ATR）质量更高
- 指数栏（F&G/VIX/DXY）+ 热力图丰富
- 排序规则更合理（强度优先）

**已完成**（✅）：
1. **扫描范围**：已改为「热门标的配置表 + 用户自选股」双源模式 → ✅
2. **统一标的字典**：symbols 表 + user_favorites 表 DDL、后端 CRUD 完成 → ✅
3. **前端自选股**：AiRadar.vue localStorage 迁移至 API → ✅
4. **前端选标**：StrategyManagement.vue、TradingPanel.vue 硬编码替换 API 搜索 → ✅
5. **并发获取**：ThreadPoolExecutor 并行拉取行情 → ✅
6. **24h 涨跌幅**：修复 @JsonProperty("change_24h") 前端显示 → ✅
7. **市场分类**：从 symbols 表 market 字段查询，替代正则推断 → ✅

**需优化**（🔧）：
1. **市场扫描方式**：改为分市场独立扫描，各市场独立 try/catch
2. **数据源降级**：数据库 KLine → 交易所 REST API → 其他数据源

**缺失待补**（❌）：
1. **缓存层**：加 Redis/本地缓存（30-60s TTL），支持 force 跳过
2. **自选股面板联动**：hover 创建定时任务、快速分析、交易入口
3. **AI 分析联动**：机会卡片点击触发 AI 分析
4. **数据源降级**：交易所 REST API 备用

---
### 5.9.3 QuantDinger 后端实现详解（AI 定时任务运行逻辑）

> 以下内容摘自 `QuantDinger-Vue与Lenzeto系统-AI交易机会雷达与定时任务功能对比分析.md` 第 2.4 节

**核心服务**：[`services/portfolio_monitor.py`](file:///F:/project/third/QuantDinger-main/backend_api_python/app/services/portfolio_monitor.py)（约 1840 行）

**启动方式**：
- 应用启动时调用 `portfolio_monitor.start_monitor_service()`
- 启动一个后台守护线程，独立于请求生命周期

**核心循环 — `_monitor_loop()`（每 30 秒轮询）**：
```
while not _stop_event.is_set():
    ├─ 1. _check_position_alerts()
    │    检查价格/盈亏预警（独立于监视器，见下方说明）
    │
    ├─ 2. 查询到期监视器:
    │    SELECT id, user_id FROM qd_position_monitors
    │    WHERE is_active = 1 AND next_run_at <= NOW()
    │    ORDER BY next_run_at ASC LIMIT 20
    │    │
    │    └─ 对每个到期监视器:
    │         └─ run_single_monitor(monitor_id, user_id, skip_notification=True)
    │
    ├─ 3. 按用户合并通知:
    │    for uid, results in user_results.items():
    │      if len(results) == 1:
    │        _send_monitor_notification(...)   # 单条通知
    │      else:
    │        _send_batch_notification(...)     # 批量合并通知
    │
    └─ _stop_event.wait(30)  # 休眠 30 秒
```

**单个监视器执行流程 — `run_single_monitor()`**：
```
1. 从 DB 加载监视器配置
   ├─ name, position_ids, config, notification_config
   └─ config 包含: symbol, market, run_interval_minutes, language

2. 关联持仓方式
   ├─ position_ids 非空 → 按 ID 加载持仓
   └─ config.symbol 存在 → 从自选股匹配持仓
       └─ 无匹配持仓 → 创建虚拟观察标的（不做实际交易）

3. 跳过规则（满足任一即跳过）
   ├─ 标的已被移出自选股 → 跳过并记录警告
   ├─ 无匹配持仓 → 跳过
   └─ 积分不足 → 跳过（积分计费检查）

4. 执行 AI 分析
   ├─ 按 (market, symbol) 去重，避免重复分析
   ├─ ThreadPoolExecutor(max_workers=5) 并行分析
   └─ 分析引擎: fast_analysis

5. 推进任务调度 — _bump_monitor_schedule()
   └─ next_run_at = NOW() + INTERVAL interval_minutes

6. 构建报告
   ├─ HTML 格式报告（站内通知）
   └─ Telegram 格式报告

7. 通知发送
   ├─ skip_notification=True → 由外层批量发送
   └─ skip_notification=False → 立即发送
```

**通知渠道解析 — `_resolve_notification_delivery()`**：
```
1. 从 DB 加载用户 email、notification_settings
2. 合并 targets:
   ├─ email → 用户注册邮箱
   ├─ telegram → telegram_chat_id
   └─ webhook → webhook_url
3. 检查每个渠道是否有有效送达地址
4. 无任何渠道可达 → 强制追加 'browser' 站内通知保底
```

**创建时立即执行**：
- `POST /api/portfolio/monitors` 创建监视器时立即另起线程执行第一轮

**价格/盈亏预警系统（独立于监视器）**：
- **数据表**：`qd_position_alerts`
- **检查时机**：`_monitor_loop()` 每轮循环首先执行 `_check_position_alerts()`
- **支持预警类型**：`price_above` / `price_below` / `pnl_above` / `pnl_below`

**后端任务调度方式对比**：

| 维度 | QuantDinger | Lenzeto |
|------|-------------|---------|
| 调度方式 | 后台守护线程轮询 | XXL-JOB 中心化调度 |
| 轮询间隔 | 30 秒 | 30 秒（XXL-JOB） |
| 任务表 | qd_position_monitors | ai_analysis_tasks |
| 通知渠道 | Email/Telegram/Webhook/Browser | notify_channels JSON |
| 积分计费 | ✅ 内置 | ✅ @QuotaCheck |
| 任务创建 | 立即后台运行第一轮 | 等待 XXL-JOB 下次调度 |
| 价格预警 | ✅ 独立预警系统 | 设计文档未提及 |
| 批量通知 | ✅ 按用户合并发送 | 设计文档未提及 |

---

### 5.9.4 Lenzeto vs QuantDinger run_single_monitor() 逐步骤代码级对比

> 分析日期：2026-06-03
> 进度说明：✅ 已实现 / 🔧 需优化 / ❌ 待完善

**对照 Lenzeto `AiAnalysisTaskService.executeTask()` + `executeSingleSymbol()` 与 QuantDinger `run_single_monitor()` 7 步流程**

#### 步骤 1：加载监视器/任务配置

| 维度 | QuantDinger | Lenzeto | 差异分析 | 进度 |
|------|-------------|---------|---------|------|
| **数据来源** | `SELECT ... FROM qd_position_monitors WHERE id = ?` | XXL-JOB 扫描 `ai_analysis_tasks` 表到期任务 | 架构不同，功能等效 | ✅ |
| **加载内容** | name, position_ids, config(含symbol/market/language), notification_config | symbols(JSON数组), interval_min, notify_channels, enabled | Lenzeto 多标的 vs QuantDinger 单标的+持仓关联 | ✅ |
| **代码位置** | [run_single_monitor() L1299-1335](file:///F:/project/third/QuantDinger-main/backend_api_python/app/services/portfolio_monitor.py#L1299-L1335) | [getDueTasks()](file:///F:/project/lenzeto/ai-quant/src/main/java/com/chain/ai/trade/engine/service/AiAnalysisTaskService.java#L179-L187) | 功能等效 | ✅ |

#### 步骤 2：关联持仓/解析标的

| 维度 | QuantDinger | Lenzeto | 差异分析 | 进度 |
|------|-------------|---------|---------|------|
| **关联方式** | 优先按 position_ids 加载持仓；无则按 config.symbol 从 watchlist 匹配 | 直接从 task.symbols JSON 解析为字符串列表 | QuantDinger 深度关联持仓（entry_price, quantity, pnl） | ❌ |
| **无匹配时** | 创建虚拟观察标的（quantity=0, entry_price=0） | 无此逻辑，直接按 symbol 执行分析 | Lenzeto 无持仓上下文 | ❌ |
| **代码位置** | [run_single_monitor() L1336-1383](file:///F:/project/third/QuantDinger-main/backend_api_python/app/services/portfolio_monitor.py#L1336-L1383) | [executeTask() L130-132](file:///F:/project/lenzeto/ai-quant/src/main/java/com/chain/ai/trade/engine/service/AiAnalysisTaskService.java#L130-L132) | QuantDinger 更完整 | ❌ |

#### 步骤 3：跳过规则

| 维度 | QuantDinger | Lenzeto | 差异分析 | 进度 |
|------|-------------|---------|---------|------|
| **自选股移除检查** | ✅ 检查 symbol 是否仍在 watchlist | ❌ 无此检查 | QuantDinger 避免分析已移除标的 | ❌ |
| **无持仓/无标的** | ✅ 无匹配持仓返回 skip | ❌ 无此逻辑（空列表不执行） | 功能等效但 QuantDinger 更明确 | 🔧 |
| **积分不足** | ✅ 调用 billing.check_and_consume() 逐次扣费 | ❌ 无积分检查 | Lenzeto 需补充 | ❌ |
| **代码位置** | [run_single_monitor() L1385-1426](file:///F:/project/third/QuantDinger-main/backend_api_python/app/services/portfolio_monitor.py#L1385-L1426) | 无对应实现 | QuantDinger 3 层防御更健壮 | ❌ |

#### 步骤 4：执行 AI 分析（核心差异）

**4a. 技术指标计算**

| 维度 | QuantDinger `FastAnalysisService._calculate_indicators()` | Lenzeto `MarketAnalysisService.analyze()` | 差异分析 | 进度 |
|------|-------------|---------|---------|------|
| **计算方式** | Python 规则计算 | Java 纯手工算法（Ta4j 风格） | 不同语言实现 | ✅ |
| **RSI** | 简单多空判断（<30 oversold, >70 overbought） | Wilders 平滑 RSI(14)，完整周期计算 | Lenzeto 更精准 | ✅ |
| **MACD** | 信号线交叉判断（golden/death cross） | 完整 EMA12/EMA26/DEA9 + 柱状图方向 | Lenzeto 更完整 | ✅ |
| **均线** | MA5/MA10/MA20 简单排序判断趋势 | EMA9/EMA21 指数加权 + 斜率计算 | Lenzeto 更精确 | ✅ |
| **布林带** | 无 | 完整计算：上轨/下轨/中轨/宽度百分比 | Lenzeto 优势 | ✅ |
| **ATR** | 简单 14 日 range 均值 | ATR(14) 百分比（波动率指标） | Lenzeto 更专业 | ✅ |
| **支撑/阻力** | 14 日最高/最低简单取值 | 140 周期内波谷/波峰枢轴点检测 | Lenzeto 更精确 | ✅ |
| **多周期** | 仅用 1D 数据 | 同步计算 15m/1H/4H/1D 四周期 | Lenzeto 优势 | ✅ |
| **代码位置** | [fast_analysis.py L226-320](file:///F:/project/third/QuantDinger-main/backend_api_python/app/services/fast_analysis.py#L226-L320) | [MarketAnalysisService.java L34-120](file:///F:/project/lenzeto/ai-quant/src/main/java/com/chain/ai/trade/engine/service/MarketAnalysisService.java#L34-L120) | Lenzeto 指标更深 | ✅ |

**4b. 数据收集（Prompt 输入）**

| 维度 | QuantDinger `data_collector.collect_all()` | Lenzeto `MarketDataCollector.collectPromptData()` | 差异分析 | 进度 |
|------|-------------|---------|---------|------|
| **技术指标** | RSI/MACD/MA/支撑/阻力（单周期） | RSI/趋势/MACD/BB/ATR/量比/支撑/阻力（多周期） | Lenzeto 更深入 | ✅ |
| **量价数据** | 无 | 近 5 根 15m K 线 OHLCV + 20 期均量 + 量比 | Lenzeto 优势 | ✅ |
| **宏观数据** | DXY/VIX/TNX/黄金 | 无 | QuantDinger 更广 | ❌ |
| **新闻情绪** | 新闻摘要 + 情绪标签 | 无 | QuantDinger 优势 | ❌ |
| **基本面** | 公司财务数据（美股） | 无 | QuantDinger 优势 | ❌ |
| **链上/Crypto** | 资金费率/OI/多空比/交易所净流 | 无 | QuantDinger 优势 | ❌ |
| **记忆层** | 历史相似模式检索 | 无 | QuantDinger 优势 | ❌ |
| **代码位置** | [fast_analysis.py L200-224](file:///F:/project/third/QuantDinger-main/backend_api_python/app/services/fast_analysis.py#L200-L224) | [MarketDataCollector.java L53-90](file:///F:/project/lenzeto/ai-quant/src/main/java/com/chain/ai/trade/engine/service/ai/filter/MarketDataCollector.java#L53-L90) | QuantDinger 数据维度更广 | ❌ |

**4c. LLM 调用**

| 维度 | QuantDinger | Lenzeto | 差异分析 | 进度 |
|------|-------------|---------|---------|------|
| **模型** | LLMService（可配置） | DeepSeek（默认 deepseek-chat） | 可配置程度不同 | ✅ |
| **Prompt 模式** | 超大 System Prompt（~200 行）+ 结构化输出约束 | Markdown 模板替换 + JSON 输出 | 架构不同 | ✅ |
| **输出结构** | 完整 JSON：decision/confidence/summary/analysis(含technical/fundamental/sentiment)/entry_price/stop_loss/take_profit/scores/reasons/risks | 简版 JSON：decision/confidence/score/summary/key_reasons/risks/suggestedStrength | QuantDinger 输出更丰富 | 🔧 |
| **并发** | ThreadPoolExecutor(max_workers=5) 并行分析多个标的 | 单线程逐个分析 | QuantDinger 更高效 | ❌ |
| **去重** | 按 (market, symbol) 去重，同标的只调一次 LLM | 无去重，重复标的也会重复分析 | QuantDinger 更高效 | ❌ |
| **代码位置** | [_run_ai_analysis() L396-490](file:///F:/project/third/QuantDinger-main/backend_api_python/app/services/portfolio_monitor.py#L396-L490) + [analyze()](file:///F:/project/lenzeto/ai-quant/src/main/java/com/chain/ai/trade/engine/service/ai/filter/LlmAnalyzerService.java#L48-L60) | [executeSingleSymbol() L147-170](file:///F:/project/lenzeto/ai-quant/src/main/java/com/chain/ai/trade/engine/service/AiAnalysisTaskService.java#L147-L170) | QuantDinger 并发+去重 | ❌ |

#### 步骤 5：推进调度

| 维度 | QuantDinger `_bump_monitor_schedule()` | Lenzeto `executeTask()` | 差异分析 | 进度 |
|------|-------------|---------|---------|------|
| **方式** | `UPDATE ... SET next_run_at = NOW() + INTERVAL interval_minutes` | `task.setNextRunAt(new Date(now.getTime() + intervalMin * 60_000L))` | 功能等效 | ✅ |
| **轮询** | 后台守护线程每 30s 轮询 DB | XXL-JOB 每 30s 触发 handler | 架构不同 | ✅ |
| **创建即执行** | 创建时另起线程立即执行第一轮 | 创建后等待 XXL-JOB 下次调度 | QuantDinger 体验更好 | ❌ |
| **代码位置** | run_single_monitor() 内嵌 | [executeTask() L161-163](file:///F:/project/lenzeto/ai-quant/src/main/java/com/chain/ai/trade/engine/service/AiAnalysisTaskService.java#L161-L163) | 调度时机差异 | ❌ |

#### 步骤 6：构建报告

| 维度 | QuantDinger | Lenzeto | 差异分析 | 进度 |
|------|-------------|---------|---------|------|
| **格式** | HTML 报告（_build_html_report()）+ Telegram 格式 | 直接存入 analysis_reports 表的 analysis/report_json 字段 | 策略不同：QuantDinger 推送到用户，Lenzeto 前端渲染 | ✅ |
| **内容** | 组合概览（总成本/总盈亏/总市值）、BUY/SELL/HOLD 统计、逐个标的分项（决策/置信度/技术/基本面/情绪/风险） | 决策、置信度、summary、risks、原始 LLM JSON | QuantDinger 报告内容丰富 | 🔧 |
| **代码位置** | [_build_html_report() L540+](file:///F:/project/third/QuantDinger-main/backend_api_python/app/services/portfolio_monitor.py#L540-L...) | [executeSingleSymbol() L164-170](file:///F:/project/lenzeto/ai-quant/src/main/java/com/chain/ai/trade/engine/service/AiAnalysisTaskService.java#L164-L170) | Lenzeto 数据沉淀更结构化 | ✅ |

#### 步骤 7：通知发送

| 维度 | QuantDinger | Lenzeto | 差异分析 | 进度 |
|------|-------------|---------|---------|------|
| **渠道** | Email / Telegram / Webhook / Browser（站内） | notify_channels JSON 字段（接口定义，实际发送未实现） | QuantDinger 已实现全套 | ❌ |
| **批量合并** | 按 user_id 合并多条结果统一发送 | 无 | QuantDinger 节省通知频次 | ❌ |
| **触发时机** | 支持延迟批量发送或立即发送 | 未实现通知发送 | QuantDinger 完整闭环 | ❌ |
| **代码位置** | [_send_monitor_notification()](file:///F:/project/third/QuantDinger-main/backend_api_python/app/services/portfolio_monitor.py#L1479-L1498) + _monitor_loop() | 无对应实现 | Lenzeto 需补充通知模块 | ❌ |

#### 总结

| 对比项 | QuantDinger | Lenzeto | 结论 |
|--------|-------------|---------|------|
| 加载配置 | SQL 查询 + JSON 解析 | MyBatis-Plus ORM | 功能等效 ✅ |
| 关联持仓 | ✅ 深度关联 | ❌ 无持仓上下文 | QuantDinger 更完整 ❌ |
| 跳过规则 | ✅ 3 层防御 | ❌ 无 | QuantDinger 更健壮 ❌ |
| 技术指标 | 单周期/简单 | **多周期/深入** | Lenzeto 技术面更强 ✅ |
| 数据广度 | **宏观+新闻+链上** | 仅技术指标 | QuantDinger 更全面 ❌ |
| LLM 输出 | 完整交易计划 | 简洁决策 | QuantDinger 更丰富 🔧 |
| 并发分析 | ✅ ThreadPoolExecutor 5 线程 | ❌ 单线程 | QuantDinger 更高性能 ❌ |
| 去重 | ✅ (market, symbol) 去重 | ❌ 无 | QuantDinger 更高效 ❌ |
| 调度方式 | 守护线程轮询 30s | XXL-JOB 30s | 架构不同，功能等效 ✅ |
| 创建即执行 | ✅ | ❌ 等待下次调度 | QuantDinger 体验更好 ❌ |
| 报告构建 | HTML + Telegram | 原始数据持久化 | 策略不同 ✅ |
| 通知发送 | ✅ 完整渠道 | ❌ 未实现 | QuantDinger 更完善 ❌ |
| 积分计费 | ✅ 内置 | ✅ @QuotaCheck | 两者都有 ✅ |
