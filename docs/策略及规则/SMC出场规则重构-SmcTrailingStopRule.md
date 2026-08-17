SMC 出场规则重构 — SmcTrailingStopRule 独立设计文档（v4.1 最终版）
1. 背景与目标
   1.1 现状分析
   当前 SmcDynamicExitRule（741 行）承担了过多职责，是一个典型的"上帝类"：

职责	是否独立	说明
SMC 结构止损（订单块底/溢价区底 → 止损价）	✅ 内聚	calculateBaseStop() + adjustStopForOrderBlockTest()
SMC 结构止盈（多周期订单块目标 → 目标价）	✅ 内聚	buildTargetList() + selectNearestTarget()
初始止损偏移	✅ 内聚	applyInitialStopOffset()
信号质量评分	✅ 内聚	calculateSignalQuality() + tightenStopByQuality()
移动止损（Trailing Stop）	❌ 虚假实现	仅有 useTrailingStop / trailingStopPercent 字段，getSignal() 中没有任何 trailing stop 执行逻辑
1.2 问题
Trailing stop 配置了但实际未生效 — 字段存在但 getSignal() 从未检查 trailing stop 是否触发

违反"单一职责" — 止损、止盈、移动止损、信号质量全混在一个类中

v4.0 设计文档要求的手动模式（保守/中等/激进三挡位）无法在当前架构中干净实现

与现有 DirectionalTrailingStopRule 风格不一致 — 后者是纯粹百分比回撤，不支持 SMC 结构跟踪

缺少移动止盈的同步逻辑 — 当前只考虑止损移动，未将止盈与结构变化联动

1.3 目标
text
SmcDynamicExitRule (重构前 741 行)                     SmcDynamicExitRule (重构后 ~450 行)
├─ SMC结构止损                                           ├─ SMC结构止损
├─ SMC结构止盈                                           ├─ SMC结构止盈
├─ 初始止损偏移                                          ├─ 初始止损偏移
├─ 信号质量评分                                          └─ 信号质量评分
├─ 移动止损配置(虚假字段)
└─ 数据库/指标加载
SmcTrailingStopRule (新建 ~400 行)
拆分 ──────────────────────────►      ├─ 自动模式(基于 CompositeState 自动选择)
├─ 手动模式(保守/中等/激进三挡位)
├─ SMC结构点跟踪(摆动点/内部结构点)
├─ 多周期切换(15M/1H/4H)
├─ 止损与止盈同步移动(跟随HL/LH + HH/LL)
└─ updateStopLoss() 只向有利方向移动
2. 模块定位
   与项目现有规则体系一致：

text
ai-extension (ta4j 扩展库)
├─ core/constants/
│  └─ ExitType.java                 (出场类型枚举 ← 新增 SMC_TRAILING_STOP)
├─ core/rule/
│  ├─ DirectionalStopLossRule.java        (固定止损)
│  ├─ DirectionalStopGainRule.java        (固定止盈)
│  ├─ DirectionalTrailingStopRule.java    (普通百分比移动止损)
│  ├─ DirectionalTimeBasedStopLossRule.java
│  ├─ SmcDynamicExitRule.java             (SMC结构止损止盈 ← 精简后)
│  ├─ SmcTrailingStopRule.java            (SMC结构跟踪移动止损 ← 新建)
│  ├─ ExitSignal.java                     (信号对象)
│  └─ DirectionalRule.java                (接口)
3. SmcTrailingStopRule 设计
   3.1 职责定义
   在持仓过程中，根据 SMC 结构点（摆动点或内部订单块）同步移动止损和止盈，让利润奔跑。支持自动模式和手动模式。

核心逻辑：

止损（SL）：跟踪同向结构（做多跟踪 HL，做空跟踪 LH），只向有利方向移动

止盈（TP）：跟踪反向结构（做多跟踪 HH/供应区，做空跟踪 LL/需求区），只向有利方向移动

同步原则：止损和止盈基于同一套最新结构数据同时重新计算，但不要求同时移位（HL 更新时移 SL，HH 更新时移 TP）

不承担职责（由 SmcDynamicExitRule 保留）：

入场时的初始止损偏移

基于订单块的固定止损价计算

多周期目标价止盈

信号质量评分

3.2 出场类型标识
在 ExitType.java 中新增枚举值：

java
SMC_TRAILING_STOP("SMC结构跟踪止损"),   // SmcTrailingStopRule 触发的移动止损
与 TRAILING_STOP_LOSS（普通百分比移动止损）区分，用于：

报表统计：分清出场是来自普通移动止损还是 SMC 结构跟踪

回测分析：对比两种规则的触发频率和盈亏效果

shouldClearPosition()：与 TRAILING_STOP_LOSS 行为一致，返回 true（清仓）

3.3 配置数据结构
java
public class SmcTrailingStopConfig {
private boolean enabled;                    // 是否启用
private TrailingMode mode;                  // AUTO / MANUAL
private ManualGear manualGear;              // 手动模式挡位
private double autoTrailingPercent;         // 自动模式回撤阈值（兜底）
private boolean syncTakeProfit;             // 是否同步移动止盈（默认 true）
private String entryLevel;                  // 入场级别："15M"/"1H"/"4H"
}

public enum TrailingMode {
AUTO,       // 自动模式：根据 CompositeState 自动选择周期和点类型
MANUAL      // 手动模式：用户预设挡位
}

public enum ManualGear {
CONSERVATIVE,   // 保守：4H 摆动点，偏移 0.3%
MODERATE,       // 中等：1H 内部点，偏移 0.2%
AGGRESSIVE      // 激进：15M 内部点，偏移 0.1%
}
3.4 自动模式规则表
根据 CompositeState 自动选择跟踪参数（与 v4.0 设计文档第 6.4.1 节一致）：

CompositeState	启用	止损跟踪周期	止损点类型	止盈跟踪周期	止盈点类型
STRONG_BULLISH_HEALTHY	✅	15M	摆动点	1H	前高/供应区
STRONG_BULLISH_SHALLOW_PULLBACK	✅	15M	内部结构点	1H	前高/供应区
STRONG_BULLISH_WARNING_1H	✅	1H	内部结构点	15M	强高点（快进快出）
STRONG_BULLISH_WARNING_4H	✅	1H	内部结构点	15M	强高点（快进快出）
STRONG_BULLISH_CONFIRMED_PULLBACK	❌	—	—	—	—
BULLISH_PULLBACK_ONGOING	❌	—	—	—	—
BULLISH_PULLBACK_BOTTOMING（有持仓时）	✅	15M	摆动点	1H	前高
BULLISH_PULLBACK_FAILURE	❌	—	—	—	—
BULLISH_ENDING_CONTINUE_DOWN	❌	—	—	—	—
BULLISH_ENDING_CONFIRM（谨慎）	✅	4H	摆动点	4H	前高
STRONG_BEARISH_HEALTHY	✅	15M	摆动点	1H	前低/需求区
STRONG_BEARISH_SHALLOW_BOUNCE	✅	15M	内部结构点	1H	前低/需求区
STRONG_BEARISH_WARNING_1H	✅	1H	内部结构点	15M	强低点（快进快出）
STRONG_BEARISH_WARNING_4H	✅	1H	内部结构点	15M	强低点（快进快出）
STRONG_BEARISH_CONFIRMED_BOUNCE	❌	—	—	—	—
BEARISH_PULLBACK_ONGOING	❌	—	—	—	—
BEARISH_PULLBACK_TOPPING（有持仓时）	✅	15M	摆动点	1H	前低
BEARISH_PULLBACK_FAILURE	❌	—	—	—	—
BEARISH_ENDING_CONTINUE_UP	❌	—	—	—	—
BEARISH_ENDING_CONFIRM（谨慎）	✅	4H	摆动点	4H	前低
RANGING_NO_DIRECTION	❌	—	—	—	—
RANGING_RANGE_BOUND	❌	—	—	—	—
3.5 核心算法
java
public class SmcTrailingStopRule implements DirectionalRule {

    @Override
    public ExitSignal getSignal(int index, TradingRecord tradingRecord) {
        // 1. 无持仓 → 重置状态，返回 null
        // 2. 解析当前 CompositeState（从缓存或参数中获取）
        // 3. 根据模式选择跟踪参数：
        //    - 自动模式：autoSelectParams(compositeState)
        //    - 手动模式：manualSelectParams(manualGear)
        // 4. 若当前状态不允许跟踪 → 返回 null
        // 5. 获取跟踪结构点的价格：
        //    a) 止损锚点：做多用 HL/内部低点，做空用 LH/内部高点
        //    b) 止盈锚点：做多用 HH/供应区，做空用 LL/需求区
        // 6. 计算理论止损价 = 结构点价格 × (1 ± 偏移量)
        // 7. 更新实际止损价（多头只上移，空头只下移）
        // 8. 计算理论止盈价 = 结构点价格（或固定偏移）
        // 9. 更新实际止盈价（多头只上移，空头只下移）
        // 10. 当前价格触发止损或止盈 → 返回 ExitSignal(...)
        // 11. 否则 → 返回 null
    }

    // 自动模式参数选择
    private TrailingParams autoSelectParams(CompositeState state) { ... }

    // 手动模式参数选择
    private TrailingParams manualSelectParams(ManualGear gear) { ... }

    // 获取 SMC 结构跟踪点价格
    private Double getStructuralPoint(
            SmartMoneyConceptsIndicator.Result result,
            PointType pointType,
            TradeType direction,
            AnchorType anchorType) { 
        // anchorType: STOP_LOSS 或 TAKE_PROFIT
        // SL: 做多取最低点，做空取最高点
        // TP: 做多取最高点/供应区下沿，做空取最低点/需求区上沿
    }
}
3.6 移动触发条件（结构性移动）
方向	止损（SL）移动条件	止盈（TP）移动条件
做多（BUY）	必须出现新的、被确认的 HL（更高的低点）	必须出现新的 HH（更高的高点） 或接近新的供应区
做空（SELL）	必须出现新的、被确认的 LH（更低的高点）	必须出现新的 LL（更低的低点） 或接近新的需求区
核心规则：没有新的 HL/LH，止损绝不移；没有新的 HH/LL，止盈绝不移。价格可以一路涨/跌不回头，但只要没有形成新的结构点，止损/止盈就保持不动。

3.7 主动保本（风险管理规则，非结构规则）
与结构性移动并行存在，但不依赖结构变化：

触发条件	做多动作	做空动作
浮盈 ≥ 初始风险 R 的 1.5 倍	SL 移至 入场价 + 0.1%	SL 移至 入场价 - 0.1%
浮盈 ≥ 初始风险 R 的 2 倍	SL 移至 入场价 + 0.2%	SL 移至 入场价 - 0.2%
目的：在结构确认之前提前锁住利润，防止浮盈变浮亏。尤其适用于日内短线。

3.8 止损与止盈的同步逻辑
两个规则相互独立又相互约束：

独立性：各自的移动条件不同（SL 看 HL/LH，TP 看 HH/LL），触发时机不同

约束性：TP 永远不能低于当前价格（做多）或高于当前价格（做空），即确保 TP 始终在场外

text
做多同步约束：
SL ≤ 当前价格 ≤ TP （SL 在下方，TP 在上方）
新 SL 必须 > 旧 SL（只上移）
新 TP 必须 > 旧 TP（只上移）

做空同步约束：
TP ≤ 当前价格 ≤ SL （TP 在上方？不对）
做空：TP 在下方，SL 在上方
新 SL 必须 < 旧 SL（只下移）
新 TP 必须 < 旧 TP（只下移）
4. SmcDynamicExitRule 重构
   4.1 移除内容
   移除项	说明
   useTrailingStop 字段	移至 SmcTrailingStopRule
   trailingStopPercent 字段	移至 SmcTrailingStopRule
   TrailingStopMode / ManualGear 相关	移至 SmcTrailingStopRule
   tightenStopByQuality()	保留（与信号质量相关）
   无实际调用的 trailing stop 检查代码	删除
   4.2 保留内容
   calculateBaseStop() — 基于折价区底/溢价区顶的基础止损

adjustStopForOrderBlockTest() — 订单块被测试后收紧止损

applyInitialStopOffset() — 初始止损偏移

calculateSignalQuality() — 信号质量评分

buildTargetList() + selectNearestTarget() — 多周期目标止盈

getCachedSmcResult() + 数据库加载 — SMC 指标缓存和加载

5. DefaultDealStrategyTrade.execStrategy 改造
   5.1 当前代码（需改造位置）
   DefaultDealStrategyTrade.java#L457-L529

5.2 改造后逻辑
java
// 1. 创建 SMC 结构止损止盈规则（精简版）
SmcDynamicExitRule smcExitLong = new SmcDynamicExitRule(smcMap, Trade.TradeType.BUY);
SmcDynamicExitRule smcExitShort = new SmcDynamicExitRule(smcMap, Trade.TradeType.SELL);
// ... 配置 useTargets / useStructureBreak / initialStopOffset 等 ...
exitRules.add(smcExitLong);
exitRules.add(smcExitShort);

// 2. 创建 SMC 结构跟踪移动止损规则（新增）
if (Boolean.TRUE.equals(config.smcUseTrailingStop)) {
SmcTrailingStopRule smcTrailingLong = new SmcTrailingStopRule(
smcMap, Trade.TradeType.BUY, currentSeries);
SmcTrailingStopRule smcTrailingShort = new SmcTrailingStopRule(
smcMap, Trade.TradeType.SELL, currentSeries);

    // 读取模式配置（从 smcExitJson 或 tp 配置）
    TrailingMode mode = resolveTrailingMode(config);
    smcTrailingLong.setMode(mode);
    smcTrailingShort.setMode(mode);

    if (mode == TrailingMode.MANUAL) {
        ManualGear gear = resolveManualGear(config);
        smcTrailingLong.setManualGear(gear);
        smcTrailingShort.setManualGear(gear);
    } else {
        // 自动模式: 基于 CompositeState 自动选择
        smcTrailingLong.setAutoTrailingPercent(config.smcTrailingStopPercent);
        smcTrailingShort.setAutoTrailingPercent(config.smcTrailingStopPercent);
    }

    // 同步移动止盈开关（默认开启）
    smcTrailingLong.setSyncTakeProfit(config.smcSyncTakeProfit != null ? config.smcSyncTakeProfit : true);
    smcTrailingShort.setSyncTakeProfit(config.smcSyncTakeProfit != null ? config.smcSyncTakeProfit : true);

    exitRules.add(smcTrailingLong);
    exitRules.add(smcTrailingShort);
}
5.3 smcExitJson 配置扩展
json
{
"trailingStop": {
"enabled": true,
"mode": "manual",
"manualLevel": "moderate",
"syncTakeProfit": true,
"entryLevel": "15M"
}
}
6. 前端配置 UI 更新
   6.1 当前 UI（EditStrategy.vue）
   已有 trailing stop 配置区域（breakeven 倍数 + 跟踪结构低点/高点），需在 SMC 出场规则中增加模式选择和挡位选择。

6.2 新增 UI 元素
text
┌─────────────────────────────────────────────────────────────────────┐
│  ☑ SMC 结构跟踪移动止损                                            │
│                                                                   │
│  模式：  ● 自动    ○ 手动                                         │
│  [自动模式提示] 根据市场状态自动调整周期和点类型                    │
│                                                                   │
│  手动模式：                                                        │
│  挡位：  ○ 保守（4H摆动）  ● 中等（1H内部）  ○ 激进（15M内部）   │
│                                                                   │
│  ☑ 同步移动止盈（止损上移时止盈同步上移）                          │
│                                                                   │
│  入场级别：  [15分钟] [1小时] [4小时]                             │
│  （用于确定结构跟踪的基准周期）                                     │
└─────────────────────────────────────────────────────────────────────┘
7. 模块依赖关系
   text
   SmcTrailingStopRule
   ├─ 依赖 CompositeState (ai-common)
   ├─ 依赖 SmartMoneyConceptsIndicator.Result (ai-extension)
   ├─ 依赖 ExitSignal / DirectionalRule (ai-extension)
   └─ 依赖 BarSeries (ta4j)

SmcDynamicExitRule (重构后)
├─ 依赖 CompositeState (ai-common)       ← 新增依赖
├─ 依赖 SmartMoneyConceptsIndicator.Result (ai-extension)
├─ 依赖 ExitSignal / DirectionalRule (ai-extension)
└─ 依赖 BarSeries (ta4j)
两个规则互不依赖，各自实现 DirectionalRule 接口，通过 OrDirectionalRule 组合。

8. 测试要点
   单元测试：SmcTrailingStopRule 的手动模式三挡位映射正确

单元测试：自动模式下每个 CompositeState 映射到正确的 TrailingParams

单元测试：止损价只向有利方向移动（多头只上移，空头只下移）

单元测试：止盈价只向有利方向移动（多头只上移，空头只下移）

单元测试：结构点失效（实体穿透）后停止跟踪

单元测试：同步移动止盈开关关闭时，止盈不随结构移动

集成测试：execStrategy 中两条规则通过 OrDirectionalRule 组合后正确触发

9. 注意事项
   CompositeState 推导引擎 — SmcTrailingStopRule 自动模式依赖 CompositeState，在推导引擎完成前，自动模式可降级为使用 TrendType 或直接使用手动模式

与 DirectionalTrailingStopRule 的共存 — 两者用途不同：前者是 SMC 结构跟踪（依赖指标结果），后者是简单的价格极值百分比回撤，不冲突

SignalQuality 评分与移动止损的关系 — 重构后 SignalQuality 评分保留在 SmcDynamicExitRule 中，SmcTrailingStopRule 通过 CompositeState 间接获得质量信号

分批出场与全仓模式 — 本规则支持全仓模式（单次出场）和分批模式（配合 SmcDynamicExitRule 的止盈目标），通过 syncTakeProfit 控制是否同步移动止盈

10. 版本记录
    版本	日期	修改内容
    2.1	2026-06-06	新增 ExitType.SMC_TRAILING_STOP 出场类型标识
    2.0	2026-06-06	新增 DirectionalTrailingStopRule 完整性补全方案
    1.0	2026-06-06	初版，定义 SmcTrailingStopRule 拆分方案
    4.1	2026-06-17	新增止损与止盈同步移动逻辑；明确结构性移动触发条件（仅在新HL/LH确认后移损，新HH/LL确认后移盈）；新增主动保本规则；新增全仓模式支持；补充日内短线周期选择策略