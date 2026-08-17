package com.chain.ai.trade.engine.signal.rule;

import com.chain.ai.trade.common.entity.constants.CompositeState;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.signal.rule.IndicatorMetadata.ParamDef;
import com.chain.ai.trade.engine.signal.rule.IndicatorMetadata.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Component
public class BuiltInIndicatorProvider implements IndicatorProvider {

    private static final Logger log = LoggerFactory.getLogger(BuiltInIndicatorProvider.class);

    private static final String CATEGORY_SMC = "SMC";
    private static final String CATEGORY_TECH = "TECHNICAL";
    private static final String CATEGORY_PRICE = "PRICE";
    private static final String CATEGORY_TIME = "TIME";
    private static final String CATEGORY_STATE = "STATE";

    private final Map<String, IndicatorHandler> handlers = new HashMap<>();
    private final Map<String, IndicatorMetadata> metadatas = new HashMap<>();

    @Autowired(required = false)
    private ICandlestickService candlestickService;

    public BuiltInIndicatorProvider() {
        registerAll();
    }

    private void registerAll() {
        // ==================== SMC 指标（数值型） ====================
        register("SMC_POSITION_SCORE", "SMC 位置评分", CATEGORY_SMC, "SMC 当前位置评分", "SCORE", 0.0, 100.0, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getSmcPositionScore()));
        register("SMC_NET_RR", "SMC 净盈亏比", CATEGORY_SMC, "SMC 净盈亏比", "RATIO", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getSmcNetRR()));
        register("SMC_POSITION_SCORE_15M", "SMC 15m位置评分", CATEGORY_SMC, "SMC 15分钟级别位置评分", "SCORE", 0.0, 100.0, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getSmcPositionScore15m()));
        register("SMC_IN_SUPPLY_ZONE", "SMC 是否在供应区", CATEGORY_SMC, "价格是否在供应区", "BOOLEAN", 0.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmcInSupplyZone()));
        register("SMC_IN_DEMAND_ZONE", "SMC 是否在需求区", CATEGORY_SMC, "价格是否在需求区", "BOOLEAN", 0.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmcInDemandZone()));
        register("SMC_RISK_PERCENT", "SMC 风险百分比", CATEGORY_SMC, "SMC 风险占当前价格百分比", "RATIO", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getSmcRiskPercent()));
        register("SMC_MARKET_TREND", "市场整体趋势", CATEGORY_SMC, "SMC复合趋势状态", "STATE", 0.0, null, false, null,
                (ctx, p) -> {
                    CompositeState st = ctx.getTrendState();
                    String actual = st != null ? st.name() : null;
                    if (actual == null) return IndicatorValue.of(0.0);
                    if (p != null && p.containsKey("categoryValue")) {
                        return IndicatorValue.of(actual.equals(p.get("categoryValue")) ? 1.0 : 0.0);
                    }
                    return IndicatorValue.of(actual.contains("BULLISH") || actual.contains("RISE") ? 1.0 : 0.0);
                });

        // ==================== SMC 补充指标（执行清单 §5.1~5.3） ====================
        register("SMC_INTERNAL_BOS_ALIGNED", "SMC 内部BOS一致", CATEGORY_SMC, "15M内部BOS方向是否一致", "BOOLEAN", 0.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmcInternalBosAligned()));
        register("SMC_CHAOS_EXCEPTION", "SMC 混沌特例", CATEGORY_SMC, "混沌特例是否触发", "BOOLEAN", 0.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmcChaosException()));

        // ==================== SMC 补充指标（§5 规则完整集） ====================
        register("SMC_RANGE_PERCENT_20H", "SMC 20小时振幅%", CATEGORY_SMC, "20小时价格振幅百分比", "RATIO", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getSmcRangePercent20h()));
        register("SMC_FLIP_COUNT", "SMC 翻转次数", CATEGORY_SMC, "结构方向翻转次数", "ABSOLUTE", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmcFlipCount()));
        register("SMC_1H_POSITION_RATIO", "SMC 1H位置比率", CATEGORY_SMC, "价格在1H结构中的位置比率(0~1)", "RATIO", 0.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getSmc1hPositionRatio()));
        register("SMC_4H_POSITION_RATIO", "SMC 4H位置比率", CATEGORY_SMC, "价格在4H结构中的位置比率(0~1)", "RATIO", 0.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getSmc4hPositionRatio()));
        register("SMC_4H_WAVE", "SMC 4H波次", CATEGORY_SMC, "4H级别波次（2=主升,3=加速,-2=主跌,-3=加速）", "ABSOLUTE", -5.0, 5.0, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmc4hWave()));
        register("SMC_1H_WAVE", "SMC 1H波次", CATEGORY_SMC, "1H级别波次（2=主升,3=加速,-2=主跌,-3=加速）", "ABSOLUTE", -5.0, 5.0, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmc1hWave()));
        register("SMC_4H_AGE", "SMC 4H结构年龄", CATEGORY_SMC, "4H结构持续条数，用于衰老判断", "ABSOLUTE", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmc4hAge()));
        register("SMC_1H_AGE", "SMC 1H结构年龄", CATEGORY_SMC, "1H结构持续条数，用于新鲜/老化判断", "ABSOLUTE", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmc1hAge()));
        register("SMC_HL_HEALTH", "SMC HL健康度", CATEGORY_SMC, "多头结构健康度（1=健康,0=未知,-1=危险,-2=损坏）", "ABSOLUTE", -2.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmcHlHealth()));
        register("SMC_LH_HEALTH", "SMC LH健康度", CATEGORY_SMC, "空头结构健康度（1=健康,0=未知,-1=危险,-2=损坏）", "ABSOLUTE", -2.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmcLhHealth()));
        register("SMC_RISK_REWARD_RATIO", "SMC 盈亏比", CATEGORY_SMC, "SMC 盈亏比", "RATIO", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getSmcRiskRewardRatio()));
        register("SMC_POSITION_MARGIN_PERCENT", "SMC 仓位占比%", CATEGORY_SMC, "仓位保证金占账户百分比", "RATIO", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getSmcPositionMarginPercent()));
        register("SMC_DIRECTION_ALIGNED", "SMC 方向一致", CATEGORY_SMC, "多周期方向是否一致", "BOOLEAN", 0.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getSmcDirectionAligned()));
        register("SMC_ALIGNMENT", "信号共振", CATEGORY_SMC, "信号方向与多周期共振方向是否一致（顺势做多/顺势做空/逆势/方向分歧）",
                "STATE", 0.0, null, false, null,
                (ctx, p) -> {
                    String actual = ctx.getSmcAlignment();
                    if (actual == null) return IndicatorValue.of(0.0);
                    if (p != null && p.containsKey("categoryValue")) {
                        return IndicatorValue.of(actual.equals(p.get("categoryValue")) ? 1.0 : 0.0);
                    }
                    return IndicatorValue.of(("顺势做多".equals(actual) || "顺势做空".equals(actual)) ? 1.0 : 0.0);
                });

        // ==================== 技术指标 ====================
        register("EMA_RATIO", "EMA 均线比值", CATEGORY_TECH, "快慢线比值", "RATIO", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getEmaRatio()));
        register("MACD", "MACD", CATEGORY_TECH, "MACD指标（支持DIF/DEA/柱状图/柱体动能/金叉/死叉/顶底背离）",
                "ABSOLUTE", null, null, true,
                Arrays.asList(
                        ParamDef.builder().key("categoryValue").label("MACD模式").type("STRING").defaultValue("MACD_LINE")
                                .options(Arrays.asList("MACD_LINE", "MACD_SIGNAL", "MACD_HISTOGRAM", "HISTOGRAM_ADAPTIVE",
                                        "HISTOGRAM_MOMENTUM",
                                        "GOLDEN_CROSS", "DEATH_CROSS", "BULLISH_DIVERGENCE", "BEARISH_DIVERGENCE")).build(),
                        ParamDef.builder().key("fast_period").label("快线周期(12)").type("INTEGER").defaultValue("12").build(),
                        ParamDef.builder().key("slow_period").label("慢线周期(26)").type("INTEGER").defaultValue("26").build(),
                        ParamDef.builder().key("signal_period").label("信号周期(9)").type("INTEGER").defaultValue("9").build(),
                        ParamDef.builder().key("timeframe").label("K线周期(选填)").type("STRING").defaultValue("").build()
                ),
                (ctx, p) -> resolveMacd(ctx, p));
        register("VOLUME_RATIO", "成交量比值", CATEGORY_TECH, "当前均量比值", "RATIO", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getVolumeRatio()));
        register("VOLUME_TREND", "成交量趋势", CATEGORY_TECH, "成交量趋势方向", "STATE", 0.0, null, false, null,
                (ctx, p) -> {
                    String actual = ctx.getVolumeTrend();
                    if (actual == null) return IndicatorValue.of(0.0);
                    if (p != null && p.containsKey("categoryValue")) {
                        return IndicatorValue.of("INCREASING".equalsIgnoreCase(p.get("categoryValue")) ? 1.0 : 0.0);
                    }
                    return IndicatorValue.of("INCREASING".equalsIgnoreCase(actual) ? 1.0 : 0.0);
                });
        register("PRICE_POSITION", "价格位置", CATEGORY_TECH, "价格在布林带/通道中的位置", "RATIO", 0.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getPricePosition()));
        register("PATTERN_TYPE", "K线形态类型", CATEGORY_TECH, "K线形态类型", "STATE", 0.0, null, false, null,
                (ctx, p) -> {
                    Set<String> patterns = ctx.getDetectedPatterns();
                    if (patterns == null || patterns.isEmpty()) return IndicatorValue.of(0.0);
                    if (p != null && p.containsKey("categoryValues")) {
                        String[] vals = p.get("categoryValues").split(",");
                        for (String val : vals) {
                            if (patterns.contains(val.trim())) return IndicatorValue.of(1.0);
                        }
                        return IndicatorValue.of(0.0);
                    }
                    if (p != null && p.containsKey("categoryValue")) {
                        return IndicatorValue.of(patterns.contains(p.get("categoryValue")) ? 1.0 : 0.0);
                    }
                    return IndicatorValue.of(0.0);
                });

        // EMA多头排列：返回 BULLISH/BEARISH
        register("EMA_TREND", "EMA趋势", CATEGORY_TECH, "多头排列：FAST_EMA > SLOW_EMA > SIGNAL_EMA（SIGNAL_EMA为可选）",
                "ENUM", new String[]{"BULLISH", "BEARISH"}, null, null, true,
                Arrays.asList(
                        ParamDef.builder().key("fast_period").label("快线周期").type("INTEGER").build(),
                        ParamDef.builder().key("slow_period").label("慢线周期").type("INTEGER").build(),
                        ParamDef.builder().key("signal_period").label("信号周期").type("INTEGER").defaultValue("").build(),
                        ParamDef.builder().key("dataPeriod").label("K线周期(选填)").type("STRING").defaultValue("").build()
                ),
                (ctx, p) -> {
                    List<WeightRuleContext.CandlestickSnapshot> kLines;
                    String dataPeriod = p != null ? p.get("dataPeriod") : null;
                    if (dataPeriod != null && !dataPeriod.isEmpty()) {
                        kLines = ctx.getKLinesForPeriod(dataPeriod, candlestickService);
                    } else {
                        kLines = ctx.getKLines();
                    }
                    if (kLines == null || kLines.size() < 30) return IndicatorValue.of("BEARISH");

                    BarSeries series = buildSeriesFromSnapshots(kLines);
                    int idx = series.getEndIndex() - 1;
                    if (idx < series.getBeginIndex()) return IndicatorValue.of("BEARISH");

                    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
                    int fastPeriod = parseIntParam(p, "fast_period", 9);
                    int slowPeriod = parseIntParam(p, "slow_period", 21);
                    if (idx < slowPeriod) return IndicatorValue.of("BEARISH");

                    double fastEma = calcEma(series, closePrice, fastPeriod, idx);
                    double slowEma = calcEma(series, closePrice, slowPeriod, idx);

                    String signalPeriodStr = p != null ? p.get("signal_period") : null;
                    if (signalPeriodStr != null && !signalPeriodStr.isEmpty()) {
                        try {
                            int signalPeriod = Integer.parseInt(signalPeriodStr);
                            if (idx >= signalPeriod) {
                                double signalEma = calcEma(series, closePrice, signalPeriod, idx);
                                return IndicatorValue.of(fastEma > slowEma && slowEma > signalEma ? "BULLISH" : "BEARISH");
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    return IndicatorValue.of(fastEma > slowEma ? "BULLISH" : "BEARISH");
                });

        // PRICE_VS_EMA：返回 ABOVE/BELOW/EQUAL
        register("PRICE_VS_EMA", "跨周期EMA位置", CATEGORY_TECH,
                "当前收盘价与指定周期EMA的位置关系（ABOVE=收在EMA上方，BELOW=收在EMA下方）",
                "ENUM", new String[]{"ABOVE", "BELOW", "EQUAL"}, null, null, true,
                Arrays.asList(
                        ParamDef.builder().key("fast_period").label("EMA周期").type("INTEGER").build(),
                        ParamDef.builder().key("dataPeriod").label("K线周期").type("STRING").defaultValue("").build()
                ),
                (ctx, p) -> {
                    List<WeightRuleContext.CandlestickSnapshot> kLines;
                    String dataPeriod = p != null ? p.get("dataPeriod") : null;
                    if (dataPeriod != null && !dataPeriod.isEmpty()) {
                        kLines = ctx.getKLinesForPeriod(dataPeriod, candlestickService);
                    } else {
                        kLines = ctx.getKLines();
                    }
                    if (kLines == null || kLines.size() < 2) return IndicatorValue.of("EQUAL");

                    BarSeries series = buildSeriesFromSnapshots(kLines);
                    int idx = series.getEndIndex() - 1;
                    if (idx < series.getBeginIndex()) return IndicatorValue.of("EQUAL");

                    int emaPeriod = parseIntParam(p, "fast_period", 20);
                    if (idx < emaPeriod) return IndicatorValue.of("EQUAL");

                    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
                    double emaValue = calcEma(series, closePrice, emaPeriod, idx);
                    double close = closePrice.getValue(idx).doubleValue();

                    double diff = (close - emaValue) / emaValue;
                    if (diff > 0.001) return IndicatorValue.of("ABOVE");
                    if (diff < -0.001) return IndicatorValue.of("BELOW");
                    return IndicatorValue.of("EQUAL");
                });

        // ==================== 时间指标 ====================
        register("DAY_OF_WEEK", "星期几", CATEGORY_TIME, "当前星期几（1=周一～7=周日）", "ABSOLUTE", 1.0, 7.0, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getDayOfWeek()));
        register("HOUR_OF_DAY", "小时", CATEGORY_TIME, "当前小时（0-23）", "ABSOLUTE", 0.0, 23.0, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getHourOfDay()));
        register("IS_TRADING_SESSION", "是否在交易时段", CATEGORY_TIME, "当前是否在特定交易时段内", "BOOLEAN", 0.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.isTradingSession() ? 1.0 : 0.0));

        // ==================== L2 信号特征指标（执行清单 §5.3） ====================
        register("FEATURE_AVG_SPACE", "L2-平均空间", CATEGORY_TECH,
                "最近20笔交替的平均绝对空间（%）", "SCORE", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getAvgSpace()));
        register("FEATURE_CUM_RATIO", "L2-累积比", CATEGORY_TECH,
                "最近20笔的 cumRatio = SUM(space)/SUM(abs_space)", "SCORE", -1.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getCumRatio()));
        register("FEATURE_DIRECTION_SEQ", "L2-连续同向序列号", CATEGORY_TECH,
                "最近连续同向的交替笔数", "ABSOLUTE", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of((double) ctx.getDirectionSeq()));
        register("FEATURE_WAIT_MINUTES", "L2-等待分钟数", CATEGORY_TECH,
                "自上次信号以来的等待分钟数", "ABSOLUTE", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getWaitMinutes()));
        register("FEATURE_LAST_DIRECTION", "L2-上一笔方向", CATEGORY_TECH,
                "上一笔交替信号的方向（LONG/SHORT/NONE）", "STATE",
                new String[]{"LONG", "SHORT", "NONE"}, null, null, false, null,
                (ctx, p) -> {
                    String dir = ctx.getLastDirection();
                    return dir != null ? IndicatorValue.of(dir) : IndicatorValue.of("NONE");
                });
        register("FEATURE_LATEST_SPACE", "L2-上一笔空间", CATEGORY_TECH,
                "上一笔交替信号的绝对空间（%）", "SCORE", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getLatestSpace()));
        register("FEATURE_CURRENT_DIRECTION", "当前信号方向", CATEGORY_TECH,
                "当前待评估信号的方向", "STATE", new String[]{"LONG", "SHORT"}, null, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getCurrentDirection()));

        // ===== 分位数指标（供 debug/追踪用） =====
        register("FEATURE_PERCENTILE_20", "L2-P20", CATEGORY_TECH,
                "abs_space 的 20% 分位数", "SCORE", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getPercentile20()));
        register("FEATURE_PERCENTILE_95", "L2-P95", CATEGORY_TECH,
                "abs_space 的 95% 分位数", "SCORE", 0.0, null, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getPercentile95()));
        register("FEATURE_CUM_RATIO_P40", "L2-cumRatio P40", CATEGORY_TECH,
                "cumRatio 的 40% 分位数", "SCORE", -1.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getCumRatioPercentile40()));
        register("FEATURE_CUM_RATIO_P60", "L2-cumRatio P60", CATEGORY_TECH,
                "cumRatio 的 60% 分位数", "SCORE", -1.0, 1.0, false, null,
                (ctx, p) -> IndicatorValue.of(ctx.getCumRatioPercentile60()));
    }

    private void register(String id, String name, String category, String description,
                          String valueType, Double min, Double max, boolean hasParams, List<ParamDef> params,
                          BiFunction<WeightRuleContext, Map<String, String>, IndicatorValue> resolver) {
        register(id, name, category, description, valueType, null, min, max, hasParams, params, resolver);
    }

    private void register(String id, String name, String category, String description,
                          String valueType, String[] enumValues, Double min, Double max, boolean hasParams, List<ParamDef> params,
                          BiFunction<WeightRuleContext, Map<String, String>, IndicatorValue> resolver) {
        handlers.put(id, new IndicatorHandler(resolver));
        IndicatorMetadata.IndicatorMetadataBuilder builder = IndicatorMetadata.builder()
                .id(id).name(name).category(category).description(description)
                .valueType(valueType)
                .valueRange(min != null || max != null ? new ValueRange(min, max) : null)
                .hasParams(hasParams)
                .params(params != null ? params : Collections.emptyList())
                .operators(deriveOperators(valueType));
        if (enumValues != null) {
            builder.enumValues(Arrays.asList(enumValues));
        }
        metadatas.put(id, builder.build());
    }

    private List<String> deriveOperators(String valueType) {
        switch (valueType) {
            case "BOOLEAN":
            case "STATE":
                return Arrays.asList("IS", "EQ", "NEQ", "IN", "NOT_IN");
            case "SCORE":
            case "RATIO":
            case "ABSOLUTE":
                return Arrays.asList("GT", "GTE", "LT", "LTE", "EQ", "NEQ", "BETWEEN", "IN", "NOT_IN");
            default:
                return Arrays.asList("GT", "GTE", "LT", "LTE", "EQ", "NEQ");
        }
    }

    @Override
    public boolean supports(String indicatorType) {
        return handlers.containsKey(indicatorType);
    }

    @Override
    public IndicatorValue resolve(WeightRuleContext ctx, Map<String, String> params) {
        String indicator = params != null ? params.get("indicator") : null;
        if (indicator == null || !handlers.containsKey(indicator)) {
            return null;
        }
        try {
            return handlers.get(indicator).resolver.apply(ctx, params != null ? params : Collections.emptyMap());
        } catch (Exception e) {
            log.warn("指标计算异常: indicator={}, error={}", indicator, e.getMessage());
            return null;
        }
    }

    @Override
    public IndicatorMetadata getMetadata() {
        return null;
    }

    public IndicatorMetadata getMetadataFor(String indicator) {
        return metadatas.get(indicator);
    }

    public List<IndicatorMetadata> getAllMetadata() {
        return new ArrayList<>(metadatas.values());
    }

    public Map<String, IndicatorMetadata> getMetadataMap() {
        return Collections.unmodifiableMap(metadatas);
    }

    /** 解析MACD指标值 */
    private IndicatorValue resolveMacd(WeightRuleContext ctx, Map<String, String> params) {
        List<WeightRuleContext.CandlestickSnapshot> kLines = ctx.getKLines();
        if (kLines == null || kLines.size() < 2) return null;

        int fast = parseIntParam(params, "fast_period", 12);
        int slow = parseIntParam(params, "slow_period", 26);
        int signal = parseIntParam(params, "signal_period", 9);
        String categoryValue = params != null ? params.get("categoryValue") : null;
        if (categoryValue == null || categoryValue.isEmpty()) categoryValue = "MACD_LINE";

        // 如果指定了timeframe，通过 ctx.getKLinesForPeriod 懒加载获取对应周期K线
        String timeframe = params != null ? params.get("timeframe") : null;
        if (timeframe != null && !timeframe.isEmpty() && ctx.getSymbol() != null && candlestickService != null) {
            List<WeightRuleContext.CandlestickSnapshot> periodKLines = ctx.getKLinesForPeriod(timeframe, candlestickService);
            if (periodKLines != null && periodKLines.size() >= 2) {
                kLines = periodKLines;
            }
        }

        BarSeries series = buildSeriesFromSnapshots(kLines);
        int idx = series.getEndIndex() - 1;
        if (idx < series.getBeginIndex()) return null;
        if (idx < slow + signal) return null;

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, fast, slow);
        double macdLine = macd.getValue(idx).doubleValue();
        double macdSignal = macd.getSignalLine(signal).getValue(idx).doubleValue();
        double macdHistogram = macd.getHistogram(signal).getValue(idx).doubleValue();

        switch (categoryValue.toUpperCase()) {
            case "MACD_SIGNAL":
                return IndicatorValue.of(macdSignal);
            case "MACD_HISTOGRAM":
                return IndicatorValue.of(macdHistogram);
            case "HISTOGRAM_ADAPTIVE":
                return IndicatorValue.of(ctx.isBuy() ? macdHistogram : -macdHistogram);
            case "HISTOGRAM_MOMENTUM": {
                if (idx - 1 < series.getBeginIndex()) return IndicatorValue.of("BEARISH_SHRINK");
                double prevHist = macd.getHistogram(signal).getValue(idx - 1).doubleValue();
                if (macdHistogram > 0 && macdHistogram > prevHist) return IndicatorValue.of("BULLISH_EXPAND");
                if (macdHistogram > 0 && macdHistogram < prevHist) return IndicatorValue.of("BULLISH_SHRINK");
                if (macdHistogram < 0 && macdHistogram < prevHist) return IndicatorValue.of("BEARISH_EXPAND");
                return IndicatorValue.of("BEARISH_SHRINK");
            }
            case "GOLDEN_CROSS":
                if (idx - 1 < series.getBeginIndex()) return IndicatorValue.of(0.0);
                double prevLine = macd.getValue(idx - 1).doubleValue();
                double prevSignal = macd.getSignalLine(signal).getValue(idx - 1).doubleValue();
                return IndicatorValue.of((prevLine <= prevSignal && macdLine > macdSignal) ? 1.0 : 0.0);
            case "DEATH_CROSS":
                if (idx - 1 < series.getBeginIndex()) return IndicatorValue.of(0.0);
                double prevLine2 = macd.getValue(idx - 1).doubleValue();
                double prevSignal2 = macd.getSignalLine(signal).getValue(idx - 1).doubleValue();
                return IndicatorValue.of((prevLine2 >= prevSignal2 && macdLine < macdSignal) ? 1.0 : 0.0);
            case "BULLISH_DIVERGENCE":
                return IndicatorValue.of(detectBullishDivergence(series, closePrice, macd, idx, 30) ? 1.0 : 0.0);
            case "BEARISH_DIVERGENCE":
                return IndicatorValue.of(detectBearishDivergence(series, closePrice, macd, idx, 30) ? 1.0 : 0.0);
            default:
                return IndicatorValue.of(macdLine);
        }
    }

    /** 简单底背离检测 */
    private boolean detectBullishDivergence(BarSeries series, ClosePriceIndicator closePrice,
                                            MACDIndicator macd, int endIdx, int lookback) {
        int startIdx = Math.max(series.getBeginIndex(), endIdx - lookback + 1);
        if (endIdx - startIdx < 5) return false;

        int lastLowIdx = startIdx;
        for (int i = startIdx; i <= endIdx; i++) {
            if (closePrice.getValue(i).doubleValue() < closePrice.getValue(lastLowIdx).doubleValue()) {
                lastLowIdx = i;
            }
        }
        int prevStart = Math.max(series.getBeginIndex(), lastLowIdx - lookback);
        int prevLowIdx = prevStart;
        for (int i = prevStart; i < lastLowIdx; i++) {
            if (closePrice.getValue(i).doubleValue() < closePrice.getValue(prevLowIdx).doubleValue()) {
                prevLowIdx = i;
            }
        }
        if (prevLowIdx == lastLowIdx) return false;

        double priceLow1 = closePrice.getValue(prevLowIdx).doubleValue();
        double priceLow2 = closePrice.getValue(lastLowIdx).doubleValue();
        double macdLow1 = macd.getValue(prevLowIdx).doubleValue();
        double macdLow2 = macd.getValue(lastLowIdx).doubleValue();

        return priceLow2 < priceLow1 && macdLow2 > macdLow1;
    }

    /** 简单顶背离检测 */
    private boolean detectBearishDivergence(BarSeries series, ClosePriceIndicator closePrice,
                                            MACDIndicator macd, int endIdx, int lookback) {
        int startIdx = Math.max(series.getBeginIndex(), endIdx - lookback + 1);
        if (endIdx - startIdx < 5) return false;

        int lastHighIdx = startIdx;
        for (int i = startIdx; i <= endIdx; i++) {
            if (closePrice.getValue(i).doubleValue() > closePrice.getValue(lastHighIdx).doubleValue()) {
                lastHighIdx = i;
            }
        }
        int prevStart = Math.max(series.getBeginIndex(), lastHighIdx - lookback);
        int prevHighIdx = prevStart;
        for (int i = prevStart; i < lastHighIdx; i++) {
            if (closePrice.getValue(i).doubleValue() > closePrice.getValue(prevHighIdx).doubleValue()) {
                prevHighIdx = i;
            }
        }
        if (prevHighIdx == lastHighIdx) return false;

        double priceHigh1 = closePrice.getValue(prevHighIdx).doubleValue();
        double priceHigh2 = closePrice.getValue(lastHighIdx).doubleValue();
        double macdHigh1 = macd.getValue(prevHighIdx).doubleValue();
        double macdHigh2 = macd.getValue(lastHighIdx).doubleValue();

        return priceHigh2 > priceHigh1 && macdHigh2 < macdHigh1;
    }

    // ==================== 工具方法 ====================

    /** 计算 EMA 值 */
    private static double calcEma(BarSeries series, ClosePriceIndicator closePrice, int period, int idx) {
        EMAIndicator ema = new EMAIndicator(closePrice, period);
        return ema.getValue(idx).doubleValue();
    }

    /** 计算 SMA 值 */
    private static double calcSma(BarSeries series, ClosePriceIndicator closePrice, int period, int idx) {
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        return sma.getValue(idx).doubleValue();
    }

    /** 从CandlestickSnapshot列表构建BarSeries */
    private static BarSeries buildSeriesFromSnapshots(List<WeightRuleContext.CandlestickSnapshot> kLines) {
        if (kLines == null || kLines.isEmpty()) {
            return IndicatorWrapHelper.buildSeries(java.util.Collections.emptyList());
        }
        long gapMillis = kLines.size() >= 2
                ? kLines.get(1).getId() - kLines.get(0).getId()
                : 3600000L;
        CandlestickIntervalEnum interval = resolveInterval(gapMillis);
        return IndicatorWrapHelper.buildSeries(kLines.stream().map(k -> Candlestick.builder()
                .id(k.getId())
                .openPrice(BigDecimal.valueOf(k.getOpen()))
                .highPrice(BigDecimal.valueOf(k.getHigh()))
                .lowPrice(BigDecimal.valueOf(k.getLow()))
                .closePrice(BigDecimal.valueOf(k.getClose()))
                .volume(BigDecimal.ZERO)
                .candlestickIntervalEnum(interval)
                .build()
        ).collect(Collectors.toList()));
    }

    private static CandlestickIntervalEnum resolveInterval(long gapMillis) {
        long gapMinutes = gapMillis / 60_000;
        for (CandlestickIntervalEnum e : CandlestickIntervalEnum.values()) {
            if (e.getMinNum() != null && e.getMinNum() == (int) gapMinutes) {
                return e;
            }
        }
        return CandlestickIntervalEnum.MIN60;
    }

    private static int parseIntParam(Map<String, String> params, String key, int defaultVal) {
        if (params == null || !params.containsKey(key)) return defaultVal;
        try {
            return Integer.parseInt(params.get(key));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static class IndicatorHandler {
        final BiFunction<WeightRuleContext, Map<String, String>, IndicatorValue> resolver;

        IndicatorHandler(BiFunction<WeightRuleContext, Map<String, String>, IndicatorValue> resolver) {
            this.resolver = resolver;
        }
    }

}
