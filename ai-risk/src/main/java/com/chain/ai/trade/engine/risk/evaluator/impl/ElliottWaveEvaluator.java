package com.chain.ai.trade.engine.risk.evaluator.impl;

import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.entity.dto.*;
import com.chain.ai.trade.engine.risk.common.TimeFrame;
import com.chain.ai.trade.engine.risk.evaluator.EvaluationContext;
import com.chain.ai.trade.engine.risk.evaluator.QualityEvaluationResult;
import com.chain.ai.trade.engine.risk.evaluator.QualityEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

import static com.chain.ai.trade.engine.risk.evaluator.impl.SmartWeightAdjuster.truncateString;

/**
 * 艾略特波浪分析评估器
 * 评估波浪结构、相位、斐波那契
 */
@Slf4j
@Component
public class ElliottWaveEvaluator implements QualityEvaluator {

    @Value("${risk.evaluator.elliott-wave.weight:1.5}")
    private double weight;

    @Value("${risk.evaluator.elliott-wave.min-bars:200}")
    private int minBars;

    @Value("${risk.evaluator.elliott-wave.fib-tolerance:0.25}")
    private double fibTolerance;

    // 多因子权重配置
    @Value("${risk.evaluator.elliott-wave.factors.base-confidence:0.35}")
    private double baseConfidenceWeight;

    @Value("${risk.evaluator.elliott-wave.factors.structure-completeness:0.25}")
    private double structureCompletenessWeight;

    @Value("${risk.evaluator.elliott-wave.factors.confluence:0.15}")
    private double confluenceWeight;

    @Value("${risk.evaluator.elliott-wave.factors.invalidation:0.15}")
    private double invalidationWeight;

    @Value("${risk.evaluator.elliott-wave.factors.direction-match:0.10}")
    private double directionMatchWeight;

    @Value("${risk.evaluator.elliott-wave.factors.price-position:0.05}")
    private double pricePositionWeight;

    // 风险等级阈值配置
    @Value("${risk.evaluator.elliott-wave.risk-thresholds.high-risk-max:0.4}")
    private double highRiskMax;

    @Value("${risk.evaluator.elliott-wave.risk-thresholds.medium-risk-max:0.7}")
    private double mediumRiskMax;

    @Value("${risk.evaluator.elliott-wave.risk-thresholds.low-risk-min:0.7}")
    private double lowRiskMin;

    // 价格位置分析配置
    @Value("${risk.evaluator.elliott-wave.price-position.channel-upper-threshold:80}")
    private double channelUpperThreshold;

    @Value("${risk.evaluator.elliott-wave.price-position.channel-lower-threshold:20}")
    private double channelLowerThreshold;

    @Value("${risk.evaluator.elliott-wave.price-position.min-risk-reward-ratio:1.5}")
    private double minRiskRewardRatio;

    @Value("${risk.evaluator.elliott-wave.price-position.warning-risk-reward-ratio:1.0}")
    private double warningRiskRewardRatio;

    // 多周期分析配置
    @Value("${risk.evaluator.elliott-wave.multi-timeframe.enabled:false}")
    private boolean multiTimeFrameEnabled;

    @Value("${risk.evaluator.elliott-wave.multi-timeframe.weights.hourly:0.5}")
    private double hourlyWeight;

    @Value("${risk.evaluator.elliott-wave.multi-timeframe.weights.quarterly:0.3}")
    private double quarterlyWeight;

    @Value("${risk.evaluator.elliott-wave.multi-timeframe.weights.minute:0.2}")
    private double minuteWeight;

    @Value("${risk.evaluator.elliott-wave.multi-timeframe.min-agreement:0.6}")
    private double minAgreement;

    // 场景选择配置
    @Value("${risk.evaluator.elliott-wave.scenario-selection.prioritize-current-phase:true}")
    private boolean prioritizeCurrentPhase;

    @Value("${risk.evaluator.elliott-wave.scenario-selection.min-confidence-difference:5.0}")
    private double minConfidenceDifference;

    @Value("${risk.evaluator.elliott-wave.scenario-selection.direction-weight:0.6}")
    private double directionWeight;

    @Value("${risk.evaluator.elliott-wave.scenario-selection.phase-weight:0.4}")
    private double phaseWeight;

    // ========== 配置包装类（非静态内部类） ==========

    /**
     * 因子权重配置
     */
    private class FactorWeights {
        final double baseConfidence;
        final double structureCompleteness;
        final double confluence;
        final double invalidation;
        final double directionMatch;
        final double pricePosition;

        FactorWeights() {
            this.baseConfidence = baseConfidenceWeight;
            this.structureCompleteness = structureCompletenessWeight;
            this.confluence = confluenceWeight;
            this.invalidation = invalidationWeight;
            this.directionMatch = directionMatchWeight;
            this.pricePosition = pricePositionWeight;
        }
    }

    /**
     * 风险等级阈值配置
     */
    private class RiskThresholds {
        final double highRiskMax;
        final double mediumRiskMax;
        final double lowRiskMin;

        RiskThresholds() {
            this.highRiskMax = ElliottWaveEvaluator.this.highRiskMax;
            this.mediumRiskMax = ElliottWaveEvaluator.this.mediumRiskMax;
            this.lowRiskMin = ElliottWaveEvaluator.this.lowRiskMin;
        }
    }

    /**
     * 价格位置分析配置
     */
    private class PricePositionConfig {
        final double channelUpperThreshold;
        final double channelLowerThreshold;
        final double minRiskRewardRatio;
        final double warningRiskRewardRatio;

        PricePositionConfig() {
            this.channelUpperThreshold = ElliottWaveEvaluator.this.channelUpperThreshold;
            this.channelLowerThreshold = ElliottWaveEvaluator.this.channelLowerThreshold;
            this.minRiskRewardRatio = ElliottWaveEvaluator.this.minRiskRewardRatio;
            this.warningRiskRewardRatio = ElliottWaveEvaluator.this.warningRiskRewardRatio;
        }
    }

    /**
     * 多周期分析配置
     */
    private class MultiTimeFrameConfig {
        final boolean enabled;
        final double hourlyWeight;
        final double quarterlyWeight;
        final double minuteWeight;
        final double minAgreement;

        MultiTimeFrameConfig() {
            this.enabled = ElliottWaveEvaluator.this.multiTimeFrameEnabled;
            this.hourlyWeight = ElliottWaveEvaluator.this.hourlyWeight;
            this.quarterlyWeight = ElliottWaveEvaluator.this.quarterlyWeight;
            this.minuteWeight = ElliottWaveEvaluator.this.minuteWeight;
            this.minAgreement = ElliottWaveEvaluator.this.minAgreement;
        }
    }

    /**
     * 场景选择配置
     */
    private class ScenarioSelectionConfig {
        final boolean prioritizeCurrentPhase;
        final double minConfidenceDifference;
        final double directionWeight;
        final double phaseWeight;

        ScenarioSelectionConfig() {
            this.prioritizeCurrentPhase = ElliottWaveEvaluator.this.prioritizeCurrentPhase;
            this.minConfidenceDifference = ElliottWaveEvaluator.this.minConfidenceDifference;
            this.directionWeight = ElliottWaveEvaluator.this.directionWeight;
            this.phaseWeight = ElliottWaveEvaluator.this.phaseWeight;
        }
    }

    // ========== 数据容器类 ==========

    /**
     * 艾略特波浪分析结果容器
     */
    @Data
    private static class ElliottAnalysisResult {
        private TradingSignalDto signal;
        private BarSeries series;
        private int endIndex;
        private ElliottPhase currentPhase;
        private boolean invalidation;
        private ElliottChannel channel;
        private ElliottRatio ratio;
        private org.ta4j.core.num.Num confluenceScoreNum;
        private int waveCountValue;
        private int filteredWaveCountValue;
        private org.ta4j.core.indicators.elliott.ElliottScenarioSet scenarioSet;
        private Optional<ElliottScenario> baseCase;
        private PricePositionInfo priceInfo;
    }

    /**
     * 分数计算结果容器
     */
    @Data
    private static class ScoreResult {
        private double baseConfidenceScore;
        private double structureScore;
        private double confluenceScore;
        private double invalidationScore;
        private double directionMatchScore;
        private double pricePositionScore;
    }

    // ========== 服务类 ==========

    /**
     * 艾略特波浪分析服务
     */
    class ElliottAnalysisService {

        /**
         * 执行单周期艾略特波浪分析
         */
        public ElliottAnalysisResult analyzeSingleTimeFrame(TradingSignalDto signal, AnalysisData data) {
            BarSeries series = IndicatorWrapHelper.buildSeries(data.getBars());
            int endIndex = series.getEndIndex() - 1;

            // 创建ElliottWaveFacade
            ElliottDegree degree = ElliottDegree.PRIMARY;
            ElliottSwingCompressor compressor = new ElliottSwingCompressor(series);
            ElliottWaveFacade facade = ElliottWaveFacade.zigZag(
                    series,
                    degree,
                    Optional.of(series.numFactory().numOf(fibTolerance)),
                    Optional.of(compressor)
            );

            // 获取当前相位
            ElliottPhase currentPhase = facade.phase().getValue(endIndex);

            // 获取失效状态
            boolean invalidation = facade.invalidation().getValue(endIndex);

            // 获取通道信息
            ElliottChannel channel = facade.channel().getValue(endIndex);

            // 获取斐波那契比率
            ElliottRatio ratio = facade.ratio().getValue(endIndex);

            // 获取汇合评分
            org.ta4j.core.num.Num confluenceScoreNum = facade.confluence().getValue(endIndex);

            // 获取波浪计数
            int waveCountValue = facade.waveCount().getValue(endIndex);
            int filteredWaveCountValue = facade.filteredWaveCount().getValue(endIndex);

            // 获取场景分析
            org.ta4j.core.indicators.elliott.ElliottScenarioSet scenarioSet = facade.scenarios().getValue(endIndex);
            Optional<ElliottScenario> baseCase = selectBestScenario(currentPhase, signal, scenarioSet);

            // 计算价格位置信息
            PricePositionInfo priceInfo = calculatePricePosition(signal, series, channel, baseCase, currentPhase);

            // 生成交易建议（增强版）
            generateTradingRecommendationsEnhanced(signal, priceInfo, currentPhase, baseCase, invalidation, channel);

            // 构建结果
            ElliottAnalysisResult result = new ElliottAnalysisResult();
            result.setSignal(signal);
            result.setSeries(series);
            result.setEndIndex(endIndex);
            result.setCurrentPhase(currentPhase);
            result.setInvalidation(invalidation);
            result.setChannel(channel);
            result.setRatio(ratio);
            result.setConfluenceScoreNum(confluenceScoreNum);
            result.setWaveCountValue(waveCountValue);
            result.setFilteredWaveCountValue(filteredWaveCountValue);
            result.setScenarioSet(scenarioSet);
            result.setBaseCase(baseCase);
            result.setPriceInfo(priceInfo);

            return result;
        }

        /**
         * 增强的多周期分析
         */
        public MultiTimeFrameAnalysis analyzeMultiTimeFrame(TradingSignalDto signal, EvaluationContext context) {
            Map<TimeFrame, AnalysisData> multiTimeFrameData = context.getMultiTimeFrameData();

            if (multiTimeFrameData == null || multiTimeFrameData.isEmpty()) {
                log.warn("未提供多周期数据，无法进行多周期分析");
                return null;
            }

            Map<TimeFrame, TimeFrameAnalysis> analyses = new HashMap<>();

            // 1. 分析各周期
            for (Map.Entry<TimeFrame, AnalysisData> entry : multiTimeFrameData.entrySet()) {
                TimeFrame tf = entry.getKey();
                AnalysisData data = entry.getValue();

                try {
                    TimeFrameAnalysis tfAnalysis = analyzeTimeFrame(signal, data, tf);
                    if (tfAnalysis != null) {
                        analyses.put(tf, tfAnalysis);
                    }
                } catch (Exception e) {
                    log.warn("{}周期分析失败: {}", tf, e.getMessage());
                }
            }

            if (analyses.isEmpty()) {
                return null;
            }

            // 2. 智能权重调整
            SmartWeightAdjuster weightAdjuster = new SmartWeightAdjuster();
            Map<TimeFrame, Double> adjustedWeights = weightAdjuster.calculateAdjustedWeights(analyses, signal);

            // 3. 计算综合结果
            String trendDirection = calculateEnhancedTrendDirection(analyses, adjustedWeights);
            int agreementLevel = calculateEnhancedAgreementLevel(analyses, adjustedWeights);
            double compositeScore = calculateEnhancedCompositeScore(analyses, adjustedWeights);

            // 4. 生成警告和建议
            List<String> warnings = generateEnhancedWarnings(analyses, compositeScore, agreementLevel);
            List<String> recommendations = generateEnhancedRecommendations(analyses, compositeScore, trendDirection, signal);

            // 5. 记录增强的多周期分析结果
            MultiTimeFrameAnalysis multiAnalysis = MultiTimeFrameAnalysis.builder()
                    .analyses(analyses)
                    .compositeScore(compositeScore)
                    .trendDirection(trendDirection)
                    .agreementLevel(agreementLevel)
                    .warnings(warnings)
                    .recommendations(recommendations)
                    .build();

            // 使用增强的日志方法
            weightAdjuster.logEnhancedMultiTimeFrameAnalysis(signal, multiAnalysis);


            return multiAnalysis;
        }

        /**
         * 分析指定时间框架的数据
         */
        public TimeFrameAnalysis analyzeTimeFrame(TradingSignalDto signal, AnalysisData data, TimeFrame timeFrame) {
            log.info("========== {}周期艾略特波浪分析开始 (交易对: {}, 信号ID: {}, 信号类型: {}) ==========",
                    timeFrame, signal.getSymbol(), signal.getId(), signal.getType());

            // 检查数据是否足够
            if (data == null || data.getBars().size() < minBars) {
                log.warn("{}周期数据不足，需要至少{}根K线", timeFrame, minBars);
                log.info("========== {}周期艾略特波浪分析结束 ==========", timeFrame);
                return null;
            }

            try {
                // 构建BarSeries
                BarSeries series = IndicatorWrapHelper.buildSeries(data.getBars());
                int endIndex = series.getEndIndex() - 1;

                // 创建ElliottWaveFacade
                ElliottDegree degree = getDegreeForTimeFrame(timeFrame);
                ElliottSwingCompressor compressor = new ElliottSwingCompressor(series);
                ElliottWaveFacade facade = ElliottWaveFacade.zigZag(
                        series,
                        degree,
                        Optional.of(series.numFactory().numOf(fibTolerance)),
                        Optional.of(compressor)
                );

                // 获取分析结果
                ElliottPhase currentPhase = facade.phase().getValue(endIndex);
                boolean invalidation = facade.invalidation().getValue(endIndex);
                ElliottChannel channel = facade.channel().getValue(endIndex);
                org.ta4j.core.num.Num confluenceScoreNum = facade.confluence().getValue(endIndex);

                // ========== 打印详细分析结果 ==========

                // 1. 相位信息
                log.info("【{}周期相位信息】", timeFrame);
                log.info("  当前相位: {}", currentPhase);
                log.info("  是否为驱动浪: {}", currentPhase.isImpulse());
                log.info("  是否为调整浪: {}", currentPhase.isCorrective());

                // 2. 失效状态
                log.info("【{}周期失效状态】", timeFrame);
                log.info("  是否失效: {}", invalidation);

                // 3. 通道信息
                log.info("【{}周期通道信息】", timeFrame);
                if (channel != null && channel.isValid()) {
                    log.info("  通道有效: {}", channel.isValid());
                    log.info("  上轨: {}", channel.upper() != null ? channel.upper().toString() : "N/A");
                    log.info("  下轨: {}", channel.lower() != null ? channel.lower().toString() : "N/A");
                    log.info("  中轨: {}", channel.median() != null ? channel.median().toString() : "N/A");
                } else {
                    log.info("  通道有效: false");
                }

                // 4. 汇合评分
                log.info("【{}周期汇合评分】", timeFrame);
                log.info("  汇合评分: {}", confluenceScoreNum != null ? confluenceScoreNum.toString() : "N/A");
                boolean isConfluent = confluenceScoreNum != null && confluenceScoreNum.isGreaterThan(series.numFactory().numOf(0.5));
                log.info("  是否汇合: {}", isConfluent);

                // 获取最佳场景
                org.ta4j.core.indicators.elliott.ElliottScenarioSet scenarioSet = facade.scenarios().getValue(endIndex);

                // 5. 场景分析
                log.info("【{}周期场景分析】", timeFrame);
                Optional<ElliottScenario> baseCase = scenarioSet.base();
                if (baseCase.isPresent()) {
                    ElliottScenario base = baseCase.get();
                    log.info("  基础场景:");
                    log.info("    相位: {}", base.currentPhase());
                    log.info("    类型: {}", base.type());
                    log.info("    置信度: {}%", String.format("%.2f", base.confidence().asPercentage()));
                    String direction = base.hasKnownDirection() ? (base.isBullish() ? "BULLISH" : "BEARISH") : "UNKNOWN";
                    log.info("    方向: {}", direction);
                    log.info("    失效价格: {}", base.invalidationPrice() != null ? base.invalidationPrice().toString() : "N/A");
                    log.info("    主要目标: {}", base.primaryTarget() != null ? base.primaryTarget().toString() : "N/A");

                    // 打印因子评分
                    ElliottConfidence elliottConfidence = base.confidence();
                    log.info("    因子评分:");
                    log.info("      斐波那契: {}%", String.format("%.2f", elliottConfidence.fibonacciScore().doubleValue() * 100.0));
                    log.info("      时间: {}%", String.format("%.2f", elliottConfidence.timeProportionScore().doubleValue() * 100.0));
                    log.info("      交替: {}%", String.format("%.2f", elliottConfidence.alternationScore().doubleValue() * 100.0));
                    log.info("      通道: {}%", String.format("%.2f", elliottConfidence.channelScore().doubleValue() * 100.0));
                    log.info("      完整性: {}%", String.format("%.2f", elliottConfidence.completenessScore().doubleValue() * 100.0));
                    log.info("    主要原因: {}", elliottConfidence.primaryReason() != null ? elliottConfidence.primaryReason() : "N/A");
                    log.info("    最弱因子: {}", elliottConfidence.weakestFactor() != null ? elliottConfidence.weakestFactor() : "N/A");
                } else {
                    log.info("  基础场景: 无");
                }

                List<ElliottScenario> alternatives = scenarioSet.alternatives();
                if (!alternatives.isEmpty()) {
                    log.info("  备选场景 ({} 个):", alternatives.size());
                    for (int i = 0; i < Math.min(alternatives.size(), 3); i++) {
                        ElliottScenario alt = alternatives.get(i);
                        log.info("    {}. 相位: {}, 类型: {}, 置信度: {}%",
                                i + 1, alt.currentPhase(), alt.type(), String.format("%.2f", alt.confidence().asPercentage()));
                    }
                } else {
                    log.info("  备选场景: 无");
                }

                Optional<ElliottScenario> bestScenario = selectBestScenario(currentPhase, signal, scenarioSet);

                // 计算价格位置信息
                PricePositionInfo priceInfo = calculatePricePosition(signal, series, channel, bestScenario, currentPhase);

                // 生成交易建议（增强版）
                generateTradingRecommendationsEnhanced(signal, priceInfo, currentPhase, bestScenario, invalidation, channel);

                // 计算得分
                double score = bestScenario.map(s -> s.confidence().asPercentage() / 100.0).orElse(0.5);

                // 判断方向
                boolean isBullish = bestScenario.map(s -> s.hasKnownDirection() && s.isBullish()).orElse(false);

                // 6. 最终结果摘要（增强版，包含目标信息）
                log.info("【{}周期分析结果摘要】", timeFrame);
                log.info("  最终得分: {}", String.format("%.3f", score));
                log.info("  趋势方向: {}", isBullish ? "BULLISH" : "BEARISH");
                log.info("  相位: {}", currentPhase);
                log.info("  置信度: {}%", String.format("%.2f", bestScenario.map(s -> s.confidence().asPercentage()).orElse(0.0)));
                log.info("  失效状态: {}", invalidation ? "是" : "否");
                log.info("  汇合度: {}%", String.format("%.1f", confluenceScoreNum != null ? confluenceScoreNum.doubleValue() * 100 : 0.0));

                // 价格位置信息详细日志
                logPricePositionInfoDetails(timeFrame, priceInfo, invalidation);

                log.info("========== {}周期艾略特波浪分析结束 ==========", timeFrame);

                return TimeFrameAnalysis.builder()
                        .timeFrame(timeFrame)
                        .currentPhase(currentPhase)
                        .isBullish(isBullish)
                        .confidence(bestScenario.map(s -> s.confidence().asPercentage()).orElse(0.0))
                        .score(score)
                        .priceInfo(priceInfo)
                        .channel(channel)
                        .bestScenario(bestScenario.orElse(null))
                        .invalidation(invalidation)
                        .confluenceScore(confluenceScoreNum != null ? confluenceScoreNum.doubleValue() : 0.0)
                        // 新增目标信息
                        .priceTargets(priceInfo != null ? priceInfo.getPriceTargets() : null)
                        .stopLossLevels(priceInfo != null ? priceInfo.getStopLossLevels() : null)
                        .optimalStopLoss(priceInfo != null ? priceInfo.getOptimalStopLoss() : null)
                        .optimalTakeProfit(priceInfo != null ? priceInfo.getOptimalTakeProfit() : null)
                        .breakevenPrice(priceInfo != null ? priceInfo.getBreakevenPrice() : null)
                        .targetStrategy(priceInfo != null ? priceInfo.getTargetStrategy() : null)
                        .stopLossStrategy(priceInfo != null ? priceInfo.getStopLossStrategy() : null)
                        .build();

            } catch (Exception e) {
                log.error("{}周期艾略特波浪分析失败: {}", timeFrame, e.getMessage(), e);
                log.info("========== {}周期艾略特波浪分析结束 ==========", timeFrame);
                return null;
            }
        }

        /**
         * 记录价格位置信息详情
         */
        private void logPricePositionInfoDetails(TimeFrame timeFrame, PricePositionInfo priceInfo, boolean isInvalidation) {
            if (priceInfo != null) {
                log.info("【价格位置信息】");
                log.info("  📊 当前价格: {}", String.format("%.4f", priceInfo.getCurrentPrice()));

                // 通道位置信息
                if (priceInfo.getChannelPosition() != null) {
                    log.info("  📍 通道位置: {}%", String.format("%.1f", priceInfo.getChannelPosition()));
                }
                if (priceInfo.getZone() != null) {
                    try {
                        PriceZone zone = PriceZone.valueOf(priceInfo.getZone());
                        log.info("      区域: {} ({})", zone.getName(), zone.getDescription());
                    } catch (IllegalArgumentException e) {
                        log.info("      区域: {}", priceInfo.getZone());
                    }
                }

                // 关键价格距离信息
                if (priceInfo.getDistanceToInvalidation() != null) {
                    double distance = Math.abs(priceInfo.getDistanceToInvalidation());
                    String direction = priceInfo.getAboveInvalidation() != null && priceInfo.getAboveInvalidation() ? "已超过" : "未达到";
                    log.info("  🚨 距离失效价格: {}% ({}点, {})",
                            String.format("%.2f", distance),
                            String.format("%.4f", priceInfo.getPriceToInvalidation()), direction);
                }

                if (priceInfo.getDistanceToTarget() != null) {
                    double distance = Math.abs(priceInfo.getDistanceToTarget());
                    String direction = priceInfo.getDistanceToTarget() > 0 ? "已超过" : "未达到";
                    log.info("  🎯 距离目标价格: {}% ({}点, {})",
                            String.format("%.2f", distance),
                            String.format("%.4f", priceInfo.getPriceToTarget()), direction);
                }

                // 风险收益比
                if (priceInfo.getRiskRewardRatio() != null) {
                    PricePositionConfig config = getPricePositionConfig();
                    String rrStatus = priceInfo.getRiskRewardRatio() >= config.minRiskRewardRatio ? "✅ 良好" :
                            priceInfo.getRiskRewardRatio() >= config.warningRiskRewardRatio ? "⚠️ 可接受" : "❌ 偏低";
                    log.info("  ⚖️  风险收益比: 1:{} ({})",
                            String.format("%.2f", priceInfo.getRiskRewardRatio()), rrStatus);
                }

                // 详细止盈止损目标
                if (priceInfo.getPriceTargets() != null && !priceInfo.getPriceTargets().isEmpty()) {
                    log.info("  🎯 【止盈目标】");
                    for (PriceTarget target : priceInfo.getPriceTargets()) {
                        if (target.getLevel() <= 3) {
                            String rrRatio = target.getRiskRewardRatio() > 0 ?
                                    String.format("1:%.2f", target.getRiskRewardRatio()) : "N/A";
                            log.info("    目标{}: {} ({}，概率{}%，风险收益比{})",
                                    target.getLevel(),
                                    String.format("%.4f", target.getPrice()),
                                    target.getDescription(),
                                    String.format("%.0f", target.getProbability() * 100),
                                    rrRatio);
                        }
                    }
                }

                if (priceInfo.getStopLossLevels() != null && !priceInfo.getStopLossLevels().isEmpty()) {
                    log.info("  🛡️  【止损水平】");
                    for (StopLossLevel stopLoss : priceInfo.getStopLossLevels()) {
                        if (stopLoss.getLevel() <= 3) {
                            log.info("    {}止损: {} ({}，风险{}%)",
                                    stopLoss.isPrimary() ? "主要" : "备选",
                                    String.format("%.4f", stopLoss.getPrice()),
                                    stopLoss.getDescription(),
                                    String.format("%.1f", stopLoss.getRiskPercentage()));
                        }
                    }
                }

                // 最优组合建议
                if (priceInfo.getOptimalTakeProfit() != null && priceInfo.getOptimalStopLoss() != null) {
                    double risk = Math.abs(priceInfo.getCurrentPrice() - priceInfo.getOptimalStopLoss());
                    double reward = Math.abs(priceInfo.getOptimalTakeProfit() - priceInfo.getCurrentPrice());
                    double rrRatio = risk > 0 ? reward / risk : 0;

                    log.info("  💡 【推荐组合】");
                    log.info("    入场价: {}", String.format("%.4f", priceInfo.getCurrentPrice()));
                    log.info("    止损位: {} (风险: {})",
                            String.format("%.4f", priceInfo.getOptimalStopLoss()),
                            String.format("%.2f%%", risk / priceInfo.getCurrentPrice() * 100));
                    log.info("    止盈位: {} (收益: {})",
                            String.format("%.4f", priceInfo.getOptimalTakeProfit()),
                            String.format("%.2f%%", reward / priceInfo.getCurrentPrice() * 100));
                    log.info("    风险收益比: 1:{}", String.format("%.2f", rrRatio));

                    if (rrRatio < 1.0) {
                        log.warn("    ⚠️  警告：风险收益比低于1:1，不建议交易");
                    } else if (rrRatio < 1.5) {
                        log.info("    ℹ️  提示：风险收益比较低，建议谨慎操作");
                    }
                }

                // 失效状态详细分析
                if (isInvalidation) {
                    if (priceInfo.getDistanceToInvalidation() != null) {
                        double distance = Math.abs(priceInfo.getDistanceToInvalidation());
                        String severity;
                        if (distance > 15.0) severity = "⚠️ 轻微失效（距离>15%）";
                        else if (distance > 10.0) severity = "⚠️ 中等失效（距离10-15%）";
                        else if (distance > 5.0) severity = "⚠️ 显著失效（距离5-10%）";
                        else if (distance > 2.0) severity = "🚨 严重失效（距离2-5%）";
                        else severity = "🚨 极度严重失效（距离<2%）";

                        log.warn("  🚨 失效状态分析: {}", severity);
                    }
                }
            }
        }

        /**
         * 计算趋势方向
         */
        private String calculateTrendDirection(Map<TimeFrame, TimeFrameAnalysis> analyses) {
            long bullishCount = analyses.values().stream()
                    .filter(TimeFrameAnalysis::isBullish)
                    .count();
            long total = analyses.size();

            double bullishRatio = (double) bullishCount / total;

            if (bullishRatio >= 0.8) {
                return "CONFIRMED_BULLISH";
            } else if (bullishRatio <= 0.2) {
                return "CONFIRMED_BEARISH";
            } else if (bullishRatio >= 0.6) {
                return "MOSTLY_BULLISH";
            } else if (bullishRatio <= 0.4) {
                return "MOSTLY_BEARISH";
            } else {
                return "MIXED";
            }
        }

        /**
         * 计算主导相位
         */
        private ElliottPhase calculateDominantPhase(Map<TimeFrame, TimeFrameAnalysis> analyses) {
            Map<ElliottPhase, Integer> phaseCount = new HashMap<>();

            for (TimeFrameAnalysis analysis : analyses.values()) {
                if (analysis.getCurrentPhase() != null) {
                    phaseCount.put(analysis.getCurrentPhase(),
                            phaseCount.getOrDefault(analysis.getCurrentPhase(), 0) + 1);
                }
            }

            if (phaseCount.isEmpty()) {
                return null;
            }

            return phaseCount.entrySet().stream()
                    .max(Map.Entry.<ElliottPhase, Integer>comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        /**
         * 计算一致性等级
         */
        private int calculateAgreementLevel(Map<TimeFrame, TimeFrameAnalysis> analyses) {
            if (analyses.size() < 2) {
                return 3; // 单周期默认为中等一致性
            }

            // 方向一致性
            long bullishCount = analyses.values().stream()
                    .filter(TimeFrameAnalysis::isBullish).count();
            double directionAgreement = (double) Math.max(bullishCount, analyses.size() - bullishCount)
                    / analyses.size();

            // 相位一致性
            Map<ElliottPhase, Integer> phaseCount = new HashMap<>();
            for (TimeFrameAnalysis analysis : analyses.values()) {
                if (analysis.getCurrentPhase() != null) {
                    phaseCount.put(analysis.getCurrentPhase(),
                            phaseCount.getOrDefault(analysis.getCurrentPhase(), 0) + 1);
                }
            }
            double phaseAgreement = phaseCount.values().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0) / (double) analyses.size();

            // 加权计算一致性分数
            double agreementScore = (directionAgreement * 0.6 + phaseAgreement * 0.4);

            // 转换为1-5级
            if (agreementScore >= 0.9) return 5;
            if (agreementScore >= 0.8) return 4;
            if (agreementScore >= 0.6) return 3;
            if (agreementScore >= 0.4) return 2;
            return 1;
        }

        /**
         * 计算综合得分
         */
        private double calculateCompositeScore(Map<TimeFrame, TimeFrameAnalysis> analyses, MultiTimeFrameConfig config) {
            double weightedSum = 0;
            double totalWeight = 0;

            for (Map.Entry<TimeFrame, TimeFrameAnalysis> entry : analyses.entrySet()) {
                TimeFrame tf = entry.getKey();
                TimeFrameAnalysis analysis = entry.getValue();

                double weight = getWeightForTimeFrame(tf, config);
                weightedSum += analysis.getScore() * weight;
                totalWeight += weight;
            }

            return totalWeight > 0 ? weightedSum / totalWeight : 0.5;
        }

        /**
         * 获取周期权重
         */
        private double getWeightForTimeFrame(TimeFrame timeFrame, MultiTimeFrameConfig config) {
            switch (timeFrame) {
                case H1:
                    return config.hourlyWeight;
                case M15:
                    return config.quarterlyWeight;
                case M5:
                case M1:
                    return config.minuteWeight;
                default:
                    return 0.2; // 默认权重
            }
        }

        /**
         * 生成多周期警告
         */
        private List<String> generateMultiTimeFrameWarnings(Map<TimeFrame, TimeFrameAnalysis> analyses, int agreementLevel) {
            List<String> warnings = new ArrayList<>();

            if (agreementLevel < 3) {
                warnings.add("多周期一致性较低，信号可靠性降低");
            }

            long invalidatedCount = analyses.values().stream()
                    .filter(TimeFrameAnalysis::isInvalidation)
                    .count();
            if (invalidatedCount > 0) {
                warnings.add(String.format("{}个周期波浪结构已失效", invalidatedCount));
            }

            return warnings;
        }

        /**
         * 生成多周期建议
         */
        private List<String> generateMultiTimeFrameRecommendations(Map<TimeFrame, TimeFrameAnalysis> analyses,
                                                                   String trendDirection, int agreementLevel) {
            List<String> recommendations = new ArrayList<>();

            if (agreementLevel >= 4) {
                recommendations.add("多周期一致性较高，增强信号可靠性");
            }

            if ("CONFIRMED_BULLISH".equals(trendDirection) || "CONFIRMED_BEARISH".equals(trendDirection)) {
                recommendations.add("大周期确认趋势方向，支持信号");
            }

            return recommendations;
        }

        /**
         * 记录多周期分析结果
         */
        public void logMultiTimeFrameAnalysis(TradingSignalDto signal, MultiTimeFrameAnalysis multiAnalysis) {
            log.info("=".repeat(80));
            log.info("🌊 多周期艾略特波浪综合分析报告");
            log.info("交易对: {}, 信号ID: {}, 类型: {}",
                    signal.getSymbol(), signal.getId(), signal.getType());
            log.info("-".repeat(80));

            // 总体概况
            log.info("【多周期总体概况】");
            log.info("  综合得分: {}/100", String.format("%.0f", multiAnalysis.getCompositeScore() * 100));
            log.info("  趋势方向: {}", multiAnalysis.getTrendDirection());
            log.info("  主导相位: {}", multiAnalysis.getDominantPhase() != null ?
                    multiAnalysis.getDominantPhase() : "UNKNOWN");
            log.info("  一致性等级: {}/5", multiAnalysis.getAgreementLevel());

            // 各周期详细分析
            log.info("【各周期详细分析】");
            log.info("  ┌───────────┬───────────┬─────────┬─────────┬─────────┬─────────┬─────────┐");
            log.info("  │ 周期      │ 相位      │ 方向    │ 置信度  │ 得分    │ 失效    │ 汇合度  │");
            log.info("  ├───────────┼───────────┼─────────┼─────────┼─────────┼─────────┼─────────┤");

            for (Map.Entry<TimeFrame, TimeFrameAnalysis> entry : multiAnalysis.getAnalyses().entrySet()) {
                TimeFrame tf = entry.getKey();
                TimeFrameAnalysis analysis = entry.getValue();

                String phase = analysis.getCurrentPhase() != null ?
                        analysis.getCurrentPhase().toString() : "N/A";
                String direction = analysis.isBullish() ? "BULLISH" : "BEARISH";
                String confidence = String.format("%.1f", analysis.getConfidence());
                String score = String.format("%.1f", analysis.getScore() * 100);
                String invalidation = analysis.isInvalidation() ? "是" : "否";
                String confluence = String.format("%.1f", analysis.getConfluenceScore() * 100);

                log.info("  │ {}{}{}{}{}{}{} │",
                        String.format("%-9s", tf.toString()),
                        String.format("%-9s", phase),
                        String.format("%-7s", direction),
                        String.format("%7s", confidence),
                        String.format("%7s", score),
                        String.format("%7s", invalidation),
                        String.format("%7s", confluence));
            }
            log.info("  └───────────┴───────────┴─────────┴─────────┴─────────┴─────────┴─────────┘");

            // 多周期交易建议
            log.info("【多周期交易建议】");
            for (String recommendation : multiAnalysis.getRecommendations()) {
                log.info("  ✓ {}", recommendation);
            }

            // 多周期警告
            if (!multiAnalysis.getWarnings().isEmpty()) {
                log.warn("【多周期警告】");
                for (String warning : multiAnalysis.getWarnings()) {
                    log.warn("  ⚠️ {}", warning);
                }
            }

            log.info("=".repeat(80));
        }

        /**
         * 记录结构化分析结果
         */
        public void logStructuredResults(TradingSignalDto signal, ElliottAnalysisResult analysis,
                                         double finalScore, String riskLevel, TradeAdvice tradeAdvice,
                                         ScoreResult scores) {
            log.info("=".repeat(80));
            log.info("📊 艾略特波浪深度分析报告");
            log.info("交易对: {}, 信号ID: {}, 类型: {}",
                    signal.getSymbol(), signal.getId(), signal.getType());
            log.info("信号触发价格: {}", String.format("%.4f", signal.getTriggerPrice()));
            log.info("-".repeat(80));

            // 1. 核心评分
            log.info("【核心评分】");
            log.info("  综合得分: {}/100", String.format("%.0f", finalScore * 100));
            log.info("  风险等级: {} ({})", getRiskLevelDescription(riskLevel), riskLevel);
            log.info("  交易建议: {} - {}", tradeAdvice.getAction(), tradeAdvice.getReason());

            // 2. 详细因子分析（表格形式）
            log.info("【详细因子分析】");
            log.info("  ┌─────────────────┬─────────┬─────────┬────────────────────┐");
            log.info("  │ 因子            │ 得分    │ 权重    │ 状态               │");
            log.info("  ├─────────────────┼─────────┼─────────┼────────────────────┤");

            FactorWeights weights = getFactorWeights();
            log.info("  │ 基础置信度      │ {} │ {}│ {} │",
                    String.format("%6.1f", scores.getBaseConfidenceScore() * 100),
                    String.format("%6.1f", weights.baseConfidence * 100),
                    getScoreIndicator(scores.getBaseConfidenceScore()));
            log.info("  │ 结构完整性      │ {} │ {}│ {} │",
                    String.format("%6.1f", scores.getStructureScore() * 100),
                    String.format("%6.1f", weights.structureCompleteness * 100),
                    getScoreIndicator(scores.getStructureScore()));
            log.info("  │ 技术汇合        │ {} │ {}│ {} │",
                    String.format("%6.1f", scores.getConfluenceScore() * 100),
                    String.format("%6.1f", weights.confluence * 100),
                    getScoreIndicator(scores.getConfluenceScore()));
            log.info("  │ 失效状态        │ {} │ {}│ {} │",
                    String.format("%6.1f", scores.getInvalidationScore() * 100),
                    String.format("%6.1f", weights.invalidation * 100),
                    getScoreIndicator(scores.getInvalidationScore()));
            log.info("  │ 方向匹配        │ {} │ {}│ {} │",
                    String.format("%6.1f", scores.getDirectionMatchScore() * 100),
                    String.format("%6.1f", weights.directionMatch * 100),
                    getScoreIndicator(scores.getDirectionMatchScore()));
            log.info("  │ 价格位置        │ {} │ {}│ {} │",
                    String.format("%6.1f", scores.getPricePositionScore() * 100),
                    String.format("%6.1f", weights.pricePosition * 100),
                    getScoreIndicator(scores.getPricePositionScore()));
            log.info("  └─────────────────┴─────────┴─────────┴────────────────────┘");

            // 价格位置分析
            logEnhancedPricePositionAnalysis(analysis.getPriceInfo(), analysis.isInvalidation());

            // 交易建议详情
            logTradeAdvice(tradeAdvice);

            // 警告信息
            logWarnings(finalScore, analysis);

            log.info("=".repeat(80));
        }

        /**
         * 记录价格位置分析
         */
        /**
         * 增强的价格位置分析日志（包含详细止盈止损目标）
         */
        void logEnhancedPricePositionAnalysis(PricePositionInfo priceInfo, boolean isInvalidation) {
            log.info("【价格位置分析】");
            if (priceInfo != null) {
                log.info("  📊 当前价格: {}", String.format("%.4f", priceInfo.getCurrentPrice()));

                // 通道位置信息
                if (priceInfo.getChannelPosition() != null) {
                    try {
                        PriceZone zone = PriceZone.valueOf(priceInfo.getZone());
                        log.info("  📍 通道位置: {}% ({})",
                                String.format("%.1f", priceInfo.getChannelPosition()), zone.getName());
                        log.info("    位置解读: {}", zone.getDescription());

                        if (priceInfo.getAboveMedian() != null) {
                            log.info("    相对中轨: {} ({})",
                                    priceInfo.getAboveMedian() ? "上方" : "下方",
                                    priceInfo.getAboveMedian() ? "偏强" : "偏弱");
                        }
                    } catch (IllegalArgumentException e) {
                        // 忽略无效的区域值
                    }
                }

                if (priceInfo.getDistanceToInvalidation() != null) {
                    log.info("  🚨 距离失效价格: {}% ({}点, {})",
                            String.format("%.2f", Math.abs(priceInfo.getDistanceToInvalidation())),
                            String.format("%.4f", priceInfo.getPriceToInvalidation()),
                            priceInfo.getAboveInvalidation() != null && priceInfo.getAboveInvalidation() ? "已失效" : "未失效");
                }

                if (priceInfo.getDistanceToTarget() != null) {
                    String direction = priceInfo.getDistanceToTarget() > 0 ? "已超过" : "未达到";
                    log.info("  🎯 距离目标价格: {}% ({}点, {})",
                            String.format("%.2f", Math.abs(priceInfo.getDistanceToTarget())),
                            String.format("%.4f", priceInfo.getPriceToTarget()),
                            direction);
                }

                if (priceInfo.getRiskRewardRatio() != null) {
                    PricePositionConfig config = getPricePositionConfig();
                    String rrStatus = priceInfo.getRiskRewardRatio() >= config.minRiskRewardRatio ? "良好" :
                            priceInfo.getRiskRewardRatio() >= config.warningRiskRewardRatio ? "可接受" : "偏低";
                    log.info("  ⚖️  风险收益比: 1:{} ({})",
                            String.format("%.2f", priceInfo.getRiskRewardRatio()), rrStatus);
                }

                if (priceInfo.getShortMomentum() != null) {
                    String momentumDesc = priceInfo.getShortMomentum() > 0.3 ? "上涨动能强" :
                            priceInfo.getShortMomentum() < -0.3 ? "下跌动能强" : "震荡整理";
                    log.info("  ⚡ 短期动能: {} ({})",
                            String.format("%.2f", priceInfo.getShortMomentum()), momentumDesc);
                }

                if (priceInfo.getWaveMomentum() != null) {
                    log.info("  🌊 波浪动能: {}", priceInfo.getWaveMomentum());
                }

                // 新增：详细止盈止损目标表格
               this.logDetailedTargets(priceInfo);

                // 新增：交易策略总结
                this.logTradingStrategySummary(priceInfo, isInvalidation);

                // 交易建议（原有代码）
                if (priceInfo.getEntryRecommendation() != null) {
                    log.info("  🚀 入场建议: {}", priceInfo.getEntryRecommendation());
                }
                if (priceInfo.getStopLossRecommendation() != null) {
                    log.info("  🛡️  止损建议: {}", priceInfo.getStopLossRecommendation());
                }
                if (priceInfo.getTakeProfitRecommendation() != null) {
                    log.info("  💰 止盈建议: {}", priceInfo.getTakeProfitRecommendation());
                }
            }
        }

        /**
         * 记录详细的目标信息
         */
        void logDetailedTargets(PricePositionInfo priceInfo) {
            if (priceInfo.getPriceTargets() != null && !priceInfo.getPriceTargets().isEmpty()) {
                log.info("  🎯 【详细止盈目标】");
                log.info("    ┌─────┬──────────┬─────────┬─────────┬────────────┬─────────────────┐");
                log.info("    │ 级别 │ 目标价格 │ 距离%   │ 概率    │ 风险收益比 │ 依据            │");
                log.info("    ├─────┼──────────┼─────────┼─────────┼────────────┼─────────────────┤");

                for (PriceTarget target : priceInfo.getPriceTargets()) {
                    if (target.getLevel() <= 3) { // 只显示前3个主要目标
                        String rrRatio = target.getRiskRewardRatio() > 0 ?
                                String.format("1:%.2f", target.getRiskRewardRatio()) : "N/A";

                        log.info("    │ %3d │ %10.4f │ %7.2f%% │ %6.0f%% │ %10s │ %-15s │",
                                target.getLevel(),
                                target.getPrice(),
                                target.getDistanceFromCurrent(),
                                target.getProbability() * 100,
                                rrRatio,
                                truncateString(target.getDescription(), 15));
                    }
                }
                log.info("    └─────┴──────────┴─────────┴─────────┴────────────┴─────────────────┘");
            }

            if (priceInfo.getStopLossLevels() != null && !priceInfo.getStopLossLevels().isEmpty()) {
                log.info("  🛡️  【详细止损水平】");
                log.info("    ┌─────┬──────────┬─────────┬────────────┬─────────┬─────────────────┐");
                log.info("    │ 级别 │ 止损价格 │ 风险%   │ 类型       │ 主要    │ 依据            │");
                log.info("    ├─────┼──────────┼─────────┼────────────┼─────────┼─────────────────┤");

                for (StopLossLevel stopLoss : priceInfo.getStopLossLevels()) {
                    log.info("    │ %3d │ %10.4f │ %7.2f%% │ %-10s │ %7s │ %-15s │",
                            stopLoss.getLevel(),
                            stopLoss.getPrice(),
                            stopLoss.getRiskPercentage(),
                            truncateString(stopLoss.getType(), 10),
                            stopLoss.isPrimary() ? "✓" : "",
                            truncateString(stopLoss.getDescription(), 15));
                }
                log.info("    └─────┴──────────┴─────────┴────────────┴─────────┴─────────────────┘");
            }
        }

        /**
         * 记录交易策略总结
         */
        void logTradingStrategySummary(PricePositionInfo priceInfo, boolean isInvalidation) {
            log.info("  📈 【交易策略建议】");

            // 最优止盈止损建议
            if (priceInfo.getOptimalTakeProfit() != null && priceInfo.getOptimalStopLoss() != null) {
                double currentPrice = priceInfo.getCurrentPrice();
                double stopLoss = priceInfo.getOptimalStopLoss();
                double takeProfit = priceInfo.getOptimalTakeProfit();

                double risk = Math.abs(currentPrice - stopLoss);
                double reward = Math.abs(takeProfit - currentPrice);
                double rrRatio = risk > 0 ? reward / risk : 0;

                log.info("    ✅ 推荐组合：");
                log.info("      入场价: {}", String.format("%.4f", currentPrice));
                log.info("      止损位: {} (风险: {})",
                        String.format("%.4f", stopLoss),
                        String.format("%.2f%%", risk / currentPrice * 100));
                log.info("      止盈位: {} (收益: {})",
                        String.format("%.4f", takeProfit),
                        String.format("%.2f%%", reward / currentPrice * 100));
                log.info("      风险收益比: 1:{}", String.format("%.2f", rrRatio));

                if (priceInfo.getBreakevenPrice() != null) {
                    log.info("      盈亏平衡点: {}", String.format("%.4f", priceInfo.getBreakevenPrice()));
                }

                // 根据风险收益比给出建议
                if (rrRatio < 1.0) {
                    log.warn("    ⚠️  警告：风险收益比低于1:1，不建议交易");
                } else if (rrRatio < 1.5) {
                    log.info("    ℹ️  提示：风险收益比较低(1:{})，建议谨慎操作", String.format("%.2f", rrRatio));
                } else if (rrRatio >= 2.0) {
                    log.info("    ✅ 良好：风险收益比优秀(1:{})，可积极考虑", String.format("%.2f", rrRatio));
                }
            }

            // 目标策略描述
            if (priceInfo.getTargetStrategy() != null) {
                log.info("    🎯 目标策略：");
                for (String line : priceInfo.getTargetStrategy().split("\n")) {
                    log.info("      {}", line);
                }
            }

            // 止损策略描述
            if (priceInfo.getStopLossStrategy() != null) {
                log.info("    🛡️  止损策略：");
                for (String line : priceInfo.getStopLossStrategy().split("\n")) {
                    log.info("      {}", line);
                }
            }

            // 特别警告（如果失效）
            if (isInvalidation) {
                log.warn("    ⚠️  重要警告：波浪结构已失效，所有目标建议仅供参考");
            }
        }


        /**
         * 记录交易建议
         */
        private void logTradeAdvice(TradeAdvice tradeAdvice) {
            log.info("【交易建议汇总】");
            if (tradeAdvice.getBias() != null) {
                log.info("  操作倾向: {}", tradeAdvice.getBias());
            }
            if (tradeAdvice.getEntryStrategy() != null) {
                log.info("  入场策略: {}", tradeAdvice.getEntryStrategy());
            }
            if (tradeAdvice.getStopLoss() > 0) {
                log.info("  建议止损: {}", String.format("%.4f", tradeAdvice.getStopLoss()));
            }
            if (tradeAdvice.getTakeProfit() > 0) {
                log.info("  建议止盈: {}", String.format("%.4f", tradeAdvice.getTakeProfit()));
            }
            if (tradeAdvice.getTakeProfitRecommendation() != null) {
                log.info("  💰 止盈建议: {}", tradeAdvice.getTakeProfitRecommendation());
            }
            if (tradeAdvice.getDynamicStopLoss() != null) {
                log.info("  动态止损: {}", tradeAdvice.getDynamicStopLoss());
            }
        }

        /**
         * 记录警告信息
         */
        private void logWarnings(double finalScore, ElliottAnalysisResult analysis) {
            if (finalScore < highRiskMax || analysis.isInvalidation()) {
                log.warn("⚠️  重要警告: 波浪结构不可靠，建议放弃此信号");
            }

            if (analysis.getPriceInfo() != null && analysis.getPriceInfo().getRiskRewardRatio() != null) {
                PricePositionConfig config = getPricePositionConfig();
                if (analysis.getPriceInfo().getRiskRewardRatio() < config.warningRiskRewardRatio) {
                    log.warn("⚠️  风险警告: 风险收益比偏低(1:{})，建议等待更好机会",
                            String.format("%.2f", analysis.getPriceInfo().getRiskRewardRatio()));
                }
            }
        }
    }

    /**
     * 分数计算器
     */
    private class ScoreCalculator {

        /**
         * 计算所有因子得分
         */
        public ScoreResult calculateAllScores(ElliottAnalysisResult analysis, TradingSignalDto signal) {
            FactorWeights weights = getFactorWeights();
            ScoreResult scores = new ScoreResult();

            scores.setBaseConfidenceScore(calculateBaseConfidenceScore(analysis.getBaseCase()));
            scores.setStructureScore(calculateStructureScore(
                    analysis.getBaseCase(),
                    analysis.getWaveCountValue(),
                    analysis.getFilteredWaveCountValue()));
            scores.setConfluenceScore(calculateConfluenceScore(analysis.getConfluenceScoreNum()));
            // 获取当前价格
            double currentPrice = analysis.series.getBar(analysis.endIndex).getClosePrice().doubleValue();

            scores.setInvalidationScore(calculateInvalidationScore(
                    analysis.isInvalidation(),
                    analysis.getChannel(),
                    analysis.getBaseCase(),
                    analysis.getPriceInfo(),
                    currentPrice));
            scores.setDirectionMatchScore(calculateDirectionMatchScore(
                    signal,
                    analysis.getBaseCase(),
                    analysis.getCurrentPhase()));
            scores.setPricePositionScore(calculatePricePositionScore(analysis.getPriceInfo(), analysis.getCurrentPhase()));

            return scores;
        }

        /**
         * 计算加权综合得分
         */
        public double calculateWeightedScore(ScoreResult scores) {
            FactorWeights weights = getFactorWeights();
            return scores.getBaseConfidenceScore() * weights.baseConfidence
                    + scores.getStructureScore() * weights.structureCompleteness
                    + scores.getConfluenceScore() * weights.confluence
                    + scores.getInvalidationScore() * weights.invalidation
                    + scores.getDirectionMatchScore() * weights.directionMatch
                    + scores.getPricePositionScore() * weights.pricePosition;
        }

        /**
         * 确定风险等级
         */
        public String determineRiskLevel(ScoreResult scores, ElliottAnalysisResult analysis) {
            RiskThresholds thresholds = getRiskThresholds();
            PricePositionInfo priceInfo = analysis.getPriceInfo();

            // 构建风险评分矩阵
            Map<String, Double> riskFactors = new HashMap<>();

            // 综合得分因子
            riskFactors.put("finalScore", 1.0 - calculateWeightedScore(scores));

            // 失效状态因子
            riskFactors.put("invalidation", analysis.isInvalidation() ? 0.8 : 0.0);

            // 方向匹配因子
            double directionRisk = calculateDirectionRisk(scores.getDirectionMatchScore());
            riskFactors.put("direction", directionRisk);

            // 汇合评分因子
            riskFactors.put("confluence", scores.getConfluenceScore() < 0.6 ? 0.3 : 0.0);

            // 价格位置因子
            if (priceInfo != null) {
                riskFactors.put("position", calculatePositionRisk(priceInfo));
            }

            // 风险收益比因子
            if (priceInfo != null && priceInfo.getRiskRewardRatio() != null) {
                double rrRisk = priceInfo.getRiskRewardRatio() < 1.0 ? 0.6 :
                        priceInfo.getRiskRewardRatio() < 1.5 ? 0.3 : 0.0;
                riskFactors.put("riskReward", rrRisk);
            }

            // 波浪相位因子
            riskFactors.put("phase", calculatePhaseRisk(analysis.getCurrentPhase()));

            // 计算综合风险分数
            double totalRisk = riskFactors.values().stream().mapToDouble(Double::doubleValue).sum();
            double normalizedRisk = Math.min(1.0, Math.max(0.0, totalRisk / Math.max(1.0, riskFactors.size())));

            // 根据归一化风险分数确定等级
            if (normalizedRisk > thresholds.highRiskMax) {
                return "HIGH_RISK";
            } else if (normalizedRisk > thresholds.mediumRiskMax) {
                return "MEDIUM_RISK";
            } else {
                return "LOW_RISK";
            }
        }

        private double calculateDirectionRisk(double directionMatchScore) {
            if (directionMatchScore < 0.3) {
                return 0.7;
            } else if (directionMatchScore < 0.5) {
                return 0.4;
            } else {
                return 0.1;
            }
        }

        private double calculatePositionRisk(PricePositionInfo priceInfo) {
            if (priceInfo.getZone() == null) {
                return 0.0;
            }

            try {
                PriceZone zone = PriceZone.valueOf(priceInfo.getZone());
                switch (zone) {
                    case UPPER_BAND:
                    case OUTSIDE_UPPER:
                        return 0.4;
                    case LOWER_BAND:
                    case OUTSIDE_LOWER:
                        return 0.3;
                    default:
                        return 0.0;
                }
            } catch (IllegalArgumentException e) {
                return 0.0;
            }
        }

        private double calculatePhaseRisk(ElliottPhase currentPhase) {
            if (currentPhase == null) {
                return 0.0;
            }

            if (currentPhase == ElliottPhase.WAVE5) {
                return 0.4; // 第5浪末端风险高
            } else if (currentPhase == ElliottPhase.WAVE3) {
                return -0.2; // 第3浪主升浪，风险降低
            }
            return 0.0;
        }
    }

    /**
     * 建议生成器
     */
    private class RecommendationGenerator {

        /**
         * 生成建议列表
         */
        public List<String> generateRecommendations(ScoreResult scores, ElliottAnalysisResult analysis) {
            List<String> recommendations = new ArrayList<>();
            double finalScore = new ScoreCalculator().calculateWeightedScore(scores);

            // 整体评分建议
            if (finalScore > 0.8) {
                recommendations.add("✅ 艾略特波浪分析强烈确认，信号可靠性极高");
            } else if (finalScore > 0.6) {
                recommendations.add("✓ 艾略特波浪分析确认良好，信号可靠性较高");
            } else if (finalScore > 0.4) {
                recommendations.add("⚠️ 艾略特波浪分析确认一般，建议谨慎操作");
            } else {
                recommendations.add("❌ 艾略特波浪分析确认不足，建议放弃该信号");
            }

            // 具体因子建议
            addFactorRecommendations(recommendations, scores);

            // 相位建议
            if (analysis.getCurrentPhase() != null) {
                addPhaseRecommendations(recommendations, analysis);
            }

            return recommendations;
        }

        /**
         * 构建因子映射
         */
        public Map<String, Object> buildFactorsMap(ScoreResult scores, ElliottAnalysisResult analysis) {
            Map<String, Object> factors = new HashMap<>();
            factors.put("currentPhase", analysis.getCurrentPhase() != null ?
                    analysis.getCurrentPhase().toString() : "UNKNOWN");
            factors.put("baseConfidenceScore", String.format("%.2f", scores.getBaseConfidenceScore()));
            factors.put("structureScore", String.format("%.2f", scores.getStructureScore()));
            factors.put("confluenceScore", String.format("%.2f", scores.getConfluenceScore()));
            factors.put("invalidationScore", String.format("%.2f", scores.getInvalidationScore()));
            factors.put("directionMatchScore", String.format("%.2f", scores.getDirectionMatchScore()));
            factors.put("pricePositionScore", String.format("%.2f", scores.getPricePositionScore()));

            // 添加价格位置信息
            if (analysis.getPriceInfo() != null) {
                addPriceInfoToFactors(factors, analysis.getPriceInfo());
            }

            // 添加详细评分原因
            factors.put("scoreReasons", buildScoreReasons(scores));

            // 添加基础场景信息
            if (analysis.getBaseCase().isPresent()) {
                addBaseCaseToFactors(factors, analysis.getBaseCase().get());
            }

            return factors;
        }

        /**
         * 构建摘要
         */
        public String buildSummary(ElliottAnalysisResult analysis, ScoreResult scores) {
            double finalScore = new ScoreCalculator().calculateWeightedScore(scores);
            return String.format("相位:%s, 总分:%.2f, 基础:%.2f, 结构:%.2f, 价格位置:%.2f",
                    analysis.getCurrentPhase() != null ? analysis.getCurrentPhase().toString() : "UNKNOWN",
                    finalScore, scores.getBaseConfidenceScore(), scores.getStructureScore(),
                    scores.getPricePositionScore());
        }

        /**
         * 生成交易建议
         */
        public TradeAdvice generateTradeAdvice(ElliottAnalysisResult analysis, double finalScore,
                                               String riskLevel, TradingSignalDto signal) {
            TradeAdvice advice = new TradeAdvice();

            if (!analysis.getBaseCase().isPresent()) {
                advice.setAction("AVOID");
                advice.setReason("无可靠基础场景");
                return advice;
            }

            ElliottScenario base = analysis.getBaseCase().get();
            PricePositionInfo priceInfo = analysis.getPriceInfo();

            // 检查风险收益比
            if (shouldAvoidDueToPoorRiskReward(priceInfo)) {
                advice.setAction("AVOID");
                advice.setReason(String.format("风险收益比极差(1:%.2f)且无补偿因素，不值得交易",
                        priceInfo.getRiskRewardRatio()));
                return advice;
            }

            // 检查失效状态
            if (analysis.isInvalidation()) {
                if (shouldAvoidDueToInvalidation(priceInfo)) {
                    advice.setAction("AVOID");
                    advice.setReason("波浪结构已失效且接近失效价格");
                    return advice;
                }
                advice.setAction("CAUTIOUS");
                advice.setReason("波浪结构已失效，但价格距离失效位较远");
            }

            // 检查方向匹配
            double directionMatchScore = calculateDirectionMatchScore(signal, analysis.getBaseCase(), analysis.getCurrentPhase());
            if (directionMatchScore < 0.3) {
                if (analysis.getCurrentPhase() != null &&
                        (analysis.getCurrentPhase() == ElliottPhase.WAVE3 || analysis.getCurrentPhase() == ElliottPhase.WAVE5)) {
                    advice.setAction("CAUTIOUS");
                    advice.setReason("信号方向与波浪方向不一致，但处于主升浪阶段");
                } else if ("HIGH_RISK".equals(riskLevel)) {
                    advice.setAction("AVOID");
                    advice.setReason("信号方向与波浪方向严重不一致");
                    return advice;
                } else {
                    advice.setAction("CAUTIOUS");
                    advice.setReason("信号方向与波浪方向不一致，需谨慎");
                }
            }

            // 根据风险等级确定最终行动
            if (advice.getAction() == null) {
                setActionBasedOnRiskLevel(advice, riskLevel);
            }

            // 提供具体建议
            if ("CAUTIOUS".equals(advice.getAction()) || "CONFIRMED".equals(advice.getAction())) {
                addDetailedAdvice(advice, analysis, signal);
            }

            return advice;
        }

        private void addFactorRecommendations(List<String> recommendations, ScoreResult scores) {
            if (scores.getBaseConfidenceScore() < 0.6) {
                recommendations.add("波浪基础置信度较低（" + String.format("%.0f%%", scores.getBaseConfidenceScore() * 100) +
                        "），考虑降低仓位");
            }

            if (scores.getStructureScore() < 0.5) {
                recommendations.add("波浪结构完整性不足，可能存在计数错误");
            }

            if (scores.getConfluenceScore() < 0.6) {
                recommendations.add("技术汇合度不高，缺乏多重验证");
            }

            if (scores.getInvalidationScore() < 0.5) {
                recommendations.add("波浪结构已失效或接近失效，风险较高");
            }

            if (scores.getDirectionMatchScore() < 0.5) {
                recommendations.add("信号方向与波浪方向不一致，存在矛盾");
            }
        }

        private void addPhaseRecommendations(List<String> recommendations, ElliottAnalysisResult analysis) {
            ElliottPhase phase = analysis.getCurrentPhase();
            if (phase == ElliottPhase.WAVE2 || phase == ElliottPhase.WAVE4) {
                recommendations.add("当前为调整浪，建议等待调整结束确认");
            } else if (phase == ElliottPhase.WAVE3) {
                recommendations.add("当前为主升浪3，是理想的买入时机");
            } else if (phase == ElliottPhase.WAVE5) {
                recommendations.add("当前为末端浪5，注意动能衰竭");
            }
        }

        private void addPriceInfoToFactors(Map<String, Object> factors, PricePositionInfo priceInfo) {
            factors.put("currentPrice", priceInfo.getCurrentPrice());
            factors.put("channelPosition", priceInfo.getChannelPosition());
            factors.put("priceZone", priceInfo.getZone());
            factors.put("riskRewardRatio", priceInfo.getRiskRewardRatio());
            factors.put("shortMomentum", priceInfo.getShortMomentum());
        }

        private List<String> buildScoreReasons(ScoreResult scores) {
            List<String> scoreReasons = new ArrayList<>();
            if (scores.getBaseConfidenceScore() > 0.7) scoreReasons.add("高置信度基础场景");
            if (scores.getStructureScore() > 0.7) scoreReasons.add("清晰波浪结构");
            if (scores.getConfluenceScore() > 0.7) scoreReasons.add("强技术汇合");
            if (scores.getInvalidationScore() > 0.7) scoreReasons.add("波浪结构稳定");
            if (scores.getDirectionMatchScore() > 0.7) scoreReasons.add("方向高度一致");
            if (scores.getPricePositionScore() > 0.7) scoreReasons.add("价格位置理想");
            return scoreReasons;
        }

        private void addBaseCaseToFactors(Map<String, Object> factors, ElliottScenario base) {
            factors.put("baseCaseConfidence", base.confidence().asPercentage());
            factors.put("baseCaseDirection", base.hasKnownDirection() ?
                    (base.isBullish() ? "BULLISH" : "BEARISH") : "UNKNOWN");
        }

        private boolean shouldAvoidDueToPoorRiskReward(PricePositionInfo priceInfo) {
            if (priceInfo == null || priceInfo.getRiskRewardRatio() == null) {
                return false;
            }

            PricePositionConfig config = getPricePositionConfig();
            return priceInfo.getRiskRewardRatio() < 0.5; // 极差风险收益比
        }

        private boolean shouldAvoidDueToInvalidation(PricePositionInfo priceInfo) {
            if (priceInfo == null || priceInfo.getDistanceToInvalidation() == null) {
                return true;
            }

            double distance = Math.abs(priceInfo.getDistanceToInvalidation());
            return distance <= 5.0; // 距离失效价格太近
        }

        private void setActionBasedOnRiskLevel(TradeAdvice advice, String riskLevel) {
            switch (riskLevel) {
                case "HIGH_RISK":
                    advice.setAction("AVOID");
                    advice.setReason("综合风险评估过高");
                    break;
                case "MEDIUM_RISK":
                    advice.setAction("CAUTIOUS");
                    advice.setReason("存在一定风险，需谨慎操作");
                    break;
                case "LOW_RISK":
                    advice.setAction("CONFIRMED");
                    advice.setReason("波浪分析确认良好");
                    break;
                default:
                    advice.setAction("CAUTIOUS");
                    advice.setReason("波浪分析存在不确定性");
                    break;
            }
        }

        private void addDetailedAdvice(TradeAdvice advice, ElliottAnalysisResult analysis, TradingSignalDto signal) {
            ElliottPhase phase = analysis.getCurrentPhase();
            boolean isBuySignal = signal.getType().name().equals("BUY");
            ElliottScenario base = analysis.getBaseCase().get();
            boolean scenarioBullish = base.hasKnownDirection() && base.isBullish();

            if (phase != null) {
                if (phase.isImpulse()) {
                    if (scenarioBullish && isBuySignal) {
                        advice.setBias("BULLISH");
                        advice.setEntryStrategy("等待回调至斐波那契支撑位");
                    } else if (!scenarioBullish && !isBuySignal) {
                        advice.setBias("BEARISH");
                        advice.setEntryStrategy("等待反弹至斐波那契阻力位");
                    } else {
                        // 方向不一致但处于驱动浪
                        if (isBuySignal) {
                            advice.setBias("BULLISH");
                            advice.setEntryStrategy("等待价格回调至更有利位置，分批建仓");
                        } else {
                            advice.setBias("BEARISH");
                            advice.setEntryStrategy("等待价格反弹至更有利位置，分批建仓");
                        }
                    }
                } else if (phase.isCorrective()) {
                    if (scenarioBullish) {
                        advice.setBias("BEARISH");
                        advice.setEntryStrategy("反弹做空，目标调整结束");
                    } else {
                        advice.setBias("BULLISH");
                        advice.setEntryStrategy("回调做多，目标调整结束");
                    }
                }
            }

            // 设置止损和目标
            setStopLossAndTarget(advice, analysis);
        }

        private void setStopLossAndTarget(TradeAdvice advice, ElliottAnalysisResult analysis) {
            PricePositionInfo priceInfo = analysis.getPriceInfo();

            if (priceInfo != null && priceInfo.getInvalidationPrice() != null) {
                double stopLossPrice = priceInfo.getInvalidationPrice();
                advice.setStopLoss(stopLossPrice);
                advice.setDynamicStopLoss(String.format("价格突破%.2f（波浪失效价）立即止损", stopLossPrice));
            }

            if (priceInfo != null && priceInfo.getTargetPrice() != null) {
                double targetPrice = priceInfo.getTargetPrice();
                advice.setTakeProfit(targetPrice);

                PricePositionConfig config = getPricePositionConfig();
                if (priceInfo.getRiskRewardRatio() != null && priceInfo.getRiskRewardRatio() < config.minRiskRewardRatio) {
                    advice.setTakeProfitRecommendation(String.format("保守目标：%.2f，风险收益比1:%.2f（风险收益比较低）",
                            targetPrice, priceInfo.getRiskRewardRatio()));
                } else {
                    advice.setTakeProfitRecommendation(String.format("第一目标：%.2f", targetPrice));
                }
            }
        }
    }

    @Override
    public String getId() {
        return "elliott-wave";
    }

    @Override
    public String getName() {
        return "艾略特波浪分析评估器";
    }

    @Override
    public String getDescription() {
        return "评估波浪结构、相位、斐波那契";
    }

    @Override
    public double getWeight() {
        return weight;
    }

    @Override
    public boolean requiresMultiTimeFrame() {
        return true; // 艾略特波浪分析需要多周期数据
    }

    // ========== 服务类实例（延迟初始化） ==========
    private volatile FactorWeights factorWeights;
    private volatile RiskThresholds riskThresholds;
    private volatile PricePositionConfig pricePositionConfig;
    private volatile MultiTimeFrameConfig multiTimeFrameConfig;
    private volatile ScenarioSelectionConfig scenarioSelectionConfig;

    // 服务类实例（单例模式）
    private volatile ElliottAnalysisService analysisService;
    private volatile ScoreCalculator scoreCalculator;
    private volatile RecommendationGenerator recommendationGenerator;

    /**
     * 获取因子权重配置（延迟初始化）
     */
    private FactorWeights getFactorWeights() {
        if (factorWeights == null) {
            synchronized (this) {
                if (factorWeights == null) {
                    factorWeights = new FactorWeights();
                }
            }
        }
        return factorWeights;
    }

    /**
     * 获取风险阈值配置（延迟初始化）
     */
    private RiskThresholds getRiskThresholds() {
        if (riskThresholds == null) {
            synchronized (this) {
                if (riskThresholds == null) {
                    riskThresholds = new RiskThresholds();
                }
            }
        }
        return riskThresholds;
    }

    /**
     * 获取价格位置配置（延迟初始化）
     */
    private PricePositionConfig getPricePositionConfig() {
        if (pricePositionConfig == null) {
            synchronized (this) {
                if (pricePositionConfig == null) {
                    pricePositionConfig = new PricePositionConfig();
                }
            }
        }
        return pricePositionConfig;
    }

    /**
     * 获取多周期配置（延迟初始化）
     */
    private MultiTimeFrameConfig getMultiTimeFrameConfig() {
        if (multiTimeFrameConfig == null) {
            synchronized (this) {
                if (multiTimeFrameConfig == null) {
                    multiTimeFrameConfig = new MultiTimeFrameConfig();
                }
            }
        }
        return multiTimeFrameConfig;
    }

    /**
     * 获取场景选择配置（延迟初始化）
     */
    private ScenarioSelectionConfig getScenarioSelectionConfig() {
        if (scenarioSelectionConfig == null) {
            synchronized (this) {
                if (scenarioSelectionConfig == null) {
                    scenarioSelectionConfig = new ScenarioSelectionConfig();
                }
            }
        }
        return scenarioSelectionConfig;
    }

    /**
     * 获取分析服务（延迟初始化）
     */
    private ElliottAnalysisService getAnalysisService() {
        if (analysisService == null) {
            synchronized (this) {
                if (analysisService == null) {
                    analysisService = new ElliottAnalysisService();
                }
            }
        }
        return analysisService;
    }

    /**
     * 获取分数计算器（延迟初始化）
     */
    private ScoreCalculator getScoreCalculator() {
        if (scoreCalculator == null) {
            synchronized (this) {
                if (scoreCalculator == null) {
                    scoreCalculator = new ScoreCalculator();
                }
            }
        }
        return scoreCalculator;
    }

    /**
     * 获取建议生成器（延迟初始化）
     */
    private RecommendationGenerator getRecommendationGenerator() {
        if (recommendationGenerator == null) {
            synchronized (this) {
                if (recommendationGenerator == null) {
                    recommendationGenerator = new RecommendationGenerator();
                }
            }
        }
        return recommendationGenerator;
    }

    @Override
    public QualityEvaluationResult evaluate(TradingSignalDto signal, EvaluationContext context) {
        AnalysisData data = context.getAnalysisData();

        // 1. 数据验证
        if (data == null || data.getBars().size() < minBars) {
            return createInsufficientDataResult(signal);
        }

        try {
            // 2. 检查是否使用多周期分析（使用上下文中的配置）
            if (context.isMultiTimeFrameEnabled() && context.hasMultiTimeFrameData()) {
                // 获取多周期配置用于权重计算
                MultiTimeFrameConfig multiConfig = getMultiTimeFrameConfig();
                return evaluateWithMultiTimeFrame(signal, context, multiConfig);
            }

            // 3. 单周期分析
            return evaluateSingleTimeFrame(signal, data);

        } catch (Exception e) {
            log.error("艾略特波浪分析失败: {}", e.getMessage(), e);
            return createErrorResult(signal, e.getMessage());
        }
    }

    /**
     * 创建数据不足的结果
     */
    private QualityEvaluationResult createInsufficientDataResult(TradingSignalDto signal) {
        return QualityEvaluationResult.builder()
                .evaluatorId(getId())
                .signalId(signal.getId())
                .score(0.5)
                .weight(getWeight())
                .summary("数据不足，无法进行艾略特波浪分析")
                .warnings(List.of("K线数据不足，需要至少" + minBars + "根K线"))
                .build();
    }

    /**
     * 创建错误结果
     */
    private QualityEvaluationResult createErrorResult(TradingSignalDto signal, String errorMessage) {
        return QualityEvaluationResult.builder()
                .evaluatorId(getId())
                .signalId(signal.getId())
                .score(0.5)
                .weight(getWeight())
                .summary("艾略特波浪分析失败: " + errorMessage)
                .warnings(List.of("分析过程出现错误"))
                .build();
    }

    /**
     * 执行多周期分析
     */
    private QualityEvaluationResult evaluateWithMultiTimeFrame(TradingSignalDto signal, EvaluationContext context, MultiTimeFrameConfig multiConfig) {
        // 使用新的多周期分析协调器
        ElliottAnalysisService analysisService = getAnalysisService();
        SmartWeightAdjuster weightAdjuster = new SmartWeightAdjuster();
        MultiTimeFrameCoordinator coordinator = new MultiTimeFrameCoordinator(analysisService, weightAdjuster);

        return coordinator.evaluateWithMultiTimeFrame(signal, context, getWeight(), getId());
    }

    /**
     * 生成最终交易建议
     */
    private String generateFinalTradeAdvice(
            MultiTimeFrameAnalysis multiAnalysis,
            TradingSignalDto signal,
            double finalScore) {

        // 获取关键指标
        String trendDirection = multiAnalysis.getTrendDirection();
        int agreementLevel = multiAnalysis.getAgreementLevel();
        Map<TimeFrame, TimeFrameAnalysis> analyses = multiAnalysis.getAnalyses();

        boolean signalBuy = signal.getType().name().equals("BUY");

        // 1. 检查严重问题
        int invalidCount = 0;
        int nearInvalidCount = 0; // 距离失效很近的周期数

        for (TimeFrameAnalysis analysis : analyses.values()) {
            if (analysis.isInvalidation()) {
                invalidCount++;
                // 检查是否距离失效很近
                if (analysis.getPriceInfo() != null &&
                        analysis.getPriceInfo().getDistanceToInvalidation() != null &&
                        Math.abs(analysis.getPriceInfo().getDistanceToInvalidation()) < 3.0) {
                    nearInvalidCount++;
                }
            }
        }

        // 严重问题：多个周期距离失效很近
        if (nearInvalidCount >= 2) {
            return "AVOID - 多个周期波浪结构接近失效价格";
        }

        // 2. 检查方向矛盾
        boolean hasDirectionConflict = false;
        boolean strongBullish = "STRONG_BULLISH".equals(trendDirection) || "BULLISH".equals(trendDirection);
        boolean strongBearish = "STRONG_BEARISH".equals(trendDirection) || "BEARISH".equals(trendDirection);

        if ((signalBuy && strongBearish) || (!signalBuy && strongBullish)) {
            hasDirectionConflict = true;
        }

        // 3. 检查一致性
        boolean lowAgreement = agreementLevel <= 2;

        // 4. 综合决策
        if (hasDirectionConflict && lowAgreement) {
            return "AVOID - 方向矛盾且一致性低";
        }

        if (finalScore < 0.4) {
            return "AVOID - 综合得分过低";
        }

        if (finalScore >= 0.7 && agreementLevel >= 4 && !hasDirectionConflict) {
            return "CONFIRMED - 高得分、高一致性、方向一致";
        }

        if (finalScore >= 0.5 && agreementLevel >= 3) {
            return "CAUTIOUS - 中等得分和一致性";
        }

        return "AVOID - 未满足交易条件";
    }

    /**
     * 执行单周期分析
     */
    private QualityEvaluationResult evaluateSingleTimeFrame(TradingSignalDto signal, AnalysisData data) {
        // 1. 执行艾略特波浪分析
        ElliottAnalysisService analysisService = getAnalysisService();
        ElliottAnalysisResult analysisResult = analysisService.analyzeSingleTimeFrame(signal, data);

        // 2. 计算各因子得分
        ScoreCalculator scoreCalculator = getScoreCalculator();
        ScoreResult scores = scoreCalculator.calculateAllScores(analysisResult, signal);

        // 3. 计算加权综合得分
        double finalScore = scoreCalculator.calculateWeightedScore(scores);
        finalScore = Math.max(0.0, Math.min(1.0, finalScore));

        // 4. 确定风险等级
        String riskLevel = scoreCalculator.determineRiskLevel(scores, analysisResult);

        // 5. 生成建议
        RecommendationGenerator recommendationGenerator = getRecommendationGenerator();
        List<String> recommendations = recommendationGenerator.generateRecommendations(scores, analysisResult);

        // 6. 构建因子映射
        Map<String, Object> factors = recommendationGenerator.buildFactorsMap(scores, analysisResult);
        factors.put("riskLevel", riskLevel);

        // 7. 生成交易建议详情
        TradeAdvice tradeAdvice = recommendationGenerator.generateTradeAdvice(analysisResult, finalScore, riskLevel, signal);

        // 8. 结构化日志输出
        analysisService.logStructuredResults(signal, analysisResult, finalScore, riskLevel, tradeAdvice, scores);

        // 9. 构建 PriceAnalysisDto
        PriceAnalysisDto priceAnalysisDto = buildSingleTimeFramePriceAnalysis(
                analysisResult, signal, finalScore, riskLevel, tradeAdvice, recommendations);

        return QualityEvaluationResult.builder()
                .evaluatorId(getId())
                .signalId(signal.getId())
                .score(finalScore)
                .weight(getWeight())
                .factors(factors)
                .summary(recommendationGenerator.buildSummary(analysisResult, scores))
                .recommendations(recommendations)
                .priceAnalysisDto(priceAnalysisDto)
                .build();
    }

    /**
     * 构建单周期价格分析DTO
     */
    private PriceAnalysisDto buildSingleTimeFramePriceAnalysis(
            ElliottAnalysisResult analysisResult,
            TradingSignalDto signal,
            double finalScore,
            String riskLevel,
            TradeAdvice tradeAdvice,
            List<String> recommendations) {

        boolean isBuySignal = signal.getType().name().equals("BUY");
        PricePositionInfo priceInfo = analysisResult.getPriceInfo();

        // 获取趋势方向
        String currentTrend = analysisResult.getBaseCase()
                .map(base -> base.hasKnownDirection() ?
                        (base.isBullish() ? "BULLISH" : "BEARISH") : "NEUTRAL")
                .orElse("NEUTRAL");

        // 构建趋势方向描述
        String trendDirection = currentTrend;
        if (analysisResult.getCurrentPhase() != null) {
            trendDirection = currentTrend + " - " + analysisResult.getCurrentPhase();
        }

        // 生成警告列表
        List<String> warnings = new ArrayList<>();
        if (analysisResult.isInvalidation()) {
            warnings.add("波浪结构已失效");
        }
        if (priceInfo != null && priceInfo.getRiskRewardRatio() != null &&
                priceInfo.getRiskRewardRatio() < 1.0) {
            warnings.add("风险收益比偏低（低于1:1）");
        }
        if ("HIGH".equals(riskLevel)) {
            warnings.add("高风险等级");
        }

        // 确定交易建议
        String tradingAdvice = "CAUTIOUS";
        if (finalScore >= 0.7 && !analysisResult.isInvalidation() &&
                priceInfo != null && priceInfo.getRiskRewardRatio() != null &&
                priceInfo.getRiskRewardRatio() >= 2.0) {
            tradingAdvice = "CONFIRMED";
        } else if (finalScore < 0.4 || analysisResult.isInvalidation() ||
                (priceInfo != null && priceInfo.getRiskRewardRatio() != null &&
                        priceInfo.getRiskRewardRatio() < 1.0)) {
            tradingAdvice = "AVOID";
        }

        return PriceAnalysisDto.builder()
                .currentPrice(signal.getTriggerPrice())
                .currentTrend(currentTrend)
                .trendDirection(trendDirection)
                .agreementLevel(3) // 单周期默认一致性为3
                .compositeScore(finalScore)
                .priceTargets(priceInfo != null ? priceInfo.getPriceTargets() : null)
                .stopLossLevels(priceInfo != null ? priceInfo.getStopLossLevels() : null)
                .optimalStopLoss(priceInfo != null ? priceInfo.getOptimalStopLoss() : null)
                .optimalTakeProfit(priceInfo != null ? priceInfo.getOptimalTakeProfit() : null)
                .riskRewardRatio(priceInfo != null ? priceInfo.getRiskRewardRatio() : null)
                .breakevenPrice(priceInfo != null ? priceInfo.getBreakevenPrice() : null)
                .targetStrategy(priceInfo != null ? priceInfo.getTargetStrategy() : null)
                .stopLossStrategy(priceInfo != null ? priceInfo.getStopLossStrategy() : null)
                .warnings(warnings)
                .recommendations(recommendations)
                .tradingAdvice(tradingAdvice)
                .build();
    }

    /**
     * 计算基础场景置信度得分
     */
    private double calculateBaseConfidenceScore(Optional<ElliottScenario> baseCase) {
        if (!baseCase.isPresent()) {
            return 0.3;
        }
        ElliottScenario base = baseCase.get();
        return base.confidence().asPercentage() / 100.0;
    }

    /**
     * 计算结构完整性得分
     */
    private double calculateStructureScore(Optional<ElliottScenario> baseCase,
                                           int waveCount, int filteredWaveCount) {
        if (!baseCase.isPresent()) {
            return 0.3;
        }

        ElliottScenario base = baseCase.get();
        ElliottConfidence confidence = base.confidence();

        // 1. 完整性因子（已提供）
        double completeness = confidence.completenessScore().doubleValue();

        // 2. 波浪数量因子（过滤后波浪太少可能有问题）
        double countFactor = 1.0;
        if (filteredWaveCount < 5) {
            countFactor = 0.5;
        } else if (filteredWaveCount > 15) {
            countFactor = 0.8; // 波浪太多可能过度拟合
        }

        // 3. 斐波那契因子
        double fibonacciFactor = confidence.fibonacciScore().doubleValue();

        // 4. 通道因子
        double channelFactor = confidence.channelScore().doubleValue();

        // 加权平均
        return (completeness * 0.4 + countFactor * 0.2 +
                fibonacciFactor * 0.3 + channelFactor * 0.1);
    }

    /**
     * 计算汇合评分得分
     */
    private double calculateConfluenceScore(org.ta4j.core.num.Num confluenceScoreNum) {
        if (confluenceScoreNum == null) {
            return 0.5;
        }

        double confluence = confluenceScoreNum.doubleValue();

        // 汇合评分非线性转换
        if (confluence > 0.8) return 1.0;  // 高度汇合
        if (confluence > 0.6) return 0.8;  // 良好汇合
        if (confluence > 0.4) return 0.6;  // 一般汇合
        if (confluence > 0.2) return 0.4;  // 弱汇合
        return 0.2;  // 无汇合
    }

    /**
     * 计算失效状态得分（考虑距离失效价格的距离）
     */
    private double calculateInvalidationScore(boolean invalidation,
                                              ElliottChannel channel,
                                              Optional<ElliottScenario> baseCase,
                                              PricePositionInfo priceInfo,
                                              double currentPrice) {

        if (!invalidation) {
            // 未失效，进一步检查通道
            if (channel != null && channel.isValid()) {
                return 1.0;  // 通道有效且未失效
            }
            return 0.7;  // 未失效但通道无效
        }

        // ========== 已失效情况的智能评分 ==========

        // 基础失效分数
        double baseScore = 0.1;

        // 如果有价格位置信息，根据距离调整
        if (priceInfo != null) {
            if (priceInfo.getDistanceToInvalidation() != null) {
                double distance = Math.abs(priceInfo.getDistanceToInvalidation());

                // 距离越远，失效影响越小（非线性调整）
                if (distance > 15.0) {
                    baseScore = 0.8;  // 距离>15%，几乎不影响
                } else if (distance > 10.0) {
                    baseScore = 0.7;
                } else if (distance > 5.0) {
                    baseScore = 0.5;
                } else if (distance > 3.0) {
                    baseScore = 0.3;
                } else if (distance > 1.0) {
                    baseScore = 0.2;
                }
                // 距离<1%，保持0.1

            }

            // 额外考虑风险收益比
            if (priceInfo.getRiskRewardRatio() != null) {
                double rrRatio = priceInfo.getRiskRewardRatio();
                if (rrRatio > 3.0) {
                    baseScore = Math.min(1.0, baseScore + 0.2); // 高收益可补偿
                } else if (rrRatio > 2.0) {
                    baseScore = Math.min(1.0, baseScore + 0.1);
                }
            }
        }

        // 考虑基础场景置信度
        if (baseCase.isPresent()) {
            ElliottScenario base = baseCase.get();
            double confidence = base.confidence().asPercentage();

            // 高置信度可部分补偿失效
            if (confidence > 80) {
                baseScore = Math.min(1.0, baseScore + 0.3);
            } else if (confidence > 60) {
                baseScore = Math.min(1.0, baseScore + 0.2);
            } else if (confidence > 40) {
                baseScore = Math.min(1.0, baseScore + 0.1);
            }
        }

        // 考虑通道质量
        if (channel != null && channel.isValid()) {
            baseScore = Math.min(1.0, baseScore + 0.1); // 有效通道补偿
        }

        return Math.max(0.1, Math.min(1.0, baseScore));
    }

    /**
     * 计算方向匹配得分（增强版，考虑相位和短期动能）
     */
    private double calculateDirectionMatchScore(TradingSignalDto signal,
                                                Optional<ElliottScenario> baseCase,
                                                ElliottPhase currentPhase) {
        // 基础版本，不包含价格位置信息（保持向后兼容）
        return calculateDirectionMatchScoreEnhanced(signal, baseCase, currentPhase, null);
    }

    /**
     * 计算方向匹配得分（增强版：包含价格位置信息）
     */
    private double calculateDirectionMatchScoreEnhanced(TradingSignalDto signal,
                                                        Optional<ElliottScenario> baseCase,
                                                        ElliottPhase currentPhase,
                                                        PricePositionInfo priceInfo) {
        if (!baseCase.isPresent()) {
            return 0.5;
        }

        ElliottScenario base = baseCase.get();
        if (!base.hasKnownDirection()) {
            return 0.5;
        }

        boolean scenarioBullish = base.isBullish();
        boolean signalBuy = signal.getType().name().equals("BUY");

        // 基础方向匹配
        boolean directionMatch = (signalBuy && scenarioBullish) || (!signalBuy && !scenarioBullish);

        if (directionMatch) {
            // 方向一致，结合相位判断强度
            if (currentPhase != null) {
                if (currentPhase.isImpulse() && scenarioBullish && signalBuy) {
                    return 1.0;  // 驱动浪看涨 + 买入信号
                } else if (currentPhase.isCorrective() && !scenarioBullish && !signalBuy) {
                    return 1.0;  // 调整浪看跌 + 卖出信号
                }
            }
            return 0.9;  // 方向一致但相位不完美
        } else {
            // 方向不一致，但考虑波浪相位和短期动能因素
            double baseScore = 0.1;
            double phaseFactor = 1.0;
            double momentumFactor = 1.0;

            // 考虑波浪相位
            if (currentPhase != null) {
                if (currentPhase.isImpulse()) {
                    // 驱动浪阶段，即使方向不一致也应降低惩罚
                    if (currentPhase == ElliottPhase.WAVE3) {
                        phaseFactor = 0.7; // 第3浪是最强驱动浪，降低惩罚
                    } else if (currentPhase == ElliottPhase.WAVE1 || currentPhase == ElliottPhase.WAVE5) {
                        phaseFactor = 0.5; // 第1浪或第5浪，中等惩罚
                    }
                }
            }

            // 考虑短期动能
            if (priceInfo != null && priceInfo.getShortMomentum() != null) {
                double momentum = priceInfo.getShortMomentum();
                // 如果短期动能强烈支持信号方向
                if ((signalBuy && momentum > 0.5) || (!signalBuy && momentum < -0.5)) {
                    momentumFactor = 0.6; // 降低惩罚
                }
            }

            // 综合评分（提高基础得分，然后应用因子）
            return Math.max(0.2, Math.min(0.5, baseScore + (1.0 - phaseFactor * momentumFactor) * 0.3));
        }
    }




    /**
     * 确定风险等级（增强版：使用多维风险矩阵）
     */
    private String determineRiskLevelEnhanced(double finalScore,
                                              boolean invalidation,
                                              double directionMatchScore,
                                              double confluenceScore,
                                              PricePositionInfo priceInfo,
                                              ElliottPhase currentPhase) {
        // 构建风险评分矩阵
        Map<String, Double> riskFactors = new HashMap<>();

        // 1. 综合得分因子（得分越低风险越高）
        riskFactors.put("finalScore", 1.0 - finalScore);

        // 2. 失效状态因子
        riskFactors.put("invalidation", invalidation ? 0.8 : 0.0);

        // 3. 方向匹配因子（改为非线性）
        double directionRisk;
        if (directionMatchScore < 0.3) {
            directionRisk = 0.7;
        } else if (directionMatchScore < 0.5) {
            directionRisk = 0.4;
        } else {
            directionRisk = 0.1;
        }
        riskFactors.put("direction", directionRisk);

        // 4. 汇合评分因子
        riskFactors.put("confluence", confluenceScore < 0.6 ? 0.3 : 0.0);

        // 5. 价格位置因子
        if (priceInfo != null) {
            double positionRisk = 0.0;
            if (priceInfo.getZone() != null) {
                try {
                    PriceZone zone = PriceZone.valueOf(priceInfo.getZone());
                    switch (zone) {
                        case UPPER_BAND:
                        case OUTSIDE_UPPER:
                            positionRisk = 0.4;
                            break;
                        case LOWER_BAND:
                        case OUTSIDE_LOWER:
                            positionRisk = 0.3;
                            break;
                        default:
                            positionRisk = 0.0;
                    }
                } catch (IllegalArgumentException e) {
                    // 忽略无效的区域值
                }
            }
            riskFactors.put("position", positionRisk);
        }

        // 6. 风险收益比因子
        if (priceInfo != null && priceInfo.getRiskRewardRatio() != null) {
            double rrRatio = priceInfo.getRiskRewardRatio();
            double rrRisk = rrRatio < 1.0 ? 0.6 :
                    rrRatio < 1.5 ? 0.3 : 0.0;
            riskFactors.put("riskReward", rrRisk);
        }

        // 7. 波浪相位因子（降低风险的因素）
        if (currentPhase != null) {
            double phaseRisk = 0.0;
            if (currentPhase == ElliottPhase.WAVE5) {
                phaseRisk = 0.4; // 第5浪末端风险高
            } else if (currentPhase == ElliottPhase.WAVE3) {
                phaseRisk = -0.2; // 第3浪主升浪，风险降低
            }
            riskFactors.put("phase", phaseRisk);
        }

        // 计算综合风险分数（0-1，越高风险越高）
        double totalRisk = riskFactors.values().stream().mapToDouble(Double::doubleValue).sum();

        // 归一化到0-1（考虑风险因子数量）
        double normalizedRisk = Math.min(1.0, Math.max(0.0, totalRisk / Math.max(1.0, riskFactors.size())));

        // 根据归一化风险分数确定等级
        if (normalizedRisk > 0.6) {
            return "HIGH_RISK";
        } else if (normalizedRisk > 0.3) {
            return "MEDIUM_RISK";
        } else {
            return "LOW_RISK";
        }
    }



    /**
     * 智能场景选择：根据当前相位、信号方向和置信度选择最佳场景
     */
    private Optional<ElliottScenario> selectBestScenario(
            ElliottPhase currentPhase,
            TradingSignalDto signal,
            org.ta4j.core.indicators.elliott.ElliottScenarioSet scenarioSet) {

        if (scenarioSet == null) {
            return Optional.empty();
        }

        // 获取基础场景和备选场景
        Optional<ElliottScenario> baseCase = scenarioSet.base();
        List<ElliottScenario> alternatives = scenarioSet.alternatives();

        // 如果没有基础场景，返回空
        if (!baseCase.isPresent()) {
            return Optional.empty();
        }

        // 如果只有一个基础场景且没有备选场景，直接返回基础场景
        if (alternatives == null || alternatives.isEmpty()) {
            return baseCase;
        }

        // 判断信号方向
        boolean signalBuy = signal.getType().name().equals("BUY");

        // 构建所有场景列表（基础场景 + 备选场景）
        List<ElliottScenario> allScenarios = new ArrayList<>();
        allScenarios.add(baseCase.get());
        allScenarios.addAll(alternatives);

        // 如果启用优先考虑当前相位的策略
        if (prioritizeCurrentPhase && currentPhase != null) {
            // 优先选择与当前相位一致的场景
            List<ElliottScenario> phaseMatchedScenarios = allScenarios.stream()
                    .filter(s -> s.currentPhase() == currentPhase)
                    .collect(Collectors.toList());

            if (!phaseMatchedScenarios.isEmpty()) {
                // 在相位匹配的场景中，优先选择方向与信号一致的场景
                List<ElliottScenario> directionMatchedScenarios = phaseMatchedScenarios.stream()
                        .filter(s -> {
                            if (!s.hasKnownDirection()) {
                                return false;
                            }
                            boolean scenarioBullish = s.isBullish();
                            return (signalBuy && scenarioBullish) || (!signalBuy && !scenarioBullish);
                        })
                        .collect(Collectors.toList());

                if (!directionMatchedScenarios.isEmpty()) {
                    // 选择置信度最高的
                    return directionMatchedScenarios.stream()
                            .max(Comparator.comparing(s -> s.confidence().asPercentage()));
                } else {
                    // 如果没有方向匹配的，选择相位匹配中置信度最高的
                    return phaseMatchedScenarios.stream()
                            .max(Comparator.comparing(s -> s.confidence().asPercentage()));
                }
            }
        }

        // 如果没有启用优先级策略，或者没有相位匹配的场景，使用综合评分
        // 计算每个场景的评分（考虑方向匹配和置信度）
        ElliottScenario bestScenario = allScenarios.stream()
                .max(Comparator.comparing(s -> {
                    double score = 0.0;

                    // 置信度因子（0-1）
                    double confidenceScore = s.confidence().asPercentage() / 100.0;
                    score += confidenceScore * (1.0 - directionWeight - phaseWeight);

                    // 方向匹配因子（0-1）
                    double directionScore = 0.5; // 默认中性
                    if (s.hasKnownDirection()) {
                        boolean scenarioBullish = s.isBullish();
                        if ((signalBuy && scenarioBullish) || (!signalBuy && !scenarioBullish)) {
                            directionScore = 1.0; // 方向一致
                        } else {
                            directionScore = 0.0; // 方向不一致
                        }
                    }
                    score += directionScore * directionWeight;

                    // 相位匹配因子（0-1）
                    double phaseScore = (currentPhase != null && s.currentPhase() == currentPhase) ? 1.0 : 0.0;
                    score += phaseScore * phaseWeight;

                    return score;
                }))
                .orElse(baseCase.get());

        return Optional.of(bestScenario);
    }

    /**
     * 交易建议内部类
     */
    private static class TradeAdvice {
        private String action; // AVOID, CAUTIOUS, CONFIRMED
        private String reason;
        private String bias; // BULLISH, BEARISH
        private String entryStrategy;
        private double stopLoss;
        private double takeProfit;
        private String dynamicStopLoss;
        private String takeProfitRecommendation; // 新增

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getBias() { return bias; }
        public void setBias(String bias) { this.bias = bias; }

        public String getEntryStrategy() { return entryStrategy; }
        public void setEntryStrategy(String entryStrategy) { this.entryStrategy = entryStrategy; }

        public double getStopLoss() { return stopLoss; }
        public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }

        public double getTakeProfit() { return takeProfit; }
        public void setTakeProfit(double takeProfit) { this.takeProfit = takeProfit; }

        public String getDynamicStopLoss() { return dynamicStopLoss; }
        public void setDynamicStopLoss(String dynamicStopLoss) { this.dynamicStopLoss = dynamicStopLoss; }
        public String getTakeProfitRecommendation() { return takeProfitRecommendation; }
        public void setTakeProfitRecommendation(String takeProfitRecommendation) { this.takeProfitRecommendation = takeProfitRecommendation; }
    }

    /**
     * 结构化日志输出（完整版，包含价格位置分析）
     */
    private void logStructuredResults(
            TradingSignalDto signal,
            ElliottPhase currentPhase,
            boolean invalidation,
            ElliottChannel channel,
            ElliottRatio ratio,
            org.ta4j.core.num.Num confluenceScoreNum,
            int waveCountValue,
            int filteredWaveCountValue,
            Optional<ElliottScenario> baseCase,
            List<ElliottScenario> alternatives,
            double finalScore,
            String riskLevel,
            TradeAdvice tradeAdvice,
            double baseConfidenceScore,
            double structureScore,
            double confluenceScore,
            double invalidationScore,
            double directionMatchScore,
            double pricePositionScore,
            PricePositionInfo priceInfo) {
        log.info("=".repeat(80));
        log.info("📊 艾略特波浪深度分析报告");
        log.info("交易对: {}, 信号ID: {}, 类型: {}",
                signal.getSymbol(), signal.getId(), signal.getType());
        log.info("信号触发价格: {}", String.format("%.4f", signal.getTriggerPrice()));
        log.info("-".repeat(80));

        // 1. 核心评分
        log.info("【核心评分】");
        log.info("  综合得分: {}/100", String.format("%.0f", finalScore * 100));
        log.info("  风险等级: {} ({})", getRiskLevelDescription(riskLevel), riskLevel);
        log.info("  交易建议: {} - {}", tradeAdvice.getAction(), tradeAdvice.getReason());

        // 2. 详细因子分析（表格形式）
        log.info("【详细因子分析】");
        log.info("  ┌─────────────────┬─────────┬─────────┬────────────────────┐");
        log.info("  │ 因子            │ 得分    │ 权重    │ 状态               │");
        log.info("  ├─────────────────┼─────────┼─────────┼────────────────────┤");
        log.info("  │ 基础置信度      │ {} │ {}│ {} │",
                String.format("%6.1f", baseConfidenceScore * 100), String.format("%6.1f", baseConfidenceWeight * 100),
                getScoreIndicator(baseConfidenceScore));
        log.info("  │ 结构完整性      │ {} │ {}│ {} │",
                String.format("%6.1f", structureScore * 100), String.format("%6.1f", structureCompletenessWeight * 100),
                getScoreIndicator(structureScore));
        log.info("  │ 技术汇合        │ {} │ {}│ {} │",
                String.format("%6.1f", confluenceScore * 100), String.format("%6.1f", confluenceWeight * 100),
                getScoreIndicator(confluenceScore));
        log.info("  │ 失效状态        │ {} │ {}│ {} │",
                String.format("%6.1f", invalidationScore * 100), String.format("%6.1f", invalidationWeight * 100),
                getScoreIndicator(invalidationScore));
        log.info("  │ 方向匹配        │ {} │ {}│ {} │",
                String.format("%6.1f", directionMatchScore * 100), String.format("%6.1f", directionMatchWeight * 100),
                getScoreIndicator(directionMatchScore));
        log.info("  │ 价格位置        │ {} │ {}│ {} │",
                String.format("%6.1f", pricePositionScore * 100), String.format("%6.1f", pricePositionWeight * 100),
                getScoreIndicator(pricePositionScore));
        log.info("  └─────────────────┴─────────┴─────────┴────────────────────┘");

        // 3. 价格位置分析（新增）
        log.info("【价格位置分析】");
        if (priceInfo != null) {
            log.info("  📊 当前价格: {}", String.format("%.4f", priceInfo.getCurrentPrice()));

            if (priceInfo.getChannelPosition() != null) {
                try {
                    PriceZone zone = PriceZone.valueOf(priceInfo.getZone());
                    log.info("  📍 通道位置: {}% ({})",
                            String.format("%.1f", priceInfo.getChannelPosition()), zone.getName());
                    log.info("    位置解读: {}", zone.getDescription());

                    if (priceInfo.getAboveMedian() != null) {
                        log.info("    相对中轨: {} ({})",
                                priceInfo.getAboveMedian() ? "上方" : "下方",
                                priceInfo.getAboveMedian() ? "偏强" : "偏弱");
                    }
                } catch (IllegalArgumentException e) {
                    // 忽略无效的区域值
                }
            }

            if (priceInfo.getDistanceToInvalidation() != null) {
                log.info("  🚨 距离失效价格: {}% ({}点, {})",
                        String.format("%.2f", Math.abs(priceInfo.getDistanceToInvalidation())),
                        String.format("%.4f", priceInfo.getPriceToInvalidation()),
                        priceInfo.getAboveInvalidation() != null && priceInfo.getAboveInvalidation() ? "已失效" : "未失效");
            }

            if (priceInfo.getDistanceToTarget() != null) {
                String direction = priceInfo.getDistanceToTarget() > 0 ? "已超过" : "未达到";
                log.info("  🎯 距离目标价格: {}% ({}点, {})",
                        String.format("%.2f", Math.abs(priceInfo.getDistanceToTarget())),
                        String.format("%.4f", priceInfo.getPriceToTarget()),
                        direction);
            }

            if (priceInfo.getRiskRewardRatio() != null) {
                String rrStatus = priceInfo.getRiskRewardRatio() >= minRiskRewardRatio ? "良好" :
                        priceInfo.getRiskRewardRatio() >= warningRiskRewardRatio ? "可接受" : "偏低";
                log.info("  ⚖️  风险收益比: 1:{} ({})",
                        String.format("%.2f", priceInfo.getRiskRewardRatio()), rrStatus);
            }

            if (priceInfo.getShortMomentum() != null) {
                String momentumDesc = priceInfo.getShortMomentum() > 0.3 ? "上涨动能强" :
                        priceInfo.getShortMomentum() < -0.3 ? "下跌动能强" : "震荡整理";
                log.info("  ⚡ 短期动能: {} ({})",
                        String.format("%.2f", priceInfo.getShortMomentum()), momentumDesc);
            }

            if (priceInfo.getWaveMomentum() != null) {
                log.info("  🌊 波浪动能: {}", priceInfo.getWaveMomentum());
            }

            // 交易建议
            if (priceInfo.getEntryRecommendation() != null) {
                log.info("  🚀 入场建议: {}", priceInfo.getEntryRecommendation());
            }
            if (priceInfo.getStopLossRecommendation() != null) {
                log.info("  🛡️  止损建议: {}", priceInfo.getStopLossRecommendation());
            }
            if (priceInfo.getTakeProfitRecommendation() != null) {
                log.info("  💰 止盈建议: {}", priceInfo.getTakeProfitRecommendation());
            }
        }

        // 4. 交易建议详情
        log.info("【交易建议汇总】");
        if (tradeAdvice.getBias() != null) {
            log.info("  操作倾向: {}", tradeAdvice.getBias());
        }
        if (tradeAdvice.getEntryStrategy() != null) {
            log.info("  入场策略: {}", tradeAdvice.getEntryStrategy());
        }
        if (tradeAdvice.getStopLoss() > 0) {
            log.info("  建议止损: {}", String.format("%.4f", tradeAdvice.getStopLoss()));
        }
        if (tradeAdvice.getTakeProfit() > 0) {
            log.info("  建议止盈: {}", String.format("%.4f", tradeAdvice.getTakeProfit()));
        }
        if (tradeAdvice.getTakeProfitRecommendation() != null) {
            log.info("  💰 止盈建议: {}", tradeAdvice.getTakeProfitRecommendation());
        }
        if (tradeAdvice.getDynamicStopLoss() != null) {
            log.info("  动态止损: {}", tradeAdvice.getDynamicStopLoss());
        }

        // 5. 警告信息
        if (finalScore < highRiskMax || invalidation) {
            log.warn("⚠️  重要警告: 波浪结构不可靠，建议放弃此信号");
        }

        // 6. 风险收益比警告
        if (priceInfo != null && priceInfo.getRiskRewardRatio() != null &&
                priceInfo.getRiskRewardRatio() < warningRiskRewardRatio) {
            log.warn("⚠️  风险警告: 风险收益比偏低(1:{})，建议等待更好机会",
                    String.format("%.2f", priceInfo.getRiskRewardRatio()));
        }

        log.info("=".repeat(80));
    }

    /**
     * 获取风险等级描述
     */
    private String getRiskLevelDescription(String riskLevel) {
        switch (riskLevel) {
            case "LOW_RISK":
                return "低风险";
            case "MEDIUM_RISK":
                return "中风险";
            case "HIGH_RISK":
                return "高风险";
            default:
                return "未知";
        }
    }

    /**
     * 获取评分指示器
     */
    private String getScoreIndicator(double score) {
        if (score > 0.8) return "✅ 优秀";
        if (score > 0.6) return "✓ 良好";
        if (score > 0.4) return "⚠️ 一般";
        return "❌ 较差";
    }

    /**
     * 价格区域枚举
     */
    private enum PriceZone {
        UPPER_BAND("上轨区域", "价格接近通道上轨，可能超买"),
        UPPER_HALF("通道上半部", "价格偏强"),
        MIDDLE("中轨附近", "价格均衡"),
        LOWER_HALF("通道下半部", "价格偏弱"),
        LOWER_BAND("下轨区域", "价格接近通道下轨，可能超卖"),
        OUTSIDE_UPPER("突破上轨", "价格突破通道，趋势强劲"),
        OUTSIDE_LOWER("跌破下轨", "价格跌破通道，趋势转弱");

        private final String name;
        private final String description;

        PriceZone(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    /**
     * 价格位置信息类（增强版 - 添加详细目标信息）
     */
    @Data
    @Builder
    public static class PricePositionInfo {
        private double currentPrice;          // 当前价格（信号触发价）
        private Double channelPosition;       // 通道位置百分比 (0-100)
        private String zone;                  // 价格区域
        private Boolean aboveMedian;          // 是否在中轨上方
        private Boolean belowMedian;          // 是否在中轨下方
        private Double distanceToInvalidation; // 距离失效价格的百分比
        private Boolean aboveInvalidation;    // 是否超过失效价格
        private Double invalidationPrice;     // 失效价格（绝对值）
        private Double priceToInvalidation;   // 距离失效价格的点数
        private Double distanceToTarget;      // 距离目标价格的百分比
        private Boolean aboveTarget;          // 是否超过目标价格
        private Double targetPrice;           // 目标价格（绝对值）
        private Double priceToTarget;         // 距离目标价格的点数
        private Double riskRewardRatio;       // 风险收益比
        private Double shortMomentum;         // 短期动能（-1到1）
        private String waveMomentum;          // 波浪动能描述
        private String entryRecommendation;   // 入场建议
        private String stopLossRecommendation; // 止损建议
        private String takeProfitRecommendation; // 止盈建议

        // 新增：详细交易目标信息
        private List<PriceTarget> priceTargets;          // 价格目标列表
        private List<StopLossLevel> stopLossLevels;      // 止损水平列表
        private Double optimalStopLoss;                 // 最优止损位
        private Double optimalTakeProfit;               // 最优止盈位
        private Double breakevenPrice;                  // 盈亏平衡点
        private String targetStrategy;                  // 目标策略描述
        private String stopLossStrategy;                // 止损策略描述




    }

    /**
     * 查找信号时间对应的K线索引
     */
    private int findSignalIndex(BarSeries series, LocalDateTime signalTime) {
        // 将 LocalDateTime 转换为 Instant（使用系统默认时区）
        Instant signalInstant = signalTime.atZone(ZoneId.systemDefault()).toInstant();

        for (int i = 0; i < series.getBarCount(); i++) {
            if (!series.getBar(i).getEndTime().isBefore(signalInstant)) {
                return i;
            }
        }
        return series.getEndIndex();
    }

    /**
     * 计算价格位置信息
     */
    private PricePositionInfo calculatePricePosition(
            TradingSignalDto signal,
            BarSeries series,
            ElliottChannel channel,
            Optional<ElliottScenario> baseCase,
            ElliottPhase currentPhase) {
        PricePositionInfo.PricePositionInfoBuilder builder = PricePositionInfo.builder();

        // 1. 设置当前价格（信号触发价）
        double currentPrice = signal.getTriggerPrice();
        builder.currentPrice(currentPrice);
        org.ta4j.core.num.Num currentPriceNum = series.numFactory().numOf(currentPrice);

        // 2. 计算通道位置
        if (channel != null && channel.isValid() &&
                channel.upper() != null && channel.lower() != null) {

            org.ta4j.core.num.Num upper = channel.upper();
            org.ta4j.core.num.Num lower = channel.lower();
            org.ta4j.core.num.Num median = channel.median();

            // 计算通道宽度和位置
            org.ta4j.core.num.Num channelWidth = upper.minus(lower);
            if (channelWidth.doubleValue() > 0) {
                double position = (currentPriceNum.minus(lower).dividedBy(channelWidth).doubleValue()) * 100;
                builder.channelPosition(position);
                builder.aboveMedian(currentPriceNum.isGreaterThan(median));
                builder.belowMedian(currentPriceNum.isLessThan(median));

                // 确定价格区域
                if (currentPriceNum.isGreaterThan(upper)) {
                    builder.zone(PriceZone.OUTSIDE_UPPER.name());
                } else if (currentPriceNum.isLessThan(lower)) {
                    builder.zone(PriceZone.OUTSIDE_LOWER.name());
                } else if (position >= channelUpperThreshold) {
                    builder.zone(PriceZone.UPPER_BAND.name());
                } else if (position <= channelLowerThreshold) {
                    builder.zone(PriceZone.LOWER_BAND.name());
                } else if (position > 55) {
                    builder.zone(PriceZone.UPPER_HALF.name());
                } else if (position < 45) {
                    builder.zone(PriceZone.LOWER_HALF.name());
                } else {
                    builder.zone(PriceZone.MIDDLE.name());
                }
            }
        }

        // 3. 计算与关键价格的距离
        if (baseCase.isPresent()) {
            ElliottScenario base = baseCase.get();

            // 失效价格
            if (base.invalidationPrice() != null) {
                org.ta4j.core.num.Num invalidationPrice = base.invalidationPrice();
                double priceDiff = currentPriceNum.minus(invalidationPrice).doubleValue();
                double percentDiff = (priceDiff / invalidationPrice.doubleValue()) * 100;
                builder.distanceToInvalidation(percentDiff);
                builder.aboveInvalidation(currentPriceNum.isGreaterThan(invalidationPrice));
                builder.invalidationPrice(invalidationPrice.doubleValue());
                builder.priceToInvalidation(Math.abs(priceDiff));
            }

            // 目标价格
            if (base.primaryTarget() != null) {
                org.ta4j.core.num.Num targetPrice = base.primaryTarget();
                double priceDiff = currentPriceNum.minus(targetPrice).doubleValue();
                double percentDiff = (priceDiff / targetPrice.doubleValue()) * 100;
                builder.distanceToTarget(percentDiff);
                builder.aboveTarget(currentPriceNum.isGreaterThan(targetPrice));
                builder.targetPrice(targetPrice.doubleValue());
                builder.priceToTarget(Math.abs(priceDiff));
            }

            // 4. 计算风险收益比
            PricePositionInfo tempInfo = builder.build();
            if (tempInfo.getTargetPrice() != null && tempInfo.getInvalidationPrice() != null) {
                double risk = Math.abs(currentPrice - tempInfo.getInvalidationPrice());
                double reward = Math.abs(tempInfo.getTargetPrice() - currentPrice);
                if (risk > 0) {
                    builder.riskRewardRatio(reward / risk);
                }
            }
        }

        PricePositionInfo info = builder.build();

        // 5. 计算短期动能
        calculateMomentum(series, info, currentPhase, signal);

        return info;
    }

    /**
     * 计算短期动能
     */
    private void calculateMomentum(
            BarSeries series,
            PricePositionInfo info,
            ElliottPhase currentPhase,
            TradingSignalDto signal) {
        try {
            // 获取信号时间对应的K线索引
            int signalIndex = findSignalIndex(series, signal.getTimestamp());

            // 计算前5根K线的动量
            int startIdx = Math.max(0, signalIndex - 5);
            double momentumSum = 0;
            int count = 0;

            for (int i = startIdx + 1; i <= signalIndex; i++) {
                if (i < series.getBarCount()) {
                    double close = series.getBar(i).getClosePrice().doubleValue();
                    double prevClose = series.getBar(i - 1).getClosePrice().doubleValue();

                    if (close > prevClose) momentumSum += 1;
                    else if (close < prevClose) momentumSum -= 1;
                    count++;
                }
            }

            info.setShortMomentum(count > 0 ? momentumSum / count : 0.0);

            // 根据波浪相位设置动能描述
            if (currentPhase != null) {
                switch (currentPhase) {
                    case WAVE1:
                        info.setWaveMomentum("驱动浪1开始，新生动能");
                        break;
                    case WAVE2:
                        info.setWaveMomentum("调整浪2回调，下跌动能");
                        break;
                    case WAVE3:
                        info.setWaveMomentum("驱动浪3主升，最强动能");
                        break;
                    case WAVE4:
                        info.setWaveMomentum("调整浪4回调，下跌动能");
                        break;
                    case WAVE5:
                        info.setWaveMomentum("驱动浪5末端，动能衰竭");
                        break;
                    case CORRECTIVE_A:
                        info.setWaveMomentum("调整浪A，反趋势动能");
                        break;
                    case CORRECTIVE_B:
                        info.setWaveMomentum("调整浪B，反弹动能");
                        break;
                    case CORRECTIVE_C:
                        info.setWaveMomentum("调整浪C，主跌/主升动能");
                        break;
                    default:
                        info.setWaveMomentum("未知波浪动能");
                }
            }
        } catch (Exception e) {
            log.warn("计算动能时出错: {}", e.getMessage());
            info.setShortMomentum(0.0);
            info.setWaveMomentum("动能计算失败");
        }
    }

    /**
     * 增强的交易建议生成（包含详细止盈止损目标）
     */
    private void generateTradingRecommendationsEnhanced(
            TradingSignalDto signal,
            PricePositionInfo info,
            ElliottPhase currentPhase,
            Optional<ElliottScenario> baseCase,
            boolean invalidation,
            ElliottChannel channel) {

        StringBuilder entrySb = new StringBuilder();
        StringBuilder stopLossSb = new StringBuilder();
        StringBuilder takeProfitSb = new StringBuilder();

        boolean isBuySignal = signal.getType().name().equals("BUY");
        double currentPrice = signal.getTriggerPrice();

        // 1. 生成详细价格目标
        List<PriceTarget> priceTargets = calculatePriceTargets(
                signal, info, currentPhase, baseCase, channel, currentPrice);
        info.setPriceTargets(priceTargets);

        // 2. 生成详细止损水平
        List<StopLossLevel> stopLossLevels = calculateStopLossLevels(
                signal, info, currentPhase, baseCase, channel, currentPrice);
        info.setStopLossLevels(stopLossLevels);

        // 3. 计算最优止盈止损
        calculateOptimalTargets(info, priceTargets, stopLossLevels, currentPrice, isBuySignal);

        // 4. 生成策略描述
        String targetStrategy = generateTargetStrategy(priceTargets, currentPhase, isBuySignal);
        String stopLossStrategy = generateStopLossStrategy(stopLossLevels, invalidation, currentPhase);
        info.setTargetStrategy(targetStrategy);
        info.setStopLossStrategy(stopLossStrategy);

        // 5. 计算盈亏平衡点
        if (info.getOptimalStopLoss() != null && info.getOptimalTakeProfit() != null) {
            double risk = Math.abs(currentPrice - info.getOptimalStopLoss());
            double reward = Math.abs(info.getOptimalTakeProfit() - currentPrice);
            if (risk > 0) {
                // 盈亏平衡点 = 入场价 + 手续费和滑点的补偿
                double breakevenPoint = currentPrice + (isBuySignal ? risk * 0.002 : -risk * 0.002);
                info.setBreakevenPrice(breakevenPoint);
            }
        }

        // 6. 生成建议文本（保持原有逻辑）
        if (info.getZone() != null) {
            try {
                PriceZone zone = PriceZone.valueOf(info.getZone());
                switch (zone) {
                    case LOWER_BAND:
                    case OUTSIDE_LOWER:
                        if (isBuySignal) {
                            entrySb.append("价格处于通道下轨附近，是良好的买入位置");
                        } else {
                            entrySb.append("价格已深度回调，卖出信号风险较高");
                        }
                        break;
                    case UPPER_BAND:
                    case OUTSIDE_UPPER:
                        if (!isBuySignal) {
                            entrySb.append("价格处于通道上轨附近，是良好的卖出位置");
                        } else {
                            entrySb.append("价格已大幅上涨，买入信号风险较高");
                        }
                        break;
                    case MIDDLE:
                        entrySb.append("价格处于通道中轨，方向待明确");
                        break;
                    case UPPER_HALF:
                        entrySb.append(isBuySignal ? "价格偏强，可考虑买入" : "价格偏高，可考虑卖出");
                        break;
                    case LOWER_HALF:
                        entrySb.append(isBuySignal ? "价格偏弱，谨慎买入" : "价格偏低，谨慎卖出");
                        break;
                }
            } catch (IllegalArgumentException e) {
                // 忽略无效的区域值
            }
        }

        // 根据波浪相位补充建议
        if (currentPhase != null) {
            switch (currentPhase) {
                case WAVE2:
                case WAVE4:
                    entrySb.append("。当前为调整浪，建议等待调整结束确认");
                    break;
                case WAVE3:
                    if (isBuySignal) {
                        entrySb.append("。当前为主升浪3，是理想的买入时机");
                    }
                    break;
                case WAVE5:
                    entrySb.append("。当前为末端浪5，注意动能衰竭");
                    break;
                default:
                    break;
            }
        }

        // 生成止损建议
        if (!stopLossLevels.isEmpty()) {
            for (StopLossLevel stopLoss : stopLossLevels) {
                if (stopLoss.isPrimary()) {
                    stopLossSb.append(String.format("主要止损设在 %.4f（%s）",
                            stopLoss.getPrice(), stopLoss.getDescription()));
                    break;
                }
            }
        }

        // 生成止盈建议
        if (!priceTargets.isEmpty()) {
            for (PriceTarget target : priceTargets) {
                if (target.getLevel() == 1) {
                    takeProfitSb.append(String.format("第一目标 %.4f（%s，风险收益比 1:%.2f）",
                            target.getPrice(), target.getDescription(), target.getRiskRewardRatio()));
                    break;
                }
            }
        }

        // 失效警告
        if (invalidation) {
            entrySb.insert(0, "⚠️ 波浪结构已失效，");
            stopLossSb.append("（结构失效，建议更严格止损）");
        }

        info.setEntryRecommendation(entrySb.toString());
        info.setStopLossRecommendation(stopLossSb.toString());
        info.setTakeProfitRecommendation(takeProfitSb.toString());
    }

    /**
     * 计算价格目标
     */
    private List<PriceTarget> calculatePriceTargets(
            TradingSignalDto signal,
            PricePositionInfo info,
            ElliottPhase currentPhase,
            Optional<ElliottScenario> baseCase,
            ElliottChannel channel,
            double currentPrice) {

        List<PriceTarget> targets = new ArrayList<>();
        boolean isBuySignal = signal.getType().name().equals("BUY");

        // 目标1：波浪主要目标（来自基础场景）
        if (baseCase.isPresent() && baseCase.get().primaryTarget() != null) {
            double targetPrice = baseCase.get().primaryTarget().doubleValue();
            double distance = Math.abs(targetPrice - currentPrice) / currentPrice * 100;

            PriceTarget target = PriceTarget.builder()
                    .level(1)
                    .price(targetPrice)
                    .probability(0.6) // 基础目标概率
                    .description("波浪主要目标")
                    .basedOn("艾略特波浪目标价")
                    .distanceFromCurrent(distance)
                    .build();

            // 计算该目标的风险收益比
            if (info.getInvalidationPrice() != null) {
                double risk = Math.abs(currentPrice - info.getInvalidationPrice());
                double reward = Math.abs(targetPrice - currentPrice);
                if (risk > 0) {
                    target.setRiskRewardRatio(reward / risk);
                }
            }

            targets.add(target);
        }

        // 目标2：斐波那契扩展目标
        if (channel != null && channel.isValid()) {
            double channelWidth = channel.upper().doubleValue() - channel.lower().doubleValue();

            // 计算斐波那契扩展位
            double fib1618 = isBuySignal ?
                    currentPrice + channelWidth * 0.618 :
                    currentPrice - channelWidth * 0.618;

            double fib2618 = isBuySignal ?
                    currentPrice + channelWidth * 1.618 :
                    currentPrice - channelWidth * 1.618;

            targets.add(PriceTarget.builder()
                    .level(2)
                    .price(fib1618)
                    .probability(0.4)
                    .description("斐波那契61.8%扩展位")
                    .basedOn("通道宽度斐波那契扩展")
                    .distanceFromCurrent(Math.abs(fib1618 - currentPrice) / currentPrice * 100)
                    .build());

            targets.add(PriceTarget.builder()
                    .level(3)
                    .price(fib2618)
                    .probability(0.3)
                    .description("斐波那契161.8%扩展位")
                    .basedOn("通道宽度斐波那契扩展")
                    .distanceFromCurrent(Math.abs(fib2618 - currentPrice) / currentPrice * 100)
                    .build());
        }

        // 目标3：通道边界目标
        if (channel != null && channel.isValid()) {
            double channelTarget = isBuySignal ?
                    channel.upper().doubleValue() :
                    channel.lower().doubleValue();

            targets.add(PriceTarget.builder()
                    .level(targets.size() + 1)
                    .price(channelTarget)
                    .probability(0.5)
                    .description("通道边界")
                    .basedOn("通道上/下轨")
                    .distanceFromCurrent(Math.abs(channelTarget - currentPrice) / currentPrice * 100)
                    .build());
        }

        // 根据距离排序（买入信号按升序，卖出信号按降序）
        targets.sort((t1, t2) -> {
            if (isBuySignal) {
                return Double.compare(t1.getPrice(), t2.getPrice());
            } else {
                return Double.compare(t2.getPrice(), t1.getPrice());
            }
        });

        // 重新分配级别
        for (int i = 0; i < targets.size(); i++) {
            targets.get(i).setLevel(i + 1);
        }

        return targets;
    }

    /**
     * 计算止损水平
     */
    private List<StopLossLevel> calculateStopLossLevels(
            TradingSignalDto signal,
            PricePositionInfo info,
            ElliottPhase currentPhase,
            Optional<ElliottScenario> baseCase,
            ElliottChannel channel,
            double currentPrice) {

        List<StopLossLevel> stopLossLevels = new ArrayList<>();
        boolean isBuySignal = signal.getType().name().equals("BUY");

        // 止损1：波浪失效价
        if (info.getInvalidationPrice() != null) {
            double stopPrice = info.getInvalidationPrice();
            double riskPercentage = Math.abs(stopPrice - currentPrice) / currentPrice * 100;

            stopLossLevels.add(StopLossLevel.builder()
                    .level(1)
                    .price(stopPrice)
                    .type("固定止损")
                    .description("波浪失效价")
                    .basedOn("艾略特波浪失效价格")
                    .riskPercentage(riskPercentage)
                    .isPrimary(true)
                    .build());
        }

        // 止损2：通道边界
        if (channel != null && channel.isValid()) {
            double channelStop = isBuySignal ?
                    channel.lower().doubleValue() :
                    channel.upper().doubleValue();

            double riskPercentage = Math.abs(channelStop - currentPrice) / currentPrice * 100;

            stopLossLevels.add(StopLossLevel.builder()
                    .level(2)
                    .price(channelStop)
                    .type("通道止损")
                    .description("通道边界")
                    .basedOn("通道下/上轨")
                    .riskPercentage(riskPercentage)
                    .isPrimary(false)
                    .build());
        }

        // 止损3：最近支撑/阻力位（简化实现）
        if (currentPrice > 0) {
            double recentLevelStop;
            if (isBuySignal) {
                // 买入信号：设置在最近支撑位下方2%
                recentLevelStop = currentPrice * 0.98;
            } else {
                // 卖出信号：设置在最近阻力位上方2%
                recentLevelStop = currentPrice * 1.02;
            }

            double riskPercentage = Math.abs(recentLevelStop - currentPrice) / currentPrice * 100;

            stopLossLevels.add(StopLossLevel.builder()
                    .level(3)
                    .price(recentLevelStop)
                    .type("技术止损")
                    .description("最近支撑/阻力位")
                    .basedOn("价格结构支撑阻力")
                    .riskPercentage(riskPercentage)
                    .isPrimary(false)
                    .build());
        }

        // 根据风险百分比排序（风险越小越好）
        stopLossLevels.sort(Comparator.comparingDouble(StopLossLevel::getRiskPercentage));

        return stopLossLevels;
    }

    /**
     * 计算最优止盈止损
     */
    private void calculateOptimalTargets(
            PricePositionInfo info,
            List<PriceTarget> priceTargets,
            List<StopLossLevel> stopLossLevels,
            double currentPrice,
            boolean isBuySignal) {

        if (priceTargets.isEmpty() || stopLossLevels.isEmpty()) {
            return;
        }

        // 寻找最佳风险收益比的组合
        double bestRRRatio = 0;
        PriceTarget bestTarget = null;
        StopLossLevel bestStopLoss = null;

        for (StopLossLevel stopLoss : stopLossLevels) {
            // 只考虑主要止损或风险较低的止损
            if (stopLoss.isPrimary() || stopLoss.getRiskPercentage() < 5.0) {
                for (PriceTarget target : priceTargets) {
                    if (target.getLevel() <= 3) { // 只考虑前3个目标
                        double risk = Math.abs(currentPrice - stopLoss.getPrice());
                        double reward = Math.abs(target.getPrice() - currentPrice);

                        if (risk > 0) {
                            double rrRatio = reward / risk;

                            // 检查方向是否正确
                            boolean directionValid = (isBuySignal && target.getPrice() > currentPrice) ||
                                    (!isBuySignal && target.getPrice() < currentPrice);

                            if (directionValid && rrRatio > bestRRRatio && rrRatio > 1.0) {
                                bestRRRatio = rrRatio;
                                bestTarget = target;
                                bestStopLoss = stopLoss;
                            }
                        }
                    }
                }
            }
        }

        if (bestTarget != null && bestStopLoss != null) {
            info.setOptimalTakeProfit(bestTarget.getPrice());
            info.setOptimalStopLoss(bestStopLoss.getPrice());
        }
    }

    /**
     * 生成目标策略描述
     */
    private String generateTargetStrategy(
            List<PriceTarget> priceTargets,
            ElliottPhase currentPhase,
            boolean isBuySignal) {

        if (priceTargets.isEmpty()) {
            return "无明确目标策略";
        }

        StringBuilder strategy = new StringBuilder();

        // 根据波浪相位调整策略
        if (currentPhase != null) {
            switch (currentPhase) {
                case WAVE3:
                    strategy.append("主升浪阶段，目标可以更积极。");
                    break;
                case WAVE5:
                    strategy.append("末端浪阶段，注意动能衰竭，目标应保守。");
                    break;
                case WAVE2:
                case WAVE4:
                    strategy.append("调整浪阶段，目标应等待突破确认。");
                    break;
                default:
                    strategy.append("根据波浪相位制定目标策略。");
                    break;
            }
        }

        // 添加具体目标建议
        strategy.append("建议采用分批止盈：");
        for (PriceTarget target : priceTargets) {
            if (target.getLevel() <= 3) {
                strategy.append(String.format("\n- 目标%d: %.4f (%s，概率%.0f%%)",
                        target.getLevel(), target.getPrice(),
                        target.getDescription(), target.getProbability() * 100));
            }
        }

        return strategy.toString();
    }

    /**
     * 生成止损策略描述
     */
    private String generateStopLossStrategy(
            List<StopLossLevel> stopLossLevels,
            boolean invalidation,
            ElliottPhase currentPhase) {

        if (stopLossLevels.isEmpty()) {
            return "无明确止损策略";
        }

        StringBuilder strategy = new StringBuilder();

        if (invalidation) {
            strategy.append("⚠️ 波浪结构已失效，建议使用更严格的止损。");
        }

        // 根据相位调整止损策略
        if (currentPhase != null) {
            switch (currentPhase) {
                case WAVE3:
                    strategy.append("主升浪阶段，止损可以相对宽松。");
                    break;
                case WAVE5:
                    strategy.append("末端浪阶段，波动可能加大，需收紧止损。");
                    break;
                case WAVE1:
                    strategy.append("启动浪阶段，建议设置较紧止损以控制风险。");
                    break;
                default:
                    break;
            }
        }

        // 添加具体止损建议
        strategy.append("建议止损策略：");
        for (StopLossLevel stopLoss : stopLossLevels) {
            if (stopLoss.isPrimary() || stopLoss.getLevel() <= 2) {
                strategy.append(String.format("\n- %s止损: %.4f (风险%.1f%%，%s)",
                        stopLoss.isPrimary() ? "主要" : "备选",
                        stopLoss.getPrice(), stopLoss.getRiskPercentage(),
                        stopLoss.getDescription()));
            }
        }

        return strategy.toString();
    }

    /**
     * 计算价格位置得分
     */
    private double calculatePricePositionScore(PricePositionInfo info, ElliottPhase phase) {
        double score = 0.5; // 基础分

        // 1. 通道位置评分
        if (info.getZone() != null) {
            try {
                PriceZone zone = PriceZone.valueOf(info.getZone());

                switch (zone) {
                    case MIDDLE:
                        score += 0.2; // 中轨附近最佳
                        break;
                    case UPPER_HALF:
                    case LOWER_HALF:
                        score += 0.1; // 通道半区
                        break;
                    case UPPER_BAND:
                    case LOWER_BAND:
                        score -= 0.1; // 接近边界风险高
                        break;
                    case OUTSIDE_UPPER:
                    case OUTSIDE_LOWER:
                        score -= 0.3; // 突破通道风险更高
                        break;
                }
            } catch (IllegalArgumentException e) {
                // 忽略无效的区域值
            }
        }

        // 2. 距离关键价格评分
        if (info.getDistanceToInvalidation() != null) {
            double distance = Math.abs(info.getDistanceToInvalidation());
            if (distance < 1.0) score -= 0.3; // 太近风险高
            else if (distance < 3.0) score -= 0.1;
            else if (distance > 10.0) score += 0.1; // 安全距离
        }

        if (info.getDistanceToTarget() != null) {
            double distance = Math.abs(info.getDistanceToTarget());
            if (distance < 2.0) score -= 0.2; // 接近目标，空间有限
            else if (distance > 15.0) score += 0.2; // 空间充足
        }

        // 3. 动能与相位匹配评分
        if (phase != null && info.getShortMomentum() != null) {
            boolean impulsePhase = phase.isImpulse();
            boolean bullishMomentum = info.getShortMomentum() > 0;

            if ((impulsePhase && bullishMomentum) || (!impulsePhase && !bullishMomentum)) {
                score += 0.2; // 相位与动能一致
            } else {
                score -= 0.2; // 相位与动能矛盾
            }
        }

        // 4. 风险收益比评分
        if (info.getRiskRewardRatio() != null) {
            double rrRatio = info.getRiskRewardRatio();
            if (rrRatio >= minRiskRewardRatio) {
                score += 0.2; // 良好风险收益比
            } else if (rrRatio >= warningRiskRewardRatio) {
                score += 0.1; // 可接受风险收益比
            } else {
                score -= 0.2; // 风险收益比不足
            }
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    // ========== 多周期分析相关代码 ==========

    /**
     * 时间框架分析结果
     */
    @Data
    @Builder
    public static class TimeFrameAnalysis {
        private TimeFrame timeFrame;          // 时间框架
        private ElliottPhase currentPhase;    // 当前相位
        private boolean isBullish;           // 是否看涨
        private double confidence;           // 置信度
        private double score;                // 综合得分
        private PricePositionInfo priceInfo; // 价格位置信息
        private ElliottChannel channel;      // 通道信息
        private ElliottScenario bestScenario; // 最佳场景
        private boolean invalidation;        // 是否失效
        private double confluenceScore;      // 汇合评分

        // 新增目标信息
        private List<PriceTarget> priceTargets;     // 价格目标列表
        private List<StopLossLevel> stopLossLevels; // 止损水平列表
        private Double optimalStopLoss;      // 最优止损位
        private Double optimalTakeProfit;    // 最优止盈位
        private Double breakevenPrice;       // 盈亏平衡点
        private String targetStrategy;       // 目标策略描述
        private String stopLossStrategy;     // 止损策略描述
    }

    /**
     * 多周期分析综合结果
     */
    @Data
    @Builder
    public static class MultiTimeFrameAnalysis {
        private double compositeScore;       // 综合得分
        private String trendDirection;       // 趋势方向（CONFIRMED_BULLISH, CONFIRMED_BEARISH, MIXED）
        private int agreementLevel;          // 一致性等级 1-5
        private Map<TimeFrame, TimeFrameAnalysis> analyses; // 各周期分析
        private List<String> warnings;       // 多周期警告
        private List<String> recommendations; // 多周期建议
        private ElliottPhase dominantPhase;  // 主导相位（多数周期支持的相位）
        private PriceAnalysisDto priceAnalysisDto;

    }

    /**
     * 根据时间框架确定波浪级别
     */
    private ElliottDegree getDegreeForTimeFrame(TimeFrame timeFrame) {
        switch (timeFrame) {
            case H1:
                return ElliottDegree.PRIMARY;      // 主浪
            case M15:
                return ElliottDegree.INTERMEDIATE; // 中间浪
            case M5:
            case M1:
                return ElliottDegree.MINOR;       // 小浪
            default:
                return ElliottDegree.MINOR;
        }
    }

    /**
     * 获取周期权重
     */
    private double getWeightForTimeFrame(TimeFrame timeFrame) {
        switch (timeFrame) {
            case H1:
                return hourlyWeight;
            case M15:
                return quarterlyWeight;
            case M5:
            case M1:
                return minuteWeight;
            default:
                return 0.2; // 默认权重
        }
    }

    /**
     * 单周期分析（抽取现有逻辑）
     * 注意：此方法将现有的单周期分析逻辑抽取出来，供多周期分析使用
     * 当前实现为框架，需要进一步重构现有代码来调用此方法
     */
    private TimeFrameAnalysis analyzeSingleTimeFrame(
            TradingSignalDto signal,
            AnalysisData data,
            TimeFrame timeFrame) {
        // 检查数据是否足够
        if (data == null || data.getBars().size() < minBars) {
            log.warn("{}周期数据不足，需要至少{}根K线", timeFrame, minBars);
            return null;
        }

        try {
            // 构建BarSeries
            BarSeries series = IndicatorWrapHelper.buildSeries(data.getBars());
            int endIndex = series.getEndIndex() - 1;

            // 创建ElliottWaveFacade
            ElliottDegree degree = getDegreeForTimeFrame(timeFrame);
            ElliottSwingCompressor compressor = new ElliottSwingCompressor(series);
            ElliottWaveFacade facade = ElliottWaveFacade.zigZag(
                    series,
                    degree,
                    Optional.of(series.numFactory().numOf(fibTolerance)),
                    Optional.of(compressor)
            );

            // 获取分析结果
            ElliottPhase currentPhase = facade.phase().getValue(endIndex);
            boolean invalidation = facade.invalidation().getValue(endIndex);
            ElliottChannel channel = facade.channel().getValue(endIndex);
            org.ta4j.core.num.Num confluenceScoreNum = facade.confluence().getValue(endIndex);

            // 获取最佳场景
            org.ta4j.core.indicators.elliott.ElliottScenarioSet scenarioSet = facade.scenarios().getValue(endIndex);
            Optional<ElliottScenario> bestScenario = selectBestScenario(currentPhase, signal, scenarioSet);

            // 计算价格位置信息
            PricePositionInfo priceInfo = calculatePricePosition(
                    signal, series, channel, bestScenario, currentPhase);

            // 计算得分（简化版，使用基础置信度）
            double score = bestScenario.map(s -> s.confidence().asPercentage() / 100.0).orElse(0.5);

            // 判断方向
            boolean isBullish = bestScenario.map(s -> s.hasKnownDirection() && s.isBullish()).orElse(false);

            return TimeFrameAnalysis.builder()
                    .timeFrame(timeFrame)
                    .currentPhase(currentPhase)
                    .isBullish(isBullish)
                    .confidence(bestScenario.map(s -> s.confidence().asPercentage()).orElse(0.0))
                    .score(score)
                    .priceInfo(priceInfo)
                    .channel(channel)
                    .bestScenario(bestScenario.orElse(null))
                    .invalidation(invalidation)
                    .confluenceScore(confluenceScoreNum != null ? confluenceScoreNum.doubleValue() : 0.0)
                    .build();

        } catch (Exception e) {
            log.error("{}周期分析失败: {}", timeFrame, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 执行多周期分析
     */
    private MultiTimeFrameAnalysis analyzeMultiTimeFrame(
            TradingSignalDto signal,
            EvaluationContext context) {
        MultiTimeFrameAnalysis.MultiTimeFrameAnalysisBuilder builder = MultiTimeFrameAnalysis.builder();

        Map<TimeFrame, AnalysisData> multiTimeFrameData = context.getMultiTimeFrameData();

        if (multiTimeFrameData == null || multiTimeFrameData.isEmpty()) {
            log.warn("未提供多周期数据，无法进行多周期分析");
            return null;
        }

        Map<TimeFrame, TimeFrameAnalysis> analyses = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // 获取分析服务实例
        ElliottAnalysisService analysisService = getAnalysisService();

        // 对各周期进行分析
        for (Map.Entry<TimeFrame, AnalysisData> entry : multiTimeFrameData.entrySet()) {
            TimeFrame tf = entry.getKey();
            AnalysisData data = entry.getValue();

            try {
                TimeFrameAnalysis tfAnalysis = analysisService.analyzeTimeFrame(signal, data, tf);
                if (tfAnalysis != null) {
                    analyses.put(tf, tfAnalysis);
                }
            } catch (Exception e) {
                log.warn("{}周期分析失败: {}", tf, e.getMessage());
            }
        }

        if (analyses.isEmpty()) {
            return null;
        }

        // 计算综合结果
        String trendDirection = calculateTrendDirection(analyses);
        ElliottPhase dominantPhase = calculateDominantPhase(analyses);
        int agreementLevel = calculateAgreementLevel(analyses);
        double compositeScore = calculateCompositeScore(analyses);

        // 生成多周期警告和建议
        warnings.addAll(generateMultiTimeFrameWarnings(analyses, agreementLevel));
        recommendations.addAll(generateMultiTimeFrameRecommendations(analyses, trendDirection, agreementLevel));

        builder.analyses(analyses)
                .trendDirection(trendDirection)
                .dominantPhase(dominantPhase)
                .agreementLevel(agreementLevel)
                .compositeScore(compositeScore)
                .warnings(warnings)
                .recommendations(recommendations);

        return builder.build();
    }

    /**
     * 计算趋势方向
     */
    private String calculateTrendDirection(Map<TimeFrame, TimeFrameAnalysis> analyses) {
        long bullishCount = analyses.values().stream()
                .filter(TimeFrameAnalysis::isBullish)
                .count();
        long total = analyses.size();

        double bullishRatio = (double) bullishCount / total;

        if (bullishRatio >= 0.8) {
            return "CONFIRMED_BULLISH";
        } else if (bullishRatio <= 0.2) {
            return "CONFIRMED_BEARISH";
        } else if (bullishRatio >= 0.6) {
            return "MOSTLY_BULLISH";
        } else if (bullishRatio <= 0.4) {
            return "MOSTLY_BEARISH";
        } else {
            return "MIXED";
        }
    }

    /**
     * 计算主导相位
     */
    private ElliottPhase calculateDominantPhase(Map<TimeFrame, TimeFrameAnalysis> analyses) {
        Map<ElliottPhase, Integer> phaseCount = new HashMap<>();

        for (TimeFrameAnalysis analysis : analyses.values()) {
            if (analysis.getCurrentPhase() != null) {
                phaseCount.put(analysis.getCurrentPhase(),
                        phaseCount.getOrDefault(analysis.getCurrentPhase(), 0) + 1);
            }
        }

        if (phaseCount.isEmpty()) {
            return null;
        }

        return phaseCount.entrySet().stream()
                .max(Map.Entry.<ElliottPhase, Integer>comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 计算一致性等级（1-5级，5为最高）
     */
    private int calculateAgreementLevel(Map<TimeFrame, TimeFrameAnalysis> analyses) {
        if (analyses.size() < 2) {
            return 3; // 单周期默认为中等一致性
        }

        // 方向一致性
        long bullishCount = analyses.values().stream()
                .filter(TimeFrameAnalysis::isBullish).count();
        double directionAgreement = (double) Math.max(bullishCount, analyses.size() - bullishCount)
                / analyses.size();

        // 相位一致性
        Map<ElliottPhase, Integer> phaseCount = new HashMap<>();
        for (TimeFrameAnalysis analysis : analyses.values()) {
            if (analysis.getCurrentPhase() != null) {
                phaseCount.put(analysis.getCurrentPhase(),
                        phaseCount.getOrDefault(analysis.getCurrentPhase(), 0) + 1);
            }
        }
        double phaseAgreement = phaseCount.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) / (double) analyses.size();

        // 加权计算一致性分数
        double agreementScore = (directionAgreement * 0.6 + phaseAgreement * 0.4);

        // 转换为1-5级
        if (agreementScore >= 0.9) return 5;
        if (agreementScore >= 0.8) return 4;
        if (agreementScore >= 0.6) return 3;
        if (agreementScore >= 0.4) return 2;
        return 1;
    }

    /**
     * 计算综合得分（加权平均）
     */
    private double calculateCompositeScore(Map<TimeFrame, TimeFrameAnalysis> analyses) {
        double weightedSum = 0;
        double totalWeight = 0;

        for (Map.Entry<TimeFrame, TimeFrameAnalysis> entry : analyses.entrySet()) {
            TimeFrame tf = entry.getKey();
            TimeFrameAnalysis analysis = entry.getValue();

            double weight = getWeightForTimeFrame(tf);
            weightedSum += analysis.getScore() * weight;
            totalWeight += weight;
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0.5;
    }

    /**
     * 增强的趋势方向计算（考虑权重）
     */
    private String calculateEnhancedTrendDirection(
            Map<TimeFrame, TimeFrameAnalysis> analyses,
            Map<TimeFrame, Double> weights) {

        double bullishWeight = 0;
        double totalWeight = 0;

        for (Map.Entry<TimeFrame, TimeFrameAnalysis> entry : analyses.entrySet()) {
            TimeFrame tf = entry.getKey();
            TimeFrameAnalysis analysis = entry.getValue();

            double weight = weights.getOrDefault(tf, 0.2);
            if (analysis.isBullish()) {
                bullishWeight += weight;
            }
            totalWeight += weight;
        }

        double bullishRatio = totalWeight > 0 ? bullishWeight / totalWeight : 0;

        if (bullishRatio >= 0.75) return "STRONG_BULLISH";
        if (bullishRatio >= 0.60) return "BULLISH";
        if (bullishRatio <= 0.25) return "STRONG_BEARISH";
        if (bullishRatio <= 0.40) return "BEARISH";
        return "NEUTRAL";
    }

    /**
     * 增强的一致性计算
     */
    private int calculateEnhancedAgreementLevel(
            Map<TimeFrame, TimeFrameAnalysis> analyses,
            Map<TimeFrame, Double> weights) {

        if (analyses.size() < 2) return 3;

        // 方向一致性
        double directionAgreement = calculateWeightedDirectionAgreement(analyses, weights);

        // 相位一致性
        double phaseAgreement = calculateWeightedPhaseAgreement(analyses, weights);

        // 综合一致性分数
        double agreementScore = directionAgreement * 0.7 + phaseAgreement * 0.3;

        // 转换为1-5级
        if (agreementScore >= 0.85) return 5;
        if (agreementScore >= 0.70) return 4;
        if (agreementScore >= 0.50) return 3;
        if (agreementScore >= 0.30) return 2;
        return 1;
    }

    /**
     * 计算加权方向一致性
     */
    private double calculateWeightedDirectionAgreement(
            Map<TimeFrame, TimeFrameAnalysis> analyses,
            Map<TimeFrame, Double> weights) {

        double totalWeight = 0;
        double agreementWeight = 0;

        boolean[] directions = new boolean[analyses.size()];
        double[] directionWeights = new double[analyses.size()];

        int index = 0;
        for (Map.Entry<TimeFrame, TimeFrameAnalysis> entry : analyses.entrySet()) {
            directions[index] = entry.getValue().isBullish();
            directionWeights[index] = weights.getOrDefault(entry.getKey(), 0.2);
            totalWeight += directionWeights[index];
            index++;
        }

        // 计算每对方向之间的一致性
        for (int i = 0; i < directions.length; i++) {
            for (int j = i + 1; j < directions.length; j++) {
                if (directions[i] == directions[j]) {
                    agreementWeight += (directionWeights[i] + directionWeights[j]) / 2;
                }
            }
        }

        double maxPossibleAgreement = totalWeight * (totalWeight - directionWeights[0]) / 2;
        return maxPossibleAgreement > 0 ? agreementWeight / maxPossibleAgreement : 1.0;
    }

    /**
     * 计算加权相位一致性
     */
    private double calculateWeightedPhaseAgreement(
            Map<TimeFrame, TimeFrameAnalysis> analyses,
            Map<TimeFrame, Double> weights) {

        double totalWeight = 0;
        double agreementWeight = 0;

        ElliottPhase[] phases = new ElliottPhase[analyses.size()];
        double[] phaseWeights = new double[analyses.size()];

        int index = 0;
        for (Map.Entry<TimeFrame, TimeFrameAnalysis> entry : analyses.entrySet()) {
            phases[index] = entry.getValue().getCurrentPhase();
            phaseWeights[index] = weights.getOrDefault(entry.getKey(), 0.2);
            totalWeight += phaseWeights[index];
            index++;
        }

        // 计算相位一致性（相同相位或相邻相位认为一致）
        for (int i = 0; i < phases.length; i++) {
            for (int j = i + 1; j < phases.length; j++) {
                if (phases[i] != null && phases[j] != null) {
                    if (phases[i] == phases[j] || arePhasesCompatible(phases[i], phases[j])) {
                        agreementWeight += (phaseWeights[i] + phaseWeights[j]) / 2;
                    }
                }
            }
        }

        double maxPossibleAgreement = totalWeight * (totalWeight - phaseWeights[0]) / 2;
        return maxPossibleAgreement > 0 ? agreementWeight / maxPossibleAgreement : 1.0;
    }

    /**
     * 检查相位是否兼容
     */
    private boolean arePhasesCompatible(ElliottPhase phase1, ElliottPhase phase2) {
        // 简单实现：相邻相位认为兼容
        int phase1Order = getPhaseOrder(phase1);
        int phase2Order = getPhaseOrder(phase2);
        return Math.abs(phase1Order - phase2Order) <= 1;
    }

    /**
     * 获取相位的顺序值
     */
    private int getPhaseOrder(ElliottPhase phase) {
        switch (phase) {
            case WAVE1: return 1;
            case WAVE2: return 2;
            case WAVE3: return 3;
            case WAVE4: return 4;
            case WAVE5: return 5;
            default: return 0;
        }
    }

    /**
     * 增强的综合得分计算
     */
    private double calculateEnhancedCompositeScore(
            Map<TimeFrame, TimeFrameAnalysis> analyses,
            Map<TimeFrame, Double> weights) {

        double weightedSum = 0;
        double totalWeight = 0;

        for (Map.Entry<TimeFrame, TimeFrameAnalysis> entry : analyses.entrySet()) {
            TimeFrame tf = entry.getKey();
            TimeFrameAnalysis analysis = entry.getValue();

            double weight = weights.getOrDefault(tf, 0.2);

            // 计算周期增强得分（考虑失效状态的严重性）
            double cycleScore = calculateEnhancedCycleScore(analysis);

            weightedSum += cycleScore * weight;
            totalWeight += weight;
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0.5;
    }

    /**
     * 计算周期增强得分
     */
    private double calculateEnhancedCycleScore(TimeFrameAnalysis analysis) {
        double baseScore = analysis.getScore();

        // 1. 失效状态调整
        if (analysis.isInvalidation()) {
            double invalidationFactor = calculateInvalidationFactor(analysis);
            baseScore *= invalidationFactor;
        }

        // 2. 汇合度调整
        double confluenceFactor = 0.5 + (analysis.getConfluenceScore() * 0.5); // 0.5-1.0
        baseScore *= confluenceFactor;

        // 3. 置信度调整
        double confidenceFactor = analysis.getConfidence() / 100.0; // 0.0-1.0
        baseScore = (baseScore * 0.7) + (confidenceFactor * 0.3);

        return Math.max(0.1, Math.min(1.0, baseScore));
    }

    /**
     * 计算失效状态影响因子
     */
    private double calculateInvalidationFactor(TimeFrameAnalysis analysis) {
        if (!analysis.isInvalidation() || analysis.getPriceInfo() == null) {
            return 1.0;
        }

        Double distance = analysis.getPriceInfo().getDistanceToInvalidation();
        if (distance == null) {
            return 0.4; // 无距离信息，中等影响
        }

        double absDistance = Math.abs(distance);

        // 距离衰减函数
        if (absDistance > 20.0) return 0.9;
        if (absDistance > 15.0) return 0.8;
        if (absDistance > 10.0) return 0.7;
        if (absDistance > 5.0) return 0.6;
        if (absDistance > 3.0) return 0.5;
        if (absDistance > 1.0) return 0.4;
        return 0.3; // 距离很近，严重影响
    }

    /**
     * 生成增强警告
     */
    private List<String> generateEnhancedWarnings(
            Map<TimeFrame, TimeFrameAnalysis> analyses,
            double compositeScore,
            int agreementLevel) {

        List<String> warnings = new ArrayList<>();

        // 检查严重失效
        int invalidCount = 0;
        int nearInvalidCount = 0;

        for (TimeFrameAnalysis analysis : analyses.values()) {
            if (analysis.isInvalidation()) {
                invalidCount++;
                // 检查是否距离失效很近
                if (analysis.getPriceInfo() != null &&
                        analysis.getPriceInfo().getDistanceToInvalidation() != null &&
                        Math.abs(analysis.getPriceInfo().getDistanceToInvalidation()) < 3.0) {
                    nearInvalidCount++;
                }
            }
        }

        if (nearInvalidCount >= 2) {
            warnings.add("多个周期波浪结构接近失效价格");
        } else if (nearInvalidCount == 1) {
            warnings.add("存在周期波浪结构接近失效价格");
        }

        // 低一致性警告
        if (agreementLevel <= 2) {
            warnings.add("多周期一致性很低");
        }

        // 低得分警告
        if (compositeScore < 0.4) {
            warnings.add("综合得分过低");
        }

        return warnings;
    }

    /**
     * 生成增强建议
     */
    private List<String> generateEnhancedRecommendations(
            Map<TimeFrame, TimeFrameAnalysis> analyses,
            double compositeScore,
            String trendDirection,
            TradingSignalDto signal) {

        List<String> recommendations = new ArrayList<>();

        boolean signalBuy = signal.getType().name().equals("BUY");

        // 基于综合得分和趋势方向的建议
        if (compositeScore >= 0.7) {
            if (("STRONG_BULLISH".equals(trendDirection) && signalBuy) ||
                    ("STRONG_BEARISH".equals(trendDirection) && !signalBuy)) {
                recommendations.add("多周期确认强势趋势，支持交易");
            } else {
                recommendations.add("综合得分良好，但需注意趋势方向一致性");
            }
        } else if (compositeScore >= 0.5) {
            recommendations.add("中等得分，建议谨慎交易并设置止损");
        } else {
            recommendations.add("得分较低，建议等待更好机会");
        }

        // 检查是否有高质量周期
        boolean hasHighQuality = analyses.values().stream()
                .anyMatch(a -> a.getConfidence() > 80 && a.getConfluenceScore() > 0.7);

        if (hasHighQuality) {
            recommendations.add("存在高质量周期分析，可增强信心");
        }

        return recommendations;
    }

    /**
     * 生成多周期警告
     */
    private List<String> generateMultiTimeFrameWarnings(Map<TimeFrame, TimeFrameAnalysis> analyses, int agreementLevel) {
        List<String> warnings = new ArrayList<>();

        if (agreementLevel < 3) {
            warnings.add("多周期一致性较低，信号可靠性降低");
        }

        long invalidatedCount = analyses.values().stream()
                .filter(TimeFrameAnalysis::isInvalidation)
                .count();
        if (invalidatedCount > 0) {
            warnings.add(String.format("{}个周期波浪结构已失效", invalidatedCount));
        }

        return warnings;
    }

    /**
     * 生成多周期建议
     */
    private List<String> generateMultiTimeFrameRecommendations(
            Map<TimeFrame, TimeFrameAnalysis> analyses,
            String trendDirection,
            int agreementLevel) {
        List<String> recommendations = new ArrayList<>();

        if (agreementLevel >= 4) {
            recommendations.add("多周期一致性较高，增强信号可靠性");
        }

        if ("CONFIRMED_BULLISH".equals(trendDirection) || "CONFIRMED_BEARISH".equals(trendDirection)) {
            recommendations.add("大周期确认趋势方向，支持信号");
        }

        return recommendations;
    }

    /**
     * 记录多周期分析结果
     */
    private void logMultiTimeFrameAnalysis(
            TradingSignalDto signal,
            MultiTimeFrameAnalysis multiAnalysis) {
        log.info("=".repeat(80));
        log.info("🌊 多周期艾略特波浪综合分析报告");
        log.info("交易对: {}, 信号ID: {}, 类型: {}",
                signal.getSymbol(), signal.getId(), signal.getType());
        log.info("-".repeat(80));

        // 总体概况
        log.info("【多周期总体概况】");
        log.info("  综合得分: {}/100", String.format("%.0f", multiAnalysis.getCompositeScore() * 100));
        log.info("  趋势方向: {}", multiAnalysis.getTrendDirection());
        log.info("  主导相位: {}", multiAnalysis.getDominantPhase() != null ?
                multiAnalysis.getDominantPhase() : "UNKNOWN");
        log.info("  一致性等级: {}/5", multiAnalysis.getAgreementLevel());

        // 各周期详细分析
        log.info("【各周期详细分析】");
        log.info("  ┌───────────┬───────────┬─────────┬─────────┬─────────┬─────────┬─────────┐");
        log.info("  │ 周期      │ 相位      │ 方向    │ 置信度  │ 得分    │ 失效    │ 汇合度  │");
        log.info("  ├───────────┼───────────┼─────────┼─────────┼─────────┼─────────┼─────────┤");

        for (Map.Entry<TimeFrame, TimeFrameAnalysis> entry : multiAnalysis.getAnalyses().entrySet()) {
            TimeFrame tf = entry.getKey();
            TimeFrameAnalysis analysis = entry.getValue();

            String phase = analysis.getCurrentPhase() != null ?
                    analysis.getCurrentPhase().toString() : "N/A";
            String direction = analysis.isBullish() ? "BULLISH" : "BEARISH";
            String confidence = String.format("%.1f", analysis.getConfidence());
            String score = String.format("%.1f", analysis.getScore() * 100);
            String invalidation = analysis.isInvalidation() ? "是" : "否";
            String confluence = String.format("%.1f", analysis.getConfluenceScore() * 100);

            log.info("  │ {}{}{}{}{}{}{} │",
                    String.format("%-9s", tf.toString()),
                    String.format("%-9s", phase),
                    String.format("%-7s", direction),
                    String.format("%7s", confidence),
                    String.format("%7s", score),
                    String.format("%7s", invalidation),
                    String.format("%7s", confluence));
        }
        log.info("  └───────────┴───────────┴─────────┴─────────┴─────────┴─────────┴─────────┘");

        // 多周期交易建议
        log.info("【多周期交易建议】");
        for (String recommendation : multiAnalysis.getRecommendations()) {
            log.info("  ✓ {}", recommendation);
        }

        // 多周期警告
        if (!multiAnalysis.getWarnings().isEmpty()) {
            log.warn("【多周期警告】");
            for (String warning : multiAnalysis.getWarnings()) {
                log.warn("  ⚠️ {}", warning);
            }
        }

        log.info("=".repeat(80));
    }


}

