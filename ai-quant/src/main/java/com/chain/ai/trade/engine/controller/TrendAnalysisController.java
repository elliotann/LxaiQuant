package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.RecentFractalSwingLowIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineResistanceIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
@Slf4j
public class TrendAnalysisController {

    private final ICandlestickService candlestickService;

    @Autowired(required = false)
    private RedisCache redisCache;

    @GetMapping("/trend-analysis")
    public ResponseEntity<Map<String, Object>> getTrendAnalysis(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam(defaultValue = "500") Integer limit) {
        log.info("收到趋势分析请求: symbol={}, interval={}, limit={}", symbol, interval, limit);

        try {
            int safeLimit = (limit == null || limit <= 0) ? 500 : Math.min(limit, 2000);
            CandlestickIntervalEnum intervalEnum = parseInterval(interval);
            if (intervalEnum == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "无效的interval参数");
                return ResponseEntity.badRequest().body(result);
            }

            String cacheKey = buildTrendCacheKey(symbol, intervalEnum, safeLimit);
            Map<String, Object> cached = getTrendCache(cacheKey);
            if (cached != null) {
                refreshTrendCacheAsync(symbol, intervalEnum, safeLimit, cacheKey);
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", cached);
                return ResponseEntity.ok(result);
            }

            Map<String, Object> data = computeTrendAnalysis(symbol, intervalEnum, safeLimit);
            putTrendCache(cacheKey, data);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("趋势分析失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "趋势分析失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    private Map<String, Object> computeTrendAnalysis(String symbol, CandlestickIntervalEnum interval, int limit) {
        List<Candlestick> klines = getRecentKlines(symbol, interval, limit);
        Map<String, Object> data = new HashMap<>();
        if (klines == null || klines.isEmpty()) {
            data.put("trendDirection", "sideways");
            data.put("trendStrength", "weak");
            data.put("swingHighs", List.of());
            data.put("swingLows", List.of());
            return data;
        }

        BarSeries series = IndicatorWrapHelper.buildSeries(klines);
        int endIndex = series.getEndIndex();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        addBollingerBands(series, closePrice, endIndex, data, 20, "bb20");
        addBollingerBands(series, closePrice, endIndex, data, 50, "bb50");

        data.put("swingHighs", buildSwingPoints(klines, new RecentFractalSwingHighIndicator(series), true));
        data.put("swingLows", buildSwingPoints(klines, new RecentFractalSwingLowIndicator(series), false));

        TrendLineSupportIndicator supportIndicator = new TrendLineSupportIndicator(series, 3, Math.min(50, series.getBarCount()));
        TrendLineResistanceIndicator resistanceIndicator = new TrendLineResistanceIndicator(series, 3, Math.min(50, series.getBarCount()));

        AbstractTrendLineIndicator.TrendLineSegment supportSegment = supportIndicator.getCurrentSegment();
        AbstractTrendLineIndicator.TrendLineSegment resistanceSegment = resistanceIndicator.getCurrentSegment();

        TrendSummary trendSummary = summarizeTrend(series, closePrice, supportSegment, resistanceSegment);
        data.put("trendDirection", trendSummary.direction());
        data.put("trendStrength", trendSummary.strength());
        if (supportSegment != null && supportSegment.slope != null) {
            data.put("supportSlope", supportSegment.slope.doubleValue());
        }
        if (resistanceSegment != null && resistanceSegment.slope != null) {
            data.put("resistanceSlope", resistanceSegment.slope.doubleValue());
        }
        if (series.getBarCount() > 0) {
            data.put("supportLinePrice", supportIndicator.getValue(endIndex).doubleValue());
            data.put("resistanceLinePrice", resistanceIndicator.getValue(endIndex).doubleValue());
        }

        return data;
    }

    private void addBollingerBands(BarSeries series, ClosePriceIndicator closePrice, int endIndex,
                                   Map<String, Object> data, int period, String prefix) {
        if (series.getBarCount() < period) {
            return;
        }
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, period);
        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(sma);
        BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(middle, stdDev);
        BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(middle, stdDev);

        data.put(prefix + "_upper", upper.getValue(endIndex).doubleValue());
        data.put(prefix + "_lower", lower.getValue(endIndex).doubleValue());
        data.put(prefix + "_middle", middle.getValue(endIndex).doubleValue());
    }

    private List<Map<String, Object>> buildSwingPoints(List<Candlestick> klines, org.ta4j.core.indicators.RecentSwingIndicator indicator, boolean high) {
        List<Integer> indexes = Optional.ofNullable(indicator.getSwingPointIndexes()).orElse(List.of());
        return indexes.stream()
                .filter(index -> index >= 0 && index < klines.size())
                .map(index -> {
                    Candlestick kline = klines.get(index);
                    Map<String, Object> point = new HashMap<>();
                    point.put("price", high ? kline.getHighPrice() : kline.getLowPrice());
                    point.put("timestamp", kline.getId());
                    return point;
                })
                .collect(Collectors.toList());
    }

    private TrendSummary summarizeTrend(BarSeries series, ClosePriceIndicator closePrice,
                                        AbstractTrendLineIndicator.TrendLineSegment supportSegment,
                                        AbstractTrendLineIndicator.TrendLineSegment resistanceSegment) {
        int endIndex = series.getEndIndex();
        int startIndex = Math.max(0, endIndex - Math.min(50, series.getBarCount() - 1));
        double startClose = closePrice.getValue(startIndex).doubleValue();
        double endClose = closePrice.getValue(endIndex).doubleValue();
        double fallbackSlope = startClose == 0 ? 0 : (endClose - startClose) / Math.max(1, endIndex - startIndex);

        double slopeSupport = supportSegment != null && supportSegment.slope != null ? supportSegment.slope.doubleValue() : 0;
        double slopeResistance = resistanceSegment != null && resistanceSegment.slope != null ? resistanceSegment.slope.doubleValue() : 0;
        double slopeAvg = averageSlope(slopeSupport, slopeResistance, fallbackSlope);

        double directionThreshold = Math.max(1e-8, Math.abs(endClose) * 0.0002);
        String direction = "sideways";
        if (slopeAvg > directionThreshold) {
            direction = "uptrend";
        } else if (slopeAvg < -directionThreshold) {
            direction = "downtrend";
        }

        double slopeRate = endClose == 0 ? 0 : Math.abs(slopeAvg) / Math.abs(endClose);
        String strength = "weak";
        if (slopeRate >= 0.002) {
            strength = "strong";
        } else if (slopeRate >= 0.001) {
            strength = "medium";
        }

        int touchTotal = (supportSegment != null ? supportSegment.touchCount : 0)
                + (resistanceSegment != null ? resistanceSegment.touchCount : 0);
        if (touchTotal >= 6) {
            strength = "strong";
        } else if (touchTotal >= 3 && "weak".equals(strength)) {
            strength = "medium";
        }

        return new TrendSummary(direction, strength);
    }

    private double averageSlope(double supportSlope, double resistanceSlope, double fallbackSlope) {
        boolean hasSupport = supportSlope != 0;
        boolean hasResistance = resistanceSlope != 0;
        if (hasSupport && hasResistance) {
            return (supportSlope + resistanceSlope) / 2.0;
        }
        if (hasSupport) {
            return supportSlope;
        }
        if (hasResistance) {
            return resistanceSlope;
        }
        return fallbackSlope;
    }

    private List<Candlestick> getRecentKlines(String symbol, CandlestickIntervalEnum interval, int limit) {
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

    private String buildTrendCacheKey(String symbol, CandlestickIntervalEnum interval, int limit) {
        return "trend:analysis:" + symbol + ":" + interval.name() + ":" + limit;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getTrendCache(String cacheKey) {
        if (redisCache == null) {
            return null;
        }
        Object cached = redisCache.get(cacheKey);
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }
        return null;
    }

    private void putTrendCache(String cacheKey, Map<String, Object> data) {
        if (redisCache == null || data == null) {
            return;
        }
        redisCache.put(cacheKey, data, 30L);
    }

    private void refreshTrendCacheAsync(String symbol, CandlestickIntervalEnum interval, int limit, String cacheKey) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> data = computeTrendAnalysis(symbol, interval, limit);
                putTrendCache(cacheKey, data);
            } catch (Exception e) {
                log.debug("趋势分析缓存刷新失败: {}", e.getMessage());
            }
        });
    }

    private record TrendSummary(String direction, String strength) {
    }
}
