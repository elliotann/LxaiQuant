package com.chain.ai.trade.engine.service.ml;

import com.chain.ai.trade.engine.service.ml.factor.Node;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

import java.util.*;
import java.util.function.BiFunction;

public class FeatureFactory {

    public static List<DynamicFeature> createAllVariants(BarSeries series,
                                                          Map<String, List<?>> selectedPool) {
        List<DynamicFeature> variants = new ArrayList<>();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        for (Map.Entry<String, List<?>> entry : selectedPool.entrySet()) {
            String type = entry.getKey();
            List<?> paramsList = entry.getValue();
            if (paramsList == null || paramsList.isEmpty()) continue;

            if (paramsList.get(0) instanceof Number) {
                int paramCount = getExpectedParamCount(type);
                if (paramCount <= 0) continue;
                List<Number> buffer = new ArrayList<>();
                for (Object raw : paramsList) {
                    if (raw instanceof Number) {
                        buffer.add((Number) raw);
                        if (buffer.size() == paramCount) {
                            DynamicFeature f = create(series, closePrice, type, new ArrayList<>(buffer));
                            if (f != null) variants.add(f);
                            buffer.clear();
                        }
                    }
                }
            } else {
                for (Object raw : paramsList) {
                    DynamicFeature f = create(series, closePrice, type, raw);
                    if (f != null) variants.add(f);
                }
            }
        }
        return variants;
    }

    private static int getExpectedParamCount(String type) {
        switch (type.toUpperCase()) {
            case "RSI":
            case "ATR":
            case "PRICECHANGE":
            case "STOCHASTICK":
            case "BOLLINGERB":
            case "LINEARREGSLOPE":
                return 1;
            case "EMADIFF":
            case "VOLUMERATIO":
                return 2;
            case "MACD":
            case "MACD_SIGNAL":
            case "MACDSIGNAL":
                return 3;
            default:
                return 0;
        }
    }

    public static DynamicFeature create(BarSeries series, ClosePriceIndicator closePrice,
                                         String type, Object params) {
        switch (type.toUpperCase()) {
            case "RSI":
                return createRsi(series, closePrice, toInt(params));
            case "MACD":
                return createMacd(series, closePrice, toIntArray(params));
            case "MACD_SIGNAL":
            case "MACDSIGNAL":
                return createMacdSignal(series, closePrice, toIntArray(params));
            case "EMADIFF":
                return createEmaDiff(series, closePrice, toIntArray(params));
            case "ATR":
                return createAtr(series, closePrice, toInt(params));
            case "VOLUMERATIO":
                return createVolumeRatio(series, toIntArray(params));
            case "PRICECHANGE":
                return createPriceChange(series, closePrice, toInt(params));
            case "STOCHASTICK":
                return createStochasticK(series, closePrice, toInt(params));
            case "BOLLINGERB":
                return createBollingerB(series, closePrice, toInt(params));
            case "LINEARREGSLOPE":
                return createLinearRegSlope(series, closePrice, toInt(params));
            default:
                return null;
        }
    }

    private static DynamicFeature createRsi(BarSeries series, ClosePriceIndicator closePrice, int period) {
        RSIIndicator rsi = new RSIIndicator(closePrice, period);
        return named("RSI_" + period, i -> {
            double v = rsi.getValue(i).doubleValue();
            return Double.isFinite(v) ? v : 50;
        });
    }

    private static DynamicFeature createMacd(BarSeries series, ClosePriceIndicator closePrice, int[] p) {
        int fast = p[0], slow = p[1], signal = p[2];
        MACDIndicator macd = new MACDIndicator(closePrice, fast, slow);
        return named("MACD_" + fast + "_" + slow + "_" + signal, i -> {
            double v = macd.getValue(i).doubleValue();
            return Double.isFinite(v) ? v : 0;
        });
    }

    private static DynamicFeature createMacdSignal(BarSeries series, ClosePriceIndicator closePrice, int[] p) {
        int fast = p[0], slow = p[1], signal = p[2];
        MACDIndicator macd = new MACDIndicator(closePrice, fast, slow);
        EMAIndicator macdSignal = new EMAIndicator(macd, signal);
        return named("MACD_Signal_" + fast + "_" + slow + "_" + signal, i -> {
            double v = macdSignal.getValue(i).doubleValue();
            return Double.isFinite(v) ? v : 0;
        });
    }

    private static DynamicFeature createEmaDiff(BarSeries series, ClosePriceIndicator closePrice, int[] p) {
        int fast = p[0], slow = p[1];
        EMAIndicator emaFast = new EMAIndicator(closePrice, fast);
        EMAIndicator emaSlow = new EMAIndicator(closePrice, slow);
        return named("EMADiff_" + fast + "_" + slow, i -> {
            double sv = emaSlow.getValue(i).doubleValue();
            if (sv == 0) return 0;
            double v = (emaFast.getValue(i).doubleValue() - sv) / sv * 100;
            return Double.isFinite(v) ? v : 0;
        });
    }

    private static DynamicFeature createAtr(BarSeries series, ClosePriceIndicator closePrice, int period) {
        org.ta4j.core.indicators.helpers.TRIndicator tr = new org.ta4j.core.indicators.helpers.TRIndicator(series);
        SMAIndicator atr = new SMAIndicator(tr, period);
        return named("ATR_" + period, i -> {
            double cp = closePrice.getValue(i).doubleValue();
            if (cp == 0) return 0;
            double v = atr.getValue(i).doubleValue() / cp;
            return Double.isFinite(v) ? v : 0;
        });
    }

    private static DynamicFeature createVolumeRatio(BarSeries series, int[] p) {
        int shortPeriod = p[0], longPeriod = p[1];
        org.ta4j.core.indicators.helpers.VolumeIndicator vol = new org.ta4j.core.indicators.helpers.VolumeIndicator(series);
        SMAIndicator volShort = new SMAIndicator(vol, shortPeriod);
        SMAIndicator volLong = new SMAIndicator(vol, longPeriod);
        return named("VolumeRatio_" + shortPeriod + "_" + longPeriod, i -> {
            double lv = volLong.getValue(i).doubleValue();
            if (lv == 0) return 1;
            double v = volShort.getValue(i).doubleValue() / lv;
            return Double.isFinite(v) ? v : 1;
        });
    }

    private static DynamicFeature createPriceChange(BarSeries series, ClosePriceIndicator closePrice, int bars) {
        return named("PriceChange_" + bars, i -> {
            if (i < bars) return 0;
            double prev = closePrice.getValue(i - bars).doubleValue();
            if (prev == 0) return 0;
            double v = (closePrice.getValue(i).doubleValue() - prev) / prev * 100;
            return Double.isFinite(v) ? v : 0;
        });
    }

    private static DynamicFeature createStochasticK(BarSeries series, ClosePriceIndicator closePrice, int period) {
        org.ta4j.core.indicators.StochasticOscillatorKIndicator stochK =
                new org.ta4j.core.indicators.StochasticOscillatorKIndicator(series, period);
        return named("StochasticK_" + period, i -> {
            double v = stochK.getValue(i).doubleValue();
            return Double.isFinite(v) ? v : 50;
        });
    }

    private static DynamicFeature createBollingerB(BarSeries series, ClosePriceIndicator closePrice, int period) {
        EMAIndicator ema = new EMAIndicator(closePrice, period);
        StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, period);
        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(ema);
        BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(middle, sd);
        BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(middle, sd);
        return named("BollingerB_" + period, i -> {
            double u = upper.getValue(i).doubleValue();
            double l = lower.getValue(i).doubleValue();
            if (u - l == 0) return 0.5;
            double v = (closePrice.getValue(i).doubleValue() - l) / (u - l);
            return Double.isFinite(v) ? v : 0.5;
        });
    }

    private static DynamicFeature createLinearRegSlope(BarSeries series, ClosePriceIndicator closePrice, int bars) {
        SimpleLinearRegressionIndicator lr = new SimpleLinearRegressionIndicator(closePrice, bars,
                SimpleLinearRegressionIndicator.SimpleLinearRegressionType.SLOPE);
        return named("LinearRegSlope_" + bars, i -> {
            double v = lr.getValue(i).doubleValue();
            return Double.isFinite(v) ? v : 0;
        });
    }

    private static DynamicFeature named(String name, java.util.function.IntToDoubleFunction fn) {
        return new DynamicFeature() {
            @Override public String getVariantName() { return name; }
            @Override public double extract(int index) { return fn.applyAsDouble(index); }
        };
    }

    private static int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof List) return ((Number) ((List<?>) o).get(0)).intValue();
        return Integer.parseInt(o.toString());
    }

    @SuppressWarnings("unchecked")
    private static int[] toIntArray(Object o) {
        if (o instanceof List) {
            List<?> list = (List<?>) o;
            int[] result = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = ((Number) list.get(i)).intValue();
            }
            return result;
        }
        if (o instanceof int[]) return (int[]) o;
        if (o instanceof Number) return new int[]{((Number) o).intValue()};
        String[] parts = o.toString().replaceAll("[\\[\\]]", "").split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Integer.parseInt(parts[i].trim());
        return result;
    }

    public static DynamicFeature createFromExpression(String name, Node expression,
                                                       Map<String, double[]> data) {
        return named(name, i -> {
            double v = expression.eval(i, data);
            return Double.isFinite(v) ? v : 0;
        });
    }
}