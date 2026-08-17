OpenClaw 与本地量化交易系统集成设计文档（更新版）
版本历史
版本	日期	作者	变更描述
1.0	2025-03-15	系统架构师	初始版本，包含基础互通、自主聊天界面、高级扩展及数据关联设计
1.1	2025-03-15	系统架构师	重构交易计划模块，与现有系统实现对齐（API 路径、路线 A/B、计划对象规范、确认流程）
1. 引言
   1.1 背景
   当前量化交易系统已实现自动化信号生成和订单执行，信号表 technical_signal 存储技术指标信号，订单相关表（ai_trade_order、ai_trade_order_item、ai_trade_order_close）记录交易全过程。为进一步提升决策智能化水平，拟引入 AI 助手 OpenClaw，作为“决策副驾”，在现有信号基础上进行综合分析，生成更精细的交易计划，并交由原交易引擎执行。

1.2 目标
双向通信：OpenClaw 能调用量化系统 API 获取数据、执行操作；量化系统能通过自主聊天界面调用 OpenClaw 的 AI 能力。

决策增强：OpenClaw 结合技术信号和市场信息生成交易计划，计划经人工确认后执行，形成“信号 → AI 决策 → 人工确认 → 订单”完整链路。

实时交互：聊天界面支持 SSE 流式回复，提升用户体验；结构化数据可在前端渲染为图表。

可追溯：交易计划携带唯一 ID 和关联信息（信号 ID、对话 ID），便于复盘分析。

安全可控：所有交易指令必须经过人工确认，强制鉴权，支持幂等。

2. 总体架构
   2.1 核心组件
   OpenClaw：AI 核心，通过 Channel 接收外部消息，通过 Skill 调用量化系统 API。

量化系统后端：提供 REST API 供 OpenClaw 调用，包含信号查询、订单执行、计划管理等；同时运行交易引擎，定时扫描信号表或经确认后直接下单。

量化系统前端：自主开发的 Web 界面，包含自定义聊天组件，可发送消息至 OpenClaw 并接收 SSE 流式回复，渲染图表和计划卡片。

数据库：MySQL，存储信号、计划（可选）、订单等数据。

2.2 交互流程概览













3. 详细设计
   3.1 OpenClaw 侧组件
   3.1.1 量化交易 Skill（quant-trader-skill）
   功能：将自然语言指令转换为对量化系统桥接 API 的调用。

动作：

get_trading_signals：查询信号

execute_order：执行订单（经确认后调用）

check_risk：查询风险

create_trade_plan：创建交易计划（返回计划内容及预览 ID）

实现：Node.js 模块，通过 HTTP 调用量化系统 /api/openclaw/* 接口。

3.1.2 HTTP/SSE 混合 Channel（当前实现）
功能：提供 HTTP 接口供量化系统前端调用，接收消息并返回 AI 回复，支持 SSE 流式输出。

端点：

POST /api/llm/generate：前端调用此接口发送用户消息，后端转发给 OpenClaw，并以 SSE 格式逐步返回回复内容。

说明：原文档中的 WebSocket Channel 作为可选未来增强，当前采用 SSE 实现流式效果，降低复杂度。

3.2 量化系统后端桥接 API 设计
所有 OpenClaw 调用的接口路径前缀统一为 /api/openclaw/*，与现有后端路由一致。

3.2.1 信号相关
方法	路径	描述	请求参数	返回
GET	/api/openclaw/signals	获取信号	strategy, symbol, limit	信号列表
3.2.2 订单相关
方法	路径	描述	请求参数	返回
POST	/api/openclaw/orders/open/preview	预检开仓（不下单）	accountId, symbol, side, orderType, quantity, limitPrice?, leverage?, requestId?, metadata?	previewId + 预检详情
POST	/api/openclaw/orders/open/confirm	确认开仓（真正下单）	previewId	订单信息
POST	/api/openclaw/orders/close/preview	预检平仓（不下单）	accountId, symbol, side, quantity?, orderType?, limitPrice?, requestId?, metadata?	previewId + 预检详情
POST	/api/openclaw/orders/close/confirm	确认平仓（真正下单）	previewId	订单信息
GET	/api/openclaw/orders/positions	查询当前持仓订单	accountId, symbol	持仓订单列表
GET	/api/openclaw/orders/order-status	查询订单状态	orderId?, orderSn?	订单状态/详情
3.2.3 风险相关
方法	路径	描述	请求参数	返回
GET	/api/openclaw/risk/status	查询风险	无	风险指标
3.2.4 计划相关（核心，路线 A 推荐以“订单预检/确认”落地）
说明：
当前系统已具备“预检（preview）→人工确认（confirm）→下单”的安全链路，前端可直接将预检结果渲染为“交易计划卡片”，无需强依赖独立的 trade-plans 管理接口。
若未来需要对计划进行长期存档、查询与复盘（路线 B），可在此基础上扩展 trade-plans 接口与 ai_trade_plan 表。

可选接口（路线 B / 计划归档使用）：
方法	路径	描述	请求参数	返回
POST	/api/openclaw/trade-plans	创建交易计划（归档）	plan 对象（含 planContent）	planUuid, plan 详情
GET	/api/openclaw/trade-plans/{planUuid}	查询计划	无	计划详情及关联订单
3.3 数据库表设计
3.3.1 现有表结构（保持不变）
technical_signal：技术信号表（用户已提供建表语句）

ai_trade_order：订单主表

ai_trade_order_item：订单明细表

ai_trade_order_close：平仓记录表

3.3.2 新增表：ai_trade_plan（可选路线 B 用）
若选择将计划落库以便长期复盘，需创建以下表（命名与现有表一致）：

sql
CREATE TABLE `ai_trade_plan` (
`id` bigint NOT NULL AUTO_INCREMENT,
`plan_uuid` varchar(64) NOT NULL COMMENT '计划唯一标识（供OpenClaw使用）',
`name` varchar(255) DEFAULT NULL COMMENT '计划名称',
`description` text COMMENT '自然语言描述',
`source` varchar(50) DEFAULT 'openclaw' COMMENT '来源',
`user_id` varchar(64) DEFAULT NULL COMMENT '触发用户ID',
`conversation_id` varchar(64) DEFAULT NULL COMMENT '关联对话ID',
`status` enum('pending','confirmed','executed','cancelled','failed') DEFAULT 'pending' COMMENT '计划状态',
`signal_ids` json DEFAULT NULL COMMENT '关联信号ID列表',
`plan_content` json NOT NULL COMMENT '完整计划内容（含入场/止损/止盈等）',
`order_ids` json DEFAULT NULL COMMENT '生成的订单ID列表',
`execution_summary` json DEFAULT NULL COMMENT '执行结果汇总',
`preview_id` varchar(64) DEFAULT NULL COMMENT '预检ID（用于确认校验）',
`created_at` datetime DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
UNIQUE KEY `uk_plan_uuid` (`plan_uuid`),
KEY `idx_status` (`status`),
KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI交易计划表';
3.3.3 订单表增加字段（用于追溯）
sql
ALTER TABLE `ai_trade_order` ADD COLUMN `plan_id` bigint DEFAULT NULL COMMENT '关联计划ID';
ALTER TABLE `ai_trade_order_item` ADD COLUMN `plan_id` bigint DEFAULT NULL COMMENT '关联计划ID';
ALTER TABLE `ai_trade_order_close` ADD COLUMN `plan_id` bigint DEFAULT NULL COMMENT '关联计划ID';
说明：plan_id 关联到 ai_trade_plan.id，与原有的 signal_id 并存，形成“信号 → 计划 → 订单”的多级追溯。

3.4 交易计划模块（核心流程）
3.4.1 设计目标
辅助决策：OpenClaw 生成的结构化交易计划，供用户在 UI 预览和确认，而非全自动执行。

安全可控：所有交易指令必须经过人工确认（Confirm）后才发往交易引擎。

可追溯：计划需携带唯一 ID 和关联信息（如信号 ID、对话 ID），便于后续复盘。

灵活扩展：当前以“副驾式半自动”为主，未来可平滑过渡到全自动落库执行模式。

3.4.2 两条可选路线
路线 A（推荐当前落地）：计划 = 可视化预检 + 人工确认

流程：

OpenClaw 生成“交易计划”（含入场、止损、止盈、仓位、风险检查等），并触发对应的订单预检接口，获取 previewId。
前端以卡片形式展示计划摘要，并展示 previewId（预检凭证）与可选 planUuid（计划标识）。
用户审阅后，点击“确认”按钮，前端调用对应 confirm 接口（开仓/平仓），携带 previewId。
后端验证 previewId 有效性与时效性后，真正下单，并返回订单信息。
此路线不强依赖 ai_trade_plan 表；若需要追溯，可选择性落库（例如仅记录 planUuid、previewId、orderSn/orderId、trace 字段）。
优点：快速落地，与现有“副驾式半自动”模式无缝衔接，无需改造交易引擎扫描逻辑。

路线 B（产品化/复盘驱动）：计划落库 + 引擎扫描

流程：

OpenClaw 生成计划，调用 /api/openclaw/trade-plans 将计划存入 ai_trade_plan 表，状态为 pending。
交易引擎定时扫描该表，获取待执行计划，按 plan_content 中的指令下单。
执行后更新计划状态和 order_ids。
优点：适合全自动策略、批量执行、历史复盘。

缺点：需改造交易引擎，增加扫描模块，适合未来系统演进。

文档后续内容以路线 A 为主，同时标注路线 B 的扩展点。

3.4.3 计划对象规范（plan_content 字段）
为使计划具备可执行性和可复盘性，plan_content 必须包含以下核心字段：

字段	类型	必填	说明
symbol	string	是	交易对，格式与交易引擎一致，如 BTC-USDT-SWAP
side	string	是	方向，统一为 LONG 或 SHORT（引擎内部可映射为 BUY/SELL）
entry	object	是	入场条件：type（市价/限价/突破/回踩）、price（若限价）、trigger（若条件单）
stopLoss	object	是	止损规则：type（固定价/ATR/结构）、price 或 distance（如 2%）
takeProfit	array	是	止盈目标列表：每个目标含 price 或 percent、volume（分批平仓比例）
quantity	number	是	开仓数量或金额（与 quantityType 配合，如 fixed/percent）
riskChecks	object	否	风险检查：maxLossAmount、maxPositionPercent、leverage、cooldownSeconds
executionPolicy	object	否	执行策略：orderType（市价/限价）、slippageTolerance、timeInForce、cancelAfter
idempotencyKey	string	是	幂等键（由 OpenClaw 生成，防重复确认/下单）
trace	object	是	追溯信息：signalIds、conversationId、userId、requestId
validUntil	string	否	计划有效期（ISO8601），超过则前端应提示失效并禁止确认
invalidateConditions	array	否	失效条件列表（如“已突破入场区间”“波动率过高”“信号过期”）

说明：
previewId 为后端在 preview 阶段签发的短期凭证，用于 confirm 校验，建议作为接口响应字段返回并由前端保存，不作为 plan_content 的业务字段长期存档。
示例：

json
{
"symbol": "BTC-USDT-SWAP",
"side": "LONG",
"entry": {
"type": "limit",
"price": 50000
},
"stopLoss": {
"type": "fixed",
"price": 49500
},
"takeProfit": [
{ "price": 51000, "volume": 0.5 },
{ "price": 52000, "volume": 0.5 }
],
"quantity": 0.01,
"quantityType": "fixed",
"riskChecks": {
"maxLossAmount": 100,
"leverage": 10
},
"executionPolicy": {
"orderType": "limit",
"slippageTolerance": 0.001,
"timeInForce": "GTC"
},
"idempotencyKey": "req_abc123",
"trace": {
"signalIds": [1001, 1002],
"conversationId": "conv_xyz",
"userId": "user_789"
}
}
3.4.4 执行与确认交互时序图（路线 A）
3.4.5 追溯字段说明
planUuid：计划唯一标识，由 OpenClaw 生成，用于归档与查询（路线 B 或可选落库使用）。路线 A 的确认执行以 previewId 为准。

previewId：预检凭证，由后端在 preview 阶段生成并返回，确认时携带，用于防止重复确认、确保预览内容未被篡改，且具备时效性。

idempotencyKey：OpenClaw 生成的幂等键，后端记录已处理的键，避免重复下单。

trace.signalIds：关联的信号 ID 列表，便于从信号追溯至计划和订单。

trace.conversationId：关联的 OpenClaw 对话 ID，可追溯完整的对话上下文。

3.5 前端聊天界面设计
3.5.1 组件功能
消息输入框（支持发送）

消息列表显示（区分用户/助理）

SSE 流式输出效果：逐步显示 AI 回复内容

结构化数据渲染：根据返回的 data 字段，调用对应的图表组件（ECharts/表格）

计划卡片渲染：当返回 planContent 时，以卡片形式展示入场、止损、止盈、风险检查等，并附带确认按钮

3.5.2 与后端通信
发送消息：POST /api/llm/generate，携带 stream 与 messages 数组（OpenAI messages 格式），由后端转发 OpenClaw 并 SSE 返回。
示例：
json
{
  "stream": true,
  "messages": [
    { "role": "system", "content": "你是小灵宝..." },
    { "role": "user", "content": "帮我制定 BTC 交易计划" }
  ]
}

接收响应：SSE 格式，每个 chunk 包含 type（chunk/done/error）和内容。

确认计划（路线 A）：调用对应 confirm 接口并携带 previewId：
POST /api/openclaw/orders/open/confirm   body: { "previewId": "..." }
POST /api/openclaw/orders/close/confirm  body: { "previewId": "..." }

3.5.3 结构化数据渲染示例
javascript
if (data.data) {
if (data.data.type === 'positions') {
renderPositionsTable(data.data.positions);
} else if (data.data.type === 'risk_gauge') {
renderRiskGauge(data.data.drawdown);
} else if (data.data.planContent) {
renderPlanCard(data.data.planContent, data.data.previewId, data.data.planUuid);
}
}
3.6 多轮对话管理
当前实现以“前端携带历史 messages”完成多轮对话（前端维护 chat history 并随请求发送），不强依赖 conversation_id。
如未来需要跨会话长期记忆，可扩展 conversation_id：首次请求不传，后端生成并在 SSE done 事件中返回；后续请求携带该 conversation_id。
若需跨会话长期记忆，可在量化系统侧将对话历史存入数据库（扩展）。

4. 安全设计
   强制鉴权：所有 /api/openclaw/* 接口必须验证请求头中的 X-OpenClaw-Token，与后端配置的 OPENCLAW_BRIDGE_TOKEN 匹配。若该环境变量未配置，接口应直接返回 503 并拒绝服务，防止因配置遗漏导致接口暴露。

幂等保障：idempotencyKey 由 OpenClaw 生成，后端记录已处理的键，确保同一请求不会被重复执行。

下单限额：下单前校验订单金额、数量是否超过用户/系统预设上限，防止 AI 误操作或恶意调用。

人工确认：任何实际下单操作必须经过用户点击确认，后端接口需验证 previewId 的有效性与时效性（建议 5–30 分钟，当前实现可按 30 分钟配置）。

操作审计：所有通过 OpenClaw 触发的操作记录到日志表（可选），便于追溯。

5. 实施步骤建议
   环境准备：确认 OpenClaw 版本，熟悉插件开发流程；配置 OPENCLAW_BRIDGE_TOKEN 环境变量。

开发 Skill：实现 quant-trader-skill，包含 get_trading_signals、execute_order、check_risk、create_trade_plan 等动作，测试调用桥接 API。

开发后端桥接接口：实现 /api/openclaw/* 各端点，确保鉴权、幂等、限额等功能。

数据库准备：按需创建 ai_trade_plan 表（如选路线 B），订单表增加 plan_id 字段。

修改前端：集成聊天组件，支持 SSE 流式接收和计划卡片渲染；实现确认按钮逻辑。

测试：先仿真环境，验证计划生成、确认、执行全流程，确保幂等和安全控制有效。

灰度上线：先开放部分策略，逐步推广。

6. 未来扩展
   语音交互：前端增加语音输入/输出。

主动推送：OpenClaw 在检测到极端行情时主动推送预警。

策略优化：利用历史交易计划和订单数据训练模型，优化计划生成质量。

全自动模式：逐步过渡到路线 B，实现计划落库和引擎扫描执行。

多机器人协作：支持多个 OpenClaw 实例或 Skill 协同工作。

7. 附录：代码示例
   7.1 OpenClaw Skill 核心（简化）
   javascript
   // quant-trader-skill/index.js
   const axios = require('axios');
   const API_BASE = 'http://quant-system:8000/api/openclaw';

module.exports = async function run(action, params, context) {
try {
switch (action) {
case 'get_trading_signals':
return await getSignals(params);
case 'create_trade_plan':
return await createPlan(params, context);
// ... 其他动作
}
} catch (e) {
return { success: false, message: e.message };
}
};

async function createPlan(params, context) {
const payload = {
name: params.name,
description: params.description,
signal_ids: params.signal_ids,
plan_content: params.plan_content,
source: 'openclaw',
user_id: context.userId,
conversation_id: context.conversationId
};
const res = await axios.post(`${API_BASE}/trade-plans`, payload);
return { success: true, data: res.data, message: `计划已创建` };
}
7.2 后端确认接口示例（Python Flask，以开仓确认为例）
python
@app.route('/api/openclaw/orders/open/confirm', methods=['POST'])
def confirm_open_order():
# 1. 鉴权
token = request.headers.get('X-OpenClaw-Token')
if token != os.environ.get('OPENCLAW_BRIDGE_TOKEN'):
return jsonify({'error': 'Unauthorized'}), 401

    # 2. 获取 previewId
    preview_id = request.json.get('previewId')
    if not preview_id:
        return jsonify({'error': 'Missing previewId'}), 400

    # 3. 验证 previewId 有效性（例如从缓存中获取）
    preview_data = cache.get(f"preview:{preview_id}")
    if not preview_data:
        return jsonify({'error': 'Invalid or expired preview'}), 400

    # 4. 幂等检查
    if redis.sismember("processed_idempotency_keys", preview_data['idempotencyKey']):
        return jsonify({'error': 'Duplicate request'}), 409

    # 5. 风控检查、下单...
    order_ids = place_orders(preview_data['plan_content'])

    # 6. 记录幂等键
    redis.sadd("processed_idempotency_keys", preview_data['idempotencyKey'])

    return jsonify({'success': True, 'order_ids': order_ids})
8. 结语
   本设计文档完整描述了 OpenClaw 与现有量化交易系统的集成方案，覆盖了通信、决策增强、数据关联、前端交互和安全等方面。实施后，量化系统将具备 AI 辅助决策能力，且不破坏原有自动交易核心。后续可根据实际运行情况持续优化，并逐步向全自动模式演进。
