DeepSeek 智能行情分析与量化交易系统集成设计文档（完整最终版）
版本：6.0
最后更新：2026-05-07
基于现有 technical_signal 与 trade_signal 表结构，保持 extra_params 格式一致性，实现 DeepSeek 分析结果无缝接入量化交易系统。

1. 设计目标
   利用 DeepSeek 大语言模型（纯文本，无函数调用）分析多周期 K 线数据，生成自然语言交易报告和结构化 JSON（tradeplan）。

将分析建议（方向、入场类型、止盈止损等）转换为 technical_signal 记录，新增 signal_source='DEEPSEEK' 标识来源，保留 data_source 表示交易所平台。

关键要求：转换后的 extra_params 必须与现有技术信号的格式完全兼容（包含 priceTargets、stopLossLevels、optimalStopLoss、optimalTakeProfit 等顶层字段），确保量化系统无需修改解析代码。

通过 trade_signal 表管理订单执行状态（下单、成交、平仓等），technical_signal 仅存储纯技术信号。

支持手动生成信号（前端按钮）和自动生成信号（用户配置开启）。

2. 整体架构
   text
   [前端图表] → 手动触发 / 定时任务
   │
   ▼
   [后端] 从 MySQL 读取多周期K线、账户余额、持仓 → 构造 USER 提示词
   │
   ▼
   [DeepSeek API] → 返回自然语言报告 + tradeplan JSON
   │
   ▼
   [后端] 存储建议到 trading_advice，自然语言报告返回前端（tradeplan 隐藏）
   │
   ▼
   [前端] 渲染报告，根据 tradeplan 内容在相关位置插入操作按钮（如“生成限价单信号”）
   │
   ▼（用户点击按钮 或 自动模式触发）
   [后端] 解析 tradeplan → 按兼容格式构建 extra_params → 插入 technical_signal
   │
   ▼
   [量化系统] 轮询 technical_signal（LEFT JOIN trade_signal 过滤未处理信号）→ 解析 extra_params
   │
   ▼
   [量化系统] 执行交易，创建 trade_signal 记录（关联 technical_signal_id），更新订单状态
3. 数据库表结构
   3.1 现有表 technical_signal 需增加字段（最小化变更）
   sql
   -- 增加信号来源字段（核心）
   ALTER TABLE technical_signal
   ADD COLUMN `signal_source` VARCHAR(50) DEFAULT NULL
   COMMENT '信号来源: DEEPSEEK, MACD, RSI, BOLL, AI等'
   AFTER `data_source`;

-- 增加原始建议ID（便于追溯）
ALTER TABLE technical_signal
ADD COLUMN `source_advice_id` VARCHAR(64) DEFAULT NULL
COMMENT '关联的 DeepSeek 建议ID';
说明：

data_source 保持原有语义，填入交易所平台（如 "OKX"、"BINANCE"）。

signal_source 用于区分信号来源，DeepSeek 固定为 'DEEPSEEK'。

不添加 order_status 和 order_id，订单执行状态由 trade_signal 管理。

3.2 辅助表：存储 DeepSeek 原始建议（新增）
sql
CREATE TABLE trading_advice (
id BIGINT AUTO_INCREMENT PRIMARY KEY,
advice_id VARCHAR(64) UNIQUE NOT NULL,
symbol VARCHAR(20) NOT NULL,
natural_report TEXT,
tradeplan_json JSON,
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
3.3 现有表 trade_signal 保持不变
trade_signal 已包含：

technical_signal_id：关联 technical_signal.id

order_action、status、order_sn、executed_price、stop_loss_price、take_profit_price 等字段，用于记录订单执行详情。无需修改。

4. DeepSeek 交互规范
   4.1 系统提示词（SYSTEM）核心要点
   角色：资深加密货币合约交易员。

数据规则：所有实时数据由用户提供，模型不得主动获取。

风控：单笔亏损 ≤ 权益 1.5%；余额不足时明确写出原因。

订单类型：必须区分 LIMIT / CONDITION / MARKET，且遵守对应字段约束（限价单带价格，条件单带表达式，市价单无价格/条件）。

输出格式：先自然语言报告（固定章节），后 ````tradeplan` JSON 代码块。

JSON schema：stopLoss 为数值，takeProfit 数组每个元素含 level（价格）和 ratio（平仓比例，总和为 1）。

4.2 期望的 tradeplan JSON 结构（沿用现有输出）
json
{
"type": "live_advice_v1",
"facts": {
"symbol": "ETH-USDT-SWAP",
"interval": "15m",
"accountId": "221212112",
"snapshotTs": "2026-05-07T13:00:00+08:00",
"latestPrice": 2329.26,
"riskStatus": {
"balance": 0,
"positions": [
{
"symbol": "ETH-USDT-SWAP",
"side": "LONG",
"quantity": 1,
"entryPrice": 2419.16
}
],
"marginRate": 0,
"maxLeverage": 10
}
},
"advice": {
"direction": "SHORT",
"entry": {
"type": "LIMIT",
"price": 2332.17,
"condition": null
},
"stopLoss": 2356.50,
"takeProfit": [
{ "level": 2324.25, "ratio": 0.3 },
{ "level": 2313.02, "ratio": 0.4 },
{ "level": 2303.87, "ratio": 0.3 }
],
"positionSize": {
"suggestedContracts": null,
"riskPercent": null,
"calculationBasis": "余额不可用，无法计算仓位"
},
"validUntil": "2026-05-08T13:00:00+08:00",
"monitorConditions": [
"价格在2330-2335区间盘整且15分钟RSI低于50"
],
"reason": "1小时趋势空头，反弹至阻力2332附近做空，严格止损；现有多头持仓与趋势相反，建议对冲或减仓"
},
"tradePlanDraft": null
}
注：alternativeAdvice 字段可选，若存在则可单独生成备选策略信号。

5. 技术信号转换规则（重点：兼容现有 extra_params 格式）
   5.1 基础字段映射
   technical_signal 字段	映射逻辑
   data_source	用户默认交易所（从配置获取，如 "OKX"）
   signal_source	"DEEPSEEK"
   strategy_name	"DeepSeek_TrendFollowing"（可配置）
   indicator	"AI_STRATEGY"
   timeframe	facts.interval
   symbol	facts.symbol
   technical_direction	advice.direction → "BULLISH"(LONG) / "BEARISH"(SHORT) / "NEUTRAL"(NO_TRADE)
   entry_type	advice.entry.type（LIMIT/CONDITION/MARKET）
   limit_price	若 entry_type='LIMIT'，填 advice.entry.price；否则 NULL
   signal_hash	MD5(symbol + timeframe + kline_time + 'DEEPSEEK' + direction)
   source_advice_id	原始建议 ID（从 trading_advice.advice_id 获取）
   kline_timestamp / kline_time	当前 K 线时间戳（从行情数据获取）
   extra_params	按 5.2 规范构建
   5.2 extra_params 构建规范（与现有信号兼容）
   现有系统 extra_params 示例（来自 SMC 策略）：

json
{
"priceTargets": [...],
"stopLossLevels": [...],
"optimalStopLoss": 3385.478,
"optimalTakeProfit": 3315.353,
"smc": {...}
}
为保持兼容，DeepSeek 信号生成的 extra_params 必须包含 priceTargets、stopLossLevels、optimalStopLoss、optimalTakeProfit 四个顶层字段。同时可附加 _deepseek 字段存储原始建议信息（不强制但推荐）。

5.2.1 止损转换 → stopLossLevels 与 optimalStopLoss
根据 advice.stopLoss 创建一个 StopLossLevel 对象：

json
{
"level": 1,
"price": 2356.50,
"type": "固定止损",
"description": "DeepSeek 建议止损位",
"basedOn": "AI分析",
"riskPercentage": 1.5,          // 若 positionSize.riskPercent 有值则用它，否则默认 1.5
"primary": true
}
stopLossLevels = [上述对象]

optimalStopLoss = advice.stopLoss

5.2.2 止盈转换 → priceTargets 与 optimalTakeProfit
遍历 advice.takeProfit 数组，为每个元素生成一个 PriceTarget 对象：

json
{
"level": 1,                       // 序号（从1开始）
"price": 2324.25,                 // 目标价格（取自原 level 字段）
"probability": 0.7,              // 默认 0.7，可后续从置信度获取
"description": "DeepSeek 第1止盈目标",
"basedOn": "DeepSeek_AI",
"distanceFromCurrent": 5.01,     // |price - facts.latestPrice|
"riskRewardRatio": 0.33          // 根据入场价、止损价、目标价计算
}
priceTargets = 对象数组

optimalTakeProfit = 第一个止盈目标的价格（advice.takeProfit[0].level）

5.2.3 可选：保留原始建议
json
"_deepseek": {
"adviceId": "adv_xxx",
"tradeplan": { ... }   // 完整原始 tradeplan 对象
}
5.2.4 最终 extra_params 示例
json
{
"priceTargets": [
{"level":1,"price":2324.25,"probability":0.7,"description":"DeepSeek 第1止盈目标","basedOn":"DeepSeek_AI","distanceFromCurrent":5.01,"riskRewardRatio":0.33},
{"level":2,"price":2313.02,"probability":0.7,"description":"DeepSeek 第2止盈目标","basedOn":"DeepSeek_AI","distanceFromCurrent":16.24,"riskRewardRatio":0.66},
{"level":3,"price":2303.87,"probability":0.7,"description":"DeepSeek 第3止盈目标","basedOn":"DeepSeek_AI","distanceFromCurrent":25.39,"riskRewardRatio":1.0}
],
"stopLossLevels": [
{"level":1,"price":2356.50,"type":"固定止损","description":"DeepSeek 建议止损位","basedOn":"AI分析","riskPercentage":1.5,"primary":true}
],
"optimalStopLoss": 2356.50,
"optimalTakeProfit": 2324.25,
"_deepseek": {
"adviceId": "adv_20260507_001",
"tradeplan": { "type":"live_advice_v1", ... }
}
}
5.3 兼容性说明
量化系统读取 extra_params 时，只要依赖 priceTargets、stopLossLevels、optimalStopLoss、optimalTakeProfit 字段，就能正确解析 DeepSeek 信号。

如果量化系统还使用其他字段（如 smc），DeepSeek 信号中不包含这些字段，系统需做好空值处理（忽略或使用默认值）。

通过 _deepseek.adviceId 可追溯原始分析建议。

6. 前端交互与按钮设计
   6.1 按钮注入位置
   基于前端渲染的自然语言报告，在以下段落附近插入按钮（按钮根据隐藏的 tradeplan 内容决定是否显示）：

位置	按钮文案	对应后端 action
主策略段落下方	[生成限价单信号]（若 entry.type='LIMIT'）	limit_signal
备选策略段落下方	[生成条件单信号]（若有 alternativeAdvice）	cond_signal
持仓管理段落下方	[生成对冲信号] / [生成平仓信号]	hedge_signal / close_signal
执行清单末尾	[一键生成全部信号]	all_signals
风险提醒下方	[我已阅读风险，继续执行]	前端解锁其他按钮
6.2 按钮点击流程
用户点击按钮，弹出确认框，显示信号摘要（方向、价格、止损、止盈等）。

用户确认后调用后端接口：

text
POST /api/signal/create-from-advice
{ "adviceId": "xxx", "action": "limit_signal" }
后端执行转换，插入 technical_signal 记录，返回信号 ID。

前端提示：“信号已生成，量化系统将自动处理”。

6.3 自动信号生成配置
用户可在设置中开启“自动生成信号”模式，并选择允许的动作类型（限价单、条件单、对冲、平仓）。开启后，后端每次收到 DeepSeek 建议时自动调用上述接口生成信号。

7. 后端 API 设计
   7.1 手动分析触发
   text
   POST /api/analyze/manual
   Request: { "symbol": "ETH-USDT-SWAP", "interval": "15m" }
   Response: { "adviceId": "xxx", "naturalReport": "..." }
   7.2 从建议生成信号
   text
   POST /api/signal/create-from-advice
   Request: { "adviceId": "xxx", "action": "limit_signal" }
   Response: { "success": true, "signalId": 12345 }
   处理逻辑：

根据 adviceId 查询 trading_advice，获取 tradeplan_json。

根据 action 确定使用 advice 还是 alternativeAdvice，或生成对冲/平仓信号。

执行字段映射（见第5节），构造 extra_params。

插入 technical_signal 表。

返回信号 ID。

7.3 自动信号生成配置
text
GET /api/signal/auto-config   // 获取配置
POST /api/signal/auto-config  // 保存配置
配置项示例：

json
{
"enabled": true,
"allowedActions": ["limit_signal", "cond_signal"],
"maxRiskPercent": 2.0,
"onlySimulation": false
}
8. 量化系统消费信号
   8.1 轮询未处理的信号
   量化系统定期执行以下查询（例如每 5 秒）：

sql
SELECT ts.*
FROM technical_signal ts
LEFT JOIN trade_signal tr ON tr.technical_signal_id = ts.id
WHERE ts.signal_source = 'DEEPSEEK' AND tr.id IS NULL
ORDER BY ts.create_time ASC
LIMIT 10;
8.2 执行交易
对每条信号：

解析 extra_params，获取 priceTargets、stopLossLevels、optimalStopLoss、optimalTakeProfit 以及原始 _deepseek.tradeplan。

根据 entry_type 和 limit_price 确定订单类型和价格：

LIMIT：限价单，价格使用 limit_price

MARKET：市价单

CONDITION：条件单（若交易所支持，否则需额外实现价格监控）

调用交易所 API 下单。

在 trade_signal 表中创建记录：

technical_signal_id = ts.id

order_action = "BUY"（如果 technical_direction='BULLISH'）或 "SELL"（如果 BEARISH'）

expected_price = 限价单价格（或 NULL 如果是市价单）

stop_loss_price = optimalStopLoss

take_profit_price = optimalTakeProfit

status = "EXECUTED" 或 "FAILED"

order_sn = 交易所返回的订单号（若成功）

如果下单失败，记录失败原因到 execution_note 字段。

8.3 止盈止损处理
止损：使用 stopLossLevels 中 primary=true 的止损价格，设置止损单。

止盈：可使用 priceTargets 数组创建分批止盈限价单（每个目标按 ratio 比例平仓），或仅使用 optimalTakeProfit 作为单一止盈点。建议量化系统复用已有的 PriceTargetsInfo 处理逻辑。

9. 注意事项与扩展
   余额不足处理：生成信号时即使余额为 0 也可插入信号；量化系统执行时会因余额不足而失败，并将 trade_signal.status 置为 FAILED。可在自动生成配置中阻止余额不足时生成信号。

重复信号防护：通过 signal_hash 唯一索引防止短时间内重复插入相同信号；通过 LEFT JOIN 避免已生成 trade_signal 的信号被重复处理。

条件单实现：若交易所不支持条件单，可在量化系统中单独实现价格监控服务，满足条件后创建 trade_signal 并下单。

多交易所支持：technical_signal.data_source 字段区分交易所，量化系统据此选择对应交易 API。

性能优化：K 线数据传递时每个周期最多 50 根，使用 CSV 格式减少 token。

10. 实施计划
    数据库变更：执行 ALTER 语句添加 signal_source 和 source_advice_id 字段。

后端开发：

DeepSeek 调用模块（提示词构造、响应解析）。

信号转换服务（按第5节构建 extra_params）。

API 接口（手动分析、生成信号、自动配置）。

前端开发：报告渲染、按钮注入、与后端联调。

量化系统改造：

增加对 signal_source='DEEPSEEK' 信号的轮询。

实现执行逻辑，创建 trade_signal 记录。

测试：模拟盘全流程测试（手动生成信号 → 量化执行 → 状态更新）。

上线：灰度测试后正式发布。

11. 总结
    本设计通过最小化数据库变更，严格保持 extra_params 格式与现有信号兼容，实现了 DeepSeek 分析结果无缝接入量化交易系统。关键点：

新增 signal_source 字段标识来源，data_source 仍代表交易所。

extra_params 统一包含 priceTargets、stopLossLevels、optimalStopLoss、optimalTakeProfit，量化系统无需改动解析代码。

支持手动按钮和自动配置两种信号生成模式。

利用 trade_signal 管理订单执行，职责清晰。

按本设计实施，开发成本低，风险可控，且可平滑扩展到未来其他 AI 策略。