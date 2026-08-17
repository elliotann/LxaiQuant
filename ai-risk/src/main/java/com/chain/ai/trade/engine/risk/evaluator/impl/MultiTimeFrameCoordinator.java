package com.chain.ai.trade.engine.risk.evaluator.impl;

import com.chain.ai.trade.engine.entity.dto.AnalysisData;
import com.chain.ai.trade.engine.entity.dto.PriceAnalysisDto;
import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
import com.chain.ai.trade.engine.risk.common.TimeFrame;
import com.chain.ai.trade.engine.risk.evaluator.EvaluationContext;
import com.chain.ai.trade.engine.risk.evaluator.QualityEvaluationResult;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.indicators.elliott.ElliottPhase;

import java.util.*;

/**
 * 多周期分析协调器
 * 负责协调多周期艾略特波浪分析的三个阶段流程
 */
@Slf4j
public class MultiTimeFrameCoordinator {

    private final ElliottWaveEvaluator.ElliottAnalysisService analysisService;
    private final SmartWeightAdjuster weightAdjuster;

    public MultiTimeFrameCoordinator(
            ElliottWaveEvaluator.ElliottAnalysisService analysisService,
            SmartWeightAdjuster weightAdjuster) {
        this.analysisService = analysisService;
        this.weightAdjuster = weightAdjuster;
    }

    /**
     * 执行多周期分析（清晰的三阶段流程）
     */
    public QualityEvaluationResult evaluateWithMultiTimeFrame(
            TradingSignalDto signal,
            EvaluationContext context,
            double evaluatorWeight,
            String evaluatorId) {

        log.info("开始多周期艾略特波浪分析 - 信号ID: {}, 交易对: {}",
                signal.getId(), signal.getSymbol());

        try {
            // ========== 阶段1: 各周期独立分析 ==========
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> periodAnalyses =
                    analyzeEachTimeFrame(signal, context);

            if (periodAnalyses.isEmpty()) {
                log.warn("所有周期分析均失败，回退到单周期分析");
                return createFallbackResult(signal, evaluatorWeight, evaluatorId);
            }

            // ========== 阶段2: 综合汇总分析 ==========
            MultiTimeFrameSummary summary =
                    summarizeMultiTimeFrameAnalysis(periodAnalyses, signal);

            // ========== 阶段3: 生成最终结果 ==========
            return generateFinalResult(summary, signal, evaluatorWeight, evaluatorId);

        } catch (Exception e) {
            log.error("多周期分析失败: {}", e.getMessage(), e);
            return createErrorResult(signal, evaluatorWeight, evaluatorId,
                    "多周期分析失败: " + e.getMessage());
        }
    }

    /**
     * 阶段1: 分析各周期（独立并行）
     */
    private Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyzeEachTimeFrame(
            TradingSignalDto signal,
            EvaluationContext context) {

        Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses = new HashMap<>();
        Map<TimeFrame, AnalysisData> multiTimeFrameData =
                context.getMultiTimeFrameData();

        if (multiTimeFrameData == null || multiTimeFrameData.isEmpty()) {
            log.warn("未提供多周期数据");
            return analyses;
        }

        log.info("开始各周期独立分析，共{}个周期", multiTimeFrameData.size());

        // 可以并行处理各周期分析
        for (Map.Entry<TimeFrame, AnalysisData> entry :
                multiTimeFrameData.entrySet()) {
            TimeFrame tf = entry.getKey();
            AnalysisData data = entry.getValue();

            try {
                log.debug("开始分析{}周期", tf);
                ElliottWaveEvaluator.TimeFrameAnalysis analysis =
                        analysisService.analyzeTimeFrame(signal, data, tf);

                if (analysis != null) {
                    analyses.put(tf, analysis);
                    log.debug("{}周期分析完成，得分: {}", tf, String.format("%.2f", analysis.getScore()));
                } else {
                    log.warn("{}周期分析返回空结果", tf);
                }

            } catch (Exception e) {
                log.error("{}周期分析失败: {}", tf, e.getMessage());
                // 继续分析其他周期，不中断整个流程
            }
        }

        log.info("各周期分析完成，成功{}个，失败{}个",
                analyses.size(), multiTimeFrameData.size() - analyses.size());

        return analyses;
    }

    /**
     * 阶段2: 综合汇总分析
     */
    private MultiTimeFrameSummary summarizeMultiTimeFrameAnalysis(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> periodAnalyses,
            TradingSignalDto signal) {

        MultiTimeFrameSummary.MultiTimeFrameSummaryBuilder summaryBuilder = MultiTimeFrameSummary.builder();

        // 2.1 计算各周期权重
        Map<TimeFrame, Double> weights = weightAdjuster.calculateAdjustedWeights(
                periodAnalyses, signal);

        // 2.2 计算综合得分（加权平均）
        double compositeScore = calculateCompositeScore(periodAnalyses, weights);

        // 2.3 计算趋势方向
        String trendDirection = calculateTrendDirection(periodAnalyses, weights);

        // 2.4 计算一致性等级
        int agreementLevel = calculateAgreementLevel(periodAnalyses);

        // 2.5 计算价格目标（通过 SmartWeightAdjuster）
        PriceAnalysisDto priceAnalysis = weightAdjuster.calculateAndLogComprehensiveTargets(
                periodAnalyses, signal);

        // 2.6 确定主导相位
        ElliottPhase dominantPhase = calculateDominantPhase(periodAnalyses);

        // 2.7 生成警告和建议
        List<String> warnings = generateWarnings(periodAnalyses, compositeScore, agreementLevel, signal);
        List<String> recommendations = generateRecommendations(periodAnalyses, compositeScore,
                trendDirection, agreementLevel, signal);

        // 2.8 构建汇总对象
        return summaryBuilder
                .periodAnalyses(periodAnalyses)
                .weights(weights)
                .compositeScore(compositeScore)
                .trendDirection(trendDirection)
                .agreementLevel(agreementLevel)
                .priceAnalysis(priceAnalysis)
                .dominantPhase(dominantPhase)
                .warnings(warnings)
                .recommendations(recommendations)
                .build();
    }

    /**
     * 阶段3: 生成最终结果
     */
    private QualityEvaluationResult generateFinalResult(
            MultiTimeFrameSummary summary,
            TradingSignalDto signal,
            double evaluatorWeight,
            String evaluatorId) {

        // 3.1 记录详细分析日志
        logMultiTimeFrameAnalysis(signal, summary);

        // 3.2 生成最终交易建议
        String tradeAdvice = generateFinalTradeAdvice(summary, signal);

        // 3.3 构建因子映射
        Map<String, Object> factors = buildFactorMap(summary, tradeAdvice);

        // 3.4 构建建议列表
        List<String> recommendations = buildRecommendations(summary, tradeAdvice);

        // 3.5 生成摘要
        String summaryText = buildSummaryText(summary, tradeAdvice);

        // 3.6 构建最终结果
        return QualityEvaluationResult.builder()
                .evaluatorId(evaluatorId)
                .signalId(signal.getId())
                .score(summary.getCompositeScore())
                .weight(evaluatorWeight)
                .factors(factors)
                .priceAnalysisDto(summary.getPriceAnalysis())
                .summary(summaryText)
                .recommendations(recommendations)
                .warnings(summary.getWarnings())
                .build();
    }

    // ========== 辅助方法 ==========

    /**
     * 计算综合得分（加权平均）
     */
    private double calculateCompositeScore(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            Map<TimeFrame, Double> weights) {

        double weightedSum = 0;
        double totalWeight = 0;

        for (Map.Entry<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> entry : analyses.entrySet()) {
            TimeFrame tf = entry.getKey();
            ElliottWaveEvaluator.TimeFrameAnalysis analysis = entry.getValue();
            double weight = weights.getOrDefault(tf, 0.2);

            weightedSum += analysis.getScore() * weight;
            totalWeight += weight;
        }

        double score = totalWeight > 0 ? weightedSum / totalWeight : 0.5;
        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * 计算趋势方向
     */
    private String calculateTrendDirection(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            Map<TimeFrame, Double> weights) {

        double bullishWeight = 0;
        double totalWeight = 0;

        for (Map.Entry<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> entry : analyses.entrySet()) {
            TimeFrame tf = entry.getKey();
            ElliottWaveEvaluator.TimeFrameAnalysis analysis = entry.getValue();
            double weight = weights.getOrDefault(tf, 0.2);

            if (analysis.isBullish()) {
                bullishWeight += weight;
            }
            totalWeight += weight;
        }

        double bullishRatio = totalWeight > 0 ? bullishWeight / totalWeight : 0.5;

        if (bullishRatio >= 0.75) return "STRONG_BULLISH";
        if (bullishRatio >= 0.60) return "BULLISH";
        if (bullishRatio <= 0.25) return "STRONG_BEARISH";
        if (bullishRatio <= 0.40) return "BEARISH";
        return "NEUTRAL";
    }

    /**
     * 计算一致性等级
     */
    private int calculateAgreementLevel(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses) {
        if (analyses.size() < 2) {
            return 3; // 单周期默认为中等一致性
        }

        // 计算方向一致性
        long bullishCount = analyses.values().stream()
                .filter(ElliottWaveEvaluator.TimeFrameAnalysis::isBullish)
                .count();

        double directionAgreement = (double) Math.max(bullishCount, analyses.size() - bullishCount)
                / analyses.size();

        // 计算相位一致性
        Map<ElliottPhase, Integer> phaseCount = new HashMap<>();
        for (ElliottWaveEvaluator.TimeFrameAnalysis analysis : analyses.values()) {
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
        double agreementScore = (directionAgreement * 0.7 + phaseAgreement * 0.3);

        // 转换为1-5级
        if (agreementScore >= 0.85) return 5;
        if (agreementScore >= 0.70) return 4;
        if (agreementScore >= 0.50) return 3;
        if (agreementScore >= 0.30) return 2;
        return 1;
    }

    /**
     * 计算主导相位
     */
    private ElliottPhase calculateDominantPhase(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses) {
        Map<ElliottPhase, Integer> phaseCount = new HashMap<>();

        for (ElliottWaveEvaluator.TimeFrameAnalysis analysis : analyses.values()) {
            if (analysis.getCurrentPhase() != null) {
                phaseCount.put(analysis.getCurrentPhase(),
                        phaseCount.getOrDefault(analysis.getCurrentPhase(), 0) + 1);
            }
        }

        if (phaseCount.isEmpty()) {
            return null;
        }

        return phaseCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 生成警告列表
     */
    private List<String> generateWarnings(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            double compositeScore,
            int agreementLevel,
            TradingSignalDto signal) {

        List<String> warnings = new ArrayList<>();

        // 检查失效周期
        long invalidCount = analyses.values().stream()
                .filter(ElliottWaveEvaluator.TimeFrameAnalysis::isInvalidation)
                .count();

        if (invalidCount > 0) {
            warnings.add(String.format("%d个周期波浪结构已失效", invalidCount));
        }

        // 检查方向一致性
        boolean signalBuy = signal.getType().name().equals("BUY");
        int bullishCount = 0;
        for (ElliottWaveEvaluator.TimeFrameAnalysis analysis : analyses.values()) {
            if (analysis.isBullish()) bullishCount++;
        }

        double bullishRatio = (double) bullishCount / analyses.size();
        if ((signalBuy && bullishRatio < 0.5) || (!signalBuy && bullishRatio > 0.5)) {
            warnings.add("信号方向与多数周期不一致");
        }

        // 检查综合得分
        if (compositeScore < 0.4) {
            warnings.add("综合得分过低");
        }

        // 检查一致性
        if (agreementLevel <= 2) {
            warnings.add("多周期一致性较低");
        }

        return warnings;
    }

    /**
     * 生成建议列表
     */
    private List<String> generateRecommendations(
            Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> analyses,
            double compositeScore,
            String trendDirection,
            int agreementLevel,
            TradingSignalDto signal) {

        List<String> recommendations = new ArrayList<>();

        // 基于综合得分
        if (compositeScore >= 0.7) {
            recommendations.add("综合得分优秀，信号可靠性高");
        } else if (compositeScore >= 0.5) {
            recommendations.add("综合得分良好，可考虑交易");
        }

        // 基于一致性
        if (agreementLevel >= 4) {
            recommendations.add("多周期一致性高，增强信号可靠性");
        }

        // 基于趋势方向
        boolean signalBuy = signal.getType().name().equals("BUY");
        if (("STRONG_BULLISH".equals(trendDirection) && signalBuy) ||
                ("STRONG_BEARISH".equals(trendDirection) && !signalBuy)) {
            recommendations.add("大周期确认强势趋势，支持交易");
        }

        return recommendations;
    }

    /**
     * 生成最终交易建议
     */
    private String generateFinalTradeAdvice(MultiTimeFrameSummary summary, TradingSignalDto signal) {
        double compositeScore = summary.getCompositeScore();
        int agreementLevel = summary.getAgreementLevel();
        String trendDirection = summary.getTrendDirection();
        boolean signalBuy = signal.getType().name().equals("BUY");

        // 检查方向一致性
        boolean trendBullish = trendDirection.contains("BULLISH");
        boolean directionConflict = (signalBuy && !trendBullish) || (!signalBuy && trendBullish);

        // 检查风险收益比
        Double riskRewardRatio = summary.getPriceAnalysis() != null ?
                summary.getPriceAnalysis().getRiskRewardRatio() : null;

        if (compositeScore < 0.4 || (riskRewardRatio != null && riskRewardRatio < 1.0)) {
            return "AVOID";
        } else if (compositeScore >= 0.7 && agreementLevel >= 4 && !directionConflict &&
                (riskRewardRatio == null || riskRewardRatio >= 2.0)) {
            return "CONFIRMED";
        } else if (compositeScore >= 0.5 && agreementLevel >= 3) {
            return "CAUTIOUS";
        } else {
            return "AVOID";
        }
    }

    /**
     * 构建因子映射
     */
    private Map<String, Object> buildFactorMap(MultiTimeFrameSummary summary, String tradeAdvice) {
        Map<String, Object> factors = new HashMap<>();

        factors.put("multiTimeFrameScore", String.format("%.2f", summary.getCompositeScore()));
        factors.put("trendDirection", summary.getTrendDirection());
        factors.put("agreementLevel", summary.getAgreementLevel());
        factors.put("tradeAdvice", tradeAdvice);

        // 添加各周期得分
        Map<String, Double> periodScores = new HashMap<>();
        for (Map.Entry<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> entry :
                summary.getPeriodAnalyses().entrySet()) {
            periodScores.put(entry.getKey().toString(), entry.getValue().getScore());
        }
        factors.put("periodScores", periodScores);

        // 添加权重信息
        factors.put("weights", summary.getWeights());

        return factors;
    }

    /**
     * 构建建议列表
     */
    private List<String> buildRecommendations(MultiTimeFrameSummary summary, String tradeAdvice) {
        List<String> recommendations = new ArrayList<>();

        // 添加交易建议
        recommendations.add(tradeAdvice);

        // 添加价格分析建议
        if (summary.getPriceAnalysis() != null &&
                summary.getPriceAnalysis().getRecommendations() != null) {
            recommendations.addAll(summary.getPriceAnalysis().getRecommendations());
        }

        // 添加汇总建议
        recommendations.addAll(summary.getRecommendations());

        return recommendations;
    }

    /**
     * 构建摘要文本
     */
    private String buildSummaryText(MultiTimeFrameSummary summary, String tradeAdvice) {
        return String.format("多周期分析: 得分%.2f, 一致性%d/5, %s, 建议:%s",
                summary.getCompositeScore(),
                summary.getAgreementLevel(),
                summary.getTrendDirection(),
                tradeAdvice);
    }

    /**
     * 记录多周期分析日志
     */
    private void logMultiTimeFrameAnalysis(TradingSignalDto signal, MultiTimeFrameSummary summary) {
        log.info("=".repeat(80));
        log.info("🌊 多周期艾略特波浪分析完成");
        log.info("交易对: {}, 信号ID: {}", signal.getSymbol(), signal.getId());
        log.info("综合得分: {}/100", String.format("%.0f", summary.getCompositeScore() * 100));
        log.info("趋势方向: {}", summary.getTrendDirection());
        log.info("一致性等级: {}/5", summary.getAgreementLevel());

        // 各周期详情
        log.info("各周期详情:");
        for (Map.Entry<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> entry :
                summary.getPeriodAnalyses().entrySet()) {
            TimeFrame tf = entry.getKey();
            ElliottWaveEvaluator.TimeFrameAnalysis analysis = entry.getValue();

            log.info("  {}周期: 相位={}, 方向={}, 得分={}, 权重={}",
                    tf,
                    analysis.getCurrentPhase(),
                    analysis.isBullish() ? "BULLISH" : "BEARISH",
                    String.format("%.2f", analysis.getScore()),
                    String.format("%.2f", summary.getWeights().getOrDefault(tf, 0.0)));
        }

        log.info("=".repeat(80));
    }

    // ========== 回退和错误处理 ==========

    private QualityEvaluationResult createFallbackResult(
            TradingSignalDto signal, double evaluatorWeight, String evaluatorId) {
        return QualityEvaluationResult.builder()
                .evaluatorId(evaluatorId)
                .signalId(signal.getId())
                .score(0.5)
                .weight(evaluatorWeight)
                .summary("多周期分析失败，回退到单周期分析")
                .warnings(List.of("所有周期分析均失败"))
                .build();
    }

    private QualityEvaluationResult createErrorResult(
            TradingSignalDto signal, double evaluatorWeight, String evaluatorId, String message) {
        return QualityEvaluationResult.builder()
                .evaluatorId(evaluatorId)
                .signalId(signal.getId())
                .score(0.5)
                .weight(evaluatorWeight)
                .summary("多周期分析失败")
                .warnings(List.of(message))
                .build();
    }

    /**
     * 多周期分析汇总
     */
    @Data
    @Builder
    static class MultiTimeFrameSummary {
        private Map<TimeFrame, ElliottWaveEvaluator.TimeFrameAnalysis> periodAnalyses; // 各周期分析结果
        private Map<TimeFrame, Double> weights;                   // 各周期权重
        private double compositeScore;                           // 综合得分
        private String trendDirection;                           // 趋势方向
        private int agreementLevel;                              // 一致性等级
        private PriceAnalysisDto priceAnalysis;                  // 价格分析
        private ElliottPhase dominantPhase;                      // 主导相位
        private List<String> warnings;                           // 警告
        private List<String> recommendations;                    // 建议
    }
}

