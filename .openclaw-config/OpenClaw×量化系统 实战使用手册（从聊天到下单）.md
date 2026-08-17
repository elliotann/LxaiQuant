# OpenClaw×量化系统 实战使用手册（从聊天到下单）

本文偏“实战流程”，面向每天实际使用小灵宝/控制台进行：**看信号 → 出计划 → 预检 → 确认下单 → 查回执 → 平仓**。

> 配置/启动/排障请先看：`量化系统与 OpenClaw 交互操作手册.md`

## 0. 开始前检查（30 秒）

- OpenClaw 控制台可打开：`http://127.0.0.1:18789/`
- 量化后端可用（baseUrl）：通常 `http://127.0.0.1:8118`
- 鉴权一致：`OPENCLAW_BRIDGE_TOKEN` 在 OpenClaw 与 ai-quant 两边一致

## 1. 两种入口怎么选

- **入口 A：实时交易页面（推荐）**
  - 适合：边聊天边确认、右侧“交易计划”面板一键 Confirm、Stop 中断
  - 入口：前端 → 实时交易 → 小灵宝

- **入口 B：OpenClaw 控制台**
  - 适合：纯联调/运维验证、快速跑工具、不依赖前端 UI
  - 入口：浏览器 → `http://127.0.0.1:18789/`

两种入口调用的是同一套工具（quant-bridge）。

## 2. 常用工具清单（记住这 8 个就够）

- `quant_list_accounts`：找 accountId / 确认是否模拟账户
- `quant_risk_status`：风控/摘要（确认账户是否可交易）
- `quant_get_signals`：拉取信号
- `quant_positions`：查持仓（按 accountId+symbol）
- `quant_order_status`：查订单状态（按 orderId/orderSn）
- `quant_open_order` + `quant_open_order_confirm`：开仓两段式
- `quant_close_order` + `quant_close_order_confirm`：平仓两段式
- `quant_trade_plan_create` + `quant_trade_plan_confirm`：计划落库 + 一键执行

## 3. 标准工作流（推荐）

### 3.1 第一次使用：确认账户与默认参数

1) 列出账户：

```json
quant_list_accounts {}
```

2) 如果你要固定默认账户/交易对（减少每次输入），在 `openclaw.json` 的插件配置里设：
- `defaultAccountId`
- `defaultSymbol`（例如 `BTC-USDT-SWAP`）

### 3.2 获取信号（辅助决策）

```json
quant_get_signals {"symbol":"BTC-USDT-SWAP","interval":"3m","limit":20}
```

### 3.3 生成交易计划（让小灵宝输出“可执行计划”）

建议把目标写清楚（示例）：
- “用模拟账户，给我一个 BTC 做多计划：入场、止损、止盈、仓位，并给出理由；不要直接下单，先让我确认。”

目标是让模型输出结构化计划（至少包含：symbol/side/entry/stopLoss/takeProfit/quantity 风险信息）。

### 3.4 预检下单（不会成交，得到 previewId）

开仓预检（示例）：

```json
quant_open_order {"side":"LONG","orderType":"MARKET","quantity":1,"leverage":1}
```

你会拿到：
- `previewId`
- `warnings`（例如数量向下取整）
- `next.tool`（下一步 confirm 工具）

### 3.5 创建交易计划（落库）并进入待执行列表

用 previewId + 计划内容创建交易计划：

```json
quant_trade_plan_create {
  "previewId":"<previewId>",
  "name":"BTC plan",
  "planContent":{
    "symbol":"BTC-USDT-SWAP",
    "side":"LONG",
    "entry":{"type":"MARKET"},
    "stopLoss":{"type":"FIXED","price":60000},
    "takeProfit":[{"price":70000,"volume":1.0}],
    "quantity":1
  },
  "trace":{
    "note":"来自小灵宝建议，人工确认执行"
  }
}
```

返回会包含：
- `planUuid`
- `status: pending`
- `next.tool: quant_trade_plan_confirm`

实时交易页面右侧“交易计划”会自动出现该 plan（pending 状态）。

### 3.6 确认执行（真正下单）

执行计划：

```json
quant_trade_plan_confirm {"planUuid":"<planUuid>","previewId":"<previewId>"}
```

结果：
- 成功：`status: executed`，并带 `executionResult`
- 失败：`status: failed`，并带错误信息

实时交易页面右侧“交易计划”会更新状态；聊天区也会显示执行回执。

### 3.7 查回执与仓位

1) 查仓位订单：

```json
quant_positions {"accountId":"<accountId>","symbol":"BTC-USDT-SWAP"}
```

2) 查订单状态（两种参数二选一）：

```json
quant_order_status {"orderSn":"<orderSn>"}
```

或：

```json
quant_order_status {"orderId":"<orderId>"}
```

## 4. 平仓流程（推荐两段式）

1) 平仓预检：

```json
quant_close_order {"side":"LONG"}
```

2) 确认平仓：

```json
quant_close_order_confirm {"previewId":"<previewId>"}
```

## 5. 常见操作习惯（强烈建议）

- 任何“实际下单”都走两段式：preview → confirm
- 下单前先看：
  - `quant_risk_status`（风控/摘要）
  - `quant_positions`（避免重复开仓或方向反了）
- 聊天里看到 previewId 卡片时再点 Confirm（不要口头让模型“直接下单”）
- 如果模型输出 JSON 与你预期不符：先让模型改计划，不要直接 confirm

## 6. 常见问题

### 6.1 返回 401/503

- 503：ai-quant 没配置 `OPENCLAW_BRIDGE_TOKEN`
- 401：OpenClaw 的请求没带 `X-OpenClaw-Token` 或 token 与后端不一致

### 6.2 previewId 过期导致 confirm 失败

- previewId 属于短期凭证（当前实现默认 30 分钟）
- 解决：重新 preview 生成新的 previewId，再 confirm

### 6.3 计划已落库但 Redis 过期还能查到吗？

- `quant_trade_plan_get` 会优先从 MySQL `ai_trade_plan` 查
- Redis 只作为短期缓存与兼容回退



总结一句： “获取建议”→ 模型回复里带 ` tradeplan 严格 JSON → 前端解析成功 → 自动出现‘生成交易计划’按钮 → 一键带入表单”。