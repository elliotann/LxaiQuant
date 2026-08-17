量化交易系统详细设计文档
版本：v3.0
核心设计理念：借鉴 QuantDinger 架构思想 + 融合 TA4J 规则模式（规约模式），“借壳 TA4J（仅指标与规则理念），重塑自研灵魂”。
目标：彻底解决 TA4J 单持仓限制及回测数据库 I/O 瓶颈，实现高性能、双向持仓、规则驱动的量化交易系统。

1. 设计背景与目标
   1.1 现存痛点
   TA4J 架构锁死：原生 Strategy 仅支持布尔买卖信号，无法表达 OPEN_LONG / CLOSE_SHORT 等四向指令；TradingRecord 强制单持仓，无法支持同一标的双向持仓。

回测性能灾难：传统方案在逐 Bar 循环中直接调用 JPA.save()，网络 IO 和事务开销将分钟级回测拖至小时级。

业务耦合严重：策略代码中混杂数据库查询，违背确定性原则，导致回测结果不可复现。

1.2 设计目标
双向持仓原生支持：通过 (Symbol, Direction) 复合维度管理仓位，允许多空共存。

规则驱动策略：借鉴 TA4J 的规约模式（Specification Pattern），将交易条件拆解为可组合、可复用的规则单元。

计算与持久化分离：回测循环内 零数据库交互，全部基于内存计算。

双模归一：回测（Backtest）与实盘（Live）共用同一套策略代码，仅通过 Spring Profile 切换执行引擎和持久化策略。

高性能：单线程回测 10 年日线数据（约 2500 根 K 线）耗时 < 500ms；分钟级回测（~50万根）耗时 < 5s。

2. 总体架构设计
   2.1 分层逻辑架构图
   text
   ┌─────────────────────────────────────────────────────────────────────────────┐
   │                           【表现层 / API Gateway】                          │
   │                    (Spring Controller 接收请求，校验参数)                    │
   └─────────────────────────────────┬───────────────────────────────────────────┘
   │
   ┌─────────────────────────────────▼───────────────────────────────────────────┐
   │                           【门面服务层 Facade】                             │
   │  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐ │
   │  │ BacktestFacade      │  │ LiveTradingFacade   │  │ OptimizationFacade  │ │
   │  │ (异步提交/状态查询)  │  │ (下单/撤单/持仓查询) │  │ (并行调参任务管理)  │ │
   │  └─────────────────────┘  └─────────────────────┘  └─────────────────────┘ │
   └─────────────────────────────────┬───────────────────────────────────────────┘
   │
   ┌─────────────────────────────────▼───────────────────────────────────────────┐
   │                         【策略注册中心 StrategyRegistry】                    │
   │            (Spring 容器管理，根据 strategyId 懒加载策略 Bean)               │
   └─────────────────────────────────┬───────────────────────────────────────────┘
   │
   ┌─────────────────────────────────▼───────────────────────────────────────────┐
   │                      【核心引擎层 Engine】(模板方法模式)                     │
   │  ┌───────────────────────┐    ┌──────────────────────────────────────────┐ │
   │  │  AbstractBaseEngine   │    │  执行路由器 (ExecutionRouter)            │ │
   │  │  (定义 run 流程骨架)   │    │  (根据 Profile 决定注入哪个具体引擎)     │ │
   │  └───────────┬───────────┘    └──────────────────────────────────────────┘ │
   │              │                                                            │
   │  ┌───────────▼───────────┐    ┌──────────────────────────────────────────┐ │
   │  │ BacktestEngine        │    │ LiveEngine                               │ │
   │  │ - 数据源: List<Bar>   │    │ - 数据源: WebSocket 流式 Bar             │ │
   │  │ - 驱动: For 循环      │    │ - 驱动: 事件监听器 / 定时轮询             │ │
   │  │ - 撮合: Close/HL模拟  │    │ - 撮合: 交易所回调 / Rest API 查询        │ │
   │  └───────────────────────┘    └──────────────────────────────────────────┘ │
   └─────────────────────────────────┬───────────────────────────────────────────┘
   │ 传入 Context
   ┌─────────────────────────────────▼───────────────────────────────────────────┐
   │                    【策略执行上下文 TradingContext】                        │
   │  (生命周期: 每次回测/实盘任务新建，@Scope("prototype"))                     │
   │  ┌─────────────────────┐  ┌─────────────────────────────────────────────┐  │
   │  │ PortfolioManager    │  │ ActionRecorder (信号记录器)                │  │
   │  │ - 内存持仓 Map      │  │ - 暂存 ActionRecord 列表                   │  │
   │  │ - 现金/净值快照列表  │  │ - 提供 getEquityCurve()                  │  │
   │  └─────────────────────┘  └─────────────────────────────────────────────┘  │
   └─────────────────────────────────┬───────────────────────────────────────────┘
   │
   ┌─────────────────────────────────▼───────────────────────────────────────────┐
   │                    【业务持久化门面 PersistenceGateway】                     │
   │  ┌─────────────────────────┐    ┌────────────────────────────────────────┐ │
   │  │ BacktestBatchImpl       │    │ LiveAsyncImpl                         │ │
   │  │ (@Profile("backtest"))  │    │ (@Profile("live"))                    │ │
   │  │ - 内存 Buffer 暂存      │    │ - @Async 异步落库                     │ │
   │  │ - flush() 批量 SaveAll  │    │ - 实时更新 Position 表                │ │
   │  └─────────────────────────┘    └────────────────────────────────────────┘ │
   └─────────────────────────────────┬───────────────────────────────────────────┘
   │
   ┌─────────────────────────────────▼───────────────────────────────────────────┐
   │                        【基础设施层 Infrastructure】                        │
   │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐  │
   │  │Ta4jIndicator│ │ 规则引擎    │ │ DataLoader  │ │ XChange API         │  │
   │  │ Helper      │ │ (Rule)      │ │ (CSV/DB)    │ │ (交易所)            │  │
   │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────────────┘  │
   └─────────────────────────────────────────────────────────────────────────────┘
3. 核心数据模型（Entity & DTO）
   3.1 数据库实体设计（JPA）
   （1）开仓记录表 open_order

java
@Entity
@Table(indexes = {@Index(columnList = "backtestBatchId"), @Index(columnList = "symbol")})
public class OpenOrder {
@Id private String orderId;          // UUID
private String strategyId;
private String symbol;
@Enumerated(EnumType.STRING) private Direction direction; // LONG, SHORT
private BigDecimal openPrice;
private BigDecimal quantity;
private LocalDateTime openTime;
private String backtestBatchId;      // 关联回测批次（回测模式必填）
private String tradeType;            // "BACKTEST" / "LIVE"
}
（2）平仓记录表 close_trade

java
@Entity
public class CloseTrade {
@Id private String tradeId;
private String openOrderId;           // 关联入场单 (Foreign Key)
private String symbol;
@Enumerated(EnumType.STRING) private Direction direction; // CLOSE_LONG, CLOSE_SHORT
private BigDecimal closePrice;
private BigDecimal quantity;
private BigDecimal pnl;               // 盈亏金额
private BigDecimal pnlPercent;        // 收益率 %
private LocalDateTime closeTime;
private String closeReason;           // STOP_LOSS, TAKE_PROFIT, SIGNAL, MANUAL
private String backtestBatchId;
}
（3）实时仓位表 position（核心：支持双向持仓）

java
@Entity
@Table(uniqueConstraints = {
@UniqueConstraint(columnNames = {"symbol", "direction"}) // 联合唯一索引
})
public class Position {
@Id private Long id;
private String symbol;
@Enumerated(EnumType.STRING) private Direction direction; // LONG / SHORT
private BigDecimal totalQuantity;
private BigDecimal avgOpenPrice;      // 加权平均开仓价
private BigDecimal currentPrice;      // 最新价（实时更新）
private BigDecimal unrealizedPnl;     // 浮动盈亏
private LocalDateTime lastUpdateTime;
}
（4）回测任务表 backtest_job（用于任务追踪）

java
@Entity
public class BacktestJob {
@Id private String batchId;
private String strategyId;
private String symbol;
private LocalDateTime startTime;
private LocalDateTime endTime;
private BigDecimal initCash;
private BigDecimal finalEquity;
private Double sharpeRatio;
private Double maxDrawdown;
private String status; // PENDING, RUNNING, SUCCESS, FAILED
private Integer totalTrades;
}
3.2 核心领域模型（内存计算用）
（1）方向与指令枚举

java
public enum Direction { LONG, SHORT }
public enum Action { OPEN_LONG, CLOSE_LONG, OPEN_SHORT, CLOSE_SHORT, HOLD }
public enum CloseReason { STOP_LOSS, TAKE_PROFIT, SIGNAL, TIMEOUT }
（2）内存持仓对象 MemoryPosition

java
@Data
public class MemoryPosition {
private String symbol;
private Direction direction;
private BigDecimal quantity;
private BigDecimal avgPrice;
// 仅内存计算用，不落库
private BigDecimal stopLossPrice;
private BigDecimal takeProfitPrice;
}
4. 规则引擎模块设计（借鉴 TA4J 规约模式）
   4.1 核心接口
   java
   @FunctionalInterface
   public interface TradingRule {
   /**
    * 判断规则是否在指定时刻满足
    * @param index 当前Bar的索引
    * @param series K线序列
    * @param context 交易上下文（可获取持仓、资金等状态）
    * @return true 表示规则满足
      */
      boolean isSatisfied(int index, BarSeries series, TradingContext context);
      }
      4.2 基础规则实现
      指标交叉类规则：

java
@Component
public class CrossedUpIndicatorRule implements TradingRule {
private final Indicator indicator1;
private final Indicator indicator2;

    public CrossedUpIndicatorRule(Indicator up, Indicator down) {
        this.indicator1 = up;
        this.indicator2 = down;
    }
    
    @Override
    public boolean isSatisfied(int index, BarSeries series, TradingContext context) {
        if (index < 1) return false;
        return indicator1.getValue(index) > indicator2.getValue(index) &&
               indicator1.getValue(index - 1) <= indicator2.getValue(index - 1);
    }
}
指标阈值类规则：

java
public class OverIndicatorRule implements TradingRule {
private final Indicator indicator;
private final Number threshold;

    @Override
    public boolean isSatisfied(int index, BarSeries series, TradingContext context) {
        return indicator.getValue(index).doubleValue() > threshold.doubleValue();
    }
}
状态感知类规则（融合你的架构）：

java
public class HasLongPositionRule implements TradingRule {
private final String symbol;

    @Override
    public boolean isSatisfied(int index, BarSeries series, TradingContext context) {
        return context.hasLongPosition(symbol);
    }
}
风险管理类规则：

java
public class StopLossRule implements TradingRule {
private final Indicator priceIndicator;
private final double percentage;

    @Override
    public boolean isSatisfied(int index, BarSeries series, TradingContext context) {
        // 获取当前持仓的平均开仓价，计算浮动亏损是否超过阈值
        String symbol = series.getName();
        if (context.hasLongPosition(symbol)) {
            BigDecimal avgPrice = context.getAvgPrice(symbol, Direction.LONG);
            double loss = (avgPrice.doubleValue() - priceIndicator.getValue(index).doubleValue()) / avgPrice.doubleValue();
            return loss >= percentage / 100.0;
        }
        // 空头止损逻辑...
        return false;
    }
}
4.3 逻辑组合规则（规约模式核心）
AndRule（与）：

java
public class AndRule implements TradingRule {
private final List<TradingRule> rules = new ArrayList<>();

    public AndRule(TradingRule... rules) {
        this.rules.addAll(Arrays.asList(rules));
    }
    
    @Override
    public boolean isSatisfied(int index, BarSeries series, TradingContext context) {
        for (TradingRule rule : rules) {
            if (!rule.isSatisfied(index, series, context)) return false;
        }
        return true;
    }
}
OrRule（或）、NotRule（非）、XorRule（异或）、VoteRule（投票） 按同模式实现。

5. 策略层设计（融合规则引擎）
   5.1 策略接口
   java
   public interface ScriptStrategy {
   void init(Map<String, Object> params);

   /**
    * 核心决策方法：通过规则组合判断交易信号
      */
      Action onBar(int index, Bar bar, TradingContext context);

   void destroy();
   }
   5.2 基于规则的策略示例
   java
   @Component("RuleBasedStrategy")
   public class RuleBasedStrategy implements ScriptStrategy {
   @Autowired private Ta4jIndicatorHelper indicatorHelper;

   private TradingRule entryLongRule;   // 开多条件
   private TradingRule entryShortRule;  // 开空条件
   private TradingRule exitLongRule;    // 平多条件
   private TradingRule exitShortRule;   // 平空条件

   private Indicator closePrice;
   private Indicator fastSMA;
   private Indicator slowSMA;
   private Indicator rsi;

   @Override
   public void init(Map<String, Object> params) {
   // 1. 准备技术指标
   // 注意：BarSeries 在回测启动时才确定，这里只保存配置
   int fast = params.getOrDefault("fast", 5);
   int slow = params.getOrDefault("slow", 20);
   int rsiPeriod = params.getOrDefault("rsiPeriod", 14);

        // 2. 组装入场规则（金叉 AND RSI > 30）
        Rule goldenCross = new CrossedUpIndicatorRule(fastSMA, slowSMA);
        Rule rsiRule = new OverIndicatorRule(rsi, 30);
        this.entryLongRule = new AndRule(goldenCross, rsiRule);
        
        // 3. 组装空头入场规则（死叉 AND RSI < 70）
        Rule deathCross = new CrossedDownIndicatorRule(fastSMA, slowSMA);
        Rule rsiOverBought = new UnderIndicatorRule(rsi, 70);
        this.entryShortRule = new AndRule(deathCross, rsiOverBought);
        
        // 4. 出场规则（反向信号 OR 止损止盈）
        this.exitLongRule = new OrRule(
            new CrossedDownIndicatorRule(fastSMA, slowSMA),  // 死叉平多
            new StopLossRule(closePrice, 5)                  // 5% 止损
        );
        this.exitShortRule = new OrRule(
            new CrossedUpIndicatorRule(fastSMA, slowSMA),    // 金叉平空
            new StopLossRule(closePrice, 5)
        );
   }

   @Override
   public Action onBar(int index, Bar bar, TradingContext context) {
   String symbol = bar.getSeries().getName();

        // 检查入场条件
        if (entryLongRule.isSatisfied(index, bar.getSeries(), context)) {
            if (context.hasShortPosition(symbol)) return Action.CLOSE_SHORT;
            if (!context.hasLongPosition(symbol)) return Action.OPEN_LONG;
        }
        
        if (entryShortRule.isSatisfied(index, bar.getSeries(), context)) {
            if (context.hasLongPosition(symbol)) return Action.CLOSE_LONG;
            if (!context.hasShortPosition(symbol)) return Action.OPEN_SHORT;
        }
        
        // 检查出场条件
        if (exitLongRule.isSatisfied(index, bar.getSeries(), context)) {
            if (context.hasLongPosition(symbol)) return Action.CLOSE_LONG;
        }
        
        if (exitShortRule.isSatisfied(index, bar.getSeries(), context)) {
            if (context.hasShortPosition(symbol)) return Action.CLOSE_SHORT;
        }
        
        return Action.HOLD;
   }
   }
6. 执行引擎层设计
   6.1 模板引擎（BaseEngine）
   java
   public abstract class BaseEngine {
   @Autowired protected PersistenceGateway persistenceGateway;
   @Autowired protected StrategyRegistry strategyRegistry;

   public final BacktestReport run(BacktestRequest request) {
   // 1. 前置准备
   BarSeries series = loadData(request);
   TradingContext context = createContext(request.getInitCash());
   ScriptStrategy strategy = strategyRegistry.getStrategy(request.getStrategyId());
   strategy.init(request.getParams());

        // 2. 执行前置钩子
        beforeRun(series, context);
        
        // 3. 核心循环（由子类实现）
        executeLoop(series, context, strategy);
        
        // 4. 后置处理：唯一一次批量落库
        afterRun(context);
        
        // 5. 生成报告
        return buildReport(context);
   }

   protected abstract void executeLoop(BarSeries series, TradingContext ctx, ScriptStrategy strategy);

   protected void afterRun(TradingContext context) {
   persistenceGateway.flush(context.getPendingOrders(),
   context.getPendingTrades(),
   context.getEquityCurve());
   }
   }
   6.2 回测引擎（BacktestEngine）
   java
   @Component
   @Profile("backtest")
   public class BacktestEngine extends BaseEngine {
   @Override
   protected void executeLoop(BarSeries series, TradingContext ctx, ScriptStrategy strategy) {
   for (int i = 0; i < series.getBarCount(); i++) {
   Bar bar = series.getBar(i);
   // 1. 策略决策（通过规则判断）
   Action action = strategy.onBar(i, bar, ctx);
   // 2. 执行（仅更新内存）
   ctx.execute(action, bar);
   // 3. 每日快照
   if (isDayEnd(bar)) ctx.recordDailyEquity(bar.getEndTime());
   }
   }
   }
7. 持久化门面设计（性能核心）
   7.1 接口定义
   java
   public interface PersistenceGateway {
   void recordOpen(OpenOrder order);
   void recordClose(CloseTrade trade);
   void flush(List<OpenOrder> opens, List<CloseTrade> closes, List<EquityPoint> equities);
   }
   7.2 回测实现（批量落库）
   java
   @Profile("backtest")
   @Component
   public class BacktestBatchGateway implements PersistenceGateway {
   private final List<OpenOrder> openBuffer = new ArrayList<>();
   private final List<CloseTrade> closeBuffer = new ArrayList<>();

   @Override
   public void recordOpen(OpenOrder order) {
   openBuffer.add(order); // 纯内存，纳秒级
   }

   @Override
   @Transactional
   public void flush(List<OpenOrder> opens, List<CloseTrade> closes, List<EquityPoint> equities) {
   openOrderRepo.saveAll(openBuffer);
   closeTradeRepo.saveAll(closeBuffer);
   equityRepo.saveAll(equities);
   openBuffer.clear();
   closeBuffer.clear();
   }
   }
   7.3 实盘实现（异步落库）
   java
   @Profile("live")
   @Component
   public class LiveAsyncGateway implements PersistenceGateway {
   @Override
   @Async("tradeExecutor")
   public void recordOpen(OpenOrder order) {
   openOrderRepo.save(order);
   updatePosition(order); // 实时更新仓位表
   }
   // flush 为空操作
   }
8. TA4J 整合方案（0.22.6 版本）
   8.1 Maven 依赖
   xml
   <dependency>
   <groupId>org.ta4j</groupId>
   <artifactId>ta4j-core</artifactId>
   <version>0.22.6</version>
   </dependency>
   8.2 指标工具类封装
   java
   @Component
   public class Ta4jIndicatorHelper {
   public double ema(BarSeries series, int index, int barCount) {
   return new EMAIndicator(new ClosePriceIndicator(series), barCount)
   .getValue(index).doubleValue();
   }

   public double rsi(BarSeries series, int index, int barCount) {
   return new RSIIndicator(new ClosePriceIndicator(series), barCount)
   .getValue(index).doubleValue();
   }

   // 在规则中直接使用 TA4J 的 Indicator 对象
   public Indicator getClosePrice(BarSeries series) {
   return new ClosePriceIndicator(series);
   }
   }
   注意：TA4J 0.22.6 要求 Java 25+，请确认 JDK 版本兼容性。

9. 性能优化策略
   优化点	实现策略	预期收益
   零 I/O 循环	for 循环中仅内存操作，禁止 JPA.save()	消除 99% 延迟
   批量写入	JPA.saveAll() + jdbc.batch_size=1000	减少网络往返
   内存数据库	回测 Profile 切换至 H2 mem 模式	磁盘 I/O 归零
   规则缓存	将规则内的指标计算结果缓存（如 CacheMap）	避免重复计算
   并行优化	参数优化时使用 ForkJoinPool，独立 Context	充分利用多核
   对象复用	Action 枚举单例；OrderResult 池化	减少 GC 压力
   配置文件示例（application-backtest.yml）
   yaml
   spring:
   datasource:
   url: jdbc:h2:mem:backtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
   jpa:
   properties:
   hibernate.jdbc.batch_size: 1000
   hibernate.order_inserts: true
   hibernate.order_updates: true
10. 目录结构与模块划分
    text
    src/main/java/com/xxx/quant/
    ├── api/                              # 控制器层
    │   ├── BacktestController.java
    │   └── LiveTradeController.java
    ├── core/                             # 核心领域
    │   ├── model/
    │   │   ├── enums/                    # Action, Direction, CloseReason
    │   │   ├── entity/                   # OpenOrder, CloseTrade, Position, BacktestJob
    │   │   └── dto/                      # BacktestRequest, BacktestReport, OrderResult
    │   ├── context/                      # 上下文（核心）
    │   │   ├── TradingContext.java
    │   │   ├── MemoryPosition.java
    │   │   └── PortfolioManager.java
    │   └── engine/                       # 引擎
    │       ├── BaseEngine.java
    │       ├── BacktestEngine.java
    │       └── LiveEngine.java
    ├── strategy/                         # 策略层
    │   ├── ScriptStrategy.java
    │   ├── StrategyRegistry.java
    │   └── impl/
    │       ├── DualMaStrategy.java
    │       └── RuleBasedStrategy.java    # 基于规则组合的策略
    ├── rules/                            # 规则引擎（借鉴 TA4J 规约模式）
    │   ├── TradingRule.java
    │   ├── base/                         # 基础规则
    │   │   ├── CrossedUpIndicatorRule.java
    │   │   ├── CrossedDownIndicatorRule.java
    │   │   ├── OverIndicatorRule.java
    │   │   ├── UnderIndicatorRule.java
    │   │   ├── StopLossRule.java
    │   │   ├── HasLongPositionRule.java
    │   │   └── HasShortPositionRule.java
    │   └── composite/                    # 逻辑组合规则
    │       ├── AndRule.java
    │       ├── OrRule.java
    │       ├── NotRule.java
    │       ├── XorRule.java
    │       └── VoteRule.java
    ├── infrastructure/                   # 基础设施
    │   ├── indicator/                    # TA4J 工具封装
    │   │   └── Ta4jIndicatorHelper.java
    │   ├── data/                         # 数据加载
    │   │   └── DataLoader.java
    │   ├── persistence/                  # 持久化门面（关键性能层）
    │   │   ├── PersistenceGateway.java
    │   │   ├── BacktestBatchGateway.java
    │   │   └── LiveAsyncGateway.java
    │   └── broker/                       # 交易所适配
    │       └── ExchangeAdapter.java
    ├── service/                          # 业务服务（实盘专用）
    │   ├── TradeBusinessService.java
    │   └── RiskManager.java
    └── config/                           # Spring 配置
    ├── AsyncConfig.java
    └── ProfileConfig.java
11. 核心流程图
    11.1 回测完整时序图
    text
    User -> BacktestFacade: 发起回测请求
    BacktestFacade -> BacktestEngine: run(request)
    BacktestEngine -> DataLoader: loadData(symbol, dateRange)
    DataLoader --> BacktestEngine: BarSeries (内存)
    BacktestEngine -> TradingContext: new Context(initCash)
    BacktestEngine -> StrategyRegistry: getStrategy(id)
    StrategyRegistry --> BacktestEngine: ScriptStrategy

loop 遍历 BarSeries (i=0 to N)
BacktestEngine -> ScriptStrategy: onBar(i, bar, context)
ScriptStrategy -> TradingRule: isSatisfied(i, series, context)
TradingRule -> Ta4jIndicatorHelper: 计算指标值
Ta4jIndicatorHelper --> TradingRule: double
TradingRule --> ScriptStrategy: true/false
ScriptStrategy --> BacktestEngine: Action (OPEN_LONG)
BacktestEngine -> TradingContext: execute(action, bar)
TradingContext -> MemoryPosition: 更新数量/均价
TradingContext -> BacktestBatchGateway: recordOpen(order)
BacktestBatchGateway -> openBuffer: add(order) (内存)
TradingContext --> BacktestEngine: OrderResult
alt 日线收盘
BacktestEngine -> TradingContext: recordDailyEquity(date)
end
end

BacktestEngine -> BacktestBatchGateway: flush(opens, closes, equities)
BacktestBatchGateway -> OpenOrderRepo: saveAll(opens) [批量]
BacktestBatchGateway -> CloseTradeRepo: saveAll(closes) [批量]
BacktestBatchGateway -> EquityRepo: saveAll(equities) [批量]
BacktestEngine -> BacktestFacade: BacktestReport
BacktestFacade --> User: 返回报告
11.2 实盘简略时序
text
Exchange WS -> LiveEngine: onBar(bar)
LiveEngine -> ScriptStrategy: onBar(index, bar, context)
ScriptStrategy -> TradingRule: isSatisfied(...)
TradingRule --> ScriptStrategy: true
ScriptStrategy --> LiveEngine: Action (OPEN_LONG)
LiveEngine -> LiveAsyncGateway: recordOpen(order)
LiveAsyncGateway --(@Async)--> OpenOrderRepo: save(order) [异步]
LiveEngine -> Exchange API: placeOrder(order) [实时下单]
12. 设计原则与约束
    策略确定性：回测策略必须完全确定性（相同输入 → 相同输出），严禁依赖外部状态（如系统时间、随机数）。

循环内无 I/O：回测主循环内绝对禁止任何数据库或网络 I/O。

Profile 隔离：回测与实盘通过 @Profile("backtest") 和 @Profile("live") 严格隔离，避免数据污染。

单一职责：策略只做决策；引擎只做驱动；上下文只做状态管理；规则只做条件判断。

线程安全：实盘场景下，TradingContext 需使用 ConcurrentHashMap 保证并发安全。

13. 实施路径（分阶段交付）
    阶段	任务	产出物	预估工时
    Phase 1	定义核心模型（Entity、Enum、DTO）	core/model/ 包完成	2天
    Phase 2	实现 TradingContext 内存账本 + 双向持仓逻辑	core/context/ 包	3天
    Phase 3	实现规则引擎（TradingRule + 基础规则 + 组合规则）	rules/ 包完成	3天
    Phase 4	实现 BacktestEngine 的 for 循环骨架 + 数据加载	core/engine/ + infrastructure/data/	2天
    Phase 5	实现 BacktestBatchGateway + flush 批量写入	infrastructure/persistence/	2天
    Phase 6	封装 Ta4jIndicatorHelper 工具类	infrastructure/indicator/	1天
    Phase 7	实现示例策略（双均线 + 规则组合）	strategy/impl/	2天
    Phase 8	集成测试 + 性能调优（批量大小、缓存策略）	测试报告	2天
    Phase 9	实现 LiveEngine + 实盘异步持久化	LiveEngine + LiveAsyncGateway	3天
14. 风险与应对
    风险	应对措施
    TA4J 0.22.6 需要 Java 25+	确认 JDK 版本，或降级至 0.15+ 版本（兼容 Java 8/11）
    回测内存溢出（海量数据）	使用批处理分页加载，或采用 List.subList() 分段回测
    实盘并发竞争（同时多个策略操作同一标的）	使用 Redis 分布式锁，保证同一标的串行执行
    规则组合过多导致性能下降	增加规则缓存（Map<String, Boolean> cache），按 (index, series.hash) 缓存结果
15. 总结
    本设计方案通过 QuantDinger 的架构思想 + TA4J 的规则模式（规约模式） + Spring Boot 的 Profile 与异步能力，实现了：

✅ 双向持仓原生支持：通过 (Symbol, Direction) 复合键管理仓位，多空共存。

✅ 规则驱动策略：将交易条件拆解为可组合的规则单元，策略代码高度声明式、可维护。

✅ 高性能回测：循环内零 I/O，批量落库，速度提升 100~1000 倍。

✅ 双模归一：同一套策略代码无缝运行于回测和实盘。

✅ TA4J 整合：仅保留指标计算能力，完全抛弃其策略/回测框架。

文档结束。如需代码层面的具体实现细节（如 OrderResult 与 EquityPoint 的完整字段定义，或 VoteRule 投票规则的实现），可随时提出。