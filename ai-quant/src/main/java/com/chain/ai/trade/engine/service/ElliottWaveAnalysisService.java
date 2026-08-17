package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.engine.controller.dto.ElliottWaveAnalysisDTO;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottChannel;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottSwingCompressor;
import org.ta4j.core.indicators.elliott.ElliottWaveFacade;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ElliottWaveAnalysisService {

    private final ICandlestickService candlestickService;

    public ElliottWaveAnalysisDTO analyze(String symbol, String interval, Integer limit, String degree, Double fibTolerance) {

        CandlestickIntervalEnum intervalEnum = parseInterval(interval);
        if (intervalEnum == null) {
            throw new IllegalArgumentException("无效的interval参数");
        }

        int take = limit == null ? 500 : Math.min(2000, Math.max(120, limit));
        List<Candlestick> klines = loadKlines(symbol, intervalEnum, take);
        if (klines == null || klines.size() < 80) {
            return null;
        }

        BarSeries series = IndicatorWrapHelper.buildSeries(klines);
        int endIndex = series.getEndIndex();
        ElliottDegree d = resolveDegree(degree, intervalEnum);
        double tol = fibTolerance == null ? 0.25 : Math.max(0.01, Math.min(0.6, fibTolerance));

        ElliottSwingCompressor compressor = new ElliottSwingCompressor(series);
        ElliottWaveFacade facade = ElliottWaveFacade.zigZag(
                series,
                d,
                Optional.of(series.numFactory().numOf(tol)),
                Optional.of(compressor)
        );

        var currentPhase = facade.phase().getValue(endIndex);
        boolean invalidation = facade.invalidation().getValue(endIndex);
        ElliottChannel channel = facade.channel().getValue(endIndex);
        var confluence = facade.confluence().getValue(endIndex);
        int waveCount = facade.waveCount().getValue(endIndex);
        int filteredWaveCount = facade.filteredWaveCount().getValue(endIndex);
        ElliottScenarioSet scenarioSet = facade.scenarios().getValue(endIndex);

        ElliottWaveAnalysisDTO.ChannelDTO channelDTO = ElliottWaveAnalysisDTO.ChannelDTO.builder()
                .valid(channel != null && channel.isValid())
                .upper(channel != null && channel.upper() != null ? channel.upper().toString() : null)
                .lower(channel != null && channel.lower() != null ? channel.lower().toString() : null)
                .median(channel != null && channel.median() != null ? channel.median().toString() : null)
                .build();

        ElliottWaveAnalysisDTO.ScenarioDTO baseScenario = scenarioSet != null
                ? scenarioSet.base().map(this::toScenarioDTO).orElse(null)
                : null;

        List<ElliottWaveAnalysisDTO.ScenarioDTO> alternatives = scenarioSet != null && scenarioSet.alternatives() != null
                ? scenarioSet.alternatives().stream()
                .sorted(Comparator.comparingDouble(s -> -safeConfidencePercent(s)))
                .limit(8)
                .map(this::toScenarioDTO)
                .toList()
                : List.of();

        List<ElliottWaveAnalysisDTO.PeriodAnalysisDTO> periodAnalyses = analyzeDefaultHigherPeriods(symbol, intervalEnum, limit, fibTolerance);

        return ElliottWaveAnalysisDTO.builder()
                .symbol(symbol)
                .interval(intervalEnum.name())
                .barCount(series.getBarCount())
                .endIndex(endIndex)
                .degree(d.name())
                .currentPhase(currentPhase != null ? translatePhase(currentPhase.name()) : null)
                .invalidation(invalidation)
                .confluenceScore(confluence != null ? confluence.doubleValue() : null)
                .waveCount(waveCount)
                .filteredWaveCount(filteredWaveCount)
                .channel(channelDTO)
                .baseScenario(baseScenario)
                .alternativeScenarios(alternatives)
                .periodAnalyses(periodAnalyses)
                .build();
    }

    private List<Candlestick> loadKlines(String symbol, CandlestickIntervalEnum interval, int limit) {
        KlineParam klineParam = KlineParam.builder()
                .symbol(symbol)
                .klineInterval(interval)
                .size(limit)
                .build();
        return candlestickService.getLastKlines(klineParam);
    }

    private CandlestickIntervalEnum parseInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            return null;
        }
        try {
            return CandlestickIntervalEnum.valueOf(interval);
        } catch (IllegalArgumentException ignored) {
            for (CandlestickIntervalEnum value : CandlestickIntervalEnum.values()) {
                if (interval.equalsIgnoreCase(value.getCode())) {
                    return value;
                }
            }
            return null;
        }
    }

    private ElliottDegree resolveDegree(String degree, CandlestickIntervalEnum interval) {
        if (degree != null && !degree.isBlank()) {
            try {
                return ElliottDegree.valueOf(degree);
            } catch (Exception ignored) {
            }
        }
        String code = interval != null ? interval.getCode() : null;
        if (code == null) return ElliottDegree.PRIMARY;
        String c = code.toLowerCase();
        if (c.contains("min")) return ElliottDegree.MINOR;
        if (c.contains("h") || c.contains("60")) return ElliottDegree.INTERMEDIATE;
        if (c.contains("d")) return ElliottDegree.PRIMARY;
        return ElliottDegree.PRIMARY;
    }

    private ElliottWaveAnalysisDTO.ScenarioDTO toScenarioDTO(ElliottScenario s) {
        if (s == null) return null;
        String direction = s.hasKnownDirection() ? (s.isBullish() ? "看涨" : "看跌") : "未知";
        return ElliottWaveAnalysisDTO.ScenarioDTO.builder()
                .id(s.id())
                .type(s.type() != null ? translateTypeCn(s.type().name()) : null)
                .currentPhase(s.currentPhase() != null ? translatePhase(s.currentPhase().name()) : null)
                .direction(direction)
                .confidencePercent(safeConfidencePercent(s))
                .invalidationPrice(s.invalidationPrice() != null ? s.invalidationPrice().toString() : null)
                .primaryTarget(s.primaryTarget() != null ? s.primaryTarget().toString() : null)
                .build();
    }

    private double safeConfidencePercent(ElliottScenario scenario) {
        try {
            return scenario != null && scenario.confidence() != null ? scenario.confidence().asPercentage() : 0.0;
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private List<ElliottWaveAnalysisDTO.PeriodAnalysisDTO> analyzeDefaultHigherPeriods(String symbol, CandlestickIntervalEnum current, Integer limit, Double fibTolerance) {
        int take = limit == null ? 500 : Math.min(2000, Math.max(120, limit));
        var targets = List.of(
                new PeriodTarget("1小时", CandlestickIntervalEnum.OKXMIN60),
                new PeriodTarget("4小时", CandlestickIntervalEnum.OKX4HOUR)
        );
        return targets.stream()
                .filter(t -> t.interval != current)
                .map(t -> {
                    List<Candlestick> ks = loadKlines(symbol, t.interval, take);
                    if (ks == null || ks.size() < 80) return null;
                    BarSeries s = IndicatorWrapHelper.buildSeries(ks);
                    ElliottDegree d = resolveDegree(null, t.interval);
                    double tol = fibTolerance == null ? 0.25 : Math.max(0.01, Math.min(0.6, fibTolerance));
                    ElliottSwingCompressor compressor = new ElliottSwingCompressor(s);
                    ElliottWaveFacade facade = ElliottWaveFacade.zigZag(
                            s,
                            d,
                            Optional.of(s.numFactory().numOf(tol)),
                            Optional.of(compressor)
                    );
                    int end = s.getEndIndex();
                    var phase = facade.phase().getValue(end);
                    boolean invalid = facade.invalidation().getValue(end);
                    var confl = facade.confluence().getValue(end);
                    int wc = facade.waveCount().getValue(end);
                    ElliottScenarioSet set = facade.scenarios().getValue(end);
                    ElliottWaveAnalysisDTO.ScenarioDTO base = set != null
                            ? set.base().map(this::toScenarioDTO).orElse(null)
                            : null;
                    return ElliottWaveAnalysisDTO.PeriodAnalysisDTO.builder()
                            .period(t.displayName)
                            .degree(d.name())
                            .barCount(s.getBarCount())
                            .currentPhase(phase != null ? translatePhase(phase.name()) : null)
                            .invalidation(invalid)
                            .confluenceScore(confl != null ? confl.doubleValue() : null)
                            .waveCount(wc)
                            .baseScenario(base)
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private record PeriodTarget(String displayName, CandlestickIntervalEnum interval) {}

    private String translateTypeCn(String type) {
        if (type == null) return null;
        return switch (type) {
            case "IMPULSE" -> "推动浪";
            case "CORRECTIVE_ZIGZAG" -> "锯齿形调整";
            case "CORRECTIVE_FLAT" -> "平台形调整";
            case "CORRECTIVE_TRIANGLE" -> "三角形调整";
            case "CORRECTIVE_DOUBLE_THREE" -> "双重三";
            case "CORRECTIVE_TRIPLE_THREE" -> "三重三";
            default -> type;
        };
    }

    private String translatePhase(String phase) {
        if (phase == null) return null;
        return switch (phase) {
            case "WAVE1" -> "第1浪";
            case "WAVE2" -> "第2浪";
            case "WAVE3" -> "第3浪";
            case "WAVE4" -> "第4浪";
            case "WAVE5" -> "第5浪";
            case "WAVE_A" -> "A浪";
            case "WAVE_B" -> "B浪";
            case "WAVE_C" -> "C浪";
            default -> phase;
        };
    }
    private String normalizeSymbol(String symbol) {
        if (symbol == null) return null;
        String s = symbol.trim();
        if (s.isEmpty()) return s;
        s = s.replace("-", "").replace("_", "").replace("/", "");
        return s.toUpperCase();
    }
}
