package com.chain.ai.trade.engine.signal.service.support;

import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.entity.dto.WeightAndConfidenceDto;
import com.chain.ai.trade.engine.signal.service.DefaultSignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.chain.ai.trade.common.entity.constants.SignalType.*;

/**
 * Range Filter 信号服务
 * 实现 Range Filter Buy and Sell 5min 策略逻辑
 * 参考: docs/script/Range Filter Buy and Sell 5min.txt
 */
@Slf4j
@Service
public class RangeFilterSignService extends DefaultSignService {

    private static final String SERVICE_KEY = "RangeFilterSignService";

    // Sampling Period (默认100，适用于5min BTCUSDC)
    @Value("${strategy.range-filter.sampling-period:100}")
    private int samplingPeriod;

    // Range Multiplier (默认3.0)
    @Value("${strategy.range-filter.range-multiplier:3.0}")
    private double rangeMultiplier;

    @Override
    public BuyAndSellWeightDto execute(IndicatorCalcDto calcDto) {
        applyConfiguredParams();
        applyOverrideParams(calcDto.getParameterOverrides());

        List<Candlestick> kLines = calcDto.getKLines();
        String symbol = calcDto.getSymbol();

        int minRequiredBars = samplingPeriod * 2 + 10;
        if (kLines.size() < minRequiredBars) {
            log.debug("交易对: {}, Range Filter 数据不足，需要至少 {} 根K线，当前 {}",
                    symbol, minRequiredBars, kLines.size());
            return new BuyAndSellWeightDto();
        }

        RangeFilterSignal signal = analyzeRangeFilterSignal(kLines, symbol);
        BuyAndSellWeightDto result = new BuyAndSellWeightDto();

        if (signal.isBuySignal()) {
            Candlestick currentCandle = kLines.get(kLines.size() - 1);
            log.info("交易对: {}, K线时间: {}, Range Filter 买入信号确认, 触发价格: {}",
                    symbol, DateUtil.longConvertDateTime(currentCandle.getId()),
                    String.format("%.4f", signal.getTriggerPrice()));
            result.setSignalType(LONG);
            saveLastSignalToRedis(symbol, "BUY");
        } else if (signal.isSellSignal()) {
            Candlestick currentCandle = kLines.get(kLines.size() - 1);
            log.info("交易对: {}, K线时间: {}, Range Filter 卖出信号确认, 触发价格: {}",
                    symbol, DateUtil.longConvertDateTime(currentCandle.getId()),
                    String.format("%.4f", signal.getTriggerPrice()));
            result.setSignalType(SHORT);
            saveLastSignalToRedis(symbol, "SELL");
        } else {
            Candlestick currentCandle = kLines.get(kLines.size() - 1);
            log.debug("交易对: {}, K线时间: {}, Range Filter 无信号",
                    symbol, DateUtil.longConvertDateTime(currentCandle.getId()));
        }

        if (result.getSignalType() != null) {
            Long signalId = saveSign(calcDto, result.getSignalType());
            result.setSignalId(signalId);
        }
        return result;
    }

    @Override
    public Double getWeight(IndicatorCalcDto calcDto) {
        WeightAndConfidenceDto result = getWeightAndConfidence(calcDto);
        return result.getWeight().doubleValue();
    }

    /**
     * Range Filter 信号分析核心逻辑
     * 对应 Pine Script:
     *   smoothrng → smrng
     *   rngfilt → filt
     *   upward/downward → 方向计数
     *   longCond/shortCond → 多空条件
     *   CondIni → 状态机
     *   longCondition/shortCondition → 最终信号
     */
    private RangeFilterSignal analyzeRangeFilterSignal(List<Candlestick> kLines, String symbol) {
        try {
            int n = kLines.size();
            double[] close = new double[n];
            for (int i = 0; i < n; i++) {
                close[i] = kLines.get(i).getClosePrice().doubleValue();
            }

            // 1. 计算平滑范围 smrng = smoothrng(src, per, mult)
            double[] smrng = calculateSmoothRange(close, samplingPeriod, rangeMultiplier);

            // 2. 计算 Range Filter (filt)
            double[] filt = new double[n];
            filt[0] = close[0];
            for (int i = 1; i < n; i++) {
                double r = smrng[i];
                double prevFilt = filt[i - 1];
                if (close[i] > prevFilt) {
                    double candidate = close[i] - r;
                    filt[i] = candidate < prevFilt ? prevFilt : candidate;
                } else {
                    double candidate = close[i] + r;
                    filt[i] = candidate > prevFilt ? prevFilt : candidate;
                }
            }

            // 3. 计算 upward / downward 方向计数
            int[] upward = new int[n];
            int[] downward = new int[n];
            upward[0] = 0;
            downward[0] = 0;
            for (int i = 1; i < n; i++) {
                if (filt[i] > filt[i - 1]) {
                    upward[i] = upward[i - 1] + 1;
                    downward[i] = 0;
                } else if (filt[i] < filt[i - 1]) {
                    upward[i] = 0;
                    downward[i] = downward[i - 1] + 1;
                } else {
                    upward[i] = upward[i - 1];
                    downward[i] = downward[i - 1];
                }
            }

            // 4. 计算多空条件 (longCond / shortCond)
            // longCond = src > filt and src > src[1] and upward > 0 or src > filt and src < src[1] and upward > 0
            // shortCond = src < filt and src < src[1] and downward > 0 or src < filt and src > src[1] and downward > 0
            boolean[] longCond = new boolean[n];
            boolean[] shortCond = new boolean[n];
            for (int i = 0; i < n; i++) {
                boolean srcGtFilt = close[i] > filt[i];
                boolean srcLtFilt = close[i] < filt[i];
                boolean srcGtPrev = i > 0 && close[i] > close[i - 1];
                boolean srcLtPrev = i > 0 && close[i] < close[i - 1];
                longCond[i] = srcGtFilt && upward[i] > 0;
                shortCond[i] = srcLtFilt && downward[i] > 0;
            }

            // 5. 状态机 CondIni 和最终信号
            // CondIni = longCond ? 1 : shortCond ? -1 : CondIni[1]
            // longCondition = longCond and CondIni[1] == -1
            // shortCondition = shortCond and CondIni[1] == 1
            int[] condIni = new int[n];
            condIni[0] = 0;
            boolean[] buySignal = new boolean[n];
            boolean[] sellSignal = new boolean[n];

            for (int i = 1; i < n; i++) {
                if (longCond[i]) {
                    condIni[i] = 1;
                } else if (shortCond[i]) {
                    condIni[i] = -1;
                } else {
                    condIni[i] = condIni[i - 1];
                }

                buySignal[i] = longCond[i] && condIni[i - 1] == -1;
                sellSignal[i] = shortCond[i] && condIni[i - 1] == 1;
            }

            int lastIdx = n - 1;
            double triggerPrice = close[lastIdx];

            if (buySignal[lastIdx]) {
                log.info("交易对: {}, Range Filter 买入信号 - 价格: {}, filt: {}, upward: {}",
                        symbol, triggerPrice, filt[lastIdx], upward[lastIdx]);
                return new RangeFilterSignal("BUY", triggerPrice);
            } else if (sellSignal[lastIdx]) {
                log.info("交易对: {}, Range Filter 卖出信号 - 价格: {}, filt: {}, downward: {}",
                        symbol, triggerPrice, filt[lastIdx], downward[lastIdx]);
                return new RangeFilterSignal("SELL", triggerPrice);
            }

            return new RangeFilterSignal("HOLD", 0.0);

        } catch (Exception e) {
            log.error("交易对: {}, Range Filter 信号分析失败: {}", symbol, e.getMessage(), e);
            return new RangeFilterSignal("HOLD", 0.0);
        }
    }

    /**
     * 计算平滑范围 (smrng)
     * Pine Script: smoothrng(x, t, m) =>
     *   wper = t * 2 - 1
     *   avrng = ta.ema(math.abs(x - x[1]), t)
     *   smoothrng = ta.ema(avrng, wper) * m
     */
    private double[] calculateSmoothRange(double[] close, int per, double mult) {
        int n = close.length;
        double[] absDiff = new double[n];
        absDiff[0] = 0.0;
        for (int i = 1; i < n; i++) {
            absDiff[i] = Math.abs(close[i] - close[i - 1]);
        }

        double[] avrng = ema(absDiff, per);
        int wper = 2 * per - 1;
        double[] smrngRaw = ema(avrng, wper);

        double[] smrng = new double[n];
        for (int i = 0; i < n; i++) {
            smrng[i] = smrngRaw[i] * mult;
        }
        return smrng;
    }

    /**
     * 计算指数移动平均 (EMA)
     * alpha = 2 / (period + 1)
     * EMA[i] = alpha * value[i] + (1 - alpha) * EMA[i-1]
     */
    private double[] ema(double[] values, int period) {
        int n = values.length;
        double[] emaValues = new double[n];
        if (n == 0) return emaValues;

        double alpha = 2.0 / (period + 1);
        emaValues[0] = values[0];
        for (int i = 1; i < n; i++) {
            emaValues[i] = alpha * values[i] + (1 - alpha) * emaValues[i - 1];
        }
        return emaValues;
    }

    /**
     * 保存最近信号到 Redis (占位)
     */
    private void saveLastSignalToRedis(String symbol, String signal) {
        log.debug("保存信号到 Redis: symbol={}, signal={}", symbol, signal);
    }

    // ==================== 配置动态更新 ====================

    private void applyConfiguredParams() {
        if (signalServiceConfigService == null) {
            return;
        }
        Map<String, Object> params = signalServiceConfigService.getParams(SERVICE_KEY);
        if (params == null || params.isEmpty()) {
            return;
        }
        samplingPeriod = getInt(params, "samplingPeriod", samplingPeriod);
        rangeMultiplier = getDouble(params, "rangeMultiplier", rangeMultiplier);
    }

    private void applyOverrideParams(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        samplingPeriod = getIntOverride(overrides, "samplingPeriod", samplingPeriod);
        rangeMultiplier = getDoubleOverride(overrides, "rangeMultiplier", rangeMultiplier);
    }

    private int getIntOverride(Map<String, String> overrides, String key, int defaultValue) {
        String value = overrides.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double getDoubleOverride(Map<String, String> overrides, String key, double defaultValue) {
        String value = overrides.get(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean getBooleanOverride(Map<String, String> overrides, String key, boolean defaultValue) {
        String value = overrides.get(key);
        if (value == null) return defaultValue;
        String text = value.trim().toLowerCase();
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) return true;
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) return false;
        return defaultValue;
    }

    private int getInt(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double getDouble(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean getBoolean(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        String text = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) return true;
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) return false;
        return defaultValue;
    }

    // ==================== 内部信号包装类 ====================

    private static class RangeFilterSignal {
        private final String signalType;
        private final double triggerPrice;

        public RangeFilterSignal(String signalType, double triggerPrice) {
            this.signalType = signalType;
            this.triggerPrice = triggerPrice;
        }

        public boolean isBuySignal() {
            return "BUY".equals(signalType);
        }

        public boolean isSellSignal() {
            return "SELL".equals(signalType);
        }

        public boolean isHold() {
            return "HOLD".equals(signalType);
        }

        public double getTriggerPrice() {
            return triggerPrice;
        }

        public String getSignalType() {
            return signalType;
        }
    }
}
