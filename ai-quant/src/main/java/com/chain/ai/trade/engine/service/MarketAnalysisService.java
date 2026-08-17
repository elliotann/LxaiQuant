package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.engine.controller.dto.MarketAnalysisDTO;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MarketAnalysisService {
    private final ICandlestickService candlestickService;

    public MarketAnalysisDTO analyze(String symbol, String interval, Integer limit) {
        String sym = normalizeSymbol(symbol);
        CandlestickIntervalEnum intervalEnum = parseInterval(interval);
        int take = limit == null ? 240 : Math.min(800, Math.max(60, limit));

        KlineParam p = KlineParam.builder()
                .symbol(sym)
                .klineInterval(intervalEnum)
                .size(take)
                .build();
        List<Candlestick> rows = candlestickService.getLastKlines(p);
        if (rows == null || rows.size() < 30) {
            return null;
        }

        List<Candlestick> series = ensureAscending(rows);
        Candlestick latest = series.get(series.size() - 1);
        Candlestick prev = series.size() > 1 ? series.get(series.size() - 2) : null;

        BigDecimal price = nvl(latest.getClosePrice());
        BigDecimal changePercent = null;
        if (prev != null && prev.getClosePrice() != null && prev.getClosePrice().compareTo(BigDecimal.ZERO) != 0) {
            changePercent = price.subtract(prev.getClosePrice())
                    .divide(prev.getClosePrice(), 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        List<BigDecimal> closes = new ArrayList<>();
        List<BigDecimal> highs = new ArrayList<>();
        List<BigDecimal> lows = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (Candlestick c : series) {
            closes.add(nvl(c.getClosePrice()));
            highs.add(nvl(c.getHighPrice()));
            lows.add(nvl(c.getLowPrice()));
            times.add(parseLongSafe(c.getTimeStr()));
        }

        BigDecimal ema9 = ema(closes, 9);
        BigDecimal ema21 = ema(closes, 21);
        BigDecimal ema20Prev5 = emaForLastN(closes, 20, Math.max(1, closes.size() - 5));

        BigDecimal rsi14 = rsi(closes, 14);
        BigDecimal atrPct = atrPercent(highs, lows, closes, 14);
        BigDecimal bbWidthPct = bollingerWidthPercent(closes, 20, new BigDecimal("2"));

        TrendResult trend = computeTrend(price, ema9, ema21, ema20Prev5);
        SentimentResult sentiment = computeSentiment(price, trend, rsi14, atrPct);

        LevelsResult levels = computeLevels(highs, lows, closes, price);
        List<String> tags = buildTags(trend, rsi14, atrPct, bbWidthPct);

        return MarketAnalysisDTO.builder()
                .symbol(sym)
                .interval(intervalEnum != null ? intervalEnum.name() : null)
                .time(times.get(times.size() - 1))
                .price(scale(price, 8))
                .changePercent(changePercent)
                .sentimentScore(sentiment.score)
                .sentimentLabel(sentiment.label)
                .trendLabel(trend.label)
                .trendStrength(trend.strength)
                .ema9(scale(ema9, 8))
                .ema21(scale(ema21, 8))
                .rsi14(rsi14 != null ? rsi14.setScale(2, RoundingMode.HALF_UP) : null)
                .atr14Percent(atrPct != null ? atrPct.setScale(4, RoundingMode.HALF_UP) : null)
                .bollingerWidthPercent(bbWidthPct != null ? bbWidthPct.setScale(4, RoundingMode.HALF_UP) : null)
                .supports(levels.supports)
                .resistances(levels.resistances)
                .tags(tags)
                .build();
    }

    private List<Candlestick> ensureAscending(List<Candlestick> rows) {
        List<Candlestick> copy = new ArrayList<>(rows);
        if (copy.size() < 2) return copy;
        Long t0 = parseLongSafe(copy.get(0).getTimeStr());
        Long t1 = parseLongSafe(copy.get(copy.size() - 1).getTimeStr());
        if (t0 != null && t1 != null && t0 > t1) {
            Collections.reverse(copy);
        }
        return copy;
    }

    private BigDecimal ema(List<BigDecimal> closes, int period) {
        if (closes == null || closes.size() < period) return null;
        double alpha = 2.0 / (period + 1.0);
        double ema = closes.get(0).doubleValue();
        for (int i = 1; i < closes.size(); i++) {
            double c = closes.get(i).doubleValue();
            ema = alpha * c + (1 - alpha) * ema;
        }
        return BigDecimal.valueOf(ema);
    }

    private BigDecimal emaForLastN(List<BigDecimal> closes, int period, int startIndexInclusive) {
        if (closes == null || closes.size() < period) return null;
        int start = Math.max(0, Math.min(closes.size() - 1, startIndexInclusive));
        double alpha = 2.0 / (period + 1.0);
        double ema = closes.get(0).doubleValue();
        for (int i = 1; i < start; i++) {
            double c = closes.get(i).doubleValue();
            ema = alpha * c + (1 - alpha) * ema;
        }
        return BigDecimal.valueOf(ema);
    }

    private BigDecimal rsi(List<BigDecimal> closes, int period) {
        if (closes == null || closes.size() <= period) return null;
        double gain = 0.0;
        double loss = 0.0;
        for (int i = 1; i <= period; i++) {
            double diff = closes.get(i).doubleValue() - closes.get(i - 1).doubleValue();
            if (diff >= 0) gain += diff;
            else loss += -diff;
        }
        gain /= period;
        loss /= period;
        for (int i = period + 1; i < closes.size(); i++) {
            double diff = closes.get(i).doubleValue() - closes.get(i - 1).doubleValue();
            double g = diff > 0 ? diff : 0;
            double l = diff < 0 ? -diff : 0;
            gain = (gain * (period - 1) + g) / period;
            loss = (loss * (period - 1) + l) / period;
        }
        if (loss == 0) return BigDecimal.valueOf(100);
        double rs = gain / loss;
        double rsi = 100.0 - (100.0 / (1.0 + rs));
        return BigDecimal.valueOf(rsi);
    }

    private BigDecimal atrPercent(List<BigDecimal> highs, List<BigDecimal> lows, List<BigDecimal> closes, int period) {
        if (highs == null || lows == null || closes == null) return null;
        if (highs.size() != lows.size() || highs.size() != closes.size()) return null;
        if (highs.size() <= period) return null;

        double atr = 0.0;
        for (int i = 1; i <= period; i++) {
            double h = highs.get(i).doubleValue();
            double l = lows.get(i).doubleValue();
            double pc = closes.get(i - 1).doubleValue();
            double tr = Math.max(h - l, Math.max(Math.abs(h - pc), Math.abs(l - pc)));
            atr += tr;
        }
        atr /= period;
        for (int i = period + 1; i < highs.size(); i++) {
            double h = highs.get(i).doubleValue();
            double l = lows.get(i).doubleValue();
            double pc = closes.get(i - 1).doubleValue();
            double tr = Math.max(h - l, Math.max(Math.abs(h - pc), Math.abs(l - pc)));
            atr = (atr * (period - 1) + tr) / period;
        }

        double price = closes.get(closes.size() - 1).doubleValue();
        if (price <= 0) return null;
        return BigDecimal.valueOf(atr / price * 100.0);
    }

    private BigDecimal bollingerWidthPercent(List<BigDecimal> closes, int period, BigDecimal k) {
        if (closes == null || closes.size() < period) return null;
        int n = closes.size();
        List<BigDecimal> window = closes.subList(n - period, n);
        double mean = 0.0;
        for (BigDecimal v : window) mean += v.doubleValue();
        mean /= period;
        if (mean == 0) return null;
        double var = 0.0;
        for (BigDecimal v : window) {
            double d = v.doubleValue() - mean;
            var += d * d;
        }
        var /= period;
        double std = Math.sqrt(var);
        double kk = k == null ? 2.0 : k.doubleValue();
        double upper = mean + kk * std;
        double lower = mean - kk * std;
        double width = (upper - lower) / mean * 100.0;
        return BigDecimal.valueOf(width);
    }

    private TrendResult computeTrend(BigDecimal price, BigDecimal ema20, BigDecimal ema60, BigDecimal ema20Prev5) {
        String label = "Neutral";
        int strength = 50;
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0 || ema20 == null || ema60 == null) {
            return new TrendResult(label, strength);
        }

        BigDecimal diffPct = ema20.subtract(ema60)
                .divide(price, 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        BigDecimal slopePct = null;
        if (ema20Prev5 != null) {
            slopePct = ema20.subtract(ema20Prev5)
                    .divide(price, 10, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        boolean up = diffPct.compareTo(BigDecimal.ZERO) > 0;
        boolean down = diffPct.compareTo(BigDecimal.ZERO) < 0;
        boolean slopeUp = slopePct != null && slopePct.compareTo(BigDecimal.ZERO) > 0;
        boolean slopeDown = slopePct != null && slopePct.compareTo(BigDecimal.ZERO) < 0;

        if (up && slopeUp) label = "Bullish";
        else if (down && slopeDown) label = "Bearish";

        double mag = Math.abs(diffPct.doubleValue()) * 160.0 + Math.abs(slopePct != null ? slopePct.doubleValue() : 0.0) * 240.0;
        strength = (int) Math.max(0, Math.min(100, 50 + (label.equals("Bullish") ? mag : (label.equals("Bearish") ? -mag : 0))));
        if (label.equals("Bearish")) strength = 100 - strength;
        return new TrendResult(label, clampInt(strength, 0, 100));
    }

    private SentimentResult computeSentiment(BigDecimal price, TrendResult trend, BigDecimal rsi14, BigDecimal atrPct) {
        double score = 50.0;
        if (trend != null) {
            if ("Bullish".equals(trend.label)) score += (trend.strength - 50) * 0.7;
            else if ("Bearish".equals(trend.label)) score -= (trend.strength - 50) * 0.7;
        }
        if (rsi14 != null) {
            score += (rsi14.doubleValue() - 50.0) * 0.45;
        }
        if (atrPct != null) {
            score -= Math.min(20.0, atrPct.doubleValue() * 2.0);
        }
        int s = clampInt((int) Math.round(score), 0, 100);
        String label;
        if (s < 30) label = "Fear";
        else if (s < 45) label = "Bearish";
        else if (s < 55) label = "Neutral";
        else if (s < 70) label = "Bullish";
        else label = "Greed";
        return new SentimentResult(s, label);
    }

    private LevelsResult computeLevels(List<BigDecimal> highs, List<BigDecimal> lows, List<BigDecimal> closes, BigDecimal price) {
        int n = closes.size();
        int lookback = Math.min(140, n);
        int start = Math.max(0, n - lookback);
        List<BigDecimal> pivH = new ArrayList<>();
        List<BigDecimal> pivL = new ArrayList<>();
        for (int i = start + 2; i < n - 2; i++) {
            BigDecimal h = highs.get(i);
            BigDecimal l = lows.get(i);
            boolean isHigh = h.compareTo(highs.get(i - 1)) > 0 && h.compareTo(highs.get(i - 2)) > 0
                    && h.compareTo(highs.get(i + 1)) > 0 && h.compareTo(highs.get(i + 2)) > 0;
            boolean isLow = l.compareTo(lows.get(i - 1)) < 0 && l.compareTo(lows.get(i - 2)) < 0
                    && l.compareTo(lows.get(i + 1)) < 0 && l.compareTo(lows.get(i + 2)) < 0;
            if (isHigh) pivH.add(h);
            if (isLow) pivL.add(l);
        }

        pivH.sort(Comparator.naturalOrder());
        pivL.sort(Comparator.naturalOrder());
        List<BigDecimal> supports = pickNearestBelow(pivL, price, 2);
        List<BigDecimal> resistances = pickNearestAbove(pivH, price, 2);
        if (supports.isEmpty()) {
            supports = pickNearestBelow(List.of(Collections.min(lows.subList(start, n))), price, 1);
        }
        if (resistances.isEmpty()) {
            resistances = pickNearestAbove(List.of(Collections.max(highs.subList(start, n))), price, 1);
        }
        return new LevelsResult(supports, resistances);
    }

    private List<BigDecimal> pickNearestBelow(List<BigDecimal> levels, BigDecimal price, int max) {
        if (price == null) return List.of();
        List<BigDecimal> out = new ArrayList<>();
        for (int i = levels.size() - 1; i >= 0 && out.size() < max; i--) {
            BigDecimal v = levels.get(i);
            if (v == null) continue;
            if (v.compareTo(price) < 0) {
                if (out.isEmpty() || out.get(out.size() - 1).subtract(v).abs().compareTo(price.multiply(new BigDecimal("0.0005"))) > 0) {
                    out.add(scale(v, 8));
                }
            }
        }
        return out;
    }

    private List<BigDecimal> pickNearestAbove(List<BigDecimal> levels, BigDecimal price, int max) {
        if (price == null) return List.of();
        List<BigDecimal> out = new ArrayList<>();
        for (int i = 0; i < levels.size() && out.size() < max; i++) {
            BigDecimal v = levels.get(i);
            if (v == null) continue;
            if (v.compareTo(price) > 0) {
                if (out.isEmpty() || out.get(out.size() - 1).subtract(v).abs().compareTo(price.multiply(new BigDecimal("0.0005"))) > 0) {
                    out.add(scale(v, 8));
                }
            }
        }
        return out;
    }

    private List<String> buildTags(TrendResult trend, BigDecimal rsi14, BigDecimal atrPct, BigDecimal bbWidthPct) {
        List<String> tags = new ArrayList<>();
        if (trend != null) {
            if ("Bullish".equals(trend.label)) tags.add("趋势偏多");
            else if ("Bearish".equals(trend.label)) tags.add("趋势偏空");
            else tags.add("趋势震荡");
        }
        if (rsi14 != null) {
            if (rsi14.compareTo(new BigDecimal("70")) >= 0) tags.add("RSI超买");
            else if (rsi14.compareTo(new BigDecimal("30")) <= 0) tags.add("RSI超卖");
        }
        if (atrPct != null) {
            if (atrPct.compareTo(new BigDecimal("1.8")) >= 0) tags.add("波动偏大");
            else if (atrPct.compareTo(new BigDecimal("0.7")) <= 0) tags.add("波动偏小");
        }
        if (bbWidthPct != null) {
            if (bbWidthPct.compareTo(new BigDecimal("3.0")) <= 0) tags.add("布林收敛");
            else if (bbWidthPct.compareTo(new BigDecimal("7.0")) >= 0) tags.add("布林扩散");
        }
        return tags;
    }

    private CandlestickIntervalEnum parseInterval(String interval) {
        if (interval == null || interval.isBlank()) return CandlestickIntervalEnum.OKXMIN3;
        String s = interval.trim();
        try {
            return CandlestickIntervalEnum.valueOf(s);
        } catch (Exception ignored) {
        }
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "1m" -> CandlestickIntervalEnum.OKXMIN1;
            case "3m" -> CandlestickIntervalEnum.OKXMIN3;
            case "5m" -> CandlestickIntervalEnum.OKXMIN5;
            case "15m" -> CandlestickIntervalEnum.OKXMIN15;
            case "30m" -> CandlestickIntervalEnum.OKXMIN30;
            case "1h", "60m" -> CandlestickIntervalEnum.OKXMIN60;
            case "4h" -> CandlestickIntervalEnum.OKX4HOUR;
            case "1d" -> CandlestickIntervalEnum.OKX1D;
            default -> CandlestickIntervalEnum.OKXMIN3;
        };
    }

    private String normalizeSymbol(String symbol) {
        String s = String.valueOf(symbol == null ? "" : symbol).trim();
        if (s.isEmpty()) return "";
        s = s.trim().replaceAll("\\s+", "").replace("/", "-").toUpperCase(Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-Z0-9]{2,12}-[A-Z0-9]{2,12}(-SWAP)?")
                .matcher(s);
        if (m.find()) return m.group();
        if (s.matches("^[A-Z0-9]{2,12}$")) return s + "-USDT-SWAP";
        return s;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal scale(BigDecimal v, int scale) {
        if (v == null) return null;
        return v.setScale(scale, RoundingMode.HALF_UP);
    }

    private Long parseLongSafe(String s) {
        try {
            if (s == null) return null;
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private record TrendResult(String label, int strength) {}

    private record SentimentResult(int score, String label) {}

    private record LevelsResult(List<BigDecimal> supports, List<BigDecimal> resistances) {}
}

