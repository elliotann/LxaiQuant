AI 复盘功能设计文档
版本：1.0
日期：2026-05-11
相关系统：量化交易系统
依赖模块：订单管理、策略管理、机器人管理、市场数据、技术信号、小灵宝聊天

1. 功能概述
   AI 复盘功能旨在自动分析用户历史交易表现，从绩效统计、策略归因、风险行为、技术信号准确性等多个维度生成结构化报告，并通过小灵宝聊天以对话形式推送给用户，同时提供优化建议。

核心能力：

交易绩效统计（总盈亏、胜率、盈亏比、夏普比率、最大回撤）

策略归因分析（不同策略/机器人的盈亏贡献，与历史基准对比）

行为风险评估（止损执行率、持仓集中度、过度交易、杠杆使用）

技术信号复盘（信号触发后价格走势一致性，按指标类型分组）

市场环境对比（与标的走势、波动率对比）

AI 生成优化建议（参数调整、止盈止损修改、仓位管理等）

用户入口：

市场行情 → 小灵宝聊天 → 输入框上方的 复盘胶囊 按钮。

复盘当前选中的机器人在指定时间范围内的交易表现。

系统异步生成报告，并在同一聊天窗口中展示摘要和详细内容。

2. 数据依赖（基于现有表结构）
   数据表	字段	用途
   ai_trade_order	order_sn, robot_id, signal_id, order_side_enum, buy_time, sell_time, buy_price, sell_price, volume, income, charge, loss_price, gain_price, trade_order_status, lever_rate	盈亏计算、持仓时长、止损触发、杠杆分析
   ai_trade_order_item	order_sn, trade_order_item_status, closed_volume, income, charge	分批交易统计
   ai_trade_order_close	order_sn, close_method (MANUAL/AUTO), close_order_type (止损/止盈/手动), income, sell_time	平仓方式统计、纪律性评估
   trading_bot	bot_id, bot_name, user_id, strategy_id, trading_pair, allocated_capital, status, statistics	机器人维度分组
   strategy	strategy_id, name, strategy_type, avg_sharpe_ratio, avg_annual_return, avg_max_drawdown, default_parameters	策略归因、历史基准对比
   vdr_candlestick	symbol, open_price, close_price, high_price, low_price, candlestick_interval_enum, time_str	市场环境计算（标的同期涨跌幅、波动率）
   technical_signal	symbol, timeframe, indicator, technical_direction (LB/SB), signal_strength, confidence, kline_timestamp, entry_type	信号准确性评估
   注意：新闻情绪暂未实现，复盘功能 Phase 1 不依赖情绪数据。

3. 核心指标定义
   3.1 绩效指标
   指标	计算公式	说明
   总盈亏	Σ(income) 其中 trade_order_status 为已完成/止损/止盈	绝对收益
   胜率	盈利订单数 / 总订单数	决策正确率
   盈亏比	平均盈利 / 平均亏损	风险回报效率
   夏普比率	(平均收益率 - 无风险利率) / 收益率标准差	风险调整后收益
   最大回撤	Max(峰值 - 谷值) / 峰值	组合最大亏损幅度
   平均持仓时长	Σ(平仓时间 - 开仓时间) / 订单数	交易风格判断
   3.2 行为指标
   指标	说明	不良信号
   止损执行率	实际止损订单数 / 应止损订单数（最低价触及止损价的订单）	<80% 提示纪律问题
   日均交易频次	订单数 / 复盘天数	高于历史均值2倍提示过度交易
   持仓集中度	Max(单品价值) / 总价值	>30% 提示集中度风险
   多空占比	BUY订单数 / SELL订单数	极端偏好可能缺乏灵活性
   杠杆使用均值	AVG(lever_rate)	高杠杆提示风险
   3.3 信号准确性指标（需关联 technical_signal）
   整体准确率 = (信号发出后 1/3/5 根 K线方向与信号方向一致的订单数) / 总关联订单数

按指标分类准确率：如 MACD 信号准确率、RSI 信号准确率、AI 信号准确率等。

按置信度区间准确率：例如置信度 >0.8 的信号准确率 vs 置信度 <0.5 的信号准确率。

3.4 市场对比指标
标的同期收益率 = (期末价 - 期初价) / 期初价

标的波动率 = 日收益率的标准差（年化）

超额收益 = 策略总收益率 - 标的同期收益率

4. 技术架构
   （待补充架构图）

5. 处理流程（Phase 1 详细）
   用户触发：点击复盘胶囊 → 输入复盘指令（如“今日复盘”）→ 系统使用当前选中的机器人。

后端接收：创建 review_tasks 记录，状态 PENDING，立即返回 reviewId。

异步任务：

数据采集：从 ai_trade_order 筛选指定时间、机器人的已平仓订单；关联平仓记录、机器人、策略；从 vdr_candlestick 获取标的行情；从 technical_signal 获取关联信号。

指标计算：分别计算绩效、行为、信号准确性、市场对比指标。

构建 Prompt：将上述指标以 JSON 格式注入模板。

多 Agent 分析：调用 LLM（复用现有 HttpClient 直连 Ollama/DeepSeek 方式）分别执行策略归因、风险评估、建议生成。

报告生成：将分析结果整合为 HTML/Markdown 格式，存入 review_tasks.report_json。

SSE 推送：通过 SSE 向客户端推送 REVIEW_COMPLETE 事件（包含摘要和报告内容）。

前端展示：小灵宝聊天窗口自动追加一条 AI 回复，展示摘要卡片和详细报告（可折叠/展开），并提供导出 PDF 按钮。

6. API 设计
   6.1 触发复盘（复用实时建议交互模式）
   前端已有“复盘”胶囊（biz = "recap"），用户输入复盘指令后调用后端新端点，交互模式与 POST /api/advice/live 完全一致。

URL：POST /api/advice/review
认证：JWT（用户已登录）

请求体：

json
{
"stream": true,
"symbolText": "复盘 过去7天",
"accountId": "acc_001",
"robotId": "bot_001",
"timeRange": {
"start": "2026-05-04T00:00:00Z",
"end": "2026-05-11T23:59:59Z"
},
"includeSignals": true,
"compareWithBenchmark": true,
"history": []
}
6.2 SSE 流式返回（与实时建议完全一致）
复用 streamAdviceResponse() 模式：文本按 80 字符分块 SSE 推送，末尾推送 done: true 事件携带完整元数据。

SSE data 字段（中间块）：

json
{"response": "根据过去7天的交易数据，总盈亏为+1,234.56 USDT..."}
SSE data 字段（末尾块）：

json
{
"done": true,
"adviceId": "adv_xxx",
"response": "根据过去7天的交易数据...",
"naturalReport": "根据过去7天的交易数据...",
"reviewId": "rev_789",
"summary": {
"totalPnl": 1234.56,
"winRate": 58.3,
"maxDrawdown": -8.2,
"topSuggestions": ["调紧止损至 3%", "降低每日开单次数"]
}
}
6.3 导出报告 PDF
URL：GET /api/review/{reviewId}/export/pdf
响应：PDF 文件流。

7. 核心数据库表（新增）
   sql
   -- 复盘任务表
   CREATE TABLE `review_tasks` (
   `id` CHAR(36) PRIMARY KEY,
   `user_id` VARCHAR(32) NOT NULL,
   `conversation_id` VARCHAR(64) NULL,      -- 关联的小灵宝会话ID
   `time_range_start` DATETIME NOT NULL,
   `time_range_end` DATETIME NOT NULL,
   `robot_id` VARCHAR(32) NOT NULL,          -- 复盘机器人ID（当前选中的机器人）
   `include_signals` TINYINT(1) DEFAULT 1,
   `compare_benchmark` TINYINT(1) DEFAULT 1,
   `status` VARCHAR(20) DEFAULT 'PENDING',   -- PENDING, PROCESSING, COMPLETED, FAILED
   `report_json` JSON,                       -- 生成的报告内容
   `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   `completed_at` TIMESTAMP NULL,
   INDEX idx_user_status (user_id, status),
   FOREIGN KEY (user_id) REFERENCES `user`(`user_id`)
   );
   指标快照表 review_metrics_snapshot 可选，用于趋势分析。

8. 关键代码实现（Java + Spring Boot）
   8.1 指标计算服务（使用 MyBatis-Plus）
   java
   @Service
   public class ReviewMetricsService {
   @Autowired private TradeOrderMapper orderMapper;
   @Autowired private TradingBotMapper botMapper;
   @Autowired private CandlestickMapper klineMapper;
   @Autowired private TechnicalSignalMapper signalMapper;

   public ReviewMetrics calculate(String userId, LocalDateTime start, LocalDateTime end, String robotId) {
   // 1. 查询订单
   List<TradeOrder> orders = orderMapper.selectList(
   new LambdaQueryWrapper<TradeOrder>()
   .eq(TradeOrder::getRobotId, robotId)
   .between(TradeOrder::getSellTime, start, end)
   .in(TradeOrder::getTradeOrderStatus, Arrays.asList(TradeOrder.TradeOrderStatus.CLOSE, TradeOrder.TradeOrderStatus.LOSS, TradeOrder.TradeOrderStatus.GAIN))
   .eq(TradeOrder::getDeleteFlag, 0)
   );
   // 2. 绩效指标
   BigDecimal totalPnL = orders.stream().map(TradeOrder::getIncome).reduce(BigDecimal.ZERO, BigDecimal::add);
   long winCount = orders.stream().filter(o -> o.getIncome().compareTo(BigDecimal.ZERO) > 0).count();
   double winRate = orders.isEmpty() ? 0 : (double) winCount / orders.size();
   // 3. 止损执行率
   List<String> orderSns = orders.stream().map(TradeOrder::getOrderSn).collect(Collectors.toList());
   long stopLossOrders = orderCloseMapper.selectCount(
   new LambdaQueryWrapper<TradeOrderClose>()
   .eq(TradeOrderClose::getCloseOrderType, "LOSS")
   .in(TradeOrderClose::getOrderSn, orderSns)
   );
   long shouldStopOrders = orders.stream().filter(o -> o.getLossPrice() != null).count();
   double stopLossRate = shouldStopOrders == 0 ? 1.0 : (double) stopLossOrders / shouldStopOrders;
   // 4. 策略归因（按 strategy_id 分组）
   Map<String, BigDecimal> strategyPnL = new HashMap<>();
   // ...更多计算
   return new ReviewMetrics(totalPnL, winRate, stopLossRate, strategyPnL);
   }
   }
   8.2 复盘 Prompt 构建
   仅负责拼接复盘指标的 prompt 模板，LLM 调用统一走 LlmGenerateController。

java
@Component
public class ReviewPromptBuilder {

    public String buildSystemPrompt(ReviewMetrics metrics) {
        return """
            你是交易复盘专家。基于以下数据，输出 JSON 格式的分析报告。

            绩效指标：
            - 总盈亏: %.2f
            - 胜率: %.2f%%
            - 盈亏比: %.2f
            - 最大回撤: %.2f%%

            行为指标：
            - 止损执行率: %.2f%%
            - 日均交易次数: %.1f
            - 持仓集中度: %.2f%%

            策略归因：
            %s

            请输出：
            {
              "strategyInsight": "策略表现优劣分析...",
              "riskInsight": "风险行为识别与建议...",
              "suggestions": ["建议1", "建议2", ...]
            }
            """.formatted(
                metrics.getTotalPnL(), metrics.getWinRate()*100,
                metrics.getProfitLossRatio(), metrics.getMaxDrawdown()*100,
                metrics.getStopLossRate()*100, metrics.getAvgDailyTrades(),
                metrics.getConcentrationRatio(), metrics.getStrategyAttributionJson()
            );
    }
}
8.3 复盘服务（复用 LiveAdvice 模式）
新增 POST /api/advice/review 端点，流程与实时建议一致：收集数据 → 调用 LLM → SSE 流式返回。

java
@RestController
@RequestMapping("/api/advice")
@RequiredArgsConstructor
public class ReviewController {
private final ReviewMetricsService metricsService;
private final ReviewPromptBuilder promptBuilder;
private final LlmGenerateController llmGenerateController;
private final ObjectMapper objectMapper;

    @PostMapping("/review")
    public Object review(@RequestBody ReviewRequest req, HttpServletResponse response) throws Exception {
        // 1. 计算复盘指标
        ReviewMetrics metrics = metricsService.calculate(
            req.accountId, req.timeRange.getStart(), req.timeRange.getEnd(), req.robotId
        );
        // 2. 构建 prompt
        String systemPrompt = promptBuilder.buildSystemPrompt(metrics);
        String userPrompt = req.symbolText;
        // 3. 调用 LLM（复用 LlmGenerateController 统一入口）
        LlmGenerateController.GenerateRequest gen = new LlmGenerateController.GenerateRequest();
        gen.stream = true;
        gen.messages = List.of(
            new LlmGenerateController.Message("system", systemPrompt),
            new LlmGenerateController.Message("user", userPrompt)
        );
        // 4. SSE 流式返回（由 LlmGenerateController 内部处理 stream 输出）
        return llmGenerateController.generate(gen, response);
    }
}
9. 前端集成要点（小灵宝聊天）
   复盘胶囊：xiaolingbaoBizPills 中已有 { key: "recap", label: "复盘" }，位于聊天输入框上方的 pill 栏。

交互流程（与实时建议完全一致）：

用户点击“复盘”pill → 输入框 placeholder 变为“发送复盘指令（例如 今日复盘 / 昨日复盘 / 本周复盘）”
用户输入复盘指令 → 调用 POST /api/advice/review（stream: true）
前端通过 fetch + ReadableStream 接收 SSE 流式响应，逐块渲染 Markdown
末尾 done: true 事件携带复盘元数据（reviewId、summary 等）
消息渲染：复盘结果以 Markdown 格式展示在聊天窗口中，与普通小灵宝消息渲染方式一致，无需特殊 UI 组件。

追问：用户可继续输入“为什么 BTC 策略亏最多”——后端根据当前对话历史查找最近的 reviewId，结合 LLM 生成上下文回答。追问走 POST /api/advice/review 时将 history 参数带上即可。

10. 实施计划
    阶段	任务	估算工时
    Phase 1	数据采集与指标计算（SQL + Java 聚合），无 AI 的基础报告	3 天
    Phase 2	集成 LLM Prompt 工程，实现策略归因、风险评估、建议生成	3 天
    Phase 3	前端复盘胶囊 UI、SSE 推送、聊天窗口渲染	2 天
    Phase 4	导出 PDF、追问上下文支持、测试与调优	2 天
11. 注意事项
    性能：复盘计算可能涉及大量订单，需使用索引（idx_robot_time_status、idx_user_id 等）并限制最大时间范围（建议不超过 90 天）。

AI 成本：使用轻量模型（如 DeepSeek）进行指标分析，大型模型仅用于最终报告生成。

安全性：报告内容需做 XSS 过滤，用户间数据隔离。

扩展性：后续可增加新闻情绪分析，只需在指标计算阶段加入情绪打分并修改 Prompt。