package com.chain.ai.trade.engine.signal.rule;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class WeightRuleEngine {

    private static final Logger engineLog = LoggerFactory.getLogger(WeightRuleEngine.class);

    private IndicatorProviderRegistry indicatorProviderRegistry;
    private ICandlestickService candlestickService;

    public void setIndicatorProviderRegistry(IndicatorProviderRegistry registry) {
        this.indicatorProviderRegistry = registry;
    }

    public void setCandlestickService(ICandlestickService candlestickService) {
        this.candlestickService = candlestickService;
    }

    public WeightRuleResult evaluate(WeightRuleConfig config, WeightRuleContext ctx) {
        if (config == null || !config.isEnabled() || config.getRules() == null || config.getRules().isEmpty()) {
            return null;
        }

        List<WeightRule> sorted = config.getRules().stream()
                .filter(r -> r != null && r.isEnabled())
                .sorted(Comparator.comparingInt(WeightRule::getOrder))
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            return null;
        }

        preProcessContext(ctx);

        double totalScore = 0;
        int matchedRules = 0;
        StringBuilder reason = new StringBuilder();

        // Pass 1: VETO rules (blacklist) — 条件匹配→否决（黑名单模式）
        for (WeightRule rule : sorted) {
            if (!"VETO".equalsIgnoreCase(rule.getType())) continue;

            boolean conditionsMet = evaluateConditions(rule.getConditions(), rule.getConditionOperator(), ctx);
            if (conditionsMet) {
                String msg = String.format("否决规则[%s]触发", rule.getName());
                engineLog.debug("WeightRule veto: {}", msg);
                return WeightRuleResult.veto(msg, 0);
            }
            double w = rule.getVetoWeight() != null ? rule.getVetoWeight() : 0.0;
            // 根据 vetoContributeScore 决定是否累加权重
            WeightScoringConfig scoringCfg = config.getScoringConfig();
            if (scoringCfg == null || scoringCfg.getVetoContributeScore() == null || scoringCfg.getVetoContributeScore()) {
                totalScore += w;
            }
            matchedRules++;
            if (reason.length() > 0) reason.append(" + ");
            reason.append(rule.getName()).append("=").append(String.format("%.2f", w));
        }

        // Pass 2: SCORING rules
        for (WeightRule rule : sorted) {
            if ("VETO".equalsIgnoreCase(rule.getType())) continue;
            boolean matched = evaluateConditions(rule.getConditions(), rule.getConditionOperator(), ctx);
            if (!matched) continue;
            double s = rule.getScore() != null ? rule.getScore() : 0;
            totalScore += s;
            matchedRules++;
            if (reason.length() > 0) reason.append(" + ");
            reason.append(rule.getName()).append("=").append(String.format("%.2f", s));
        }

        if (matchedRules == 0) {
            return WeightRuleResult.veto("无规则匹配，信号被否决", 0);
        }

        double finalWeight = scoreToWeight(totalScore, config.getScoringConfig());
        String rsn = String.format("评分: %.2f -> 权重: %.1f (%s)", totalScore, finalWeight, reason.toString());
        return WeightRuleResult.scored(finalWeight, rsn, matchedRules);
    }

    public RuleEvaluationResult evaluateWithTrace(WeightRuleConfig config, WeightRuleContext ctx) {
        if (config == null || !config.isEnabled() || config.getRules() == null || config.getRules().isEmpty()) {
            return null;
        }

        List<WeightRule> sorted = config.getRules().stream()
                .filter(r -> r != null && r.isEnabled())
                .sorted(Comparator.comparingInt(WeightRule::getOrder))
                .collect(Collectors.toList());

        if (sorted.isEmpty()) return null;

        preProcessContext(ctx);

        List<RuleEvaluationTrace> traces = new ArrayList<>();
        Map<String, Double> indicatorSnapshot = new LinkedHashMap<>();
        double totalScore = 0;
        int matchedRules = 0;
        StringBuilder reason = new StringBuilder();

        // Pass 1: VETO rules (blacklist)
        for (WeightRule rule : sorted) {
            if (!"VETO".equalsIgnoreCase(rule.getType())) continue;

            ConditionEvalResult evalResult = evaluateConditionsWithTrace(rule.getConditions(), rule.getConditionOperator(), ctx, indicatorSnapshot);

            RuleEvaluationTrace trace = new RuleEvaluationTrace();
            trace.setRuleName(rule.getName());
            trace.setRuleType(rule.getType());
            trace.setConditionOperator(rule.getConditionOperator());
            trace.setConditionResults(evalResult.conditionTraces);

            if (evalResult.matched) {
                trace.setMatched(true);
                trace.setContribution(0);
                trace.setReason("否决规则触发：条件匹配（命中危险信号）");
                traces.add(trace);
                String msg = String.format("否决规则[%s]触发", rule.getName());
                engineLog.debug("WeightRule veto: {}", msg);
                return RuleEvaluationResult.veto(msg, traces, indicatorSnapshot);
            }

            double w = rule.getVetoWeight() != null ? rule.getVetoWeight() : 0.2;
            trace.setMatched(false);
            trace.setContribution(w);
            trace.setReason(String.format("否决规则通过（条件不匹配，安全），贡献权重 %.2f", w));
            traces.add(trace);
            // 根据 vetoContributeScore 决定是否累加权重
            WeightScoringConfig scoringCfg = config.getScoringConfig();
            if (scoringCfg == null || scoringCfg.getVetoContributeScore() == null || scoringCfg.getVetoContributeScore()) {
                totalScore += w;
            }
            matchedRules++;
            if (reason.length() > 0) reason.append(" + ");
            reason.append(rule.getName()).append("=").append(String.format("%.2f", w));
        }

        // Pass 2: SCORING rules
        for (WeightRule rule : sorted) {
            if ("VETO".equalsIgnoreCase(rule.getType())) continue;

            ConditionEvalResult evalResult = evaluateConditionsWithTrace(rule.getConditions(), rule.getConditionOperator(), ctx, indicatorSnapshot);

            RuleEvaluationTrace trace = new RuleEvaluationTrace();
            trace.setRuleName(rule.getName());
            trace.setRuleType(rule.getType());
            trace.setConditionOperator(rule.getConditionOperator());
            trace.setConditionResults(evalResult.conditionTraces);

            if (!evalResult.matched) {
                trace.setMatched(false);
                trace.setContribution(0);
                trace.setReason("条件未匹配");
                traces.add(trace);
                continue;
            }

            double s = rule.getScore() != null ? rule.getScore() : 0;
            trace.setMatched(true);
            trace.setContribution(s);
            trace.setReason(String.format("规则匹配，贡献评分 %.2f", s));
            traces.add(trace);
            totalScore += s;
            matchedRules++;
            if (reason.length() > 0) reason.append(" + ");
            reason.append(rule.getName()).append("=").append(String.format("%.2f", s));
        }

        if (matchedRules == 0) {
            return RuleEvaluationResult.veto("无规则匹配，信号被否决", traces, indicatorSnapshot);
        }

        double finalWeight = scoreToWeight(totalScore, config.getScoringConfig());
        String rsn = String.format("评分: %.2f -> 权重: %.1f (%s)", totalScore, finalWeight, reason.toString());
        return RuleEvaluationResult.scored(finalWeight, totalScore, rsn, traces, indicatorSnapshot);
    }

    /**
     * 预处理 Context：检测 K 线形态（如果尚未检测）
     */
    private void preProcessContext(WeightRuleContext ctx) {
        if (ctx.getDetectedPatterns() == null && ctx.getKLines() != null && ctx.getKLines().size() >= 2) {
            ctx.setDetectedPatterns(CandlestickPatternDetector.detect(ctx.getKLines()));
        }
    }

    // ==================== 条件评估 ====================

    private boolean evaluateConditions(List<RuleCondition> conditions, String conditionOperator, WeightRuleContext ctx) {
        if (conditions == null || conditions.isEmpty()) return true;

        boolean isAnd = conditionOperator == null || "AND".equalsIgnoreCase(conditionOperator);
        boolean anyEvaluated = false;

        for (RuleCondition cond : conditions) {
            if (isDirectionSkipped(cond, ctx)) continue;
            anyEvaluated = true;
            boolean matched = evaluateCondition(cond, ctx);
            if (isAnd && !matched) return false;
            if (!isAnd && matched) return true;
        }

        if (!anyEvaluated) return false;
        return isAnd;
    }

    private ConditionEvalResult evaluateConditionsWithTrace(List<RuleCondition> conditions, String conditionOperator,
                                                            WeightRuleContext ctx, Map<String, Double> indicatorSnapshot) {
        ConditionEvalResult result = new ConditionEvalResult();
        result.conditionTraces = new ArrayList<>();

        if (conditions == null || conditions.isEmpty()) {
            result.matched = true;
            return result;
        }

        boolean isAnd = conditionOperator == null || "AND".equalsIgnoreCase(conditionOperator);
        boolean anyEvaluated = false;

        for (RuleCondition cond : conditions) {
            if (isDirectionSkipped(cond, ctx)) continue;
            anyEvaluated = true;
            RuleEvaluationTrace.ConditionTrace ct = evaluateConditionWithTrace(cond, ctx, indicatorSnapshot);
            result.conditionTraces.add(ct);
            if (isAnd && !ct.isMatched()) {
                result.matched = false;
                return result;
            }
            if (!isAnd && ct.isMatched()) {
                result.matched = true;
                return result;
            }
        }

        if (!anyEvaluated) {
            result.matched = false;
            return result;
        }
        result.matched = isAnd;
        return result;
    }

    private static boolean isDirectionSkipped(RuleCondition cond, WeightRuleContext ctx) {
        return ("LONG".equalsIgnoreCase(cond.getDirection()) && !ctx.isBuy())
                || ("SHORT".equalsIgnoreCase(cond.getDirection()) && ctx.isBuy());
    }

    /**
     * 评估单个条件：通过 registry.resolve() 统一调度指标计算
     * 根据 IndicatorValue 的类型（NUMERIC/STRING/BOOLEAN）进行对应的条件匹配
     */
    private boolean evaluateCondition(RuleCondition cond, WeightRuleContext ctx) {
        OperatorType op;
        try {
            op = OperatorType.valueOf(cond.getOperator().toUpperCase());
        } catch (Exception e) {
            engineLog.warn("未知运算符: {}", cond.getOperator());
            return false;
        }

        // 方向比较运算符不依赖指标实际值，直接比较 lastDirection 与 currentDirection
        if (op == OperatorType.EQ_CURRENT_SAME || op == OperatorType.EQ_CURRENT_OPPOSITE) {
            return evaluateDirectionCondition(op, ctx);
        }

        IndicatorValue actual = resolveIndicatorValue(cond.getIndicator(), cond.getParams(), ctx);
        if (actual == null) return false;

        switch (actual.getType()) {
            case STRING:
                return evaluateStringCondition(actual.getStringValue(), op, cond.getValue());
            case BOOLEAN:
                return evaluateBooleanCondition(actual.getBooleanValue(), op, cond.getValue());
            default: // NUMERIC
                return evaluateNumericCondition(actual.getNumericValue(), op, cond.getValue(), ctx);
        }
    }

    /** STRING 类型条件匹配 */
    private static boolean evaluateStringCondition(String actual, OperatorType op, String expected) {
        if (actual == null || expected == null) return false;
        switch (op) {
            case IS:
            case EQ:
                return actual.equalsIgnoreCase(expected);
            case NEQ:
                return !actual.equalsIgnoreCase(expected);
            case IN:
                for (String v : expected.split(",")) {
                    if (actual.equalsIgnoreCase(v.trim())) return true;
                }
                return false;
            case NOT_IN:
                for (String v : expected.split(",")) {
                    if (actual.equalsIgnoreCase(v.trim())) return false;
                }
                return true;
            default:
                return false;
        }
    }

    /** BOOLEAN 类型条件匹配 */
    private static boolean evaluateBooleanCondition(Boolean actual, OperatorType op, String expected) {
        if (actual == null) return false;
        boolean expectedBool = Boolean.parseBoolean(expected);
        switch (op) {
            case IS:
            case EQ:
                return actual == expectedBool;
            case NEQ:
                return actual != expectedBool;
            default:
                return false;
        }
    }

    /** NUMERIC 类型条件匹配 */
    private static boolean evaluateNumericCondition(Double actual, OperatorType op, String expected, WeightRuleContext ctx) {
        if (actual == null || expected == null) return false;
        double expectedVal;
        try {
            expectedVal = Double.parseDouble(expected);
        } catch (NumberFormatException e) {
            return false;
        }
        switch (op) {
            case GT: return actual > expectedVal;
            case GTE: return actual >= expectedVal;
            case LT: return actual < expectedVal;
            case LTE: return actual <= expectedVal;
            case EQ: return Math.abs(actual - expectedVal) < 1e-8;
            case NEQ: return Math.abs(actual - expectedVal) >= 1e-8;
            case IN: {
                for (String v : expected.split(",")) {
                    if (Math.abs(actual - Double.parseDouble(v.trim())) < 1e-8) return true;
                }
                return false;
            }
            case NOT_IN: {
                for (String v : expected.split(",")) {
                    if (Math.abs(actual - Double.parseDouble(v.trim())) < 1e-8) return false;
                }
                return true;
            }
            case BETWEEN: {
                String[] parts = expected.split(",");
                if (parts.length != 2) return false;
                double low = Double.parseDouble(parts[0].trim());
                double high = Double.parseDouble(parts[1].trim());
                return actual >= low && actual <= high;
            }
            // ===== 新增：abs_space 分位数比较 =====
            case LT_ABS_PERCENTILE: return actual < getAbsSpacePercentile(ctx, expectedVal);
            case GT_ABS_PERCENTILE: return actual > getAbsSpacePercentile(ctx, expectedVal);
            case LTE_ABS_PERCENTILE: return actual <= getAbsSpacePercentile(ctx, expectedVal);
            case GTE_ABS_PERCENTILE: return actual >= getAbsSpacePercentile(ctx, expectedVal);
            // ===== 新增：cumRatio 分位数比较 =====
            case LT_RATIO_PERCENTILE: return actual < getCumRatioPercentile(ctx, expectedVal);
            case GT_RATIO_PERCENTILE: return actual > getCumRatioPercentile(ctx, expectedVal);
            case LTE_RATIO_PERCENTILE: return actual <= getCumRatioPercentile(ctx, expectedVal);
            case GTE_RATIO_PERCENTILE: return actual >= getCumRatioPercentile(ctx, expectedVal);
            default:
                return false;
        }
    }

    /**
     * 获取 abs_space 分位数（expectedVal: 20/40/70/85/95）
     */
    private static double getAbsSpacePercentile(WeightRuleContext ctx, double p) {
        if (p <= 20) return ctx.getPercentile20();
        if (p <= 40) return ctx.getPercentile40();
        if (p <= 70) return ctx.getPercentile70();
        if (p <= 85) return ctx.getPercentile85();
        return ctx.getPercentile95();
    }

    /**
     * 获取 cumRatio 分位数（expectedVal: 40/60）
     */
    private static double getCumRatioPercentile(WeightRuleContext ctx, double p) {
        if (p <= 40) return ctx.getCumRatioPercentile40();
        return ctx.getCumRatioPercentile60();
    }

    /**
     * 方向比较运算符：比较 lastDirection 与 currentDirection，与指标实际值无关
     */
    private static boolean evaluateDirectionCondition(OperatorType op, WeightRuleContext ctx) {
        String last = ctx.getLastDirection();
        String current = ctx.getCurrentDirection();
        if (last == null || current == null) return false;
        if (op == OperatorType.EQ_CURRENT_SAME) return last.equals(current);
        if (op == OperatorType.EQ_CURRENT_OPPOSITE) return !last.equals(current);
        return false;
    }

    private RuleEvaluationTrace.ConditionTrace evaluateConditionWithTrace(RuleCondition cond, WeightRuleContext ctx,
                                                                           Map<String, Double> indicatorSnapshot) {
        RuleEvaluationTrace.ConditionTrace ct = new RuleEvaluationTrace.ConditionTrace();
        ct.setIndicator(cond.getIndicator());
        ct.setOperator(cond.getOperator());
        ct.setDirection(cond.getDirection());
        ct.setExpectedValue(cond.getValue());

        IndicatorValue actual = resolveIndicatorValue(cond.getIndicator(), cond.getParams(), ctx);
        if (actual != null && actual.getType() == IndicatorValue.Type.NUMERIC) {
            ct.setActualValue(actual.getNumericValue());
            recordSnapshot(indicatorSnapshot, cond, actual.getNumericValue());
        } else {
            ct.setActualValue(null);
        }

        if (actual == null) {
            ct.setMatched(false);
            ct.setReason(String.format("[%s] 指标值解析为 null → 不匹配", cond.getIndicator()));
            return ct;
        }

        OperatorType op;
        try {
            op = OperatorType.valueOf(cond.getOperator().toUpperCase());
        } catch (Exception e) {
            ct.setMatched(false);
            ct.setReason(String.format("[%s] 运算符[%s]无效 → 不匹配", cond.getIndicator(), cond.getOperator()));
            return ct;
        }

        // 方向比较运算符不依赖指标实际值
        if (op == OperatorType.EQ_CURRENT_SAME || op == OperatorType.EQ_CURRENT_OPPOSITE) {
            boolean matched = evaluateDirectionCondition(op, ctx);
            ct.setMatched(matched);
            ct.setReason(String.format("[%s] lastDirection=%s currentDirection=%s %s → %s",
                    cond.getIndicator(), ctx.getLastDirection(), ctx.getCurrentDirection(), cond.getOperator(),
                    matched ? "匹配" : "不匹配"));
            return ct;
        }

        boolean matched;
        String actualDesc;
        switch (actual.getType()) {
            case STRING:
                matched = evaluateStringCondition(actual.getStringValue(), op, cond.getValue());
                actualDesc = actual.getStringValue();
                break;
            case BOOLEAN:
                matched = evaluateBooleanCondition(actual.getBooleanValue(), op, cond.getValue());
                actualDesc = String.valueOf(actual.getBooleanValue());
                break;
            default: // NUMERIC
                matched = evaluateNumericCondition(actual.getNumericValue(), op, cond.getValue(), ctx);
                actualDesc = String.format("%.4f", actual.getNumericValue());
                break;
        }
        ct.setMatched(matched);
        ct.setReason(String.format("[%s] 实际值=%s %s 期望值=%s → %s",
                cond.getIndicator(), actualDesc, cond.getOperator(), cond.getValue(),
                matched ? "匹配" : "不匹配"));
        return ct;
    }

    private void recordSnapshot(Map<String, Double> snapshot, RuleCondition cond, Double value) {
        if (snapshot == null || cond == null || cond.getIndicator() == null) return;
        String key = cond.getIndicator();
        if (cond.getParams() != null && !cond.getParams().isEmpty()) {
            key += "(" + cond.getParams().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(",")) + ")";
        }
        if (value != null) {
            snapshot.putIfAbsent(key, value);
        }
    }

    // ==================== 指标值解析 ====================

    /**
     * 通过 SPI 机制统一调度指标计算
     * 优先通过 registry 解析，兼容旧版 IndicatorType 枚举（包装为 IndicatorValue）
     */
    private IndicatorValue resolveIndicatorValue(String indicator, Map<String, String> params, WeightRuleContext ctx) {
        if (indicator == null) return null;

        // 优先通过 SPI 机制（BuiltInIndicatorProvider）解析
        if (indicatorProviderRegistry != null) {
            IndicatorValue registryValue = indicatorProviderRegistry.resolve(indicator, ctx, params);
            if (registryValue != null) return registryValue;
        }

        // 兼容旧版硬编码枚举（包装为 IndicatorValue）
        try {
            IndicatorType type = IndicatorType.valueOf(indicator.toUpperCase());
            switch (type) {
                case PRICE_MOVE:
                    return IndicatorValue.of(computePriceMove(ctx.getKLines()));
                case SMC_POSITION_SCORE:
                    return IndicatorValue.of(ctx.getSmcPositionScore());
                case SMC_RR:
                    return IndicatorValue.of(ctx.getSmcNetRR());
                case MACD_LINE:
                case MACD_HISTOGRAM:
                case MACD_SIGNAL: {
                    int fast = parseIntParam(params, "fast", 12);
                    int slow = parseIntParam(params, "slow", 26);
                    int sigPeriod = parseIntParam(params, "signal", 9);
                    if (ctx.getKLines() == null || ctx.getKLines().size() < 2) return null;
                    BarSeries series = buildSeriesFromSnapshots(ctx.getKLines());
                    int idx = series.getEndIndex() - 1;
                    if (idx < series.getBeginIndex()) return null;
                    if (idx < slow + sigPeriod) return null;
                    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
                    MACDIndicator macd = new MACDIndicator(closePrice, fast, slow);
                    if (type == IndicatorType.MACD_HISTOGRAM) {
                        return IndicatorValue.of(macd.getHistogram(sigPeriod).getValue(idx).doubleValue());
                    } else if (type == IndicatorType.MACD_SIGNAL) {
                        return IndicatorValue.of(macd.getSignalLine(sigPeriod).getValue(idx).doubleValue());
                    } else {
                        return IndicatorValue.of(macd.getValue(idx).doubleValue());
                    }
                }
                case TIME:
                    if (ctx.getWeekday() != null) {
                        return IndicatorValue.of(ctx.getWeekday().doubleValue());
                    }
                    return IndicatorValue.of((double) System.currentTimeMillis());
                case SWING_RANGING:
                    return ctx.getSwingRanging() != null ? IndicatorValue.of(ctx.getSwingRanging() ? 1.0 : 0.0) : null;
                case SWING_BREAKOUT:
                    return ctx.getSwingBreakout() != null ? IndicatorValue.of(ctx.getSwingBreakout().doubleValue()) : null;
                case SMC_OB_RANGING:
                    return ctx.getSmcObRanging() != null ? IndicatorValue.of(ctx.getSmcObRanging() ? 1.0 : 0.0) : null;
                case WEEKDAY:
                    return ctx.getWeekday() != null ? IndicatorValue.of(ctx.getWeekday().doubleValue()) : null;
                case SMC_POSITION_15M:
                    return IndicatorValue.of(ctx.getSmcPositionScore15m());
                case SMC_RISK_PERCENT:
                    return IndicatorValue.of(ctx.getSmcRiskPercent());
                case SMC_IN_SUPPLY_ZONE:
                    return ctx.getSmcInSupplyZone() != null ? IndicatorValue.of(ctx.getSmcInSupplyZone().doubleValue()) : null;
                case SMC_IN_DEMAND_ZONE:
                    return ctx.getSmcInDemandZone() != null ? IndicatorValue.of(ctx.getSmcInDemandZone().doubleValue()) : null;
                default:
                    return null;
            }
        } catch (Exception e) {
            engineLog.warn("未知指标类型: {}", indicator);
            return null;
        }
    }

    private static double computePriceMove(List<WeightRuleContext.CandlestickSnapshot> kLines) {
        if (kLines == null || kLines.size() < 2) return 0;
        double firstClose = kLines.get(0).getClose();
        double lastClose = kLines.get(kLines.size() - 1).getClose();
        if (firstClose == 0) return 0;
        return (lastClose - firstClose) / firstClose * 100;
    }

    static double scoreToWeight(double rawScore, WeightScoringConfig cfg) {
        if (cfg == null) cfg = new WeightScoringConfig();
        double clamped = Math.max(rawScore, 0.0);
        if ("LINEAR".equalsIgnoreCase(cfg.getMappingMode())) {
            double weight = clamped * cfg.getLinearSlope();
            return Math.max(cfg.getLinearMinWeight(), Math.min(cfg.getLinearMaxWeight(), weight));
        }
        // 阶梯映射（默认 STEP）
        if (clamped >= 3.5) return 2.0;
        if (clamped >= 2.5) return 1.5;
        if (clamped >= 1.5) return 1.0;
        if (clamped >= 0.5) return 0.5;
        return clamped;
    }

    // ==================== 工具方法 ====================

    private static int parseIntParam(Map<String, String> params, String key, int defaultVal) {
        if (params == null || !params.containsKey(key)) return defaultVal;
        try {
            return Integer.parseInt(params.get(key));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static BarSeries buildSeriesFromSnapshots(List<WeightRuleContext.CandlestickSnapshot> kLines) {
        if (kLines == null || kLines.isEmpty()) return IndicatorWrapHelper.buildSeries(java.util.Collections.emptyList());
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

    private static class ConditionEvalResult {
        boolean matched;
        List<RuleEvaluationTrace.ConditionTrace> conditionTraces;
    }
}
