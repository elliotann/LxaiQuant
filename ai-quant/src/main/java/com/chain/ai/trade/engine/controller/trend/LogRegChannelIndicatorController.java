package com.chain.ai.trade.engine.controller.trend;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/trading/trend/logreg-channel")
@RequiredArgsConstructor
public class LogRegChannelIndicatorController {

    private final ICandlestickService candlestickService;

    @GetMapping
    public ApiResponse<Map<String, Object>> getLogRegChannel(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam(required = false, defaultValue = "200") Integer length,
            @RequestParam(required = false, defaultValue = "2.0") Double multiplier,
            @RequestParam(required = false, defaultValue = "120") Integer points
    ) {
        try {
            int safeLength = length == null ? 200 : Math.max(20, Math.min(length, 2000));
            double safeMultiplier = multiplier == null ? 2.0 : Math.max(0.1, Math.min(multiplier, 10.0));
            int safePoints = points == null ? 120 : Math.max(20, Math.min(points, safeLength));

            CandlestickIntervalEnum intervalEnum = parseInterval(interval);
            if (intervalEnum == null) {
                return ApiResponse.error(400, "无效的interval参数");
            }

            List<Candlestick> klines = candlestickService.getLastKlines(
                    KlineParam.builder()
                            .symbol(symbol)
                            .klineInterval(intervalEnum)
                            .size(safeLength)
                            .build()
            );
            if (klines == null || klines.size() < 20) {
                return ApiResponse.error(404, "无足够本地K线数据，无法计算LogReg通道");
            }

            klines.sort(Comparator.comparingLong(Candlestick::getId));
            int n = klines.size();

            double[] x = new double[n];
            double[] y = new double[n];
            for (int i = 0; i < n; i++) {
                x[i] = i;
                double close = toDouble(klines.get(i).getClosePrice());
                if (!(close > 0)) {
                    return ApiResponse.error(400, "K线收盘价存在非正数，无法进行对数回归");
                }
                y[i] = Math.log(close);
            }

            RegressionResult reg = linearRegression(x, y);
            if (!Double.isFinite(reg.slope) || !Double.isFinite(reg.intercept)) {
                return ApiResponse.error(500, "回归计算失败");
            }

            double[] yHat = new double[n];
            double[] resid = new double[n];
            for (int i = 0; i < n; i++) {
                yHat[i] = reg.intercept + reg.slope * x[i];
                resid[i] = y[i] - yHat[i];
            }
            double residStd = stddev(resid);

            int fromIndex = Math.max(0, n - safePoints);
            List<Map<String, Object>> series = new ArrayList<>();
            for (int i = fromIndex; i < n; i++) {
                double mid = Math.exp(yHat[i]);
                double up = Math.exp(yHat[i] + safeMultiplier * residStd);
                double lo = Math.exp(yHat[i] - safeMultiplier * residStd);
                Candlestick k = klines.get(i);
                Map<String, Object> p = new HashMap<>();
                p.put("timestamp", k.getId());
                p.put("middle", mid);
                p.put("upper", up);
                p.put("lower", lo);
                series.add(p);
            }

            double lastMiddle = Math.exp(yHat[n - 1]);
            double lastUpper = Math.exp(yHat[n - 1] + safeMultiplier * residStd);
            double lastLower = Math.exp(yHat[n - 1] - safeMultiplier * residStd);
            double lastClose = toDouble(klines.get(n - 1).getClosePrice());

            Map<String, Object> out = new HashMap<>();
            out.put("symbol", symbol);
            out.put("interval", intervalEnum.name());
            out.put("length", n);
            out.put("multiplier", safeMultiplier);
            out.put("slope", reg.slope);
            out.put("intercept", reg.intercept);
            out.put("r2", reg.r2);
            out.put("residualStd", residStd);
            out.put("lastTimestamp", klines.get(n - 1).getId());
            out.put("lastClose", lastClose);
            out.put("lastMiddle", lastMiddle);
            out.put("lastUpper", lastUpper);
            out.put("lastLower", lastLower);
            out.put("series", series);

            out.put("summary", buildSummary(symbol, intervalEnum, reg, lastClose, lastMiddle, lastUpper, lastLower));

            return ApiResponse.success("OK", out);
        } catch (Exception e) {
            log.error("LogReg通道计算失败: symbol={}, interval={}", symbol, interval, e);
            return ApiResponse.error("LogReg通道计算失败: " + e.getMessage());
        }
    }

    private String buildSummary(
            String symbol,
            CandlestickIntervalEnum interval,
            RegressionResult reg,
            double lastClose,
            double lastMiddle,
            double lastUpper,
            double lastLower
    ) {
        String direction;
        if (reg.slope > 1e-6) {
            direction = "上行";
        } else if (reg.slope < -1e-6) {
            direction = "下行";
        } else {
            direction = "震荡";
        }
        String pos;
        if (lastClose >= lastUpper) {
            pos = "触及/突破上轨";
        } else if (lastClose <= lastLower) {
            pos = "触及/跌破下轨";
        } else if (lastClose >= lastMiddle) {
            pos = "位于中轨上方";
        } else {
            pos = "位于中轨下方";
        }
        return "LogReg通道(" + interval.name() + ") " + symbol + "：趋势" + direction
                + "，" + pos
                + "；中轨=" + format2(lastMiddle)
                + " 上轨=" + format2(lastUpper)
                + " 下轨=" + format2(lastLower)
                + "，R²=" + format4(reg.r2);
    }

    private String format2(double v) {
        if (!Double.isFinite(v)) {
            return "-";
        }
        return String.format("%.2f", v);
    }

    private String format4(double v) {
        if (!Double.isFinite(v)) {
            return "-";
        }
        return String.format("%.4f", v);
    }

    private double toDouble(BigDecimal v) {
        if (v == null) {
            return Double.NaN;
        }
        return v.doubleValue();
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

    private RegressionResult linearRegression(double[] x, double[] y) {
        int n = x.length;
        double sumX = 0;
        double sumY = 0;
        double sumXX = 0;
        double sumXY = 0;
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXX += x[i] * x[i];
            sumXY += x[i] * y[i];
        }
        double denom = n * sumXX - sumX * sumX;
        if (denom == 0) {
            return new RegressionResult(Double.NaN, Double.NaN, Double.NaN);
        }
        double slope = (n * sumXY - sumX * sumY) / denom;
        double intercept = (sumY - slope * sumX) / n;

        double ssTot = 0;
        double ssRes = 0;
        double yMean = sumY / n;
        for (int i = 0; i < n; i++) {
            double yHat = intercept + slope * x[i];
            double diff = y[i] - yHat;
            ssRes += diff * diff;
            double dTot = y[i] - yMean;
            ssTot += dTot * dTot;
        }
        double r2 = ssTot == 0 ? 1.0 : 1.0 - (ssRes / ssTot);
        return new RegressionResult(slope, intercept, r2);
    }

    private double stddev(double[] v) {
        int n = v.length;
        if (n == 0) return Double.NaN;
        double sum = 0;
        for (double x : v) {
            sum += x;
        }
        double mean = sum / n;
        double var = 0;
        for (double x : v) {
            double d = x - mean;
            var += d * d;
        }
        return Math.sqrt(var / n);
    }

    private record RegressionResult(double slope, double intercept, double r2) {
    }
}

