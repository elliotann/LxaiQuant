package com.chain.ai.trade.engine.service.ai.filter;

import com.chain.ai.trade.engine.controller.dto.MarketAnalysisDTO;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.service.MarketAnalysisService;
import com.chain.ai.trade.engine.service.ai.filter.ObjectiveScorer.ScoreInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataCollector {

    private static final DateTimeFormatter CANDLE_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final MarketAnalysisService marketAnalysisService;
    private final ICandlestickService candlestickService;

    public ScoreInput collectScoreInput(String symbol, String signalDirection) {
        ScoreInput input = new ScoreInput();
        input.setSignalDirection(signalDirection);

        MarketAnalysisDTO weekly = marketAnalysisService.analyze(symbol, "1D", 200);
        MarketAnalysisDTO dto4h = marketAnalysisService.analyze(symbol, "4H", 140);
        MarketAnalysisDTO dto15m = marketAnalysisService.analyze(symbol, "15m", 100);

        input.setWeeklyTrend(weekly != null ? safeStr(weekly.getTrendLabel()) : "");
        input.setTrend4h(dto4h != null ? safeStr(dto4h.getTrendLabel()) : "");

        if (dto15m != null) {
            input.setAtr15m(dto15m.getAtr14Percent());
        }

        if (dto4h != null && dto4h.getPrice() != null) {
            BigDecimal price = dto4h.getPrice();
            List<BigDecimal> resistances = dto4h.getResistances();
            List<BigDecimal> supports = dto4h.getSupports();
            if (resistances != null && !resistances.isEmpty()) {
                BigDecimal nearest = resistances.get(0);
                input.setDistanceToResistance(nearest.subtract(price)
                        .divide(price, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
            }
            if (supports != null && !supports.isEmpty()) {
                BigDecimal nearest = supports.get(supports.size() - 1);
                input.setDistanceToSupport(price.subtract(nearest)
                        .divide(price, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
            }
        }

        String normSymbol = normalizeSymbol(symbol);
        KlineParam kp = KlineParam.builder()
                .symbol(normSymbol)
                .klineInterval(CandlestickIntervalEnum.OKXMIN15)
                .size(20)
                .build();
        List<Candlestick> klines15m = candlestickService.getLastKlines(kp);
        if (klines15m != null && !klines15m.isEmpty()) {
            BigDecimal avgVol = BigDecimal.ZERO;
            for (Candlestick c : klines15m) {
                avgVol = avgVol.add(nvl(c.getVolume()));
            }
            avgVol = avgVol.divide(BigDecimal.valueOf(klines15m.size()), 8, RoundingMode.HALF_UP);
            BigDecimal currentVol = nvl(klines15m.get(klines15m.size() - 1).getVolume());
            if (avgVol.compareTo(BigDecimal.ZERO) > 0) {
                input.setVolumeRatio(currentVol.divide(avgVol, 4, RoundingMode.HALF_UP));
            }
        }

        return input;
    }

    public MarketData collectPromptData(String symbol, String signalDirection, BigDecimal signalStrength) {
        MarketData data = new MarketData();
        data.setSymbol(symbol);
        data.setSignalDirection(signalDirection);
        data.setSignalStrength(signalStrength);
        data.setSignalTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        String normSymbol = normalizeSymbol(symbol);

        MarketAnalysisDTO weekly = marketAnalysisService.analyze(symbol, "1D", 200);
        MarketAnalysisDTO dto4h = marketAnalysisService.analyze(symbol, "4H", 140);
        MarketAnalysisDTO dto1h = marketAnalysisService.analyze(symbol, "1H", 100);
        MarketAnalysisDTO dto15m = marketAnalysisService.analyze(symbol, "15m", 100);

        if (weekly != null) {
            data.setWeeklyTrend(safeStr(weekly.getTrendLabel()));
        }
        if (dto4h != null) {
            data.setTrend4h(safeStr(dto4h.getTrendLabel()));
            data.setRsi4h(dto4h.getRsi14() != null ? dto4h.getRsi14().toString() : "");
            data.setResistanceLevels(formatLevels(dto4h.getResistances()));
            data.setSupportLevels(formatLevels(dto4h.getSupports()));
            if (dto4h.getPrice() != null) {
                List<Candlestick> klines4h = candlestickService.getLastKlines(
                        KlineParam.builder()
                                .symbol(normSymbol)
                                .klineInterval(CandlestickIntervalEnum.OKX4HOUR)
                                .size(20)
                                .build());
                data.setBbPosition4h(computeBbPosition(klines4h, dto4h.getPrice()));
            }
        }
        if (dto1h != null) {
            data.setTrend1h(safeStr(dto1h.getTrendLabel()));
            data.setRsi1h(dto1h.getRsi14() != null ? dto1h.getRsi14().toString() : "");
            if (dto1h.getPrice() != null) {
                List<Candlestick> klines1h = candlestickService.getLastKlines(
                        KlineParam.builder()
                                .symbol(normSymbol)
                                .klineInterval(CandlestickIntervalEnum.OKXMIN60)
                                .size(60)
                                .build());
                data.setMacdStatus1h(computeMacdStatus(klines1h));
            }
        }
        if (dto15m != null) {
            data.setLatestPrice(dto15m.getPrice() != null ? dto15m.getPrice().toString() : "");
            data.setRsi15m(dto15m.getRsi14() != null ? dto15m.getRsi14().toString() : "");
            data.setAtr15m(dto15m.getAtr14Percent() != null ? dto15m.getAtr14Percent().toString() : "");
        }

        VolumeData volData = computeVolumeData(normSymbol);
        data.setAvgVolume20(volData.avgVolume20);
        data.setCurrentVolume(volData.currentVolume);
        data.setVolumeRatio(volData.volumeRatio);
        data.setRecentCandles(volData.recentCandles);

        if (dto4h != null && dto4h.getPrice() != null) {
            BigDecimal price = dto4h.getPrice();
            List<BigDecimal> res = dto4h.getResistances();
            List<BigDecimal> sup = dto4h.getSupports();
            if (res != null && !res.isEmpty()) {
                BigDecimal nearest = res.get(0);
                data.setDistanceToResistance(nearest.subtract(price)
                        .divide(price, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%");
            }
            if (sup != null && !sup.isEmpty()) {
                BigDecimal nearest = sup.get(sup.size() - 1);
                data.setDistanceToSupport(price.subtract(nearest)
                        .divide(price, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%");
            }
        }

        return data;
    }

    private String computeBbPosition(List<Candlestick> klines, BigDecimal price) {
        if (klines == null || klines.size() < 20 || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        List<BigDecimal> closes = klines.stream()
                .map(c -> nvl(c.getClosePrice()))
                .collect(Collectors.toList());
        int n = closes.size();
        List<BigDecimal> window = closes.subList(n - 20, n);
        double mean = 0;
        for (BigDecimal v : window) mean += v.doubleValue();
        mean /= 20.0;
        if (mean == 0) return "";
        double var = 0;
        for (BigDecimal v : window) {
            double d = v.doubleValue() - mean;
            var += d * d;
        }
        var /= 20.0;
        double std = Math.sqrt(var);
        double upper = mean + 2 * std;
        double lower = mean - 2 * std;
        double p = price.doubleValue();

        if (p >= upper) return "上轨上方";
        if (p <= lower) return "下轨下方";
        double upperMid = (mean + upper) / 2;
        if (p >= upperMid) return "中轨与上轨之间";
        double lowerMid = (lower + mean) / 2;
        if (p <= lowerMid) return "中轨与下轨之间";
        return "中轨附近";
    }

    private String computeMacdStatus(List<Candlestick> klines) {
        if (klines == null || klines.size() < 35) return "";

        List<BigDecimal> closes = klines.stream()
                .map(c -> nvl(c.getClosePrice()))
                .collect(Collectors.toList());

        double alphaFast = 2.0 / 13.0;
        double alphaSlow = 2.0 / 27.0;
        double alphaSignal = 2.0 / 10.0;

        double ema12 = closes.get(0).doubleValue();
        double ema26 = closes.get(0).doubleValue();
        List<Double> difs = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            double c = closes.get(i).doubleValue();
            if (i == 0) {
                difs.add(0.0);
                continue;
            }
            ema12 = alphaFast * c + (1 - alphaFast) * ema12;
            ema26 = alphaSlow * c + (1 - alphaSlow) * ema26;
            difs.add(ema12 - ema26);
        }

        double dea9 = difs.get(0);
        List<Double> deas = new ArrayList<>();
        for (int i = 1; i < difs.size(); i++) {
            if (i == 1) {
                deas.add(dea9);
                continue;
            }
            dea9 = alphaSignal * difs.get(i) + (1 - alphaSignal) * dea9;
            deas.add(dea9);
        }

        int last = difs.size() - 1;
        double dif = difs.get(last);
        double dea = deas.size() > 0 ? deas.get(deas.size() - 1) : 0;
        double prevDif = difs.get(Math.max(1, last - 1));
        double prevDea = deas.size() > 1 ? deas.get(deas.size() - 2) : 0;

        boolean aboveZero = dif > 0;
        boolean goldenCross = prevDif <= prevDea && dif > dea;
        boolean deadCross = prevDif >= prevDea && dif < dea;

        StringBuilder sb = new StringBuilder();
        if (goldenCross) sb.append("金叉");
        else if (deadCross) sb.append("死叉");
        else if (dif > dea) sb.append("DIF在DEA上方");
        else sb.append("DIF在DEA下方");

        sb.append(aboveZero ? "，零轴上方" : "，零轴下方");

        double histogram = (dif - dea) * 2;
        double prevHist = (prevDif - prevDea) * 2;
        sb.append(histogram >= prevHist ? "，柱状图放大" : "，柱状图缩小");

        return sb.toString();
    }

    private VolumeData computeVolumeData(String normSymbol) {
        VolumeData vd = new VolumeData();
        KlineParam kp = KlineParam.builder()
                .symbol(normSymbol)
                .klineInterval(CandlestickIntervalEnum.OKXMIN15)
                .size(20)
                .build();
        List<Candlestick> klines = candlestickService.getLastKlines(kp);
        if (klines == null || klines.isEmpty()) return vd;

        BigDecimal avgVol = BigDecimal.ZERO;
        for (Candlestick c : klines) {
            avgVol = avgVol.add(nvl(c.getVolume()));
        }
        avgVol = avgVol.divide(BigDecimal.valueOf(klines.size()), 8, RoundingMode.HALF_UP);
        vd.avgVolume20 = avgVol.setScale(2, RoundingMode.HALF_UP).toString();

        BigDecimal currentVol = nvl(klines.get(klines.size() - 1).getVolume());
        vd.currentVolume = currentVol.setScale(2, RoundingMode.HALF_UP).toString();

        if (avgVol.compareTo(BigDecimal.ZERO) > 0) {
            vd.volumeRatio = currentVol.divide(avgVol, 2, RoundingMode.HALF_UP).toString();
        }

        int start = Math.max(0, klines.size() - 5);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < klines.size(); i++) {
            Candlestick c = klines.get(i);
            String time = formatCandleTime(c.getTimeStr());
            sb.append("[").append(time).append("]")
                    .append(" O:").append(nvl(c.getOpenPrice()).setScale(2, RoundingMode.HALF_UP))
                    .append(" H:").append(nvl(c.getHighPrice()).setScale(2, RoundingMode.HALF_UP))
                    .append(" L:").append(nvl(c.getLowPrice()).setScale(2, RoundingMode.HALF_UP))
                    .append(" C:").append(nvl(c.getClosePrice()).setScale(2, RoundingMode.HALF_UP))
                    .append(" V:").append(nvl(c.getVolume()).setScale(0, RoundingMode.HALF_UP));
            if (i < klines.size() - 1) sb.append("\n");
        }
        vd.recentCandles = sb.toString();

        return vd;
    }

    private String formatCandleTime(String timeStr) {
        try {
            long epochMs = Long.parseLong(timeStr);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                    .format(CANDLE_TIME_FMT);
        } catch (Exception e) {
            return timeStr;
        }
    }

    private String formatLevels(List<BigDecimal> levels) {
        if (levels == null || levels.isEmpty()) return "";
        return levels.stream()
                .map(v -> v.setScale(2, RoundingMode.HALF_UP).toString())
                .collect(Collectors.joining(", "));
    }

    private static String safeStr(String val) {
        return val == null ? "" : val;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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

    public static class MarketData {
        private String symbol;
        private String signalDirection;
        private BigDecimal signalStrength;
        private String signalTime;
        private String weeklyTrend;
        private String trend4h;
        private String rsi4h;
        private String bbPosition4h;
        private String trend1h;
        private String rsi1h;
        private String macdStatus1h;
        private String latestPrice;
        private String rsi15m;
        private String atr15m;
        private String avgVolume20;
        private String currentVolume;
        private String volumeRatio;
        private String resistanceLevels;
        private String supportLevels;
        private String distanceToResistance;
        private String distanceToSupport;
        private String recentCandles;

        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public String getSignalDirection() { return signalDirection; }
        public void setSignalDirection(String signalDirection) { this.signalDirection = signalDirection; }
        public BigDecimal getSignalStrength() { return signalStrength; }
        public void setSignalStrength(BigDecimal signalStrength) { this.signalStrength = signalStrength; }
        public String getSignalTime() { return signalTime; }
        public void setSignalTime(String signalTime) { this.signalTime = signalTime; }
        public String getWeeklyTrend() { return weeklyTrend; }
        public void setWeeklyTrend(String weeklyTrend) { this.weeklyTrend = weeklyTrend; }
        public String getTrend4h() { return trend4h; }
        public void setTrend4h(String trend4h) { this.trend4h = trend4h; }
        public String getRsi4h() { return rsi4h; }
        public void setRsi4h(String rsi4h) { this.rsi4h = rsi4h; }
        public String getBbPosition4h() { return bbPosition4h; }
        public void setBbPosition4h(String bbPosition4h) { this.bbPosition4h = bbPosition4h; }
        public String getTrend1h() { return trend1h; }
        public void setTrend1h(String trend1h) { this.trend1h = trend1h; }
        public String getRsi1h() { return rsi1h; }
        public void setRsi1h(String rsi1h) { this.rsi1h = rsi1h; }
        public String getMacdStatus1h() { return macdStatus1h; }
        public void setMacdStatus1h(String macdStatus1h) { this.macdStatus1h = macdStatus1h; }
        public String getLatestPrice() { return latestPrice; }
        public void setLatestPrice(String latestPrice) { this.latestPrice = latestPrice; }
        public String getRsi15m() { return rsi15m; }
        public void setRsi15m(String rsi15m) { this.rsi15m = rsi15m; }
        public String getAtr15m() { return atr15m; }
        public void setAtr15m(String atr15m) { this.atr15m = atr15m; }
        public String getAvgVolume20() { return avgVolume20; }
        public void setAvgVolume20(String avgVolume20) { this.avgVolume20 = avgVolume20; }
        public String getCurrentVolume() { return currentVolume; }
        public void setCurrentVolume(String currentVolume) { this.currentVolume = currentVolume; }
        public String getVolumeRatio() { return volumeRatio; }
        public void setVolumeRatio(String volumeRatio) { this.volumeRatio = volumeRatio; }
        public String getResistanceLevels() { return resistanceLevels; }
        public void setResistanceLevels(String resistanceLevels) { this.resistanceLevels = resistanceLevels; }
        public String getSupportLevels() { return supportLevels; }
        public void setSupportLevels(String supportLevels) { this.supportLevels = supportLevels; }
        public String getDistanceToResistance() { return distanceToResistance; }
        public void setDistanceToResistance(String distanceToResistance) { this.distanceToResistance = distanceToResistance; }
        public String getDistanceToSupport() { return distanceToSupport; }
        public void setDistanceToSupport(String distanceToSupport) { this.distanceToSupport = distanceToSupport; }
        public String getRecentCandles() { return recentCandles; }
        public void setRecentCandles(String recentCandles) { this.recentCandles = recentCandles; }
    }

    private static class VolumeData {
        String avgVolume20 = "";
        String currentVolume = "";
        String volumeRatio = "";
        String recentCandles = "";
    }
}
