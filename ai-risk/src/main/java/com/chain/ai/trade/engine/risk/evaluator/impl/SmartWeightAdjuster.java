package com.chain.ai.trade.engine.risk.evaluator.impl;

import com.chain.ai.trade.engine.entity.dto.PriceAnalysisDto;
import com.chain.ai.trade.engine.entity.dto.PriceTarget;
import com.chain.ai.trade.engine.entity.dto.StopLossLevel;
import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
import com.chain.ai.trade.engine.risk.common.TimeFrame;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.indicators.elliott.ElliottPhase;

import java.util.*;

/**
 * 智能周期权重调整器
 * 根据数据质量、方向一致性和市场状态动态调整权重
 */
@Slf4j
public class SmartWeightAdjuster {

    /**
     * 计算调整后的周期权重
     */
    public Map<TimeFrame, Double> calculateAdjustedWeights(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            TradingSignalDto signal) {

        // 基础权重配置
        Map<TimeFrame, Double> baseWeights = Map.of(
                TimeFrame.H1, 0.5,
                TimeFrame.M15, 0.3,
                TimeFrame.M3, 0.2
        );

        Map<TimeFrame, Double> adjustedWeights = new HashMap<>();
        Map<TimeFrame, Double> qualityScores = new HashMap<>();

        // 1. 计算每个周期的质量得分
        for (Map.Entry<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> entry : analyses.entrySet()) {
            TimeFrame tf = entry.getKey();
            ElliottWaveEvaluator.TimeFrameAnalysis analysis = entry.getValue();

            double qualityScore = calculateCycleQualityScore(analysis, signal);
            qualityScores.put(tf, qualityScore);
        }

        // 2. 方向一致性惩罚/奖励
        Map<TimeFrame, Double> directionScores = calculateDirectionConsistencyScores(analyses, signal);

        // 3. 综合计算调整因子
        double totalAdjustedWeight = 0;
        for (TimeFrame tf : baseWeights.keySet()) {
            if (!analyses.containsKey(tf)) {
                continue;
            }

            double baseWeight = baseWeights.get(tf);
            double qualityFactor = qualityScores.getOrDefault(tf, 1.0);
            double directionFactor = directionScores.getOrDefault(tf, 1.0);

            // 综合调整因子（质量权重0.7，方向权重0.3）
            double adjustmentFactor = qualityFactor * 0.7 + directionFactor * 0.3;

            // 应用调整
            double adjustedWeight = baseWeight * adjustmentFactor;
            adjustedWeights.put(tf, adjustedWeight);
            totalAdjustedWeight += adjustedWeight;
        }

        // 4. 归一化
        if (totalAdjustedWeight > 0) {
            for (TimeFrame tf : adjustedWeights.keySet()) {
                adjustedWeights.put(tf, adjustedWeights.get(tf) / totalAdjustedWeight);
            }
        }

        return adjustedWeights;
    }

    /**
     * 计算周期质量得分（0.0-1.0）
     */
    private double calculateCycleQualityScore(ElliottWaveEvaluator.TimeFrameAnalysis analysis, TradingSignalDto signal) {
        double score = 1.0;

        // 1. 失效状态惩罚
        if (analysis.isInvalidation()) {
            double invalidationPenalty = calculateInvalidationPenalty(analysis);
            score *= invalidationPenalty;
        }

        // 2. 汇合度奖励/惩罚
        double confluenceFactor = analysis.getConfluenceScore() > 0.7 ? 1.1 :
                analysis.getConfluenceScore() < 0.3 ? 0.8 : 1.0;
        score *= confluenceFactor;

        // 3. 置信度奖励
        double confidenceFactor = analysis.getConfidence() > 80 ? 1.2 :
                analysis.getConfidence() > 60 ? 1.1 : 1.0;
        score *= confidenceFactor;

        // 4. 通道有效性（根据您的反馈，不惩罚宽通道）
        if (analysis.getChannel() != null && analysis.getChannel().isValid()) {
            // 只检查通道是否有效，不检查宽度
            score *= 1.05; // 有效通道奖励
        }

        return Math.max(0.1, Math.min(2.0, score)); // 限制在0.1-2.0之间
    }

    /**
     * 计算失效状态惩罚（基于距离）
     */
    private double calculateInvalidationPenalty(ElliottWaveEvaluator.TimeFrameAnalysis analysis) {
        if (!analysis.isInvalidation() || analysis.getPriceInfo() == null) {
            return 0.3; // 无距离信息，中等惩罚
        }

        Double distance = analysis.getPriceInfo().getDistanceToInvalidation();
        if (distance == null) {
            return 0.3; // 无距离信息，中等惩罚
        }

        double absDistance = Math.abs(distance);

        // 距离越远，惩罚越小
        if (absDistance > 20.0) return 0.9;
        if (absDistance > 15.0) return 0.8;
        if (absDistance > 10.0) return 0.7;
        if (absDistance > 5.0) return 0.5;
        if (absDistance > 3.0) return 0.4;
        if (absDistance > 1.0) return 0.3;
        return 0.2; // 距离<1%，严重惩罚
    }

    /**
     * 计算方向一致性得分
     */
    private Map<TimeFrame, Double> calculateDirectionConsistencyScores(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            TradingSignalDto signal) {

        Map<TimeFrame, Double> scores = new HashMap<>();
        boolean signalBuy = signal.getType().name().equals("BUY");

        // 计算主导方向
        int bullishCount = 0;
        int total = analyses.size();

        for (ElliottWaveEvaluator.TimeFrameAnalysis analysis : analyses.values()) {
            if (analysis.isBullish()) bullishCount++;
        }

        boolean marketBullish = bullishCount > total / 2;

        // 为每个周期打分
        for (Map.Entry<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> entry : analyses.entrySet()) {
            TimeFrame tf = entry.getKey();
            ElliottWaveEvaluator.TimeFrameAnalysis analysis = entry.getValue();

            double directionScore = 1.0;

            // 信号方向匹配
            boolean analysisBullish = analysis.isBullish();
            boolean directionMatch = (signalBuy && analysisBullish) || (!signalBuy && !analysisBullish);

            if (!directionMatch) {
                directionScore *= 0.7; // 方向不匹配惩罚
            }

            // 与市场多数方向的一致性
            if (analysisBullish == marketBullish) {
                directionScore *= 1.1; // 与多数一致奖励
            } else {
                directionScore *= 0.9; // 与多数不一致惩罚
            }

            scores.put(tf, directionScore);
        }

        return scores;
    }

    /**
     * 记录多周期详细目标分析
     */
    private void logDetailedMultiTimeFrameTargets(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            TradingSignalDto signal) {

        log.info("【多周期详细目标分析】");

        // 按周期权重排序（H1 > M15 > M3）
        List<TimeFrame> orderedTimeFrames = Arrays.asList(TimeFrame.H1, TimeFrame.M15, TimeFrame.M3);

        for (TimeFrame tf : orderedTimeFrames) {
            if (analyses.containsKey(tf)) {
                ElliottWaveEvaluator.TimeFrameAnalysis analysis = analyses.get(tf);
                ElliottWaveEvaluator.PricePositionInfo priceInfo = analysis.getPriceInfo();

                if (priceInfo != null && analysis.getPriceTargets() != null && !analysis.getPriceTargets().isEmpty()) {
                    log.info("  📊 {}周期目标分析:", tf);

                    // 显示前3个目标
                    log.info("    ┌─────┬──────────┬─────────┬─────────┬────────────┬─────────────────┐");
                    log.info("    │ 级别 │ 目标价格 │ 距离%   │ 概率    │ 风险收益比 │ 依据            │");
                    log.info("    ├─────┼──────────┼─────────┼─────────┼────────────┼─────────────────┤");

                    for (PriceTarget target : analysis.getPriceTargets()) {
                        if (target.getLevel() <= 3) {
                            String rrRatio = target.getRiskRewardRatio() > 0 ?
                                    String.format("1:%.2f", target.getRiskRewardRatio()) : "N/A";

                            log.info("    │ {}{}{}{}{}{} │",
                                    String.format("%3d", target.getLevel()),
                                    String.format("%10.4f", target.getPrice()),
                                    String.format("%7.2f%%", target.getDistanceFromCurrent()),
                                    String.format("%6.0f%%", target.getProbability() * 100),
                                    String.format("%10s", rrRatio),
                                    String.format("%-15s", truncateString(target.getDescription(), 15)));
                        }
                    }
                    log.info("    └─────┴──────────┴─────────┴─────────┴────────────┴─────────────────┘");

                    // 显示止损水平
                    if (analysis.getStopLossLevels() != null && !analysis.getStopLossLevels().isEmpty()) {
                        log.info("    ┌─────┬──────────┬─────────┬────────────┬─────────┬─────────────────┐");
                        log.info("    │ 级别 │ 止损价格 │ 风险%   │ 类型       │ 主要    │ 依据            │");
                        log.info("    ├─────┼──────────┼─────────┼────────────┼─────────┼─────────────────┘");

                        for (StopLossLevel stopLoss : analysis.getStopLossLevels()) {
                            if (stopLoss.getLevel() <= 3) {
                                log.info("    │ {}{}{}{}{} │",
                                        String.format("%3d", stopLoss.getLevel()),
                                        String.format("%10.4f", stopLoss.getPrice()),
                                        String.format("%7.2f%%", stopLoss.getRiskPercentage()),
                                        String.format("%-10s", truncateString(stopLoss.getType(), 10)),
                                        String.format("%7s", stopLoss.isPrimary() ? "✓" : ""),
                                        String.format("%-15s", truncateString(stopLoss.getDescription(), 15)));
                            }
                        }
                        log.info("    └─────┴──────────┴─────────┴────────────┴─────────┘");
                    }

                    log.info(""); // 空行分隔

                }
            }
        }
    }

    /**
     * 生成多周期综合交易建议
     */
    private void generateMultiTimeFrameTradingAdvice(
            ElliottWaveEvaluator.MultiTimeFrameAnalysis multiAnalysis,
            TradingSignalDto signal) {

        Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses = multiAnalysis.getAnalyses();
        boolean signalBuy = signal.getType().name().equals("BUY");

        log.info("【多周期综合交易建议】");

        // 1. 趋势方向分析
        String trendDirection = multiAnalysis.getTrendDirection();
        log.info("  📈 趋势分析: {}", getTrendDirectionDescription(trendDirection));

        // 2. 一致性分析
        int agreementLevel = multiAnalysis.getAgreementLevel();
        log.info("  🤝 一致性等级: {}/5 ({})", agreementLevel, getAgreementLevelDescription(agreementLevel));

        // 3. 计算综合止盈止损目标（加权平均）
        PriceAnalysisDto priceAnalysisDto = calculateAndLogComprehensiveTargets(analyses, signal);
        multiAnalysis.setPriceAnalysisDto(priceAnalysisDto);

        // 4. 风险评估
        logRiskAssessment(analyses, multiAnalysis.getCompositeScore(), signalBuy);

        // 5. 交易策略建议
        logTradingStrategyRecommendation(analyses, trendDirection, agreementLevel, signalBuy);
    }

    /**
     * 计算并记录综合目标
     */
    public PriceAnalysisDto calculateAndLogComprehensiveTargets(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            TradingSignalDto signal) {

        boolean signalBuy = signal.getType().name().equals("BUY");
        double currentPrice = signal.getTriggerPrice();

        // 定义周期权重
        Map<TimeFrame, Double> weights = Map.of(
                TimeFrame.H1, 0.5,
                TimeFrame.M15, 0.3,
                TimeFrame.M3, 0.2
        );

        // 收集所有周期的止盈止损建议
        List<Double> stopLosses = new ArrayList<>();
        List<Double> takeProfits = new ArrayList<>();
        Map<TimeFrame, Double> stopLossWeights = new HashMap<>();
        Map<TimeFrame, Double> takeProfitWeights = new HashMap<>();
        List<PriceTarget> priceTargets = new ArrayList<>();
        List<StopLossLevel> stopLossLevels = new ArrayList<>(); // 止损水平列表
        
        // 收集趋势信息
        int bullishCount = 0;
        int bearishCount = 0;
        int invalidCount = 0;
        ElliottPhase dominantPhase = null;
        Map<ElliottPhase, Integer> phaseCount = new HashMap<>();
        
        for (Map.Entry<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> entry : analyses.entrySet()) {
            TimeFrame tf = entry.getKey();
            ElliottWaveEvaluator.TimeFrameAnalysis analysis = entry.getValue();
            
            if (analysis.getPriceTargets() != null) {
                priceTargets.addAll(analysis.getPriceTargets());
            }
            if (analysis.getStopLossLevels() != null) {
                stopLossLevels.addAll(analysis.getStopLossLevels());
            }
            
            if (analysis.getOptimalStopLoss() != null) {
                stopLosses.add(analysis.getOptimalStopLoss());
                stopLossWeights.put(tf, weights.getOrDefault(tf, 0.2) * analysis.getScore());
            }
            if (analysis.getOptimalTakeProfit() != null) {
                takeProfits.add(analysis.getOptimalTakeProfit());
                takeProfitWeights.put(tf, weights.getOrDefault(tf, 0.2) * analysis.getScore());
            }
            
            // 统计趋势和相位
            if (analysis.isBullish()) {
                bullishCount++;
            } else {
                bearishCount++;
            }
            if (analysis.isInvalidation()) {
                invalidCount++;
            }
            if (analysis.getCurrentPhase() != null) {
                phaseCount.put(analysis.getCurrentPhase(),
                        phaseCount.getOrDefault(analysis.getCurrentPhase(), 0) + 1);
            }
        }
        
        // 确定主导相位
        if (!phaseCount.isEmpty()) {
            dominantPhase = phaseCount.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        // 计算加权平均目标
        double weightedStopLoss = 0;
        double weightedTakeProfit = 0;
        double riskRewardRatio = 0;
        if (!stopLosses.isEmpty() && !takeProfits.isEmpty()) {
            weightedStopLoss = calculateWeightedAverage(stopLosses, stopLossWeights, analyses);
            weightedTakeProfit = calculateWeightedAverage(takeProfits, takeProfitWeights, analyses);

            double risk = Math.abs(currentPrice - weightedStopLoss);
            double reward = Math.abs(weightedTakeProfit - currentPrice);
            riskRewardRatio = risk > 0 ? reward / risk : 0;

            log.info("  💡 【综合交易目标】");
            log.info("    入场价格: {}", String.format("%.4f", currentPrice));
            log.info("    建议止损: {} (风险: {})",
                    String.format("%.4f", weightedStopLoss),
                    String.format("%.2f%%", risk / currentPrice * 100));
            log.info("    建议止盈: {} (收益: {})",
                    String.format("%.4f", weightedTakeProfit),
                    String.format("%.2f%%", reward / currentPrice * 100));
            log.info("    风险收益比: 1:{}", String.format("%.2f", riskRewardRatio));

            // 根据风险收益比给出建议
            if (riskRewardRatio < 1.0) {
                log.warn("    ❌ 风险收益比低于1:1，强烈不建议交易");
            } else if (riskRewardRatio < 1.5) {
                log.warn("    ⚠️  风险收益比较低(1:{})，建议等待更好机会", String.format("%.2f", riskRewardRatio));
            } else if (riskRewardRatio >= 2.0) {
                log.info("    ✅ 风险收益比优秀(1:{})，可积极考虑", String.format("%.2f", riskRewardRatio));
            }

            // 显示各周期目标对比
            log.info("    📊 【各周期目标对比】");
            for (TimeFrame tf : Arrays.asList(TimeFrame.H1, TimeFrame.M15, TimeFrame.M3)) {
                if (analyses.containsKey(tf)) {
                    ElliottWaveEvaluator.TimeFrameAnalysis analysis = analyses.get(tf);

                    if (analysis.getOptimalStopLoss() != null && analysis.getOptimalTakeProfit() != null) {
                        double cycleRisk = Math.abs(currentPrice - analysis.getOptimalStopLoss());
                        double cycleReward = Math.abs(analysis.getOptimalTakeProfit() - currentPrice);
                        double cycleRR = cycleRisk > 0 ? cycleReward / cycleRisk : 0;

                        log.info("    {}周期: 止损={}, 止盈={}, 风险收益比=1:{}",
                                tf,
                                String.format("%.4f", analysis.getOptimalStopLoss()),
                                String.format("%.4f", analysis.getOptimalTakeProfit()),
                                String.format("%.2f", cycleRR));
                    }
                }
            }
        }
        
        // 确定当前趋势
        String currentTrend = "NEUTRAL";
        if (bullishCount > bearishCount) {
            currentTrend = bullishCount >= analyses.size() * 0.7 ? "STRONG_BULLISH" : "BULLISH";
        } else if (bearishCount > bullishCount) {
            currentTrend = bearishCount >= analyses.size() * 0.7 ? "STRONG_BEARISH" : "BEARISH";
        }
        
        // 构建趋势方向描述
        String trendDirection = currentTrend;
        if (dominantPhase != null) {
            trendDirection = currentTrend + " - " + dominantPhase;
        }
        
        // 整合价格目标和止损水平（去重和排序）
        List<PriceTarget> consolidatedTargets = consolidatePriceTargets(priceTargets, signalBuy);
        List<StopLossLevel> consolidatedStopLosses = consolidateStopLossLevels(stopLossLevels);
        
        // 生成策略描述
        String targetStrategy = buildTargetStrategy(consolidatedTargets, dominantPhase, signalBuy);
        String stopLossStrategy = buildStopLossStrategy(consolidatedStopLosses, invalidCount > 0, dominantPhase);
        
        // 生成警告和建议
        List<String> warnings = generateWarnings(analyses, invalidCount, signalBuy);
        List<String> recommendations = generateRecommendations(analyses, signalBuy);
        
        // 确定交易建议
        String tradingAdvice = determineTradingAdvice(riskRewardRatio, invalidCount, analyses.size(), signalBuy, bullishCount);
        
        return PriceAnalysisDto.builder()
                .currentPrice(currentPrice)
                .currentTrend(currentTrend)
                .trendDirection(trendDirection)
                .agreementLevel(calculateAgreementLevel(analyses))
                .compositeScore(calculateCompositeScore(analyses))
                .priceTargets(consolidatedTargets)
                .stopLossLevels(consolidatedStopLosses)
                .optimalStopLoss(weightedStopLoss > 0 ? weightedStopLoss : null)
                .optimalTakeProfit(weightedTakeProfit > 0 ? weightedTakeProfit : null)
                .riskRewardRatio(riskRewardRatio > 0 ? riskRewardRatio : null)
                .breakevenPrice(calculateBreakevenPrice(currentPrice, weightedStopLoss, weightedTakeProfit))
                .targetStrategy(targetStrategy)
                .stopLossStrategy(stopLossStrategy)
                .warnings(warnings)
                .recommendations(recommendations)
                .tradingAdvice(tradingAdvice)
                .build();
    }

    /**
     * 计算加权平均值
     */
    private double calculateWeightedAverage(List<Double> values, Map<TimeFrame, Double> weights,
                                            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses) {
        double weightedSum = 0;
        double totalWeight = 0;
        int index = 0;

        for (TimeFrame tf : analyses.keySet()) {
            if (index < values.size()) {
                double weight = weights.getOrDefault(tf, 1.0);
                weightedSum += values.get(index) * weight;
                totalWeight += weight;
                index++;
            }
        }

        return totalWeight > 0 ? weightedSum / totalWeight : (values.isEmpty() ? 0 : values.get(0));
    }

    /**
     * 整合价格目标（去重和排序）
     */
    private List<PriceTarget> consolidatePriceTargets(List<PriceTarget> targets, boolean isBuySignal) {
        if (targets == null || targets.isEmpty()) {
            return new ArrayList<>();
        }

        // 按价格分组，取最高概率的目标
        Map<Double, PriceTarget> bestTargets = new HashMap<>();
        for (PriceTarget target : targets) {
            double priceKey = Math.round(target.getPrice() * 10000.0) / 10000.0; // 保留4位小数
            PriceTarget existing = bestTargets.get(priceKey);

            if (existing == null || target.getProbability() > existing.getProbability()) {
                bestTargets.put(priceKey, target);
            }
        }

        // 排序
        List<PriceTarget> consolidated = new ArrayList<>(bestTargets.values());
        consolidated.sort((t1, t2) -> {
            if (isBuySignal) {
                return Double.compare(t1.getPrice(), t2.getPrice()); // 买入：价格升序
            } else {
                return Double.compare(t2.getPrice(), t1.getPrice()); // 卖出：价格降序
            }
        });

        // 重新分配级别
        for (int i = 0; i < consolidated.size(); i++) {
            consolidated.get(i).setLevel(i + 1);
        }

        return consolidated.stream().limit(4).collect(java.util.stream.Collectors.toList()); // 最多返回4个目标
    }

    /**
     * 整合止损水平（去重和排序）
     */
    private List<StopLossLevel> consolidateStopLossLevels(List<StopLossLevel> stopLosses) {
        if (stopLosses == null || stopLosses.isEmpty()) {
            return new ArrayList<>();
        }

        // 按价格分组，取主要止损或风险最低的
        Map<Double, StopLossLevel> bestStopLosses = new HashMap<>();
        for (StopLossLevel stopLoss : stopLosses) {
            double priceKey = Math.round(stopLoss.getPrice() * 10000.0) / 10000.0;
            StopLossLevel existing = bestStopLosses.get(priceKey);

            if (existing == null ||
                    (stopLoss.isPrimary() && !existing.isPrimary()) ||
                    (stopLoss.getRiskPercentage() < existing.getRiskPercentage())) {
                bestStopLosses.put(priceKey, stopLoss);
            }
        }

        // 按风险百分比排序（风险越低越好）
        List<StopLossLevel> consolidated = new ArrayList<>(bestStopLosses.values());
        consolidated.sort(Comparator.comparingDouble(StopLossLevel::getRiskPercentage));

        // 重新分配级别
        for (int i = 0; i < consolidated.size(); i++) {
            consolidated.get(i).setLevel(i + 1);
        }

        return consolidated.stream().limit(3).collect(java.util.stream.Collectors.toList()); // 最多返回3个止损水平
    }

    /**
     * 构建目标策略描述
     */
    private String buildTargetStrategy(List<PriceTarget> targets, ElliottPhase dominantPhase, boolean isBuySignal) {
        if (targets == null || targets.isEmpty()) {
            return "无明确目标策略";
        }

        StringBuilder strategy = new StringBuilder();

        // 根据主导相位调整策略
        if (dominantPhase != null) {
            switch (dominantPhase) {
                case WAVE3:
                    strategy.append("当前处于主升浪3阶段，目标可以更积极。");
                    break;
                case WAVE5:
                    strategy.append("当前处于末端浪5阶段，注意动能衰竭，目标应保守。");
                    break;
                case WAVE2:
                case WAVE4:
                    strategy.append("当前处于调整浪阶段，目标应等待突破确认。");
                    break;
                default:
                    strategy.append("根据波浪相位制定目标策略。");
                    break;
            }
        }

        strategy.append("\n建议采用分批止盈策略：");

        // 取前3个主要目标
        List<PriceTarget> mainTargets = targets.stream()
                .filter(t -> t.getLevel() <= 3)
                .sorted(Comparator.comparingInt(PriceTarget::getLevel))
                .limit(3)
                .collect(java.util.stream.Collectors.toList());

        for (PriceTarget target : mainTargets) {
            strategy.append(String.format("\n- 目标%d: %.4f (%s，概率%.0f%%，风险收益比1:%.2f)",
                    target.getLevel(),
                    target.getPrice(),
                    target.getDescription() != null ? target.getDescription() : "未描述",
                    target.getProbability() * 100,
                    target.getRiskRewardRatio()));
        }

        return strategy.toString();
    }

    /**
     * 构建止损策略描述
     */
    private String buildStopLossStrategy(List<StopLossLevel> stopLosses, boolean hasInvalidation, ElliottPhase dominantPhase) {
        if (stopLosses == null || stopLosses.isEmpty()) {
            return "无明确止损策略";
        }

        StringBuilder strategy = new StringBuilder();

        if (hasInvalidation) {
            strategy.append("⚠️ 部分周期波浪结构已失效，建议使用更严格的止损。\n");
        }

        // 根据相位调整止损策略
        if (dominantPhase != null) {
            switch (dominantPhase) {
                case WAVE3:
                    strategy.append("主升浪阶段，止损可以相对宽松。\n");
                    break;
                case WAVE5:
                    strategy.append("末端浪阶段，波动可能加大，需收紧止损。\n");
                    break;
                case WAVE1:
                    strategy.append("启动浪阶段，建议设置较紧止损以控制风险。\n");
                    break;
            }
        }

        strategy.append("建议止损策略：");

        // 取主要止损和前2个备选止损
        List<StopLossLevel> mainStopLosses = stopLosses.stream()
                .filter(s -> s.isPrimary() || s.getLevel() <= 2)
                .sorted((s1, s2) -> {
                    if (s1.isPrimary() && !s2.isPrimary()) return -1;
                    if (!s1.isPrimary() && s2.isPrimary()) return 1;
                    return Integer.compare(s1.getLevel(), s2.getLevel());
                })
                .limit(3)
                .collect(java.util.stream.Collectors.toList());

        for (StopLossLevel stopLoss : mainStopLosses) {
            strategy.append(String.format("\n- %s止损: %.4f (风险%.1f%%，%s)",
                    stopLoss.isPrimary() ? "主要" : "备选",
                    stopLoss.getPrice(),
                    stopLoss.getRiskPercentage(),
                    stopLoss.getDescription() != null ? stopLoss.getDescription() : "未描述"));
        }

        return strategy.toString();
    }

    /**
     * 生成警告列表
     */
    private List<String> generateWarnings(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            int invalidCount,
            boolean signalBuy) {

        List<String> warnings = new ArrayList<>();

        if (invalidCount > 0) {
            warnings.add(String.format("%d个周期波浪结构已失效", invalidCount));
        }

        // 检查方向一致性
        int bullishCount = 0;
        for (ElliottWaveEvaluator.TimeFrameAnalysis analysis : analyses.values()) {
            if (analysis.isBullish()) bullishCount++;
        }

        double bullishRatio = (double) bullishCount / analyses.size();
        if ((signalBuy && bullishRatio < 0.5) || (!signalBuy && bullishRatio > 0.5)) {
            warnings.add("信号方向与多数周期不一致");
        }

        return warnings;
    }

    /**
     * 生成建议列表
     */
    private List<String> generateRecommendations(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            boolean signalBuy) {

        List<String> recommendations = new ArrayList<>();

        // 检查是否有高质量周期
        boolean hasHighQuality = analyses.values().stream()
                .anyMatch(a -> a.getConfidence() > 80 && a.getConfluenceScore() > 0.7);

        if (hasHighQuality) {
            recommendations.add("存在高质量周期分析，可增强信心");
        }

        // 风险管理建议
        recommendations.add("建议采用分批止盈策略，降低风险");
        recommendations.add("严格执行止损，控制单笔损失在2%以内");

        return recommendations;
    }

    /**
     * 确定交易建议
     */
    private String determineTradingAdvice(double riskRewardRatio, int invalidCount, int totalCount,
                                           boolean signalBuy, int bullishCount) {
        double invalidRatio = (double) invalidCount / totalCount;
        double bullishRatio = (double) bullishCount / totalCount;

        // 检查方向一致性
        boolean directionConflict = (signalBuy && bullishRatio < 0.5) || (!signalBuy && bullishRatio > 0.5);

        if (invalidRatio >= 0.5 || directionConflict || riskRewardRatio < 1.0) {
            return "AVOID";
        } else if (riskRewardRatio >= 2.0 && invalidRatio == 0 && !directionConflict) {
            return "CONFIRMED";
        } else {
            return "CAUTIOUS";
        }
    }

    /**
     * 计算一致性等级
     */
    private int calculateAgreementLevel(Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses) {
        if (analyses.isEmpty()) {
            return 1;
        }

        int bullishCount = 0;
        for (ElliottWaveEvaluator.TimeFrameAnalysis analysis : analyses.values()) {
            if (analysis.isBullish()) bullishCount++;
        }

        double agreementRatio = Math.max((double) bullishCount / analyses.size(),
                (double) (analyses.size() - bullishCount) / analyses.size());

        if (agreementRatio >= 0.9) return 5;
        if (agreementRatio >= 0.7) return 4;
        if (agreementRatio >= 0.5) return 3;
        if (agreementRatio >= 0.3) return 2;
        return 1;
    }

    /**
     * 计算综合得分
     */
    private double calculateCompositeScore(Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses) {
        if (analyses.isEmpty()) {
            return 0.0;
        }

        return analyses.values().stream()
                .mapToDouble(ElliottWaveEvaluator.TimeFrameAnalysis::getScore)
                .average()
                .orElse(0.0);
    }

    /**
     * 计算盈亏平衡点
     */
    private Double calculateBreakevenPrice(double currentPrice, double stopLoss, double takeProfit) {
        if (stopLoss <= 0 || takeProfit <= 0) {
            return null;
        }

        // 盈亏平衡点通常是当前价格（假设没有手续费）
        return currentPrice;
    }

    /**
     * 记录风险评估
     */
    private void logRiskAssessment(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            double compositeScore,
            boolean signalBuy) {

        log.info("  ⚠️  【风险评估】");

        // 1. 检查失效状态
        int invalidCount = 0;
        int nearInvalidCount = 0;

        for (ElliottWaveEvaluator.TimeFrameAnalysis analysis : analyses.values()) {
            if (analysis.isInvalidation()) {
                invalidCount++;
                ElliottWaveEvaluator.PricePositionInfo priceInfo = analysis.getPriceInfo();
                if (priceInfo != null && priceInfo.getDistanceToInvalidation() != null) {
                    if (Math.abs(priceInfo.getDistanceToInvalidation()) < 3.0) {
                        nearInvalidCount++;
                    }
                }
            }
        }

        if (nearInvalidCount >= 2) {
            log.warn("    ❌ 高风险：多个周期接近失效价格");
        } else if (invalidCount > 0) {
            log.warn("    ⚠️  中风险：{}个周期波浪结构失效", invalidCount);
        } else {
            log.info("    ✅ 低风险：所有周期波浪结构有效");
        }

        // 2. 检查方向一致性
        int bullishCount = 0;
        for (ElliottWaveEvaluator.TimeFrameAnalysis analysis : analyses.values()) {
            if (analysis.isBullish()) bullishCount++;
        }

        double bullishRatio = (double) bullishCount / analyses.size();
        if ((signalBuy && bullishRatio < 0.5) || (!signalBuy && bullishRatio > 0.5)) {
            log.warn("    ⚠️  方向风险：信号方向与多数周期不一致");
        }

        // 3. 检查综合得分
        if (compositeScore < 0.4) {
            log.warn("    ❌ 高风险：综合得分过低({}/100)", String.format("%.0f", compositeScore * 100));
        } else if (compositeScore < 0.6) {
            log.warn("    ⚠️  中风险：综合得分中等({}/100)", String.format("%.0f", compositeScore * 100));
        } else {
            log.info("    ✅ 低风险：综合得分良好({}/100)", String.format("%.0f", compositeScore * 100));
        }
    }

    /**
     * 记录交易策略建议
     */
    private void logTradingStrategyRecommendation(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            String trendDirection,
            int agreementLevel,
            boolean signalBuy) {

        log.info("  📋 【交易策略建议】");

        // 1. 入场时机建议
        if (agreementLevel >= 4) {
            log.info("    ✅ 入场时机：多周期一致性高，可立即入场");
        } else if (agreementLevel >= 3) {
            log.info("    ⚠️  入场时机：多周期一致性中等，建议等待更好价位");
        } else {
            log.info("    ❌ 入场时机：多周期一致性低，建议观望");
        }

        // 2. 仓位管理建议
        double avgScore = analyses.values().stream()
                .mapToDouble(ElliottWaveEvaluator.TimeFrameAnalysis::getScore)
                .average()
                .orElse(0);

        if (avgScore >= 0.7) {
            log.info("    ✅ 仓位管理：可考虑正常仓位");
        } else if (avgScore >= 0.5) {
            log.info("    ⚠️  仓位管理：建议轻仓试探");
        } else {
            log.info("    ❌ 仓位管理：不建议建仓");
        }

        // 3. 止损策略建议
        boolean hasInvalidation = analyses.values().stream().anyMatch(ElliottWaveEvaluator.TimeFrameAnalysis::isInvalidation);
        if (hasInvalidation) {
            log.info("    ⚠️  止损策略：存在失效周期，建议设置更严格止损");
        } else {
            log.info("    ✅ 止损策略：波浪结构稳定，可按常规止损");
        }

        // 4. 分批止盈建议
        log.info("    💰 止盈策略：建议采用分批止盈：");
        log.info("        - 第一目标：风险收益比1:1.5位置");
        log.info("        - 第二目标：风险收益比1:2.5位置");
        log.info("        - 第三目标：风险收益比1:4.0位置");
    }

    /**
     * 获取趋势方向描述
     */
    private String getTrendDirectionDescription(String trendDirection) {
        switch (trendDirection) {
            case "STRONG_BULLISH": return "强烈看涨";
            case "BULLISH": return "看涨";
            case "NEUTRAL": return "中性";
            case "BEARISH": return "看跌";
            case "STRONG_BEARISH": return "强烈看跌";
            default: return "未知";
        }
    }

    /**
     * 获取一致性等级描述
     */
    private String getAgreementLevelDescription(int agreementLevel) {
        switch (agreementLevel) {
            case 5: return "极高一致性";
            case 4: return "高一致性";
            case 3: return "中等一致性";
            case 2: return "低一致性";
            case 1: return "极低一致性";
            default: return "未知";
        }
    }

    /**
     * 截断字符串到指定长度
     */
    public static String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * 记录增强的多周期分析结果（包含详细止盈止损目标）
     */
    void logEnhancedMultiTimeFrameAnalysis(TradingSignalDto signal, ElliottWaveEvaluator.MultiTimeFrameAnalysis multiAnalysis) {
        log.info("=".repeat(120));
        log.info("🌊 多周期艾略特波浪综合分析报告（增强版）");
        log.info("交易对: {}, 信号ID: {}, 类型: {}, 触发价格: {}",
                signal.getSymbol(), signal.getId(), signal.getType(),
                String.format("%.4f", signal.getTriggerPrice()));
        log.info("-".repeat(120));

        // 总体概况
        log.info("【多周期总体概况】");
        log.info("  综合得分: {}/100", String.format("%.0f", multiAnalysis.getCompositeScore() * 100));
        log.info("  趋势方向: {}", multiAnalysis.getTrendDirection());
        log.info("  主导相位: {}", multiAnalysis.getDominantPhase() != null ?
                multiAnalysis.getDominantPhase() : "UNKNOWN");
        log.info("  一致性等级: {}/5", multiAnalysis.getAgreementLevel());

        // 各周期详细分析（增强表格）
        log.info("【各周期详细分析】");
        log.info("  ┌───────────┬───────────┬─────────┬─────────┬─────────┬─────────┬─────────┬────────────┬────────────┬────────────┬────────────┐");
        log.info("  │ 周期      │ 相位      │ 方向    │ 置信度  │ 得分    │ 失效    │ 汇合度  │ 最佳止损   │ 最佳止盈   │ 风险收益比 │ 失效距离%  │");
        log.info("  ├───────────┼───────────┼─────────┼─────────┼─────────┼─────────┼─────────┼────────────┼────────────┼────────────┼────────────┤");

        for (Map.Entry<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> entry : multiAnalysis.getAnalyses().entrySet()) {
            TimeFrame tf = entry.getKey();
            ElliottWaveEvaluator.TimeFrameAnalysis analysis = entry.getValue();
            ElliottWaveEvaluator.PricePositionInfo priceInfo = analysis.getPriceInfo();

            String phase = analysis.getCurrentPhase() != null ?
                    analysis.getCurrentPhase().toString() : "N/A";
            String direction = analysis.isBullish() ? "BULLISH" : "BEARISH";
            String confidence = String.format("%.1f", analysis.getConfidence());
            String score = String.format("%.1f", analysis.getScore() * 100);
            String invalidation = analysis.isInvalidation() ? "是" : "否";
            String confluence = String.format("%.1f", analysis.getConfluenceScore() * 100);

            // 价格位置信息
            String bestStopLoss = "N/A";
            String bestTakeProfit = "N/A";
            String riskRewardRatio = "N/A";
            String invalidationDistance = "N/A";

            if (priceInfo != null) {
                if (analysis.getOptimalStopLoss() != null) {
                    bestStopLoss = String.format("%.4f", analysis.getOptimalStopLoss());
                }
                if (analysis.getOptimalTakeProfit() != null) {
                    bestTakeProfit = String.format("%.4f", analysis.getOptimalTakeProfit());
                }
                if (priceInfo.getRiskRewardRatio() != null) {
                    riskRewardRatio = String.format("1:%.2f", priceInfo.getRiskRewardRatio());
                }
                if (priceInfo.getDistanceToInvalidation() != null) {
                    invalidationDistance = String.format("%.2f", Math.abs(priceInfo.getDistanceToInvalidation()));
                }
            }

            log.info("  │ {}{}{}{}{}{}{}{}{}{}{} │",
                    String.format("%-9s", tf.toString()),
                    String.format("%-9s", phase),
                    String.format("%-7s", direction),
                    String.format("%7s", confidence),
                    String.format("%7s", score),
                    String.format("%7s", invalidation),
                    String.format("%7s", confluence),
                    String.format("%10s", bestStopLoss),
                    String.format("%10s", bestTakeProfit),
                    String.format("%10s", riskRewardRatio),
                    String.format("%10s", invalidationDistance));
        }
        log.info("  └───────────┴───────────┴─────────┴─────────┴─────────┴─────────┴─────────┴────────────┴────────────┴────────────┴────────────┘");

        // 各周期详细目标分析
        logDetailedMultiTimeFrameTargets(multiAnalysis.getAnalyses(), signal);

        // 多周期综合交易建议
        generateMultiTimeFrameTradingAdvice(multiAnalysis, signal);

        // 多周期警告
        if (!multiAnalysis.getWarnings().isEmpty()) {
            log.warn("【多周期警告】");
            for (String warning : multiAnalysis.getWarnings()) {
                log.warn("  ⚠️ {}", warning);
            }
        }

        // 多周期建议
        if (!multiAnalysis.getRecommendations().isEmpty()) {
            log.info("【多周期建议】");
            for (String recommendation : multiAnalysis.getRecommendations()) {
                log.info("  ✓ {}", recommendation);
            }
        }

        log.info("=".repeat(120));
    }
}