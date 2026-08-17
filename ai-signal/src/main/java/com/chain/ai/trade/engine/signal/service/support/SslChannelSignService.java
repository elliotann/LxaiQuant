package com.chain.ai.trade.engine.signal.service.support;

import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.risk.adjuster.impl.QualityBasedAdjuster;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.entity.dto.WeightAndConfidenceDto;
import com.chain.ai.trade.engine.signal.service.DefaultSignService;
import com.chain.ai.trade.common.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.SMMAIndicator;
import org.ta4j.core.indicators.averages.WMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * SSL Channel 信号生成服务
 * 对应 TradingView Pine Script: "SSL Channel"
 * 支持双通道独立配置，可灵活合并信号
 */
@Slf4j
@Service
public class SslChannelSignService extends DefaultSignService {

    private static final String SERVICE_KEY = "SslChannelSignService";

    // ==================== 通道 1 配置 ====================
    @Value("${strategy.ssl-channel.channel1.show-ma1:true}")
    private boolean showMa1;
    @Value("${strategy.ssl-channel.channel1.ma1-type:EMA}")
    private String ma1Type;
    @Value("${strategy.ssl-channel.channel1.ma1-source:high}")
    private String ma1Source;
    @Value("${strategy.ssl-channel.channel1.ma1-length:200}")
    private int ma1Length;
    @Value("${strategy.ssl-channel.channel1.ma1-color:green}")
    private String ma1Color;

    @Value("${strategy.ssl-channel.channel1.show-ma2:true}")
    private boolean showMa2;
    @Value("${strategy.ssl-channel.channel1.ma2-type:EMA}")
    private String ma2Type;
    @Value("${strategy.ssl-channel.channel1.ma2-source:low}")
    private String ma2Source;
    @Value("${strategy.ssl-channel.channel1.ma2-length:200}")
    private int ma2Length;
    @Value("${strategy.ssl-channel.channel1.ma2-color:red}")
    private String ma2Color;

    @Value("${strategy.ssl-channel.channel1.show-labels:true}")
    private boolean showLabels1;

    // ==================== 通道 2 配置 ====================
    @Value("${strategy.ssl-channel.channel2.enabled:false}")
    private boolean channel2Enabled;
    @Value("${strategy.ssl-channel.channel2.show-ma3:false}")
    private boolean showMa3;
    @Value("${strategy.ssl-channel.channel2.ma3-type:SMA}")
    private String ma3Type;
    @Value("${strategy.ssl-channel.channel2.ma3-source:high}")
    private String ma3Source;
    @Value("${strategy.ssl-channel.channel2.ma3-length:20}")
    private int ma3Length;
    @Value("${strategy.ssl-channel.channel2.ma3-color:orange}")
    private String ma3Color;

    @Value("${strategy.ssl-channel.channel2.show-ma4:false}")
    private boolean showMa4;
    @Value("${strategy.ssl-channel.channel2.ma4-type:SMA}")
    private String ma4Type;
    @Value("${strategy.ssl-channel.channel2.ma4-source:low}")
    private String ma4Source;
    @Value("${strategy.ssl-channel.channel2.ma4-length:20}")
    private int ma4Length;
    @Value("${strategy.ssl-channel.channel2.ma4-color:blue}")
    private String ma4Color;

    @Value("${strategy.ssl-channel.channel2.show-labels:true}")
    private boolean showLabels2;

    // ==================== 全局参数 ====================
    @Value("${strategy.ssl-channel.wicks:false}")
    private boolean wicks;

    @Value("${strategy.ssl-channel.highlight-state:true}")
    private boolean highlightState;

    @Value("${strategy.ssl-channel.combine-mode:any}")
    private String combineMode;

    // ==================== 风险模块配置 ====================
    @Value("${strategy.ssl-channel.use-risk-module:false}")
    private boolean useRiskModule;

    @Value("${strategy.ssl-channel.risk-module-evaluators:}")
    private String riskModuleEvaluators;

    @Autowired(required = false)
    @Lazy
    private QualityBasedAdjuster qualityBasedAdjuster;

    // ==================== 内部信号类 ====================
    private static class SslChannelSignal {
        private final String signalType;   // "BUY", "SELL", "HOLD"
        private final double triggerPrice;
        private final int channel;

        public SslChannelSignal(String signalType, double triggerPrice, int channel) {
            this.signalType = signalType;
            this.triggerPrice = triggerPrice;
            this.channel = channel;
        }

        public boolean isBuy() { return "BUY".equals(signalType); }
        public boolean isSell() { return "SELL".equals(signalType); }
        public boolean isHold() { return "HOLD".equals(signalType); }
        public double getTriggerPrice() { return triggerPrice; }
        public String getSignalType() { return signalType; }
        public int getChannel() { return channel; }
    }

    // ==================== MA 计算辅助类（支持更多类型） ====================
    private static class MaCalculator {
        private final BarSeries series;
        private final IndicatorWrapper indicatorWrapper;
        private final String type;
        private final int length;
        private org.ta4j.core.Indicator<Num> cachedIndicator;

        public MaCalculator(BarSeries series, IndicatorWrapper indicatorWrapper, String type, int length) {
            this.series = series;
            this.indicatorWrapper = indicatorWrapper;
            this.type = type;
            this.length = length;
        }

        public int getLength() { return length; }

        public Num getValue(int index) {
            if (cachedIndicator == null) {
                cachedIndicator = createIndicator();
            }
            return cachedIndicator.getValue(index);
        }

        private org.ta4j.core.Indicator<Num> createIndicator() {
            String upperType = type.toUpperCase();
            switch (upperType) {
                case "SMA":
                    return new SMAIndicator(indicatorWrapper.getIndicator(), length);
                case "EMA":
                    return new EMAIndicator(indicatorWrapper.getIndicator(), length);
                case "WMA":
                    return new WMAIndicator(indicatorWrapper.getIndicator(), length);
                case "SMMA (RMA)":
                    return new SMMAIndicator(indicatorWrapper.getIndicator(), length);
                case "VWMA":
                    // VWMA 需要成交量数据，此处简化处理，可扩展
                    log.warn("VWMA not fully supported, fallback to SMA");
                    return new SMAIndicator(indicatorWrapper.getIndicator(), length);
                default:
                    log.warn("Unknown MA type: {}, fallback to SMA", type);
                    return new SMAIndicator(indicatorWrapper.getIndicator(), length);
            }
        }
    }

    private static class IndicatorWrapper {
        private final org.ta4j.core.Indicator<Num> indicator;
        public IndicatorWrapper(org.ta4j.core.Indicator<Num> indicator) { this.indicator = indicator; }
        public org.ta4j.core.Indicator<Num> getIndicator() { return indicator; }
    }

    // ==================== 核心接口实现 ====================

    @Override
    public BuyAndSellWeightDto execute(IndicatorCalcDto calcDto) {
        applyConfiguredParams();
        applyOverrideParams(calcDto.getParameterOverrides());

        List<Candlestick> kLines = calcDto.getKLines();
        String symbol = calcDto.getSymbol();

        int maxLength = Math.max(ma1Length, ma2Length);
        if (channel2Enabled) {
            maxLength = Math.max(maxLength, Math.max(ma3Length, ma4Length));
        }

        if (kLines.size() < maxLength) {
            log.debug("数据不足，返回空结果。当前K线数量: {}, 需要: {}", kLines.size(), maxLength);
            return new BuyAndSellWeightDto();
        }

        SslChannelSignal signal = analyzeSslChannelSignal(kLines, symbol);
        BuyAndSellWeightDto result = new BuyAndSellWeightDto();

        if (signal != null && !signal.isHold()) {
            Candlestick currentCandle = kLines.get(kLines.size() - 1);
            log.info("交易对: {}, K线时间: {}, SSL Channel 信号: {}, 触发价格: {}",
                    symbol, DateUtil.longConvertDateTime(currentCandle.getId()),
                    signal.getSignalType(), String.format("%.4f", signal.getTriggerPrice()));

            if (signal.isBuy()) {
                result.setSignalType(SignalType.LONG);
            } else if (signal.isSell()) {
                result.setSignalType(SignalType.SHORT);
            }

            saveLastSignalToRedis(symbol, signal.getSignalType());

            Long signalId = saveSign(calcDto, result.getSignalType());
            result.setSignalId(signalId);
        } else {
            Candlestick currentCandle = kLines.get(kLines.size() - 1);
            log.debug("交易对: {}, K线时间: {}, SSL Channel 无信号",
                    symbol, DateUtil.longConvertDateTime(currentCandle.getId()));
        }

        return result;
    }

    @Override
    public Double getWeight(IndicatorCalcDto calcDto) {
        WeightAndConfidenceDto dto = getWeightAndConfidence(calcDto);
        return dto.getWeight().doubleValue();
    }

    @Override
    public WeightAndConfidenceDto getWeightAndConfidence(IndicatorCalcDto calcDto) {
        applyConfiguredParams();
        applyOverrideParams(calcDto.getParameterOverrides());

        List<Candlestick> kLines = calcDto.getKLines();
        String symbol = calcDto.getSymbol();

        if (kLines.size() < 21) {
            return new WeightAndConfidenceDto(BigDecimal.ZERO, null, null, null);
        }

        String breakoutSignal = calcDto.getSignalType().name();
        SslChannelSignal signal = analyzeSslChannelSignal(kLines, symbol);

        if (signal == null || signal.isHold()) {
            return new WeightAndConfidenceDto(BigDecimal.ZERO, null, null, null);
        }

        boolean directionMatch = ("BUY".equals(breakoutSignal) && signal.isBuy()) ||
                ("SELL".equals(breakoutSignal) && signal.isSell());
        if (!directionMatch) {
            return new WeightAndConfidenceDto(BigDecimal.ZERO, null, null, null);
        }

        BigDecimal confidence = calculateConfidence(kLines, signal, symbol);

        double finalWeight = 1.0;
        if (qualityBasedAdjuster != null) {
            finalWeight = 1.0 * confidence.doubleValue();
        } else {
            finalWeight = confidence.doubleValue();
        }
        finalWeight = Math.max(0, Math.min(finalWeight, 3.0));

        return new WeightAndConfidenceDto(
                BigDecimal.valueOf(finalWeight).setScale(2, RoundingMode.HALF_UP),
                null, null, null
        );
    }

    // ==================== 信号分析 ====================

    private SslChannelSignal analyzeSslChannelSignal(List<Candlestick> kLines, String symbol) {
        try {
            BarSeries series = IndicatorWrapHelper.buildSeries(kLines);
            int currentIdx = series.getEndIndex() - 1;
            int prevIdx = currentIdx - 1;
            if (prevIdx < 0) {
                return new SslChannelSignal("HOLD", 0.0, 0);
            }

            List<Integer> hlv1 = calculateHlv(series, 1);
            List<Integer> hlv2 = channel2Enabled ? calculateHlv(series, 2) : null;

            SslChannelSignal signal1 = null;
            if (showMa1 && showMa2) {
                int cur = hlv1.get(currentIdx);
                int prev = hlv1.get(prevIdx);
                if (prev == -1 && cur == 1) {
                    double price = series.getBar(currentIdx).getClosePrice().doubleValue();
                    signal1 = new SslChannelSignal("BUY", price, 1);
                } else if (prev == 1 && cur == -1) {
                    double price = series.getBar(currentIdx).getClosePrice().doubleValue();
                    signal1 = new SslChannelSignal("SELL", price, 1);
                }
            }

            SslChannelSignal signal2 = null;
            if (channel2Enabled && showMa3 && showMa4) {
                int cur = hlv2.get(currentIdx);
                int prev = hlv2.get(prevIdx);
                if (prev == -1 && cur == 1) {
                    double price = series.getBar(currentIdx).getClosePrice().doubleValue();
                    signal2 = new SslChannelSignal("BUY", price, 2);
                } else if (prev == 1 && cur == -1) {
                    double price = series.getBar(currentIdx).getClosePrice().doubleValue();
                    signal2 = new SslChannelSignal("SELL", price, 2);
                }
            }

            return combineSignals(signal1, signal2);

        } catch (Exception e) {
            log.error("交易对: {}, SSL Channel 信号分析失败: {}", symbol, e.getMessage(), e);
            return new SslChannelSignal("HOLD", 0.0, 0);
        }
    }

    /**
     * 计算指定通道的 Hlv 序列（0: 初始/无方向, 1: 多头, -1: 空头）
     * 修正有效起始索引为 maxLength - 1
     */
    private List<Integer> calculateHlv(BarSeries series, int channel) {
        int length = series.getBarCount();
        List<Integer> hlv = new ArrayList<>(length);

        MaCalculator maHigh = createMaCalculator(series, channel, true);
        MaCalculator maLow  = createMaCalculator(series, channel, false);

        int maxLength = Math.max(maHigh.getLength(), maLow.getLength());
        int firstValidIndex = maxLength - 1;  // 第一个可以计算 MA 的索引

        for (int i = 0; i < length; i++) {
            if (i < firstValidIndex) {
                hlv.add(0);
                continue;
            }

            Num highMa = maHigh.getValue(i);
            Num lowMa  = maLow.getValue(i);

            // 根据 wicks 决定使用的价格
            Num priceForHigh = wicks ? series.getBar(i).getHighPrice() : series.getBar(i).getClosePrice();
            Num priceForLow  = wicks ? series.getBar(i).getLowPrice()  : series.getBar(i).getClosePrice();

            int cur;
            if (priceForHigh.isGreaterThan(highMa)) {
                cur = 1;
            } else if (priceForLow.isLessThan(lowMa)) {
                cur = -1;
            } else {
                cur = (i == 0) ? 0 : hlv.get(i - 1);
            }
            hlv.add(cur);
        }
        return hlv;
    }

    /**
     * 根据通道和上下轨创建 MaCalculator，动态选择价格源
     */
    private MaCalculator createMaCalculator(BarSeries series, int channel, boolean isHigh) {
        String type;
        int len;
        String source;
        if (channel == 1) {
            if (isHigh) {
                type = ma1Type;
                len = ma1Length;
                source = ma1Source;
            } else {
                type = ma2Type;
                len = ma2Length;
                source = ma2Source;
            }
        } else {
            if (isHigh) {
                type = ma3Type;
                len = ma3Length;
                source = ma3Source;
            } else {
                type = ma4Type;
                len = ma4Length;
                source = ma4Source;
            }
        }
        IndicatorWrapper wrapper = createIndicatorWrapper(series, source);
        return new MaCalculator(series, wrapper, type, len);
    }

    /**
     * 根据 source 字符串创建对应的 IndicatorWrapper
     */
    private IndicatorWrapper createIndicatorWrapper(BarSeries series, String source) {
        String src = source.trim().toLowerCase();
        org.ta4j.core.Indicator<Num> indicator;
        switch (src) {
            case "high":
                indicator = new HighPriceIndicator(series);
                break;
            case "low":
                indicator = new LowPriceIndicator(series);
                break;
            case "close":
                indicator = new ClosePriceIndicator(series);
                break;
            case "open":
                indicator = new OpenPriceIndicator(series);
                break;
            default:
                log.warn("Unknown source: {}, using close", source);
                indicator = new ClosePriceIndicator(series);
        }
        return new IndicatorWrapper(indicator);
    }

    /**
     * 合并两个通道的信号
     */
    private SslChannelSignal combineSignals(SslChannelSignal s1, SslChannelSignal s2) {
        if (s1 == null && s2 == null) {
            return new SslChannelSignal("HOLD", 0.0, 0);
        }
        if (s1 == null) return s2;
        if (s2 == null) return s1;

        switch (combineMode.toLowerCase()) {
            case "both":
                if (s1.isBuy() && s2.isBuy()) {
                    return new SslChannelSignal("BUY", Math.max(s1.getTriggerPrice(), s2.getTriggerPrice()), 0);
                } else if (s1.isSell() && s2.isSell()) {
                    return new SslChannelSignal("SELL", Math.max(s1.getTriggerPrice(), s2.getTriggerPrice()), 0);
                } else {
                    return new SslChannelSignal("HOLD", 0.0, 0);
                }
            case "weighted":
                return (!s1.isHold()) ? s1 : s2;
            case "any":
            default:
                return (!s1.isHold()) ? s1 : s2;
        }
    }

    /**
     * 计算置信度（基于通道宽度与价格位置）
     */
    private BigDecimal calculateConfidence(List<Candlestick> kLines, SslChannelSignal signal, String symbol) {
        try {
            BarSeries series = IndicatorWrapHelper.buildSeries(kLines);
            int idx = series.getEndIndex() - 1;

            MaCalculator maHigh = createMaCalculator(series, signal.getChannel(), true);
            MaCalculator maLow  = createMaCalculator(series, signal.getChannel(), false);

            Num highMa = maHigh.getValue(idx);
            Num lowMa  = maLow.getValue(idx);
            double width = highMa.minus(lowMa).doubleValue();
            if (width <= 0) return BigDecimal.valueOf(0.5);

            ClosePriceIndicator close = new ClosePriceIndicator(series);
            double price = close.getValue(idx).doubleValue();

            double confidence;
            if (signal.isBuy()) {
                double distFromLow = price - lowMa.doubleValue();
                confidence = Math.min(1.0, distFromLow / width);
            } else if (signal.isSell()) {
                double distFromHigh = highMa.doubleValue() - price;
                confidence = Math.min(1.0, distFromHigh / width);
            } else {
                confidence = 0.5;
            }
            confidence = Math.max(0.3, Math.min(0.9, confidence));
            return BigDecimal.valueOf(confidence);
        } catch (Exception e) {
            log.error("计算置信度失败: {}", e.getMessage());
            return BigDecimal.valueOf(0.5);
        }
    }

    // ==================== 配置管理 ====================

    private void applyConfiguredParams() {
        if (signalServiceConfigService == null) return;
        Map<String, Object> params = signalServiceConfigService.getParams(SERVICE_KEY);
        if (params == null || params.isEmpty()) return;

        showMa1 = getBoolean(params, "showMa1", showMa1);
        ma1Type = getString(params, "ma1Type", ma1Type);
        ma1Source = getString(params, "ma1Source", ma1Source);
        ma1Length = getInt(params, "ma1Length", ma1Length);
        ma1Color = getString(params, "ma1Color", ma1Color);
        showMa2 = getBoolean(params, "showMa2", showMa2);
        ma2Type = getString(params, "ma2Type", ma2Type);
        ma2Source = getString(params, "ma2Source", ma2Source);
        ma2Length = getInt(params, "ma2Length", ma2Length);
        ma2Color = getString(params, "ma2Color", ma2Color);
        showLabels1 = getBoolean(params, "showLabels1", showLabels1);

        channel2Enabled = getBoolean(params, "channel2Enabled", channel2Enabled);
        if (channel2Enabled) {
            showMa3 = getBoolean(params, "showMa3", showMa3);
            ma3Type = getString(params, "ma3Type", ma3Type);
            ma3Source = getString(params, "ma3Source", ma3Source);
            ma3Length = getInt(params, "ma3Length", ma3Length);
            ma3Color = getString(params, "ma3Color", ma3Color);
            showMa4 = getBoolean(params, "showMa4", showMa4);
            ma4Type = getString(params, "ma4Type", ma4Type);
            ma4Source = getString(params, "ma4Source", ma4Source);
            ma4Length = getInt(params, "ma4Length", ma4Length);
            ma4Color = getString(params, "ma4Color", ma4Color);
            showLabels2 = getBoolean(params, "showLabels2", showLabels2);
        }

        wicks = getBoolean(params, "wicks", wicks);
        highlightState = getBoolean(params, "highlightState", highlightState);
        combineMode = getString(params, "combineMode", combineMode);
        useRiskModule = getBoolean(params, "useRiskModule", useRiskModule);
        riskModuleEvaluators = getString(params, "riskModuleEvaluators", riskModuleEvaluators);
    }

    private void applyOverrideParams(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) return;

        showMa1 = getBooleanOverride(overrides, "showMa1", showMa1);
        ma1Type = getStringOverride(overrides, "ma1Type", ma1Type);
        ma1Source = getStringOverride(overrides, "ma1Source", ma1Source);
        ma1Length = getIntOverride(overrides, "ma1Length", ma1Length);
        ma1Color = getStringOverride(overrides, "ma1Color", ma1Color);
        showMa2 = getBooleanOverride(overrides, "showMa2", showMa2);
        ma2Type = getStringOverride(overrides, "ma2Type", ma2Type);
        ma2Source = getStringOverride(overrides, "ma2Source", ma2Source);
        ma2Length = getIntOverride(overrides, "ma2Length", ma2Length);
        ma2Color = getStringOverride(overrides, "ma2Color", ma2Color);
        showLabels1 = getBooleanOverride(overrides, "showLabels1", showLabels1);

        channel2Enabled = getBooleanOverride(overrides, "channel2Enabled", channel2Enabled);
        if (channel2Enabled) {
            showMa3 = getBooleanOverride(overrides, "showMa3", showMa3);
            ma3Type = getStringOverride(overrides, "ma3Type", ma3Type);
            ma3Source = getStringOverride(overrides, "ma3Source", ma3Source);
            ma3Length = getIntOverride(overrides, "ma3Length", ma3Length);
            ma3Color = getStringOverride(overrides, "ma3Color", ma3Color);
            showMa4 = getBooleanOverride(overrides, "showMa4", showMa4);
            ma4Type = getStringOverride(overrides, "ma4Type", ma4Type);
            ma4Source = getStringOverride(overrides, "ma4Source", ma4Source);
            ma4Length = getIntOverride(overrides, "ma4Length", ma4Length);
            ma4Color = getStringOverride(overrides, "ma4Color", ma4Color);
            showLabels2 = getBooleanOverride(overrides, "showLabels2", showLabels2);
        }

        wicks = getBooleanOverride(overrides, "wicks", wicks);
        highlightState = getBooleanOverride(overrides, "highlightState", highlightState);
        combineMode = getStringOverride(overrides, "combineMode", combineMode);
        useRiskModule = getBooleanOverride(overrides, "useRiskModule", useRiskModule);
        riskModuleEvaluators = getStringOverride(overrides, "riskModuleEvaluators", riskModuleEvaluators);
    }

    // ==================== 辅助类型转换 ====================

    private String getString(Map<String, Object> params, String key, String def) {
        Object v = params.get(key);
        return v == null ? def : String.valueOf(v).trim();
    }

    private int getInt(Map<String, Object> params, String key, int def) {
        Object v = params.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v != null) {
            try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception ignored) {}
        }
        return def;
    }

    private double getDouble(Map<String, Object> params, String key, double def) {
        Object v = params.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v != null) {
            try { return Double.parseDouble(String.valueOf(v).trim()); } catch (Exception ignored) {}
        }
        return def;
    }

    private boolean getBoolean(Map<String, Object> params, String key, boolean def) {
        Object v = params.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        if (v != null) {
            String s = String.valueOf(v).trim().toLowerCase();
            if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
            if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
        }
        return def;
    }

    private String getStringOverride(Map<String, String> overrides, String key, String def) {
        String v = overrides.get(key);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    private int getIntOverride(Map<String, String> overrides, String key, int def) {
        String v = overrides.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (Exception ignored) {}
        return def;
    }

    private double getDoubleOverride(Map<String, String> overrides, String key, double def) {
        String v = overrides.get(key);
        if (v == null) return def;
        try { return Double.parseDouble(v.trim()); } catch (Exception ignored) {}
        return def;
    }

    private boolean getBooleanOverride(Map<String, String> overrides, String key, boolean def) {
        String v = overrides.get(key);
        if (v == null) return def;
        String s = v.trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
        if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
        return def;
    }

    private void saveLastSignalToRedis(String symbol, String signal) {
        // 预留：可将最近信号存入 Redis
    }
}