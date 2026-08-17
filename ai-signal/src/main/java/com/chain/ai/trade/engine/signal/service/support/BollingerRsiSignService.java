package com.chain.ai.trade.engine.signal.service.support;


import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
import com.chain.ai.trade.engine.entity.dto.AnalysisData;
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
import org.ta4j.core.indicators.RSIIndicator;

import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

import com.chain.ai.trade.common.entity.constants.SignalType;

import com.chain.ai.trade.engine.signal.service.ITechnicalSignalService;
import com.chain.ai.trade.engine.signal.entity.dos.TechnicalSignal;


import java.math.BigDecimal;

import java.util.Collections;
import java.util.List;

import java.util.Map;

import static com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum.OKXMIN15;


/**
 * Bollinger Bands + RSI 双重策略信号生成服务
 * 严格对应 TradingView Pine Script: "Bollinger + RSI, Double Strategy (by ChartArt) v1.1"
 * 包含stop订单逻辑
 */
@Slf4j
@Service
public class BollingerRsiSignService extends DefaultSignService {

    private static final String SERVICE_KEY = "BollingerRsiSignService";
    private static final ThreadLocal<Boolean> LAST_SIGNAL_LOOKUP_ENABLED = ThreadLocal.withInitial(() -> true);

    public BollingerRsiSignService() {
        this.signalServiceKey = SERVICE_KEY;
    }

    @Value("${strategy.bollinger-rsi.rsi-length:8}")
    private int rsiLength;

    @Value("${strategy.bollinger-rsi.bb-length:108}")
    private int bbLength;

    @Value("${strategy.bollinger-rsi.bb-multiplier:2.0}")
    private double bbMultiplier;

    @Value("${strategy.bollinger-rsi.rsi-threshold:50}")
    private double rsiThreshold;

    // Andean Oscillator 权重调整配置
    @Value("${strategy.range-filter.use-andean-filter:true}")
    private boolean useAndeanFilter;

    @Value("${strategy.range-filter.andean-length:50}")
    private int andeanLength;

    @Value("${strategy.range-filter.andean-signal-length:9}")
    private int andeanSignalLength;

    @Value("${strategy.range-filter.andean-early-signal-mode:true}")
    private boolean andeanEarlySignalMode;

    // 艾略特波浪分析配置
    @Value("${strategy.bollinger-rsi.use-elliott-wave:true}")
    private boolean useElliottWave;

    @Value("${strategy.bollinger-rsi.elliott-wave-min-bars:200}")
    private int elliottWaveMinBars;

    // 风险模块配置
    @Value("${strategy.bollinger-rsi.use-risk-module:true}")
    private boolean useRiskModule; // 是否使用新的风险模块

    @Value("${strategy.bollinger-rsi.risk-module-evaluators:}")
    private String riskModuleEvaluators; // 指定要使用的评估器列表，逗号分隔，如 "elliott-wave"。如果为空则使用全局默认配置


    @Autowired(required = false)
    @Lazy
    private ITechnicalSignalService technicalSignalService;

    // 艾略特波浪置信度配置 - 权重配置
    @Value("${strategy.bollinger-rsi.elliott-confidence.weights.direction:0.30}")
    private double weightDirection;
    @Value("${strategy.bollinger-rsi.elliott-confidence.weights.structure:0.20}")
    private double weightStructure;
    @Value("${strategy.bollinger-rsi.elliott-confidence.weights.fibonacci:0.15}")
    private double weightFibonacci;
    @Value("${strategy.bollinger-rsi.elliott-confidence.weights.confluence:0.15}")
    private double weightConfluence;
    @Value("${strategy.bollinger-rsi.elliott-confidence.weights.invalidation:0.10}")
    private double weightInvalidation;
    @Value("${strategy.bollinger-rsi.elliott-confidence.weights.channel:0.05}")
    private double weightChannel;
    @Value("${strategy.bollinger-rsi.elliott-confidence.weights.price-position:0.05}")
    private double weightPricePosition;

    // 艾略特波浪置信度配置 - 惩罚配置
    @Value("${strategy.bollinger-rsi.elliott-confidence.penalties.high-confidence-opposite:0.4}")
    private double penaltyHighConfidenceOpposite;
    @Value("${strategy.bollinger-rsi.elliott-confidence.penalties.medium-confidence-opposite:0.3}")
    private double penaltyMediumConfidenceOpposite;
    @Value("${strategy.bollinger-rsi.elliott-confidence.penalties.low-confidence-opposite:0.2}")
    private double penaltyLowConfidenceOpposite;
    @Value("${strategy.bollinger-rsi.elliott-confidence.penalties.invalidation-penalty:0.3}")
    private double penaltyInvalidation;
    @Value("${strategy.bollinger-rsi.elliott-confidence.penalties.opposite-scenario:0.7}")
    private double penaltyOppositeScenario;

    // 艾略特波浪置信度配置 - 增强配置
    @Value("${strategy.bollinger-rsi.elliott-confidence.enhancements.direction-boost:1.1}")
    private double enhancementDirectionBoost;
    @Value("${strategy.bollinger-rsi.elliott-confidence.enhancements.perfect-structure-threshold:0.9}")
    private double enhancementPerfectStructureThreshold;
    @Value("${strategy.bollinger-rsi.elliott-confidence.enhancements.strong-confluence-threshold:0.8}")
    private double enhancementStrongConfluenceThreshold;

    // 艾略特波浪置信度配置 - 其他配置
    @Value("${strategy.bollinger-rsi.elliott-confidence.min-confidence:0.1}")
    private double minConfidence;
    @Value("${strategy.bollinger-rsi.elliott-confidence.max-confidence:1.0}")
    private double maxConfidence;

    @Override
    public BuyAndSellWeightDto execute(IndicatorCalcDto calcDto) {
        applyConfiguredParams();
        applyOverrideParams(calcDto.getParameterOverrides());
        List<Candlestick> kLines = calcDto.getKLines();
        String symbol = calcDto.getSymbol();

        // 从规则中获取配置（暂时只用做多规则）
        int currentRsiLength = rsiLength;
        int currentBbLength = bbLength;

        if (kLines.size() < currentBbLength + 1) {
            log.debug("数据不足，返回空结果。当前K线数量: {}, 需要: {}", kLines.size(), currentBbLength + 1);
            return new BuyAndSellWeightDto();
        }

        BollingerRsiSignal signal = analyzeBollingerRsiSignal(kLines, symbol, currentRsiLength, currentBbLength);
        // 应用K线方向过滤
        //BollingerRsiSignal signal = this.applyCandleDirectionFilter(signal1, calcDto);
        BuyAndSellWeightDto result = new BuyAndSellWeightDto();

        if (signal.isBuySignal()) {
            Candlestick currentCandle = kLines.get(kLines.size() - 1);
            log.info("交易对: {}, K线时间:{}, Bollinger+RSI 买入信号确认, 触发价格: {}",
                    symbol, DateUtil.longConvertDateTime(currentCandle.getId()),
                    String.format("%.4f", signal.getTriggerPrice()));
            result.setSignalType(SignalType.LONG);
            // 设置stop订单触发价格

            saveLastSignalToRedis(symbol, "BUY");
        } else if (signal.isSellSignal()) {
            Candlestick currentCandle = kLines.get(kLines.size() - 1);
            log.info("交易对: {}, K线时间:{}, Bollinger+RSI 卖出信号确认, 触发价格: {}",
                    symbol, DateUtil.longConvertDateTime(currentCandle.getId()),
                    String.format("%.4f", signal.getTriggerPrice()));
            result.setSignalType(SignalType.SHORT);
            // 设置stop订单触发价格

            saveLastSignalToRedis(symbol, "SELL");
        } else {
            Candlestick currentCandle = kLines.get(kLines.size() - 1);
            log.debug("交易对: {}, K线时间:{}, Bollinger+RSI 无信号",
                    symbol, DateUtil.longConvertDateTime(currentCandle.getId()));
        }

        if (null != result.getSignalType()) {
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
     * 严格按Pine Script逻辑分析 Bollinger Bands + RSI 信号（包含stop订单逻辑）
     */
    private BollingerRsiSignal analyzeBollingerRsiSignal(List<Candlestick> kLines, String symbol, int rsiLen, int bbLen) {
        try {
            BarSeries series = IndicatorWrapHelper.buildSeries(kLines);
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

            int currentIndex = series.getEndIndex()-1;
            int previousIndex = currentIndex - 1;

            if (previousIndex < 0) {
                return new BollingerRsiSignal("HOLD", 0.0);
            }

            // 计算 RSI
            RSIIndicator rsi = new RSIIndicator(closePrice, rsiLen);
            Num rsiCurrent = rsi.getValue(currentIndex);
            Num rsiPrevious = rsi.getValue(previousIndex);

            // 计算 Bollinger Bands
            SMAIndicator sma = new SMAIndicator(closePrice, bbLen);
            StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, bbLen);

            // 当前和前一根K线的布林带
            Num bbUpperCurrent = sma.getValue(currentIndex).plus(stdDev.getValue(currentIndex).multipliedBy(series.numFactory().numOf(bbMultiplier)));
            Num bbLowerCurrent = sma.getValue(currentIndex).minus(stdDev.getValue(currentIndex).multipliedBy(series.numFactory().numOf(bbMultiplier)));
            Num bbUpperPrev = sma.getValue(previousIndex).plus(stdDev.getValue(previousIndex).multipliedBy(series.numFactory().numOf(bbMultiplier)));
            Num bbLowerPrev = sma.getValue(previousIndex).minus(stdDev.getValue(previousIndex).multipliedBy(series.numFactory().numOf(bbMultiplier)));

            // 当前和前一根K线的价格
            Num priceCurrent = closePrice.getValue(currentIndex);
            Num pricePrevious = closePrice.getValue(previousIndex);

            // 查询最近信号状态
            String lastSignal = getLastSignal(symbol);

            // 实现新的多空条件逻辑
            // 条件1：原始的双重交叉确认（布林带下轨）
            boolean buySignalCondition1 =
                    // RSI从下方上穿50（严格交叉）
                    rsiPrevious.isLessThan(series.numFactory().numOf(rsiThreshold)) &&
                            rsiCurrent.isGreaterThan(series.numFactory().numOf(rsiThreshold)) &&
                            // 价格从下方上穿布林带下轨（严格交叉）
                            pricePrevious.isLessThan(bbLowerPrev) &&
                            priceCurrent.isGreaterThan(bbLowerCurrent);

            // 条件2：新增的多信号条件（布林带上轨 + 最近信号为空）
            boolean buySignalCondition2 =
                    // 最近一个信号为空（没有持仓或上一个信号是空仓）
                    (lastSignal == null || "SELL".equals(lastSignal) || "HOLD".equals(lastSignal)) &&
                            // RSI从下方上穿50（严格交叉）
                            rsiCurrent.isGreaterThan(series.numFactory().numOf(rsiThreshold)) &&
                            // 价格从下方上穿布林带上轨（严格交叉）
                            pricePrevious.isLessThan(bbUpperPrev) &&
                            priceCurrent.isGreaterThan(bbUpperCurrent);

            // 条件3：原始的双重交叉确认（布林带上轨）
            boolean sellSignalCondition1 =
                    // RSI从上方下穿50（严格交叉）
                    rsiPrevious.isGreaterThan(series.numFactory().numOf(rsiThreshold)) &&
                            rsiCurrent.isLessThan(series.numFactory().numOf(rsiThreshold)) &&
                            // 价格从上方下穿布林带上轨（严格交叉）
                            pricePrevious.isGreaterThan(bbUpperPrev) &&
                            priceCurrent.isLessThan(bbUpperCurrent);

            // 条件4：新增的空信号条件（布林带下轨 + 最近信号为多）
            boolean sellSignalCondition2 =
                    // 最近一个信号为多（有持仓或上一个信号是多头）
                    (lastSignal == null || "BUY".equals(lastSignal) || "HOLD".equals(lastSignal)) &&
                            // RSI从上方下穿50（严格交叉）
                            rsiCurrent.isLessThan(series.numFactory().numOf(rsiThreshold)) &&
                            // 价格从上方下穿布林带下轨（严格交叉）
                            pricePrevious.isGreaterThan(bbLowerPrev) &&
                            priceCurrent.isLessThan(bbLowerCurrent);

            // 组合信号条件
            boolean buySignal = buySignalCondition1 || buySignalCondition2;
            boolean sellSignal = sellSignalCondition1 || sellSignalCondition2;

            // 详细的调试日志
            log.info("交易对: {}, 时间: {}, 最近信号: {}, RSI: {}->{}, 价格: {}->{}, 布林带: [{}, {}]",
                    symbol, DateUtil.longConvertDateTime(kLines.get(kLines.size()-1).getId()),
                    lastSignal,
                    String.format("%.2f", rsiPrevious.doubleValue()),
                    String.format("%.2f", rsiCurrent.doubleValue()),
                    String.format("%.4f", pricePrevious.doubleValue()),
                    String.format("%.4f", priceCurrent.doubleValue()),
                    String.format("%.4f", bbLowerCurrent.doubleValue()),
                    String.format("%.4f", bbUpperCurrent.doubleValue()));

            // 记录各个条件的触发情况
            log.info("交易对: {}, 条件状态 - 买入1:{}, 买入2:{}, 卖出1:{}, 卖出2:{}, 最终买入:{}, 最终卖出:{}",
                    symbol, buySignalCondition1, buySignalCondition2, sellSignalCondition1, sellSignalCondition2, buySignal, sellSignal);

            if (buySignal) {
                double triggerPrice = buySignalCondition1 ? bbLowerCurrent.doubleValue() : bbUpperCurrent.doubleValue();
                String conditionDesc = buySignalCondition1 ? "布林带下轨交叉" : "布林带上轨交叉(无持仓)";
                log.info("交易对: {}, 多头信号确认 - 条件:{}, RSI交叉+价格突破", symbol, conditionDesc);
                return new BollingerRsiSignal("BUY", triggerPrice);
            } else if (sellSignal) {
                double triggerPrice = sellSignalCondition1 ? bbUpperCurrent.doubleValue() : bbLowerCurrent.doubleValue();
                String conditionDesc = sellSignalCondition1 ? "布林带上轨交叉" : "布林带下轨交叉(多头持仓)";
                log.info("交易对: {}, 空头信号确认 - 条件:{}, RSI交叉+价格突破", symbol, conditionDesc);
                return new BollingerRsiSignal("SELL", triggerPrice);
            }

            return new BollingerRsiSignal("HOLD", 0.0);

        } catch (Exception e) {
            log.error("交易对: {}, Bollinger+RSI 信号分析失败: {}", symbol, e.getMessage(), e);
            return new BollingerRsiSignal("HOLD", 0.0);
        }
    }

    /**
     * 获取最近的信号状态
     */
    private String getLastSignal(String symbol) {
        try {
            Boolean enabled = LAST_SIGNAL_LOOKUP_ENABLED.get();
            if (enabled != null && !enabled) {
                return null;
            }
            if (technicalSignalService != null) {
                // 查询最近的 BollingerRSI 信号
                List<TechnicalSignal> latestSignals = technicalSignalService.getLatestSignals(symbol, "SIGNAL_BASED", 1);
                if (latestSignals != null && !latestSignals.isEmpty()) {
                    TechnicalSignal lastSignal = latestSignals.get(0);
                    String technicalDirection = lastSignal.getTechnicalDirection();

                    // 将技术方向转换为交易信号状态
                    if (technicalDirection != null) {
                        switch (technicalDirection.toUpperCase()) {
                            case "LONG":
                            case "BULLISH":
                                return "BUY";  // 多头信号
                            case "SHORT":
                            case "BEARISH":
                                return "SELL"; // 空头信号
                            case "NEUTRAL":
                            default:
                                return "HOLD"; // 中性信号
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询最近信号失败，使用默认状态: {}", e.getMessage());
        }
        return null; // 没有历史信号
    }

    public static void disableLastSignalLookup() {
        LAST_SIGNAL_LOOKUP_ENABLED.set(false);
    }

    public static void enableLastSignalLookup() {
        LAST_SIGNAL_LOOKUP_ENABLED.set(true);
    }
    /**
     * 应用K线方向过滤 - 在所有信号生成后统一过滤
     * 规则：空头信号不能是上涨K，多头信号不能是下跌K
     */
    public BollingerRsiSignal applyCandleDirectionFilter(BollingerRsiSignal signal, IndicatorCalcDto calcDto) {
        List<Candlestick> kLines15 = get15MinKlines(calcDto.getKLines(), calcDto.getSymbol());

        if (kLines15 == null || kLines15.isEmpty()) {
            return signal;
        }

        // 找到对应的15分钟K线（使用当前K线的时间戳）
        Candlestick matched15MinKline = calcDto.getKLines().get(calcDto.getKLines().size() - 2);

        if (matched15MinKline == null) {
            return signal;
        }

        // 判断15分钟K线方向
        boolean isBullish15Min = isBullishCandleForKline(matched15MinKline);
        boolean isBearish15Min = isBearishCandleForKline(matched15MinKline);
        // 多头信号过滤：不能是下跌K
        if (signal.isBuySignal()&& isBearish15Min) {
            return new BollingerRsiSignal("HOLD", 0.0);
        }

        // 空头信号过滤：不能是上涨K
        if (signal.isSellSignal() && isBullish15Min) {

            return new BollingerRsiSignal("HOLD", 0.0);
        }
        return signal;
    }
    /**
     * 判断Candlestick是否为上涨K线
     */
    private boolean isBullishCandleForKline(Candlestick kline) {
        if (kline == null) return false;

        double open = kline.getOpenPrice().doubleValue();
        double close = kline.getClosePrice().doubleValue();

        // 收盘价高于开盘价为上涨K线
        return close > open;
    }

    /**
     * 判断Candlestick是否为下跌K线
     */
    private boolean isBearishCandleForKline(Candlestick kline) {
        if (kline == null) return false;

        double open = kline.getOpenPrice().doubleValue();
        double close = kline.getClosePrice().doubleValue();

        // 收盘价低于开盘价为下跌K线
        return close < open;
    }
    /**
     * 获取15分钟周期K线数据
     * 参考 getH1Klines 方法的实现方式
     */
    protected List<Candlestick> get15MinKlines(List<Candlestick> currentKLines, String symbol) {
        try {
            if (currentKLines == null || currentKLines.isEmpty()) {
                return Collections.emptyList();
            }
            Candlestick latest = currentKLines.get(currentKLines.size() - 1);
            return candlestickService.listByLeId(latest.getId(), symbol, OKXMIN15, 200);
        } catch (Exception e) {

            return Collections.emptyList();
        }
    }
    /**
     * 计算信号强度（考虑stop订单的距离）
     */
    private double calculateSignalStrength(List<Candlestick> kLines, BollingerRsiSignal signal, String symbol, int bbLen) {
        try {
            BarSeries series = IndicatorWrapHelper.buildSeries(kLines);
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

            int currentIndex = series.getEndIndex();
            Num currentPrice = closePrice.getValue(currentIndex);

            // 计算 Bollinger Bands 宽度
            SMAIndicator sma = new SMAIndicator(closePrice, bbLen);
            StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, bbLen);

            Num bbBasis = sma.getValue(currentIndex);
            Num bbDev = stdDev.getValue(currentIndex).multipliedBy(series.numFactory().numOf(bbMultiplier));
            Num bbUpper = bbBasis.plus(bbDev);
            Num bbLower = bbBasis.minus(bbDev);
            Num bbWidth = bbUpper.minus(bbLower);

            double strength = 0.0;

            if (signal.isBuySignal()) {
                // 买入信号强度：基于当前价格与触发价格的距离
                double distanceToTrigger = Math.abs(currentPrice.doubleValue() - signal.getTriggerPrice());
                double normalizedDistance = distanceToTrigger / bbWidth.doubleValue();
                // 距离越近，信号强度越强
                strength = Math.max(0, 1.0 - normalizedDistance * 2);
            } else if (signal.isSellSignal()) {
                // 卖出信号强度：基于当前价格与触发价格的距离
                double distanceToTrigger = Math.abs(currentPrice.doubleValue() - signal.getTriggerPrice());
                double normalizedDistance = distanceToTrigger / bbWidth.doubleValue();
                strength = Math.max(0, 1.0 - normalizedDistance * 2);
            }

            // 将强度映射到权重范围 0.5 - 2.0
            return 0.5 + (strength * 1.5);

        } catch (Exception e) {
            log.error("计算信号强度失败: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * 信号包装类，包含信号类型和stop订单触发价格
     */
    private static class BollingerRsiSignal {
        private final String signalType;
        private final double triggerPrice;

        public BollingerRsiSignal(String signalType, double triggerPrice) {
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

    /**
     * 保存信号到Redis
     */
    private void saveLastSignalToRedis(String symbol, String signal) {
        // 实现保存信号到Redis的逻辑
    }



    private void applyConfiguredParams() {
        if (signalServiceConfigService == null) {
            return;
        }
        Map<String, Object> params = signalServiceConfigService.getParams(SERVICE_KEY);
        if (params == null || params.isEmpty()) {
            return;
        }
        rsiLength = getInt(params, "rsiLength", rsiLength);
        bbLength = getInt(params, "bbLength", bbLength);
        bbMultiplier = getDouble(params, "bbMultiplier", bbMultiplier);
        rsiThreshold = getDouble(params, "rsiThreshold", rsiThreshold);
        useAndeanFilter = getBoolean(params, "useAndeanFilter", useAndeanFilter);
        andeanLength = getInt(params, "andeanLength", andeanLength);
        andeanSignalLength = getInt(params, "andeanSignalLength", andeanSignalLength);
        andeanEarlySignalMode = getBoolean(params, "andeanEarlySignalMode", andeanEarlySignalMode);
        useElliottWave = getBoolean(params, "useElliottWave", useElliottWave);
        elliottWaveMinBars = getInt(params, "elliottWaveMinBars", elliottWaveMinBars);
        useRiskModule = getBoolean(params, "useRiskModule", useRiskModule);
        riskModuleEvaluators = getString(params, "riskModuleEvaluators", riskModuleEvaluators);
    }

    private void applyOverrideParams(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        rsiLength = getIntOverride(overrides, "rsiLength", rsiLength);
        bbLength = getIntOverride(overrides, "bbLength", bbLength);
        bbMultiplier = getDoubleOverride(overrides, "bbMultiplier", bbMultiplier);
        rsiThreshold = getDoubleOverride(overrides, "rsiThreshold", rsiThreshold);
        useAndeanFilter = getBooleanOverride(overrides, "useAndeanFilter", useAndeanFilter);
        andeanLength = getIntOverride(overrides, "andeanLength", andeanLength);
        andeanSignalLength = getIntOverride(overrides, "andeanSignalLength", andeanSignalLength);
        andeanEarlySignalMode = getBooleanOverride(overrides, "andeanEarlySignalMode", andeanEarlySignalMode);
        useElliottWave = getBooleanOverride(overrides, "useElliottWave", useElliottWave);
        elliottWaveMinBars = getIntOverride(overrides, "elliottWaveMinBars", elliottWaveMinBars);
        useRiskModule = getBooleanOverride(overrides, "useRiskModule", useRiskModule);
        riskModuleEvaluators = getStringOverride(overrides, "riskModuleEvaluators", riskModuleEvaluators);
    }

    private String getStringOverride(Map<String, String> overrides, String key, String defaultValue) {
        String value = overrides.get(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private int getIntOverride(Map<String, String> overrides, String key, int defaultValue) {
        String value = overrides.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private double getDoubleOverride(Map<String, String> overrides, String key, double defaultValue) {
        String value = overrides.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private boolean getBooleanOverride(Map<String, String> overrides, String key, boolean defaultValue) {
        String value = overrides.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.trim().toLowerCase();
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
            return false;
        }
        return defaultValue;
    }

    private String getString(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        return text;
    }

    private int getInt(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private double getDouble(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private boolean getBoolean(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
            return false;
        }
        return defaultValue;
    }
}
