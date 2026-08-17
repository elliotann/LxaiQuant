DeepSeek 智能行情分析与量化交易系统集成设计文档（最终修正版）
版本：7.0
最后更新：2026-05-07
核心修正：technical_direction 使用 LB/SB/SS/BS 标识（做多/做空/平多/平空），signal_strength 为 0~2 浮点数仓位权重因子，extra_params 保持与现有信号完全兼容。

1. 设计目标
   利用 DeepSeek 大语言模型分析多周期 K 线数据，生成自然语言交易报告和结构化 JSON（tradeplan）。

将分析建议转换为 technical_signal 记录，新增 signal_source='DEEPSEEK'，保留 data_source 表示交易所平台。

关键修正：technical_direction 必须使用系统标准值：LB（做多）、SB（做空）、SS（平多）、BS（平空）。signal_strength 为 0~2 浮点数，直接作为仓位权重因子（基础仓位 × 权重 = 最终开仓金额）。

extra_params 格式与现有技术信号完全兼容（包含 priceTargets, stopLossLevels, optimalStopLoss, optimalTakeProfit），量化系统无需修改解析代码。

支持手动生成信号（前端按钮）和自动生成信号（配置开启）。

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
   [前端] 渲染报告，根据 tradeplan 内容动态注入操作按钮（如“生成限价单信号”）
   │
   ▼（用户点击按钮 或 自动模式触发）
   [后端] 解析 tradeplan → 按兼容格式构建 extra_params → 确定 technical_direction 和 signal_strength → 插入 technical_signal
   │
   ▼
   [量化系统] 轮询 technical_signal（LEFT JOIN trade_signal 过滤未处理信号）→ 解析 extra_params
   │
   ▼
   [量化系统] 使用 signal_strength 计算实际仓位，调用交易所 API 执行交易，创建 trade_signal 记录
3. 数据库表结构
   3.1 technical_signal 表增加字段（最小化变更）
   sql
   ALTER TABLE technical_signal
   ADD COLUMN `signal_source` VARCHAR(50) DEFAULT NULL
   COMMENT '信号来源: DEEPSEEK, MACD, RSI, BOLL, AI等'
   AFTER `data_source`;

ALTER TABLE technical_signal
ADD COLUMN `source_advice_id` VARCHAR(64) DEFAULT NULL
COMMENT '关联的 DeepSeek 建议ID';
说明：

data_source 保持原有语义，填入交易所平台（如 "OKX"、"BINANCE"）。

signal_source 用于区分信号来源，DeepSeek 固定为 'DEEPSEEK'。

订单执行状态由 trade_signal 管理，technical_signal 不添加 order_status、order_id。

3.2 辅助表 trading_advice（新增）
sql
CREATE TABLE trading_advice (
id BIGINT AUTO_INCREMENT PRIMARY KEY,
advice_id VARCHAR(64) UNIQUE NOT NULL,
symbol VARCHAR(20) NOT NULL,
natural_report TEXT,
tradeplan_json JSON,
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
3.3 现有 trade_signal 表保持不变
trade_signal 通过 technical_signal_id 关联 technical_signal，负责订单执行状态、订单号、成交价等。

4. DeepSeek 交互规范
   4.1 系统提示词（SYSTEM）核心要点
   角色：资深加密货币合约交易员。

数据规则：所有实时数据由用户提供，模型不得主动获取。

风控：单笔亏损 ≤ 权益 1.5%（可配置）；余额不足时明确写出原因。

订单类型：必须区分 LIMIT / CONDITION / MARKET，遵守对应字段约束。

输出：先自然语言报告（固定章节），后 ````tradeplan` JSON 代码块。

JSON schema：stopLoss 为数值，takeProfit 数组每个元素含 level（价格）和 ratio（平仓比例，总和1）。

新增：要求模型输出 signalStrength 字段（0~2 浮点数），表示仓位权重因子（1.0 为标准仓位，>1 加仓，<1 轻仓）。若模型无法判断可缺省，后端使用默认值 1.0。

4.2 期望的 tradeplan JSON 结构（含 signalStrength）
json
{
"type": "live_advice_v1",
"facts": { ... },
"advice": {
"direction": "SHORT",
"entry": { "type": "LIMIT", "price": 2325.0, "condition": null },
"stopLoss": 2335.0,
"takeProfit": [
{ "level": 2300.0, "ratio": 0.5 },
{ "level": 2290.0, "ratio": 0.5 }
],
"positionSize": {
"suggestedContracts": 2,
"riskPercent": 2.0,
"calculationBasis": "..."
},
"signalStrength": 1.2   // 新增：仓位权重，1.2表示比正常仓位大20%
},
"alternativeAdvice": { ... },
"tradePlanDraft": null
}
若模型未输出 signalStrength，后端默认使用 1.0。

5. 技术信号转换规则（关键修正）
   5.1 基础字段映射
   technical_signal 字段	映射逻辑
   data_source	用户默认交易所（如 "OKX"），从配置获取
   signal_source	"DEEPSEEK"
   strategy_name	"DeepSeek_TrendFollowing"（可配置）
   indicator	"AI_STRATEGY"
   timeframe	facts.interval
   symbol	facts.symbol
   technical_direction	见 5.2 详细规则
   signal_strength	从 tradeplan.advice.signalStrength 获取，否则默认 1.0（0~2 浮点数）
   entry_type	advice.entry.type（LIMIT/CONDITION/MARKET）
   limit_price	若 entry_type='LIMIT' 则填 advice.entry.price，否则 NULL
   signal_hash	MD5(symbol + timeframe + kline_time + 'DEEPSEEK' + direction)
   source_advice_id	原始建议 ID
   extra_params	按 5.3 规范构建
   5.2 technical_direction 映射规则（核心）
   场景	原 advice.direction / 动作	映射为 technical_direction
   开仓	LONG	LB
   开仓	SHORT	SB
   平仓（平多头）	close_signal 且原持仓为多	SS
   平仓（平空头）	close_signal 且原持仓为空	BS
   对冲（开反向仓）	hedge_signal，原持仓为多 → 开空	SB
   对冲（开反向仓）	hedge_signal，原持仓为空 → 开多	LB
   无交易	NO_TRADE	NEUTRAL（或保留原值）
   实现逻辑：

对于 limit_signal、cond_signal：根据 advice.direction 直接映射为 LB 或 SB。

对于 close_signal：需要前端或后端传入原持仓方向（从 facts.riskStatus.positions 获取），反向映射。

对于 hedge_signal：根据原持仓反向开仓，同样映射为 LB 或 SB。

5.3 extra_params 构建规范（与现有信号兼容）
必须包含以下顶层字段：

priceTargets：数组，每个元素含 level, price, probability, description, basedOn, distanceFromCurrent, riskRewardRatio。

stopLossLevels：数组，每个元素含 level, price, type, description, basedOn, riskPercentage, primary。

optimalStopLoss：数值（主止损价）。

optimalTakeProfit：数值（第一个止盈目标价格）。

_deepseek（可选）：存储原始 tradeplan 和 adviceId，用于追溯。

转换示例（基于 4.2 的 tradeplan）：

json
{
"priceTargets": [
{"level":1,"price":2300.0,"probability":0.7,"description":"DeepSeek 第1止盈目标","basedOn":"DeepSeek_AI","distanceFromCurrent":14.16,"riskRewardRatio":2.50},
{"level":2,"price":2290.0,"probability":0.7,"description":"DeepSeek 第2止盈目标","basedOn":"DeepSeek_AI","distanceFromCurrent":24.16,"riskRewardRatio":3.50}
],
"stopLossLevels": [
{"level":1,"price":2335.0,"type":"固定止损","description":"DeepSeek 建议止损位","basedOn":"AI分析","riskPercentage":2.0,"primary":true}
],
"optimalStopLoss": 2335.0,
"optimalTakeProfit": 2300.0,
"_deepseek": {
"adviceId": "adv_xxx",
"tradeplan": { ... }
}
}
6. 前端交互与按钮设计（纯文字）
   6.1 按钮注入位置（用红色粗体标注说明）
   在渲染的自然语言报告下方，前端根据隐藏的 tradeplan 动态插入按钮：

主策略段落后：[生成限价单信号]（若 entry.type='LIMIT'，对应 action=limit_signal）

备选策略段落后：[生成条件单信号]（若有 alternativeAdvice，对应 action=cond_signal）

持仓管理段落后：[生成对冲信号]、[生成平仓信号]（对应 action=hedge_signal、close_signal）

执行清单段落后：[一键生成全部信号]（对应 action=all_signals）

风险提醒段落后：去掉[我已阅读风险，继续执行]

6.2 按钮点击流程
用户点击任意生成按钮 → 弹出确认框，展示信号摘要（方向、价格、止损止盈、仓位权重等）。

确认后调用后端接口：

text
POST /api/signal/create-from-advice
{ "adviceId": "xxx", "action": "limit_signal" }
后端执行转换，插入 technical_signal，返回信号 ID。

前端提示：“信号已生成，量化系统将自动处理”。

6.3 自动信号生成配置
用户可在设置中开启“自动生成信号”模式，并选择允许的动作类型。开启后，后端收到 DeepSeek 建议时自动调用上述接口。

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
   处理逻辑（伪代码）：

java
// 1. 查询 trading_advice 获取 tradeplan
// 2. 根据 action 确定使用 advice 或 alternativeAdvice，或生成对冲/平仓参数
// 3. 确定 technical_direction（见5.2）
// 4. 确定 signal_strength = tradeplan.advice.signalStrength 或 1.0
// 5. 构建 extra_params（价格目标、止损水平，见5.3）
// 6. 填充其他字段并插入 technical_signal
7.3 自动信号生成配置
text
GET /api/signal/auto-config
POST /api/signal/auto-config
配置项：enabled, allowedActions, defaultSignalStrength, 等。

8. 量化系统消费信号
   8.1 轮询未处理信号
   sql
   SELECT ts.*
   FROM technical_signal ts
   LEFT JOIN trade_signal tr ON tr.technical_signal_id = ts.id
   WHERE ts.signal_source = 'DEEPSEEK' AND tr.id IS NULL
   ORDER BY ts.create_time ASC
   LIMIT 10;
   8.2 仓位计算
   java
   double basePosition = getUserBasePosition(); // 如 1000 USDT
   double weight = technicalSignal.getSignalStrength(); // 0~2
   double finalPosition = basePosition * weight; // 最终开仓金额
   // 根据杠杆、入场价格换算张数
   int contracts = (int)(finalPosition * leverage / entryPrice);
   8.3 执行交易
   根据 entry_type 和 limit_price 创建订单。

设置止损：使用 extra_params.stopLossLevels 中 primary=true 的价格。

设置止盈：遍历 extra_params.priceTargets，为每个目标按 ratio 创建分批限价单。

创建 trade_signal 记录，关联 technical_signal_id，更新订单状态。

9. 完整示例记录
   以下是一条符合规范的 technical_signal 记录关键字段：

text
data_source: OKX
signal_source: DEEPSEEK
strategy_name: DeepSeek_TrendFollowing
indicator: AI_STRATEGY
symbol: ETH-USDT-SWAP
timeframe: 15m
technical_direction: SB
signal_strength: 1.2
entry_type: LIMIT
limit_price: 2325.00000000
extra_params: {"priceTargets":[...],"stopLossLevels":[...],"optimalStopLoss":2335.0,"optimalTakeProfit":2300.0,"_deepseek":{...}}
signal_hash: a0ac377d478b32556807d0ccdf73a5d8
量化系统读取该信号后，technical_direction = SB 表示做空，signal_strength = 1.2 表示实际仓位为基仓的 1.2 倍。

10. 实施计划
    数据库变更：执行 ALTER 添加 signal_source 和 source_advice_id。

后端开发：

修改提示词，要求模型输出 signalStrength。

实现信号转换服务（含 technical_direction 映射和 signal_strength 提取）。

实现 API 接口。

前端开发：报告渲染、按钮注入、接口联调。

量化系统改造：

增加对 signal_source='DEEPSEEK' 信号的轮询。

实现仓位权重计算（基仓 × signal_strength）。

执行交易并创建 trade_signal。

测试：模拟盘全流程。

上线：灰度后正式发布。

文档结束。本设计完全兼容现有量化系统的信号规范，并明确了 technical_direction 和 signal_strength 的正确用法。