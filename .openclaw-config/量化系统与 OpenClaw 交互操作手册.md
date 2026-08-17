# 量化系统与 OpenClaw 交互操作手册

本文面向“使用者/运维/联调人员”，用于指导如何在本仓库内完成 **OpenClaw ↔ 量化系统（ai-quant）** 的启动、配置、联调与日常操作。

## 1. 架构概览

### 1.1 主要链路

- 自主聊天（前端实时交易）
  - 浏览器（实时交易页面） → `ai-quant`：`POST /api/llm/generate`（SSE 流式）
  - `ai-quant` → OpenClaw Gateway：转发到 OpenClaw 的 `responses` 能力
  - OpenClaw 返回流式 chunk → `ai-quant` → 浏览器展示

- 工具调用（OpenClaw 调用量化能力）
  - OpenClaw 插件 `quant-bridge` → `ai-quant`：`/api/openclaw/**`
  - `ai-quant` 执行业务逻辑（信号/风控/下单/交易计划）

### 1.2 端口约定（默认）

- OpenClaw Gateway：`http://127.0.0.1:18789/`
- OpenClaw Browser Control：`http://127.0.0.1:18791/`（OpenClaw 自身使用）
- 量化系统（ai-quant）：`http://127.0.0.1:8118/`（由 `quant-bridge` 默认配置决定）

## 2. 必要配置

### 2.1 环境变量（强制）

为了确保交易接口不会被误调用，`/api/openclaw/**` 已启用强制鉴权。

- `OPENCLAW_BRIDGE_TOKEN`
  - 作用：`ai-quant` 校验请求头 `X-OpenClaw-Token` 的共享密钥
  - 要求：`ai-quant` 与 OpenClaw（运行 `quant-bridge` 的进程环境）必须一致
  - 可配置方式：
    - `ai-quant` 配置文件：`openclaw.bridge.token`（推荐，集中配置）
    - 或环境变量：`OPENCLAW_BRIDGE_TOKEN`（兼容）
  - 若未配置：`ai-quant` 会对 `/api/openclaw/**` 返回 503（拒绝服务）

### 2.2 OpenClaw 配置文件（仓库内）

- OpenClaw State 目录（默认）：`<repo>/.openclaw-state`
- OpenClaw 配置文件：`<repo>/.openclaw-state/openclaw.json`
  - Gateway 端口、Token、插件加载路径等均在该文件中

### 2.3 quant-bridge 插件配置（默认值）

OpenClaw 的 `quant-bridge` 插件从 `openclaw.json` 中读取：

- `baseUrl`：量化系统地址（默认 `http://127.0.0.1:8118`）
- `defaultAccountId`：默认交易账户（建议配置为模拟账户）
- `defaultSymbol`：默认交易对（如 `BTC-USDT-SWAP`）
- `defaultInterval`：默认周期（如 `3m`）

说明：
- 插件 token 优先级：`plugins.entries.quant-bridge.config.token`（若存在）→ 环境变量 `OPENCLAW_BRIDGE_TOKEN`
- 建议不要把 token 明文写入配置文件，优先用环境变量注入

## 3. 启动顺序与运行方式

### 3.1 启动 ai-quant（量化后端）

确保 `OPENCLAW_BRIDGE_TOKEN` 已设置，然后启动 `ai-quant`（方式按你们现有启动习惯）。

最小检查：
- `http://127.0.0.1:8118/` 可访问（或至少接口对外可用）

### 3.2 启动 OpenClaw Gateway

推荐使用仓库脚本启动（Windows PowerShell）：

```powershell
cd F:\project\lenzeto
$env:OPENCLAW_BRIDGE_TOKEN="请替换为与 ai-quant 一致的值"
.\scripts\start-openclaw.ps1
```

后台启动（会写日志到 `.openclaw-state/logs`）：

```powershell
cd F:\project\lenzeto
$env:OPENCLAW_BRIDGE_TOKEN="请替换为与 ai-quant 一致的值"
.\scripts\start-openclaw.ps1 -Background
```

打开控制台：
- 浏览器访问：`http://127.0.0.1:18789/`

### 3.3 常见异常：EBADF

如果 OpenClaw 出现 `Error: EBADF: bad file descriptor, write`：
- 尽量使用 Node 20 LTS
- 优先使用脚本启动（可落盘 stdout/stderr），避免 stdio 被回收

## 4. 自主聊天操作（前端实时交易）

入口：
- 前端页面 → “实时交易” → “小灵宝”聊天

说明：
- 发送消息后为流式回复
- 支持 Stop 按钮中断当前生成

典型操作：
- “帮我分析当前市场趋势”
- “给我一个 BTC 的入场/止损/止盈计划（模拟账户）”

## 5. 量化工具操作（OpenClaw 调用量化系统）

### 5.1 常用工具

基础信息：
- `quant_list_accounts`：列出交易账户（找 accountId）
- `quant_get_signals`：查询信号
- `quant_risk_status`：查询风控摘要

仓位与订单：
- `quant_positions`：查询某 symbol 的持仓订单
- `quant_order_status`：按 `orderId` 或 `orderSn` 查询订单状态

下单（两段式）：
- `quant_open_order`：开仓预检（preview，不下单）
- `quant_open_order_confirm`：确认开仓（confirm，真正下单）
- `quant_close_order`：平仓预检（preview，不下单）
- `quant_close_order_confirm`：确认平仓（confirm，真正下单）

交易计划（可落库）：
- `quant_trade_plan_create`：基于 `previewId` 创建计划（返回 `planUuid`）
- `quant_trade_plan_get`：查询计划
- `quant_trade_plan_confirm`：确认执行计划（会执行对应的开仓/平仓）

### 5.2 下单推荐流程（最安全）

1) 预检开仓（不会下单）

```json
quant_open_order {"side":"LONG","orderType":"MARKET","quantity":1,"leverage":1}
```

2) 看到返回的 `previewId` 后确认下单

```json
quant_open_order_confirm {"previewId":"<previewId>"}
```

平仓同理：

```json
quant_close_order {"side":"LONG"}
quant_close_order_confirm {"previewId":"<previewId>"}
```

### 5.3 交易计划推荐流程（路线 A：先计划、后确认）

1) 先做预检，得到 `previewId`
2) 创建交易计划（可附带 planContent/trace）

```json
quant_trade_plan_create {
  "previewId": "<previewId>",
  "name": "BTC plan",
  "planContent": {
    "symbol": "BTC-USDT-SWAP",
    "side": "LONG",
    "entry": { "type": "MARKET" },
    "stopLoss": { "type": "FIXED", "price": 60000 },
    "takeProfit": [{ "price": 70000, "volume": 1.0 }]
  }
}
```

3) 确认执行（真正下单）

```json
quant_trade_plan_confirm {"planUuid":"<planUuid>","previewId":"<previewId>"}
```

## 6. 实时交易页面：交易计划面板联动

实时交易页面右侧“交易计划”列表会自动同步：
- `quant_trade_plan_create` 结果（新增 pending 计划）
- `quant_trade_plan_confirm` 结果（更新为 executed/failed）

并支持：
- 对 pending 计划一键 Confirm（自动发起 `quant_trade_plan_confirm`）

## 7. 交易计划落库（MySQL）

交易计划已支持写入 MySQL 表 `ai_trade_plan`。

首次启用需要执行建表：
- 文件：`ai-quant/src/main/resources/schema.sql` 中 `ai_trade_plan` 段落

落库行为：
- 创建计划时写入 `ai_trade_plan`
- 查询计划优先从 `ai_trade_plan` 读取（无数据才回退 Redis）
- 确认执行后更新 `status` 与 `execution_result`

## 8. 故障排查

### 8.1 /api/openclaw/** 返回 401 / 503

- 503：`OPENCLAW_BRIDGE_TOKEN` 未在 `ai-quant` 运行环境配置
- 401：OpenClaw 未携带 `X-OpenClaw-Token` 或 token 不一致

### 8.2 OpenClaw 工具调用失败

检查顺序：
- OpenClaw Gateway 是否存活：`http://127.0.0.1:18789/`（HTTP 200）
- `quant-bridge` 的 `baseUrl` 是否指向正确的 `ai-quant`
- `OPENCLAW_BRIDGE_TOKEN` 是否在 OpenClaw 与 ai-quant 两边一致

### 8.3 OpenClaw 日志位置

当使用脚本后台启动（`-Background`）：
- stdout：`<repo>/.openclaw-state/logs/gateway.out.log`
- stderr：`<repo>/.openclaw-state/logs/gateway.err.log`

