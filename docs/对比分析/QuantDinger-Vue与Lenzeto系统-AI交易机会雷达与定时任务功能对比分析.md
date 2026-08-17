# AI 交易机会雷达与创建定时任务功能对比分析

> 对比对象：QuantDinger-Vue-main（前端） vs Lenzeto 系统（完整架构设计文档）
> 分析日期：2026-06-03

---

## 1. AI 交易机会雷达（Trading Radar）对比

### 1.1 QuantDinger-Vue 实现

**页面位置**：`src/views/ai-asset-analysis/index.vue`

**UI 布局**：
- **顶部轮播区（radar-carousel）**：CSS 水平无限滚动
  - 展示 20 条机会卡片（左右各 padding 50px 渐变遮罩）
  - 每张卡片内容：市场标签（Crypto/USStock/CNStock/HKStock/Forex/PredictionMarket）、标的代码、当前价格、24h 涨跌幅、信号类型（overbought/oversold/bullish_momentum/bearish_momentum）、原因描述
  - 预测市场（PredictionMarket）卡片特殊处理：显示市场概率、机会评分、建议（YES/NO/HOLD）
  - 点击卡片：触发 AI 分析或打开预测市场分析对话框
  - 悬停卡片：暂停滚动动画
  - Crypto 标的下方有 "Trade Now" 按钮，可快速打开 QuickTrade 面板

- **主工作区（workspace-card）**：
  - Tab 1 "即时分析"：嵌入 `AnalysisView` 组件，支持多市场即时 AI 分析
  - Tab 2 "预测市场"：占位页面，未来连接 Polymarket

- **右侧自选股面板（watchlist-panel）**：
  - 自选股列表，显示实时价格、涨跌幅、火花图
  - 持仓/盈亏行（关联持仓）
  - 定时任务状态行（显示活跃监控任务）
  - hover 浮出操作：快速添加持仓、创建定时任务、删除

**后端 API**：
- `GET /api/opportunities` — 获取交易机会（多市场：Crypto/USStock/Forex/PredictionMarket）

**数据流**：
1. 页面 created 时调用 `loadOpportunities()` 获取机会数据
2. 手动刷新按钮支持 force 刷新
3. 点击机会 → 触发 AI 分析（通过 `autoAnalyzeSignal` prop 与 AnalysisView 联动）

### 1.2 Lenzeto 系统实现

**设计文档位置**：`完整架构设计文档.md` 5.8 节

**模块概述**：
- AI 雷达模块（已实现，后端为主）
- `AiRadarController` → `AiRadarService` → `MarketAnalysisService`
- 技术指标计算引擎：EMA、RSI、ATR、布林带、趋势、情绪、支撑阻力

**页面结构（设计）**：
- 顶部轮播区：交易机会卡片（标的、价格、涨跌幅、信号类型）
- 工作区 Tab 1 即时分析：指标栏、市场热力图、分析工具栏、AI分析占位区
- Tab 2 预测市场（Polymarket）– 开发中
- 自选股面板：自选标的列表，支持添加/移除

**后端架构**：
- `AiRadarController` — REST API 入口
- `AiRadarService` — 扫描机会、技术指标计算、排序
- `MarketAnalysisService` — 指标计算引擎
- 信号生成规则：超买/超卖、看涨/看跌动能、盘整
- 信号强度：strong/medium/weak

**定时调度**：
- XXL-JOB 每 30 秒执行一次雷达机会扫描
- 结果缓存 30 秒

### 1.3 对比差异总结

| 对比维度 | QuantDinger-Vue | Lenzeto 系统 | 差异分析 |
|---------|----------------|-------------|---------|
| **前端实现** | ✅ 完整实现（Vue 3 + Ant Design） | ✅ 完整实现（Vue 3 + Element Plus） | 框架不同，功能类似 |
| **轮播交互** | CSS 无限滚动动画，悬停暂停 | 设计文档未详细描述 | QuantDinger 交互更丰富 |
| **多市场覆盖** | Crypto/USStock/CNStock/HKStock/Forex/PredictionMarket | Crypto/USStock/CNStock/HKStock/Forex | Lenzeto 缺少 PredictionMarket |
| **信号类型** | 超买/超卖/看涨动能/看跌动能 | 超买/超卖/看涨动能/看跌动能/盘整 | Lenzeto 多一个盘整信号 |
| **信号强度** | 无强度分级 | strong/medium/weak 三级 | Lenzeto 分级更精细 |
| **快速交易** | ✅ 支持 QuickTrade 面板一键交易 | ❌ 未实现 | QuantDinger 多交易集成 |
| **AI 分析联动** | 通过 props 与 AnalysisView 联动 | 设计文档提及 AI 分析占位区 | 实现方式类似 |
| **后端扫描** | `/api/opportunities` REST API | XXL-JOB 每30秒扫描 + 缓存30秒 | Lenzeto 调度更明确 |
| **缓存策略** | 支持 force 参数强制刷新 | 30 秒缓存 | Lenzeto 缓存策略更清晰 |

---

### 1.4 QuantDinger 后端实现详解（数据获取逻辑）

**核心入口**：[`routes/global_market.py`](file:///F:/project/third/QuantDinger-main/backend_api_python/app/routes/global_market.py) — `GET /api/opportunities`

**请求链路**：
```
前端 GET /api/opportunities?force=false
  → trading_opportunities()
    → cached_or_compute("trading_opportunities", _compute_trading_opportunities, force)
      → _compute_trading_opportunities()
```

**缓存策略**（[`data_providers/__init__.py`](file:///F:/project/third/QuantDinger-main/backend_api_python/app/data_providers/__init__.py)）：
- `cached_or_compute` 统一缓存层，支持 `force` 参数强制刷新
- 缓存 TTL 取决于具体实现（各市场独立缓存）

**多市场顺序扫描**（`_compute_trading_opportunities`）：
```python
candidate_scanners = [
    ("Crypto",  lambda: analyze_opportunities_crypto(opportunities)),
    ("USStock", lambda: analyze_opportunities_stocks(opportunities)),
    ("Forex",   lambda: analyze_opportunities_forex(opportunities)),
    ("CNStock", lambda: analyze_opportunities_local_stocks(opportunities, "CNStock")),
    ("HKStock", lambda: analyze_opportunities_local_stocks(opportunities, "HKStock")),
]
# 先检查 is_market_visible() 跳过隐藏市场
# 单个市场失败不影响其他（try/except 兜底）
# 扫描完成后按 |change_24h| 降序排列
```

**各市场扫描器实现**（[`data_providers/opportunities.py`](file:///F:/project/third/QuantDinger-main/backend_api_python/app/data_providers/opportunities.py)）：

| 市场 | 扫描标的 | 信号阈值 | 信号类型 |
|------|---------|---------|---------|
| Crypto | 前 20 个币种 | >15% / >5% / <-15% / <-5% | overbought/oversold/bullish_momentum/bearish_momentum |
| USStock | 17 只热门美股（AAPL/MSFT/GOOGL 等） | 5% / 2% | strong / medium |
| CNStock | A 股标的 | 5% / 2% / 1% | strong / medium / weak + consolidation |
| HKStock | 港股标的 | 4% / 1.5% / 0.8% | strong / medium / weak + consolidation |
| Forex | 外汇对 | 1.5% / 0.5% | strong / medium |

**价格获取链路（三级降级）**：
1. **Yahoo Chart API** — 首选实时数据源
2. **Stooq API** — 备用数据源
3. **KlineService（数据库 K 线）** — 兜底，从本地数据库获取

**并发获取**：
- `ThreadPoolExecutor(max_workers=8)` 并行拉取多个标的实时价格
- 内置速率限制：每市场至少 300ms 间隔，避免 API 限流

**数据流总结**：
```
前端请求 → 缓存检查 → (缓存命中) → 直接返回缓存数据
                      → (缓存未命中/force) → 顺序扫描各市场
                         → 并发获取价格(ThreadPoolExecutor 8线程)
                         → 基于涨跌幅阈值生成信号和强度
                         → 按 |change_24h| 降序排序
                         → 写入缓存 → 返回前端渲染
```

---

## 2. AI 定时任务（Monitor/Scheduled Tasks）对比

### 2.1 QuantDinger-Vue 实现

**页面位置**：`src/views/ai-analysis/index.vue`

**功能入口**：
- **单标的创建**：自选股面板 hover 操作 → 点击时钟图标 → 弹出 `showMonitorModal`
- **批量创建**：点击 "schedule" 图标 → 进入批量勾选模式 → 勾选多个标的 → 点击 "Schedule" 按钮 → 弹出 `showBatchScheduleModal`

**任务配置参数**：
- 运行间隔：1h（60min）、4h（240min）、12h（720min）、24h（1440min）
- 通知渠道：Email、Telegram、Webhook（多选）
- 语言偏好：跟随用户当前语言设置

**任务管理**：
- 右侧 drawer 展示所有任务列表（`showTaskDrawer`）
- 每项展示：任务名称、状态（active/paused）、下次运行时间
- 支持 toggle 启用/暂停、删除

**后端 API**：
- `GET /api/portfolio/monitors` — 获取所有监控任务
- `POST /api/portfolio/monitors` — 创建监控任务
- `PUT /api/portfolio/monitors/{id}` — 更新任务
- `DELETE /api/portfolio/monitors/{id}` — 删除任务
- `POST /api/portfolio/monitors/{id}/run` — 手动触发执行

**创建任务流程**：
```javascript
addMonitor({
  name: `AI-${symbol}-${interval}m`,
  position_ids: [...],
  monitor_type: 'ai',
  config: {
    run_interval_minutes: interval,
    symbol,
    market,
    language: 'zh-CN' | 'en-US'
  },
  notification_config: {
    channels: ['email', 'telegram', 'webhook']
  },
  is_active: true
})
```

### 2.2 Lenzeto 系统实现

**设计文档位置**：`完整架构设计文档.md` 5.7 节

**模块概述**：AI 分析模块（已实现）

**核心概念 — MonitorTask（监控任务）**：
- 用户创建的定时分析配置
- 包含目标标的列表、分析间隔、通知渠道、启用状态

**分析间隔选项**：
- 30 分钟、1 小时、4 小时、12 小时、24 小时
- QuantDinger 多了 30 分钟选项

**数据库表设计**：
```sql
CREATE TABLE `ai_analysis_tasks` (
  `id` CHAR(36) PRIMARY KEY,
  `user_id` VARCHAR(32) NOT NULL,
  `symbols` JSON NOT NULL,           -- 多个标的
  `interval_min` INT NOT NULL DEFAULT 60,
  `notify_channels` JSON DEFAULT NULL,
  `enabled` TINYINT(1) DEFAULT 1,
  `last_run_at` TIMESTAMP NULL,
  `next_run_at` TIMESTAMP NULL,
  `xxl_job_id` INT DEFAULT NULL,     -- XXL-JOB 任务 ID
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

**后端调度**：
- 通过 XXL-JOB 实现定时执行
- 任务执行结果生成报告存入 `analysis_reports` 表

**前端状态管理**：
- 批量模式、勾选标的、任务抽屉
- 任务抽屉显示已创建的所有定时任务

### 2.3 对比差异总结

| 对比维度 | QuantDinger-Vue | Lenzeto 系统 | 差异分析 |
|---------|----------------|-------------|---------|
| **表结构** | 无独立表结构（后端 `monitors` 表） | `ai_analysis_tasks` + `analysis_reports` | Lenzeto 设计更完整，含报告表 |
| **支持多标的/任务** | 单标的创建 + 批量模式 | 一个任务可包含多个 symbols（JSON） | 设计思路不同 |
| **定时间隔选项** | 1h / 4h / 12h / 24h | 30m / 1h / 4h / 12h / 24h | Lenzeto 多了 30 分钟选项 |
| **通知渠道** | Email / Telegram / Webhook | 设计文档提及 notify_channels JSON | QuantDinger 实现更具体 |
| **后端调度** | REST API 驱动（`POST /run` 手动触发） | XXL-JOB 中心化调度（`xxl_job_id` 字段） | Lenzeto 调度更可靠 |
| **分析报告存储** | 未明确 | `analysis_reports` 表存储结果 | Lenzeto 有数据沉淀 |
| **前端创建方式** | 单标的弹窗 + 批量勾选模式 | 设计文档提及"批量定时分析" | 功能设计相似 |
| **自选股关联** | 与 watchlist 面板深度集成 | 设计文档未细述 | QuantDinger 集成更紧密 |
| **任务管理** | Drawer 展示 +启用/暂停/删除 | 设计文档未细述前端交互 | QuantDinger 前端更完整 |

---

### 2.4 QuantDinger 后端实现详解（AI 定时任务运行逻辑）

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
- `POST /api/portfolio/monitors` 创建监视器时：
  ```python
  # 插入 DB 后立即另起线程执行第一轮
  def _initial_run():
      _run_single_monitor(int(monitor_id), user_id=int(user_id))
  threading.Thread(target=_initial_run, daemon=True, name=f"monitor-init-{monitor_id}").start()
  ```

**价格/盈亏预警系统（独立于监视器）**：

- **数据表**：`qd_position_alerts`
- **检查时机**：`_monitor_loop()` 每轮循环首先执行 `_check_position_alerts()`
- **支持预警类型**：`price_above` / `price_below` / `pnl_above` / `pnl_below`
- **执行逻辑**：
  ```
  1. 查询所有活跃且未触发/已过重复间隔的预警
  2. 从 KlineService 获取实时价格
  3. 判断是否达到阈值
  4. 达到阈值 → 发送通知 → 更新 is_triggered / last_triggered_at / trigger_count
  ```

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

## 3. AI 智能创建交易机器人（AiBotDialog）对比

### 3.1 QuantDinger-Vue 实现

**页面位置**：`src/views/trading-bot/components/AiBotDialog.vue`

**功能概述**：
- 自然语言输入 → AI 生成交易机器人配置
- 对话框内完成：输入需求 → AI 分析 → 展示推荐结果 → 应用并创建机器人

**UI 流程**：
1. 快速提示词（quick prompts）引导用户输入
2. 文本输入框（支持 Ctrl+Enter 快捷发送）
3. AI 分析加载动画
4. 展示推荐结果：
   - 推荐机器人类型（Grid/Martingale/Trend/DCA/Arbitrage）
   - 基础配置（交易对、时间框架、合约类型、杠杆、初始资金）
   - 策略参数（取决于机器人类型）
   - 风控配置（止损/止盈等）
5. "Apply and Create" 按钮 → 将结果传到 `BotCreateWizard` 表单预填

**后端 API**：
- `POST /api/strategies/ai-generate` — AI 生成策略推荐（intent: 'bot_recommend'）

### 3.2 Lenzeto 系统实现

**设计文档位置**：`完整架构设计文档.md` 5.7.1 节和 5.4 节

**AI 分析核心能力**：
- 自然语言生成策略（输出为策略模板）
- 策略代码解释
- 市场问答
- 批量定时分析

**策略管理模块**：
- 策略基本信息（名称、类型、策略类、时间框架、信号ID、描述）
- 策略参数（键值对，支持动态添加）
- 策略类型支持 JAVA_CLASS、GROOVY_SCRIPT 等

**交易机器人模块**：
- 创建机器人：名称、用户ID、策略选择、交易对、杠杆、资金
- 日盈利/亏损目标
- 状态管理：CREATED/RUNNING/PAUSED/STOPPED/ERROR

### 3.3 对比差异

| 对比维度 | QuantDinger-Vue | Lenzeto 系统 | 差异分析 |
|---------|----------------|-------------|---------|
| **AI 生成方式** | 对话框内自然语言 → 生成机器人配置 | 自然语言生成策略 | QuantDinger 直接生成完整机器人配置 |
| **输出内容** | botType + baseConfig + strategyParams + riskConfig | 策略模板（代码） | QuantDinger 输出更结构化 |
| **机器人类型** | Grid/Martingale/Trend/DCA/Arbitrage 五种 | 基于策略类（JAVA_CLASS/GROOVY） | 设计理念不同 |
| **前端交互** | 对话框内完成，结果传到创建向导 | 设计文档未细述前端流程 | QuantDinger 交互更完整 |
| **积分计费** | 未体现 | 支持 @QuotaCheck 积分扣除 | Lenzeto 有商业化设计 |

---

## 4. 综合对比结论

### 4.1 QuantDinger-Vue 优势

| 优势点 | 说明 |
|--------|------|
| **前端交互更丰富** | 雷达轮播动画、quick trade 集成、hover 操作更细腻 |
| **批量操作更完善** | 批量勾选模式一键创建定时任务 |
| **AI 创建机器人集成度** | 自然语言 → 配置预览 → 一键创建，全流程闭环 |
| **多金融市场覆盖** | 包含 PredictionMarket（预测市场） |
| **QuickTrade 集成** | 直接从机会卡片一键跳转交易 |

### 4.2 Lenzeto 系统优势

| 优势点 | 说明 |
|--------|------|
| **后端架构更完善** | XXL-JOB 中心化调度、任务状态追踪（`xxl_job_id`）、分析报告持久化 |
| **数据模型更完整** | 独立 `ai_analysis_tasks` + `analysis_reports` 表，有数据沉淀 |
| **定时间隔更灵活** | 支持 30 分钟选项 |
| **信号强度分级** | 三级信号强度（strong/medium/weak） |
| **技术指标引擎** | 基于 Ta4j 的指标计算，支持更多技术分析类型 |
| **商业化计费** | 积分/会员体系，@QuotaCheck 切面自动计费 |

### 4.3 建议融合方向

| 方向 | 建议 |
|------|------|
| **前端交互** | 参考 QuantDinger 雷达轮播动画 + QuickTrade 集成，提升 Lenzeto 前端体验 |
| **多市场覆盖** | Lenzeto 可扩展 PredictionMarket 预测市场支持 |
| **批量任务创建** | 引入 QuantDinger 的批量勾选模式，提升定时任务创建效率 |
| **调度可靠性** | 保持 Lenzeto 的 XXL-JOB 调度方案，比 REST API 手动触发更可靠 |
| **数据持久化** | 保留 `analysis_reports` 表设计，便于历史分析数据回溯 |
| **AI 创建机器人** | 结合 QuantDinger 的对话式创建流程与 Lenzeto 的策略系统 |
| **定时间隔** | 增加 30 分钟选项（Lenzeto 已有设计，确认前端是否实现） |
| **积分计费** | 保留 Lenzeto 的 @QuotaCheck 设计，对 AI 分析、雷达扫描等加上计费点 |

---

*本文档仅做功能对比分析，不涉及代码修改。*
