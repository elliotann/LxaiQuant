DeepSeek 交易分析输出规范
1. 设计背景
   本设计用于指导 DeepSeek 模型（无工具调用能力）根据用户提供的多周期 K 线数据、账户信息，输出结构化的交易建议。核心目标是：

入场信号可执行：区分限价单、条件单、市价单，避免逻辑混淆。

输出稳定：固定报告章节，强制包含 JSON 代码块供程序解析。

风险可控：基于 1.5% 风控原则计算仓位，量化风险提醒。

持仓管理：若用户有现有持仓，给出具体对冲/平仓建议。

2. 订单类型定义（核心修正）
   订单类型	关键特征	适用场景	JSON entry.type	entry.price	entry.condition
   限价单 (LIMIT)	挂单，价格到达指定价才成交，不保证成交	趋势明确时的回调入场、震荡市区间边界交易	"LIMIT"	指定价格（数值）	null
   条件单 (CONDITION)	当某个条件满足时，触发一笔市价单	突破跟随：价格突破关键位后追涨/追跌	"CONDITION"	null	条件表达式（字符串）
   市价单 (MARKET)	立即以当前市场价格成交	需要立刻进场，或条件单触发后的执行方式（但条件单本身已包含市价执行）	"MARKET"	null	null
   重要规则：

限价单 不得 附加任何收盘确认条件（如 close_of_15m < ...），因为限价单成交时刻与 K 线收盘无关。

条件单的 condition 表达式可使用：价格比较（price >= 2330）、K 线属性（close_of_15m < 2320、high_of_15m >= 2325）、指标（rsi_14 < 30），支持 AND/OR。

市价单仅在用户明确要求“立即开仓”或策略无法等待时使用，通常不作为主策略的默认选择。

3. 完整输出要求
   3.1 自然语言报告部分（固定章节，不得增减）
   markdown
# {{symbol}} 趋势跟踪建议
**数据时间**：{{timestamp}}（GMT+8）
**方法**：1小时定趋势，15分钟回调入场

## 1小时趋势判定
- 当前价格
- 趋势方向（多头/空头/震荡）
- 关键阻力（由近至远列表）
- 关键支撑（由近至远列表）

## 15分钟入场策略
### 主策略（顺势回调/区间交易）
- 机会方向
- 订单类型（LIMIT / CONDITION / MARKET）
- 入场细节（限价单给出价格，条件单给出条件表达式）
- 止损设置
- 止盈目标（需带减仓比例）
- 仓位建议（若无余额则说明原因）

### 备选策略（突破跟随或其他）
- 同上结构

## 持仓管理（仅当用户有现有持仓时）
- 当前持仓明细
- 风险敞口
- 具体对冲建议（如开多少张反向单使净头寸归零，或先平仓再开新仓）

## 实时应对方案
- 当前价格应如何操作
- 下一步监控信号

## 执行清单
- 图表标注位置
- 观察周期与形态
- 入场/止损/止盈的执行步骤

## 风险提醒
- 至少包含一个量化条件（如 RSI、成交量、ATR 数值）
- 趋势反转风险
- 免责声明
  3.2 JSON 代码块部分
  必须紧跟在自然语言报告之后，以 ````tradeplan开始，以 ````` 结束。JSON 结构如下（live_advice_v1`）：

json
{
"type": "live_advice_v1",
"facts": {
"symbol": "string",
"interval": "string",
"accountId": "string",
"snapshotTs": "ISO8601",
"latestPrice": 0,
"riskStatus": {
"balance": 0 | null,
"positions": [{"symbol":"string","side":"LONG|SHORT","quantity":0,"entryPrice":0}],
"marginRate": 0 | null,
"maxLeverage": 10
}
},
"advice": {
"direction": "LONG|SHORT|NO_TRADE",
"entry": {
"type": "LIMIT|CONDITION|MARKET",
"price": 0 | null,
"condition": "string | null"
},
"stopLoss": 0 | null,
"takeProfit": [{"level": 0, "ratio": 0.0}],
"positionSize": {
"suggestedContracts": 0 | null,
"riskPercent": 0 | null,
"calculationBasis": "string"
},
"validUntil": "ISO8601",
"monitorConditions": ["string"],
"reason": "string"
},
"tradePlanDraft": null | { ... }
}
字段约束：

entry.type 为 LIMIT 时，entry.price 必填，entry.condition 必须为 null。

entry.type 为 CONDITION 时，entry.condition 必填，entry.price 为 null。

entry.type 为 MARKET 时，entry.price 和 entry.condition 均为 null。

takeProfit 数组中所有 ratio 之和应为 1.0。

positionSize.calculationBasis 必须填写，若余额为空则说明原因。

validUntil 为当前时间 + 24 小时。

monitorConditions 至少 1 个字符串。

若用户无持仓，riskStatus.positions 为空数组。

4. 风控与仓位计算
   单笔最大亏损 ≤ 账户权益 × 1.5%。

合约面值默认 1 USDT（可在 USER 消息中指定）。

计算公式：建议张数 = floor( (余额 × 0.015) / (|入场价 - 止损价| × 合约面值) )。

若余额为 0 或未提供，则 suggestedContracts 和 riskPercent 为 null，calculationBasis 写明原因。

5. 用户消息中必须提供的数据（后端负责填充）
   text
   【当前数据】
   交易品种: {{symbol}}
   当前价格: {{price}} USDT
   1小时趋势: {{trend}}
   关键支撑: {{support_list}}
   关键阻力: {{resistance_list}}
   15分钟RSI: {{rsi}}（可选，但建议提供）
   近20根15分钟K线平均成交量: {{avg_volume}}（可选）
   合约面值: 1 USDT
   账户ID: {{accountId}}
   账户可用余额: {{balance}} USDT
   总资产: {{totalAsset}} USDT
   现有持仓: {{positions}}（如 "LONG 1张 开仓价2419.16"）
   是否可执行计划: {{executable_plan}}（true/false，默认 false）
6. 理论输出示例（基于 ETH 震荡行情，含限价单 + 条件单）
   场景：ETH-USDT-SWAP，当前价 2314.16，1小时震荡偏空，区间 2280-2330。用户持有 1 张多头（开仓价 2419.16，浮亏约 2.75%），账户余额未提供。

text
# ETH-USDT-SWAP 趋势跟踪建议
**数据时间**：2026-04-22 00:00（GMT+8）
**方法**：1小时定趋势，15分钟回调入场

## 1小时趋势判定
- **当前价格**：2314.16 USDT
- **趋势方向**：震荡偏空（高点逐步降低，低点下移但2280-2300有支撑）
- **关键阻力**：2325-2330 → 2350 → 2360
- **关键支撑**：2300 → 2290 → 2280

## 15分钟入场策略
### 主策略（区间震荡高抛）
- **机会方向**：做空
- **订单类型**：限价单（LIMIT）
- **入场细节**：在 2325 USDT 挂限价空单
- **止损设置**：2330 USDT（突破区间上沿）
- **止盈目标**：
    - 第一目标 2300，平仓 50%
    - 第二目标 2290，平仓 30%
    - 第三目标 2280，平仓 20%
- **仓位建议**：因账户可用余额未提供，无法按 1.5% 风控计算张数。请自行根据资金管理开仓，建议单笔风险不超过总资金的 2%。

### 备选策略（突破跟随）
- **方向**：做空
- **订单类型**：条件单（CONDITION）
- **触发条件**：若价格放量跌破 2290，则条件单 `price <= 2290` 触发市价追空
- **止损**：2300 USDT
- **止盈**：2260-2250

## 持仓管理
- **当前持仓**：多头 1 张，开仓价 2419.16 USDT，当前浮亏约 2.75%
- **风险敞口**：净多头 1 张
- **对冲建议**：由于市场震荡偏空且主策略为做空，建议开空 1 张（与主策略方向一致），使净头寸归零；或先平仓现有多头再执行空头计划，避免双向持仓风险。

## 实时应对方案
- **当前价格 2314.16**：观望，等待价格反弹至 2325 挂单成交，或跌破 2290 触发条件单。
- **下一步动作**：监控价格是否到达 2325；若跌破 2290 且成交量放大，准备接受条件单触发。

## 执行清单
- 在图表上标注 2325 挂单位置、2330 止损线、2300/2290/2280 支撑。
- 设置限价单 2325 做空；同时设置条件单 `price <= 2290` 追空（可二选一或同时保留）。
- 若限价单成交，立即设置止损 2330 和分批止盈。

## 风险提醒
- **量化反弹风险**：当前 15 分钟 RSI 约 38.5，若价格跌至 2290 且 RSI 跌破 30 并出现长下影，反弹概率增加，追空需等待指标修复。
- **量化假突破风险**：若价格跌破 2290 时成交量小于过去 20 根 15 分钟均量的 80%，可能为假突破，不建议追空。
- **趋势反转风险**：若价格突破 2330 且 1 小时收盘站稳，空头计划失效。
- **免责声明**：本建议仅供参考，不构成投资建议，请独立决策。

````tradeplan
{
  "type": "live_advice_v1",
  "facts": {
    "symbol": "ETH-USDT-SWAP",
    "interval": "1h/15m",
    "accountId": "demo_account",
    "snapshotTs": "2026-04-22T00:00:00+08:00",
    "latestPrice": 2314.16,
    "riskStatus": {
      "balance": null,
      "positions": [
        {
          "symbol": "ETH-USDT-SWAP",
          "side": "LONG",
          "quantity": 1,
          "entryPrice": 2419.16
        }
      ],
      "marginRate": null,
      "maxLeverage": 10
    }
  },
  "advice": {
    "direction": "SHORT",
    "entry": {
      "type": "LIMIT",
      "price": 2325,
      "condition": null
    },
    "stopLoss": 2330,
    "takeProfit": [
      { "level": 2300, "ratio": 0.5 },
      { "level": 2290, "ratio": 0.3 },
      { "level": 2280, "ratio": 0.2 }
    ],
    "positionSize": {
      "suggestedContracts": null,
      "riskPercent": null,
      "calculationBasis": "账户可用余额未提供，无法按1.5%风控计算张数。请自行控制仓位，建议单笔风险不超过2%。"
    },
    "validUntil": "2026-04-23T00:00:00+08:00",
    "monitorConditions": [
      "价格反弹至2325",
      "价格未突破2330"
    ],
    "reason": "震荡偏空行情，在区间上沿挂限价空单"
  },
  "tradePlanDraft": null
}
text

**说明**：
- 主策略采用了 **限价单**（LIMIT），符合震荡市高抛逻辑。
- 备选策略使用了 **条件单**（CONDITION）用于突破跟随。
- 报告中还隐含了市价单场景（条件单触发后实际执行市价），但未单独列出，因为条件单已包含市价执行。
- 如有需要立即入场的场景，可将 `entry.type` 设为 `"MARKET"`，并省略价格和条件。

此文档与示例可直接用于系统实现。