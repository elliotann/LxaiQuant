package com.chain.ai.trade.engine.service.impl;

import com.chain.ai.trade.engine.controller.dto.*;
import com.chain.ai.trade.engine.service.TrendlineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.RecentFractalSwingLowIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.ScoringWeights;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.ToleranceSettings;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.TrendLineSegment;
import org.ta4j.core.indicators.supportresistance.TrendLineResistanceIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TrendlineServiceImpl implements TrendlineService {

    @Override
    public TrendlineData calculateTrendlines(BarSeries series, List<String> indicators, TrendlineParams params) {
        if (series == null || series.getBarCount() < 2) {
            return new TrendlineData();
        }
        TrendlineData data = new TrendlineData();
        for (String indicator : indicators) {
            switch (indicator) {
                case "support":
                    data.setSupport(calcSupport(series, params));
                    break;
                case "resistance":
                    data.setResistance(calcResistance(series, params));
                    break;
                default:
                    log.warn("未知趋势线指标: {}", indicator);
            }
        }
        return data;
    }

    private SupportResistanceData calcSupport(BarSeries series, TrendlineParams params) {
        try {
            int surroundingBars = Math.max(params.getSurroundingBars(), 1);
            int barCount = Math.max(params.getBarCount(), 20);
            int endIndex = series.getEndIndex();

            TrendLineSupportIndicator indicator = createSupportIndicator(series, surroundingBars, barCount, params);
            indicator.getValue(endIndex);
            TrendLineSegment segment = indicator.getCurrentSegment();
            if (segment == null) {
                log.warn("支撑趋势线：未找到有效线段");
                return null;
            }
            return buildResult(series, segment);
        } catch (Exception e) {
            log.error("计算支撑趋势线异常", e);
            return null;
        }
    }

    private SupportResistanceData calcResistance(BarSeries series, TrendlineParams params) {
        try {
            int surroundingBars = Math.max(params.getSurroundingBars(), 1);
            int barCount = Math.max(params.getBarCount(), 20);
            int endIndex = series.getEndIndex();

            TrendLineResistanceIndicator indicator = createResistanceIndicator(series, surroundingBars, barCount, params);
            indicator.getValue(endIndex);
            TrendLineSegment segment = indicator.getCurrentSegment();
            if (segment == null) {
                log.warn("阻力趋势线：未找到有效线段");
                return null;
            }
            return buildResult(series, segment);
        } catch (Exception e) {
            log.error("计算阻力趋势线异常", e);
            return null;
        }
    }

    private TrendLineSupportIndicator createSupportIndicator(BarSeries series, int surroundingBars, int barCount, TrendlineParams params) {
        ScoringWeights weights = resolveScoringWeights(params);
        ToleranceSettings tolerance = resolveTolerance(params);
        if (tolerance == null) {
            return new TrendLineSupportIndicator(series, surroundingBars, barCount, weights);
        }
        return new TrendLineSupportIndicator(
                new RecentFractalSwingLowIndicator(new LowPriceIndicator(series), surroundingBars, surroundingBars, 0),
                barCount,
                weights.touchCountWeight, weights.touchesExtremeWeight, weights.outsideCountWeight,
                weights.averageDeviationWeight, weights.anchorRecencyWeight,
                tolerance.mode.name(), tolerance.value, tolerance.minimumAbsolute);
    }

    private TrendLineResistanceIndicator createResistanceIndicator(BarSeries series, int surroundingBars, int barCount, TrendlineParams params) {
        ScoringWeights weights = resolveScoringWeights(params);
        ToleranceSettings tolerance = resolveTolerance(params);
        if (tolerance == null) {
            return new TrendLineResistanceIndicator(series, surroundingBars, barCount, weights);
        }
        return new TrendLineResistanceIndicator(
                new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), surroundingBars, surroundingBars, 0),
                barCount,
                weights.touchCountWeight, weights.touchesExtremeWeight, weights.outsideCountWeight,
                weights.averageDeviationWeight, weights.anchorRecencyWeight,
                tolerance.mode.name(), tolerance.value, tolerance.minimumAbsolute);
    }

    private ScoringWeights resolveScoringWeights(TrendlineParams params) {
        String preset = params.getScoringWeightsPreset();
        if (preset == null || preset.isEmpty()) {
            return ScoringWeights.defaultWeights();
        }
        switch (preset.toLowerCase().replace("-", "").replace("_", "")) {
            case "extremeswingbias":
                return ScoringWeights.extremeSwingBiasPreset();
            case "touchcountbias":
                return ScoringWeights.touchCountBiasPreset();
            default:
                return ScoringWeights.defaultWeights();
        }
    }

    private ToleranceSettings resolveTolerance(TrendlineParams params) {
        if (params.getToleranceMode() == null || params.getToleranceMode().isEmpty()) {
            return null;
        }
        double value = params.getToleranceValue() != null ? params.getToleranceValue() : 0.02;
        double minimum = params.getToleranceMinimum() != null ? params.getToleranceMinimum() : 1e-9;
        return AbstractTrendLineIndicator.ToleranceSettings.from(params.getToleranceMode(), value, minimum);
    }

    private SupportResistanceData buildResult(BarSeries series, TrendLineSegment segment) {
        SupportResistanceData data = new SupportResistanceData();

        TrendSegment trendSegment = new TrendSegment();
        trendSegment.setFirstIndex(segment.firstIndex);
        trendSegment.setSecondIndex(segment.secondIndex);
        trendSegment.setSlope(BigDecimal.valueOf(segment.slope.doubleValue()));
        trendSegment.setIntercept(BigDecimal.valueOf(segment.intercept.doubleValue()));
        trendSegment.setTouchCount(segment.touchCount);
        trendSegment.setOutsideCount(segment.outsideCount);
        trendSegment.setScore(segment.score);
        data.setSegment(trendSegment);

        long baseEpochMillis = series.getBar(series.getBeginIndex()).getEndTime().toEpochMilli();
        double slope = segment.slope.doubleValue();
        double intercept = segment.intercept.doubleValue();
        List<TimeValuePoint> linePoints = new ArrayList<>();
        int startIdx = Math.max(segment.windowStart, series.getBeginIndex());
        int endIdx = Math.min(segment.windowEnd, series.getEndIndex());
        for (int idx : new int[]{startIdx, endIdx}) {
            long epochMillis = series.getBar(idx).getEndTime().toEpochMilli();
            double timeCoord = epochMillis - baseEpochMillis;
            double price = slope * timeCoord + intercept;
            linePoints.add(new TimeValuePoint(epochMillis, price));
        }
        data.setLinePoints(linePoints);

        return data;
    }
}
