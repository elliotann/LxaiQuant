package com.chain.ai.trade.engine.signal.service.support;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.dos.SmcBarResult;
import com.chain.ai.trade.engine.data.entity.dos.SmcOrderBlock;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.rule.WeightRuleContext;
import com.chain.ai.trade.engine.signal.service.DefaultSignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.RecentFractalSwingLowIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Range Filter [DW] & Labels 策略 - 重构版（逻辑完全一致，优化代码结构）
 */
@Slf4j
@Service
public class PriceTrendChannelSignService extends DefaultSignService {

    private static final String SERVICE_KEY = "RangeFilterDWSignService";


    // ==================== 配置参数 ====================
    @Value("${strategy.rfdw.filter-type:Type 1}")
    private String filterType;
    @Value("${strategy.rfdw.movement-source:Close}")
    private String movementSource;
    @Value("${strategy.rfdw.range-quantity:2.618}")
    private double rangeQuantity;
    @Value("${strategy.rfdw.range-scale:Average Change}")
    private String rangeScale;
    @Value("${strategy.rfdw.range-period:21}")
    private int rangePeriod;
    @Value("${strategy.rfdw.smooth-range:true}")
    private boolean smoothRange;
    @Value("${strategy.rfdw.smooth-period:27}")
    private int smoothPeriod;
    @Value("${strategy.rfdw.average-filter-changes:true}")
    private boolean averageFilterChanges;
    @Value("${strategy.rfdw.average-samples:2}")
    private int averageSamples;
    @Value("${strategy.rfdw.detailed-log:false}")
    private boolean detailedLog;

    // EMA 过滤
    @Value("${strategy.rfdw.use-ema-filter:false}")
    private boolean useEmaFilter;
    @Value("${strategy.rfdw.base-ema-period:60}")
    private int baseEmaPeriod;
    @Value("${strategy.rfdw.momentum-ema-period:26}")
    private int momentumEmaPeriod;
    @Value("${strategy.rfdw.ema-filter-mode:BOTH}")
    private String emaFilterMode;

    // 高周期 MACD 过滤1
    @Value("${strategy.rfdw.use-htf-macd-filter1:false}")
    private boolean useHTFMacdFilter1;
    @Value("${strategy.rfdw.htf-macd-resolution1:60}")
    private String htfMacdResolution1;
    @Value("${strategy.rfdw.htf-macd-fast1:12}")
    private int htfMacdFast1;
    @Value("${strategy.rfdw.htf-macd-slow1:26}")
    private int htfMacdSlow1;
    @Value("${strategy.rfdw.htf-macd-signal1:9}")
    private int htfMacdSignal1;
    @Value("${strategy.rfdw.htf-macd-filter-mode1:HISTOGRAM}")
    private String htfMacdFilterMode1;

    // 高周期 MACD 过滤2
    @Value("${strategy.rfdw.use-htf-macd-filter2:false}")
    private boolean useHTFMacdFilter2;
    @Value("${strategy.rfdw.htf-macd-resolution2:15}")
    private String htfMacdResolution2;
    @Value("${strategy.rfdw.htf-macd-fast2:12}")
    private int htfMacdFast2;
    @Value("${strategy.rfdw.htf-macd-slow2:26}")
    private int htfMacdSlow2;
    @Value("${strategy.rfdw.htf-macd-signal2:9}")
    private int htfMacdSignal2;
    @Value("${strategy.rfdw.htf-macd-filter-mode2:HISTOGRAM}")
    private String htfMacdFilterMode2;

    // 双 Swing 横盘过滤与突破
    @Value("${strategy.rfdw.use-dual-swing-filter:true}")
    private boolean useDualSwingFilter;
    @Value("${strategy.rfdw.swing-lookback:3}")
    private int swingLookback;
    @Value("${strategy.rfdw.swing-allowed-equal:0}")
    private int swingAllowedEqual;
    @Value("${strategy.rfdw.swing-recent-bars:55}")
    private int swingRecentBars;
    @Value("${strategy.rfdw.swing-range-threshold:0.04}")
    private double swingRangeThreshold;
    @Value("${strategy.rfdw.allow-breakout-in-ranging:true}")
    private boolean allowBreakoutInRanging;
    @Value("${strategy.rfdw.breakout-confirmation-bars:1}")
    private int breakoutConfirmationBars;

    // 波动率自适应
    @Value("${strategy.rfdw.range-quantity-low:1.618}")
    private double rangeQuantityLow;
    @Value("${strategy.rfdw.range-quantity-high:2.618}")
    private double rangeQuantityHigh;
    @Value("${strategy.rfdw.atr-threshold:0.8}")
    private double atrThreshold;
    @Value("${strategy.rfdw.atr-period-for-dynamic:30}")
    private int atrPeriodForDynamic;

    // 时间过滤
    @Value("${strategy.rfdw.use-time-filter:false}")
    private boolean useTimeFilter;
    @Value("${strategy.rfdw.filtered-weekdays:6}")
    private String filteredWeekdays;

    // 价格变动过滤
    @Value("${strategy.rfdw.price-move-filter-enabled:false}")
    private boolean priceMoveFilterEnabled;
    @Value("${strategy.rfdw.price-move-threshold:2.0}")
    private double priceMoveThreshold;

    // 横盘交易模式
    @Value("${strategy.rfdw.enable-range-trading:false}")
    private boolean enableRangeTrading;
    @Value("${strategy.rfdw.range-entry-distance:0.001}")
    private double rangeEntryDistance;
    @Value("${strategy.rfdw.range-use-filters:false}")
    private boolean rangeUseFilters;
    @Value("${strategy.rfdw.range-boundary-type:AVERAGE}")
    private String rangeBoundaryType;

    // SMC 订单块横盘
    @Value("${strategy.rfdw.use-smc-orderblock-range:false}")
    private boolean useSmcOrderBlockRange;
    @Value("${strategy.rfdw.smc-range-threshold-percent:2.0}")
    private double smcRangeThresholdPercent;

    // 风险模块（未完全使用，保留）
    @Value("${strategy.bollinger-rsi.use-risk-module:false}")
    private boolean useRiskModule;
    @Value("${strategy.bollinger-rsi.risk-module-evaluators:}")
    private String riskModuleEvaluators;

    @Autowired(required = false)
    private PointValueProvider pointValueProvider;

    // ==================== 内部 Record 定义 ====================
    private record RangeSignalRecord(String direction, double highBound, double lowBound, long signalTime) {}
    private record SwingAnalysisResult(
            double highAvg, double lowAvg, double closeHighAvg, double closeLowAvg,
            boolean valid,
            double recentHigh, double recentLow,
            double recentCloseHigh, double recentCloseLow,
            double secondHigh, double secondLow,
            double secondCloseHigh, double secondCloseLow
    ) {
        SwingAnalysisResult() {
            this(0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
    private enum MarketState { RANGING, BREAKOUT_UP, BREAKOUT_DOWN, TRENDING }
    private record MarketStateResult(MarketState state, SwingAnalysisResult swingResult) {}
    private record SmcRangeResult(boolean ranging, double[] bounds) {}

    // ==================== 主入口 ====================
    @Override
    public BuyAndSellWeightDto execute(IndicatorCalcDto calcDto) {
        applyConfiguredParams();
        applyOverrideParams(calcDto.getParameterOverrides());

        List<Candlestick> allKLines = calcDto.getKLines();
        String symbol = calcDto.getSymbol();
        int requiredBars = Math.max(rangePeriod, smoothPeriod) + 5;

        if (allKLines.size() < requiredBars + 1) {
            log.debug("数据不足，返回空结果。当前K线数量: {}, 需要至少: {}", allKLines.size(), requiredBars + 1);
            return new BuyAndSellWeightDto();
        }

        List<Candlestick> completedKLines = allKLines.subList(0, allKLines.size() - 1);
        Candlestick currentCandle = allKLines.get(allKLines.size() - 1);

        if (isTimeFiltered(currentCandle)) {
            log.debug("交易对 {} 当前K线时间被过滤，不生成信号", symbol);
            return new BuyAndSellWeightDto();
        }

        logInfoRange(completedKLines, symbol);

        String rawSignal = analyzeSignal(completedKLines, symbol);
        if ("HOLD".equals(rawSignal)) {
            return new BuyAndSellWeightDto();
        }

        return buildResult(rawSignal, calcDto, symbol, currentCandle.getId(),currentCandle.getOpenPrice());
    }

    // ==================== 信号处理与结果构建 ====================
    private void logInfoRange(List<Candlestick> kLines, String symbol) {
        long firstTime = kLines.get(0).getId();
        long lastTime = kLines.get(kLines.size() - 1).getId();
        log.info("处理已完成K线范围: {} - {}, 交易对: {}",
                DateUtil.longConvertDateTime(firstTime),
                DateUtil.longConvertDateTime(lastTime), symbol);
    }

    private BuyAndSellWeightDto buildResult(String finalSignal, IndicatorCalcDto calcDto, String symbol, long signalTime, BigDecimal currentPrice) {
        BuyAndSellWeightDto result = new BuyAndSellWeightDto();
        if (SignalType.LONG.name().equals(finalSignal)) {
            result.setSignalType(SignalType.LONG);
            logSignal(symbol, signalTime, "买入");
            Long signalId = saveSign(calcDto, result.getSignalType(), DateUtil.longConvertDateTime(signalTime), null, null);
            result.setSignalId(signalId);
        } else if (SignalType.SHORT.name().equals(finalSignal)) {
            result.setSignalType(SignalType.SHORT);
            logSignal(symbol, signalTime, "卖出");
            Long signalId = saveSign(calcDto, result.getSignalType(), DateUtil.longConvertDateTime(signalTime), null, null);
            result.setSignalId(signalId);
        }
        return result;
    }

    private void logSignal(String symbol, long signalTime, String type) {
        log.info("交易对: {}, 信号时间(当前K线开始): {}, Range Filter {}信号确认",
                symbol, DateUtil.longConvertDateTime(signalTime), type);
    }

    // ==================== SMC 订单块横盘判断 ====================
    private SmcRangeResult isRangeBySmcOrderBlocks(String symbol, long signalTimeMs, double currentPrice) {
        if (!useSmcOrderBlockRange) return new SmcRangeResult(false, null);

        SmcBarResult smc15 = computeSmcSnapshot(symbol, CandlestickIntervalEnum.OKXMIN15, signalTimeMs);
        SmcBarResult smc1h = computeSmcSnapshot(symbol, CandlestickIntervalEnum.OKXMIN60, signalTimeMs);
        if (smc15 == null && smc1h == null) return new SmcRangeResult(false, null);

        SmcRangeResult range15 = evaluateSmcRange(smc15, currentPrice);
        SmcRangeResult range1h = evaluateSmcRange(smc1h, currentPrice);
        boolean ranging = range15.ranging || range1h.ranging;

        if (detailedLog) {
            log.debug("SMC 订单块横盘判断: 15m={}, 1h={}, 阈值={}%",
                    range15.ranging, range1h.ranging, smcRangeThresholdPercent);
        }
        if (!ranging) return new SmcRangeResult(false, null);

        Double highBound = null, lowBound = null;
        if (range15.ranging && range15.bounds != null && range15.bounds.length >= 2) {
            highBound = range15.bounds[0];
            lowBound = range15.bounds[1];
        }
        if (range1h.ranging && range1h.bounds != null && range1h.bounds.length >= 2) {
            double hb = range1h.bounds[0];
            double lb = range1h.bounds[1];
            if (highBound == null || hb < highBound) highBound = hb;
            if (lowBound == null || lb > lowBound) lowBound = lb;
        }
        if (highBound == null || lowBound == null) return new SmcRangeResult(true, null);
        if (lowBound >= highBound) {
            if (detailedLog) log.debug("SMC 订单块边界无有效横盘区间: highBound={}, lowBound={}", highBound, lowBound);
            return new SmcRangeResult(true, null);
        }
        return new SmcRangeResult(true, new double[]{highBound, lowBound});
    }

    private SmcRangeResult evaluateSmcRange(SmcBarResult smc, double currentPrice) {
        if (smc == null) return new SmcRangeResult(false, null);
        List<SmcOrderBlock> internalBlocks = smc.getInternalOrderBlocks();
        if (internalBlocks == null || internalBlocks.isEmpty()) return new SmcRangeResult(false, null);

        SmcOrderBlock nearestBullish = null, nearestBearish = null;
        long latestBullishTime = 0, latestBearishTime = 0;
        for (SmcOrderBlock ob : internalBlocks) {
            Integer bias = ob.getBias();
            Long time = ob.getTime();
            if (bias == null || time == null) continue;
            if (bias == 1 && (nearestBullish == null || time > latestBullishTime)) {
                nearestBullish = ob;
                latestBullishTime = time;
            } else if (bias == -1 && (nearestBearish == null || time > latestBearishTime)) {
                nearestBearish = ob;
                latestBearishTime = time;
            }
        }
        if (nearestBullish == null || nearestBearish == null) return new SmcRangeResult(false, null);

        double demandLow = nearestBullish.getLow();
        double demandHigh = nearestBullish.getHigh();
        double supplyLow = nearestBearish.getLow();
        double supplyHigh = nearestBearish.getHigh();

        double gap;
        if (demandHigh < supplyLow) gap = supplyLow - demandHigh;
        else if (supplyHigh < demandLow) gap = demandLow - supplyHigh;
        else gap = 0;
        double gapPercent = (gap / currentPrice) * 100;
        boolean ranging = gapPercent <= smcRangeThresholdPercent;
        if (!ranging) return new SmcRangeResult(false, null);

        if (supplyLow <= demandHigh) return new SmcRangeResult(true, null);
        return new SmcRangeResult(true, new double[]{supplyLow, demandHigh});
    }

    // ==================== Swing 分析 ====================
    private SwingAnalysisResult analyzeSwingPoints(List<Candlestick> kLines) {
        if (kLines.size() < swingLookback * 2 + swingRecentBars) {
            return new SwingAnalysisResult();
        }
        try {
            BarSeries series = IndicatorWrapHelper.buildSeries(kLines);
            HighPriceIndicator highIndicator = new HighPriceIndicator(series);
            RecentFractalSwingHighIndicator swingHigh = new RecentFractalSwingHighIndicator(highIndicator,
                    swingLookback, swingLookback, swingAllowedEqual);
            LowPriceIndicator lowIndicator = new LowPriceIndicator(series);
            RecentFractalSwingLowIndicator swingLow = new RecentFractalSwingLowIndicator(lowIndicator,
                    swingLookback, swingLookback, swingAllowedEqual);
            ClosePriceIndicator closeIndicator = new ClosePriceIndicator(series);
            RecentFractalSwingHighIndicator closeSwingHigh = new RecentFractalSwingHighIndicator(closeIndicator,
                    swingLookback, swingLookback, swingAllowedEqual);
            RecentFractalSwingLowIndicator closeSwingLow = new RecentFractalSwingLowIndicator(closeIndicator,
                    swingLookback, swingLookback, swingAllowedEqual);

            List<Double> swingHighs = new ArrayList<>(), swingLows = new ArrayList<>();
            List<Double> closeHighs = new ArrayList<>(), closeLows = new ArrayList<>();
            double recentHigh = 0, recentLow = 0, recentCloseHigh = 0, recentCloseLow = 0;

            int startIdx = Math.max(0, series.getEndIndex() - swingRecentBars);
            for (int i = startIdx; i <= series.getEndIndex(); i++) {
                Num hv = swingHigh.getValue(i);
                if (hv != null && !hv.isNaN()) {
                    swingHighs.add(hv.doubleValue());
                    recentHigh = hv.doubleValue();
                }
                Num lv = swingLow.getValue(i);
                if (lv != null && !lv.isNaN()) {
                    swingLows.add(lv.doubleValue());
                    recentLow = lv.doubleValue();
                }
                Num chv = closeSwingHigh.getValue(i);
                if (chv != null && !chv.isNaN()) {
                    closeHighs.add(chv.doubleValue());
                    recentCloseHigh = chv.doubleValue();
                }
                Num clv = closeSwingLow.getValue(i);
                if (clv != null && !clv.isNaN()) {
                    closeLows.add(clv.doubleValue());
                    recentCloseLow = clv.doubleValue();
                }
            }

            if (swingHighs.isEmpty() || swingLows.isEmpty() || closeHighs.isEmpty() || closeLows.isEmpty()) {
                return new SwingAnalysisResult();
            }

            double highAvg = swingHighs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double lowAvg = swingLows.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double closeHighAvg = closeHighs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double closeLowAvg = closeLows.stream().mapToDouble(Double::doubleValue).average().orElse(0);

            double secondHigh = 0, secondLow = 0, secondCloseHigh = 0, secondCloseLow = 0;
            if (swingHighs.size() >= 2) {
                secondHigh = swingHighs.stream().sorted(Collections.reverseOrder()).skip(1).findFirst().orElse(0.0);
            }
            if (swingLows.size() >= 2) {
                secondLow = swingLows.stream().sorted().skip(1).findFirst().orElse(0.0);
            }
            if (closeHighs.size() >= 2) {
                secondCloseHigh = closeHighs.stream().sorted(Collections.reverseOrder()).skip(1).findFirst().orElse(0.0);
            }
            if (closeLows.size() >= 2) {
                secondCloseLow = closeLows.stream().sorted().skip(1).findFirst().orElse(0.0);
            }

            return new SwingAnalysisResult(highAvg, lowAvg, closeHighAvg, closeLowAvg, true,
                    recentHigh, recentLow, recentCloseHigh, recentCloseLow,
                    secondHigh, secondLow, secondCloseHigh, secondCloseLow);
        } catch (Exception e) {
            log.error("Swing点分析异常: {}", e.getMessage(), e);
            return new SwingAnalysisResult();
        }
    }

    @Override
    protected void enrichWeightRuleContext(WeightRuleContext ctx, IndicatorCalcDto calcDto) {
        List<Candlestick> kLines = calcDto.getKLines();
        if (kLines == null || kLines.isEmpty()) return;
        long signalTimeMs = kLines.get(kLines.size() - 1).getId();
        double currentPrice = kLines.get(kLines.size() - 1).getClosePrice().doubleValue();
        String symbol = calcDto.getSymbol();

        SwingAnalysisResult swing = analyzeSwingPoints(kLines);
        if (swing.valid()) {
            double highLowRange = swing.highAvg() - swing.lowAvg();
            double closeRange = swing.closeHighAvg() - swing.closeLowAvg();
            boolean isRanging = (highLowRange / currentPrice) < swingRangeThreshold &&
                    (closeRange / currentPrice) < swingRangeThreshold;
            ctx.setSwingRanging(isRanging);

            boolean breakoutUp = currentPrice > swing.highAvg();
            boolean breakoutDown = currentPrice < swing.lowAvg();
            if (breakoutConfirmationBars > 1 && kLines.size() >= breakoutConfirmationBars) {
                boolean confirmedUp = true, confirmedDown = true;
                for (int i = kLines.size() - breakoutConfirmationBars; i < kLines.size(); i++) {
                    double close = kLines.get(i).getClosePrice().doubleValue();
                    if (close <= swing.highAvg()) confirmedUp = false;
                    if (close >= swing.lowAvg()) confirmedDown = false;
                }
                breakoutUp = breakoutUp && confirmedUp;
                breakoutDown = breakoutDown && confirmedDown;
            }
            if (breakoutUp) ctx.setSwingBreakout(1);
            else if (breakoutDown) ctx.setSwingBreakout(-1);
            else ctx.setSwingBreakout(0);
        }

        SmcRangeResult smcRange = isRangeBySmcOrderBlocks(symbol, signalTimeMs, currentPrice);
        ctx.setSmcObRanging(smcRange.ranging());

        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(signalTimeMs), ZoneId.systemDefault());
        ctx.setWeekday(dt.getDayOfWeek().getValue());
    }


    // ==================== Range Filter 核心算法 ====================
    private String analyzeSignal(List<Candlestick> kLines, String symbol) {
        int size = kLines.size();
        int requiredBars = Math.max(rangePeriod, smoothPeriod) + 2;
        if (size < requiredBars) return "HOLD";

        double dynamicRangeQty = rangeQuantity;
        double prevClose = 0, prevMid = 0, prevFilt = Double.NaN, prevFdir = 0;
        int prevCondIni = 0;
        double rfiltPrev = 0, rfiltCurrent = 0, prevRawFilt = Double.NaN;
        double[] emaAtr = new double[]{Double.NaN};
        double[] emaAc = new double[]{Double.NaN};
        double[] emaRngSmooth = new double[]{Double.NaN};
        SMAHolder smaMid = new SMAHolder(rangePeriod);
        SMAHolder smaMid2 = new SMAHolder(rangePeriod);
        double[] emaRngFilt2 = new double[]{Double.NaN};
        double[] emaHiBand2 = new double[]{Double.NaN};
        double[] emaLoBand2 = new double[]{Double.NaN};
        String finalSignal = "HOLD";

        for (int i = 0; i < size; i++) {
            Candlestick candle = kLines.get(i);
            double high = candle.getHighPrice().doubleValue();
            double low = candle.getLowPrice().doubleValue();
            double close = candle.getClosePrice().doubleValue();
            double hVal, lVal;
            if ("Wicks".equalsIgnoreCase(movementSource)) {
                hVal = high;
                lVal = low;
            } else {
                hVal = close;
                lVal = close;
            }
            double mid = (hVal + lVal) / 2;

            if (i == 0) {
                prevClose = close;
                prevMid = mid;
                rfiltCurrent = mid;
                rfiltPrev = mid;
                prevFilt = mid;
                prevRawFilt = mid;
                continue;
            }

            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            updateConditionalEMA(true, tr, rangePeriod, emaAtr);
            double change = Math.abs(mid - prevMid);
            updateConditionalEMA(true, change, rangePeriod, emaAc);
            smaMid.add(mid);
            smaMid2.add(mid * mid);
            double sd = Math.sqrt(smaMid2.getAverage() - Math.pow(smaMid.getAverage(), 2));
            double rng = calculateRangeSize(close, emaAtr[0], emaAc[0], sd, dynamicRangeQty, symbol);
            if (smoothRange) {
                updateConditionalEMA(true, rng, smoothPeriod, emaRngSmooth);
                rng = emaRngSmooth[0];
            }
            double r = rng;

            rfiltPrev = rfiltCurrent;
            double newRfilt = rfiltPrev;
            if ("Type 1".equalsIgnoreCase(filterType)) {
                if (hVal - r > rfiltPrev) newRfilt = hVal - r;
                if (lVal + r < rfiltPrev) newRfilt = lVal + r;
            } else {
                if (hVal >= rfiltPrev + r) {
                    double mult = Math.floor(Math.abs(hVal - rfiltPrev) / r);
                    newRfilt = rfiltPrev + mult * r;
                }
                if (lVal <= rfiltPrev - r) {
                    double mult = Math.floor(Math.abs(lVal - rfiltPrev) / r);
                    newRfilt = rfiltPrev - mult * r;
                }
            }
            rfiltCurrent = newRfilt;
            double filt = rfiltCurrent;
            double hiBand = filt + r;
            double loBand = filt - r;

            if (averageFilterChanges) {
                boolean changed = !Double.isNaN(prevRawFilt) && Math.abs(rfiltCurrent - prevRawFilt) > 1e-12;
                updateConditionalEMA(changed, rfiltCurrent, averageSamples, emaRngFilt2);
                updateConditionalEMA(changed, hiBand, averageSamples, emaHiBand2);
                updateConditionalEMA(changed, loBand, averageSamples, emaLoBand2);
                if (!Double.isNaN(emaRngFilt2[0])) filt = emaRngFilt2[0];
                prevRawFilt = rfiltCurrent;
            } else {
                prevRawFilt = rfiltCurrent;
            }

            double fdir;
            if (!Double.isNaN(prevFilt)) {
                if (filt > prevFilt) fdir = 1;
                else if (filt < prevFilt) fdir = -1;
                else fdir = prevFdir;
            } else {
                fdir = 0;
            }
            prevFilt = filt;
            prevFdir = fdir;

            boolean upward = fdir == 1, downward = fdir == -1;
            boolean longCond = (close > filt && close > prevClose && upward) || (close > filt && close < prevClose && upward);
            boolean shortCond = (close < filt && close < prevClose && downward) || (close < filt && close > prevClose && downward);
            int condIni;
            if (longCond) condIni = 1;
            else if (shortCond) condIni = -1;
            else condIni = prevCondIni;

            boolean longCondition = longCond && prevCondIni == -1;
            boolean shortCondition = shortCond && prevCondIni == 1;

            if (detailedLog) {
                log.info("Bar[{}]: time={}, close={}, filt={}, r={}, fdir={}, longCond={}, shortCond={}, condIni={}, prevCondIni={}, longCondition={}, shortCondition={}",
                        i, DateUtil.longConvertDateTime(candle.getId()), close, filt, r, fdir,
                        longCond, shortCond, condIni, prevCondIni, longCondition, shortCondition);
            }

            prevClose = close;
            prevMid = mid;
            prevCondIni = condIni;

            if (i == size - 1) {
                if (longCondition) finalSignal = "LONG";
                else if (shortCondition) finalSignal = "SHORT";
                else finalSignal = "HOLD";
            }
        }
        return finalSignal;
    }

    private double calculateRangeSize(double close, double atr, double ac, double sd, double rangeQty, String symbol) {
        switch (rangeScale) {
            case "Pips": return rangeQty * 0.0001;
            case "Points": return rangeQty * getPointValue(symbol);
            case "Ticks": return rangeQty * getMinTick(symbol);
            case "% of Price": return close * rangeQty / 100.0;
            case "ATR": return Double.isNaN(atr) ? 0 : rangeQty * atr;
            case "Average Change": return Double.isNaN(ac) ? 0 : rangeQty * ac;
            case "Standard Deviation": return Double.isNaN(sd) ? 0 : rangeQty * sd;
            default: return rangeQty;
        }
    }

    // ==================== 辅助工具 ====================
    private void updateConditionalEMA(boolean condition, double x, int n, double[] holder) {
        if (condition) {
            if (Double.isNaN(holder[0])) holder[0] = x;
            else holder[0] = (x - holder[0]) * (2.0 / (n + 1)) + holder[0];
        }
    }

    private static class SMAHolder {
        private final int period;
        private final LinkedList<Double> values = new LinkedList<>();
        private double sum = 0;
        SMAHolder(int period) { this.period = period; }
        void add(double value) {
            values.addLast(value);
            sum += value;
            if (values.size() > period) sum -= values.removeFirst();
        }
        double getAverage() { return values.isEmpty() ? Double.NaN : sum / values.size(); }
    }

    private boolean isTimeFiltered(Candlestick currentCandle) {
        if (!useTimeFilter) return false;
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentCandle.getId()), ZoneId.systemDefault());
        int dayOfWeek = dt.getDayOfWeek().getValue();
        for (String part : filteredWeekdays.split(",")) {
            try {
                if (dayOfWeek == Integer.parseInt(part.trim())) {
                    log.info("当前时间 {} 属于过滤星期 {}, 信号被抑制", dt, dayOfWeek);
                    return true;
                }
            } catch (NumberFormatException e) {
                log.warn("无效的星期配置: {}", part);
            }
        }
        return false;
    }

    protected double getPointValue(String symbol) {
        return pointValueProvider != null ? pointValueProvider.getPointValue(symbol) : 1.0;
    }
    protected double getMinTick(String symbol) {
        return pointValueProvider != null ? pointValueProvider.getMinTick(symbol) : 0.01;
    }

    // ==================== 参数加载（统一抽取） ====================
    private void applyConfiguredParams() {
        this.signalServiceKey = SERVICE_KEY;
        if (signalServiceConfigService == null) return;
        Map<String, Object> params = signalServiceConfigService.getParams(SERVICE_KEY);
        if (params == null || params.isEmpty()) return;

        filterType = getString(params, "filterType", filterType);
        movementSource = getString(params, "movementSource", movementSource);
        rangeQuantity = getDouble(params, "rangeQuantity", rangeQuantity);
        rangeScale = getString(params, "rangeScale", rangeScale);
        rangePeriod = getInt(params, "rangePeriod", rangePeriod);
        smoothRange = getBoolean(params, "smoothRange", smoothRange);
        smoothPeriod = getInt(params, "smoothPeriod", smoothPeriod);
        averageFilterChanges = getBoolean(params, "averageFilterChanges", averageFilterChanges);
        averageSamples = getInt(params, "averageSamples", averageSamples);
        detailedLog = getBoolean(params, "detailedLog", detailedLog);
        useEmaFilter = getBoolean(params, "useEmaFilter", useEmaFilter);
        baseEmaPeriod = getInt(params, "baseEmaPeriod", baseEmaPeriod);
        momentumEmaPeriod = getInt(params, "momentumEmaPeriod", momentumEmaPeriod);
        emaFilterMode = normalizeEmaFilterMode(getString(params, "emaFilterMode", emaFilterMode));
        useHTFMacdFilter1 = getBoolean(params, "useHTFMacdFilter1", useHTFMacdFilter1);
        htfMacdResolution1 = getString(params, "htfMacdResolution1", htfMacdResolution1);
        htfMacdFast1 = getInt(params, "htfMacdFast1", htfMacdFast1);
        htfMacdSlow1 = getInt(params, "htfMacdSlow1", htfMacdSlow1);
        htfMacdSignal1 = getInt(params, "htfMacdSignal1", htfMacdSignal1);
        htfMacdFilterMode1 = getString(params, "htfMacdFilterMode1", htfMacdFilterMode1);
        useHTFMacdFilter2 = getBoolean(params, "useHTFMacdFilter2", useHTFMacdFilter2);
        htfMacdResolution2 = getString(params, "htfMacdResolution2", htfMacdResolution2);
        htfMacdFast2 = getInt(params, "htfMacdFast2", htfMacdFast2);
        htfMacdSlow2 = getInt(params, "htfMacdSlow2", htfMacdSlow2);
        htfMacdSignal2 = getInt(params, "htfMacdSignal2", htfMacdSignal2);
        htfMacdFilterMode2 = getString(params, "htfMacdFilterMode2", htfMacdFilterMode2);
        useRiskModule = getBoolean(params, "useRiskModule", useRiskModule);
        riskModuleEvaluators = getString(params, "riskModuleEvaluators", riskModuleEvaluators);
        useDualSwingFilter = getBoolean(params, "useDualSwingFilter", useDualSwingFilter);
        swingLookback = getInt(params, "swingLookback", swingLookback);
        swingAllowedEqual = getInt(params, "swingAllowedEqual", swingAllowedEqual);
        swingRecentBars = getInt(params, "swingRecentBars", swingRecentBars);
        swingRangeThreshold = getDouble(params, "swingRangeThreshold", swingRangeThreshold);
        allowBreakoutInRanging = getBoolean(params, "allowBreakoutInRanging", allowBreakoutInRanging);
        breakoutConfirmationBars = getInt(params, "breakoutConfirmationBars", breakoutConfirmationBars);
        rangeQuantityLow = getDouble(params, "rangeQuantityLow", rangeQuantityLow);
        rangeQuantityHigh = getDouble(params, "rangeQuantityHigh", rangeQuantityHigh);
        atrThreshold = getDouble(params, "atrThreshold", atrThreshold);
        atrPeriodForDynamic = getInt(params, "atrPeriodForDynamic", atrPeriodForDynamic);
        priceMoveFilterEnabled = getBoolean(params, "priceMoveFilterEnabled", priceMoveFilterEnabled);
        priceMoveThreshold = getDouble(params, "priceMoveThreshold", priceMoveThreshold);
        enableRangeTrading = getBoolean(params, "enableRangeTrading", enableRangeTrading);
        rangeEntryDistance = getDouble(params, "rangeEntryDistance", rangeEntryDistance);
        rangeUseFilters = getBoolean(params, "rangeUseFilters", rangeUseFilters);
        rangeBoundaryType = getString(params, "rangeBoundaryType", rangeBoundaryType);
        useSmcOrderBlockRange = getBoolean(params, "useSmcOrderBlockRange", useSmcOrderBlockRange);
        smcRangeThresholdPercent = getDouble(params, "smcRangeThresholdPercent", smcRangeThresholdPercent);
        // SMC 权重参数（覆盖 DefaultSignService 默认值）
        this.smcStopLossOffset = getDouble(params, "smcStopLossOffset", this.smcStopLossOffset);
        this.smcMinTargetSpaceRatio = getDouble(params, "smcMinTargetSpaceRatio", this.smcMinTargetSpaceRatio);
        this.maxRiskPercent = getDouble(params, "maxRiskPercent", this.maxRiskPercent * 100) / 100.0;
        this.minRR = getDouble(params, "minRR", this.minRR);
        this.useEmaScore = getBoolean(params, "useEmaScore", this.useEmaScore);
    }

    private void applyOverrideParams(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) return;
        filterType = getStringOverride(overrides, "filterType", filterType);
        movementSource = getStringOverride(overrides, "movementSource", movementSource);
        rangeQuantity = getDoubleOverride(overrides, "rangeQuantity", rangeQuantity);
        rangeScale = getStringOverride(overrides, "rangeScale", rangeScale);
        rangePeriod = getIntOverride(overrides, "rangePeriod", rangePeriod);
        smoothRange = getBooleanOverride(overrides, "smoothRange", smoothRange);
        smoothPeriod = getIntOverride(overrides, "smoothPeriod", smoothPeriod);
        averageFilterChanges = getBooleanOverride(overrides, "averageFilterChanges", averageFilterChanges);
        averageSamples = getIntOverride(overrides, "averageSamples", averageSamples);
        detailedLog = getBooleanOverride(overrides, "detailedLog", detailedLog);
        useHTFMacdFilter1 = getBooleanOverride(overrides, "useHTFMacdFilter1", useHTFMacdFilter1);
        htfMacdResolution1 = getStringOverride(overrides, "htfMacdResolution1", htfMacdResolution1);
        htfMacdFast1 = getIntOverride(overrides, "htfMacdFast1", htfMacdFast1);
        htfMacdSlow1 = getIntOverride(overrides, "htfMacdSlow1", htfMacdSlow1);
        htfMacdSignal1 = getIntOverride(overrides, "htfMacdSignal1", htfMacdSignal1);
        htfMacdFilterMode1 = getStringOverride(overrides, "htfMacdFilterMode1", htfMacdFilterMode1);
        useHTFMacdFilter2 = getBooleanOverride(overrides, "useHTFMacdFilter2", useHTFMacdFilter2);
        htfMacdResolution2 = getStringOverride(overrides, "htfMacdResolution2", htfMacdResolution2);
        htfMacdFast2 = getIntOverride(overrides, "htfMacdFast2", htfMacdFast2);
        htfMacdSlow2 = getIntOverride(overrides, "htfMacdSlow2", htfMacdSlow2);
        htfMacdSignal2 = getIntOverride(overrides, "htfMacdSignal2", htfMacdSignal2);
        htfMacdFilterMode2 = getStringOverride(overrides, "htfMacdFilterMode2", htfMacdFilterMode2);
        useRiskModule = getBooleanOverride(overrides, "useRiskModule", useRiskModule);
        riskModuleEvaluators = getStringOverride(overrides, "riskModuleEvaluators", riskModuleEvaluators);
        useDualSwingFilter = getBooleanOverride(overrides, "useDualSwingFilter", useDualSwingFilter);
        swingLookback = getIntOverride(overrides, "swingLookback", swingLookback);
        swingAllowedEqual = getIntOverride(overrides, "swingAllowedEqual", swingAllowedEqual);
        swingRecentBars = getIntOverride(overrides, "swingRecentBars", swingRecentBars);
        swingRangeThreshold = getDoubleOverride(overrides, "swingRangeThreshold", swingRangeThreshold);
        allowBreakoutInRanging = getBooleanOverride(overrides, "allowBreakoutInRanging", allowBreakoutInRanging);
        breakoutConfirmationBars = getIntOverride(overrides, "breakoutConfirmationBars", breakoutConfirmationBars);
        rangeQuantityLow = getDoubleOverride(overrides, "rangeQuantityLow", rangeQuantityLow);
        rangeQuantityHigh = getDoubleOverride(overrides, "rangeQuantityHigh", rangeQuantityHigh);
        atrThreshold = getDoubleOverride(overrides, "atrThreshold", atrThreshold);
        atrPeriodForDynamic = getIntOverride(overrides, "atrPeriodForDynamic", atrPeriodForDynamic);
        priceMoveFilterEnabled = getBooleanOverride(overrides, "priceMoveFilterEnabled", priceMoveFilterEnabled);
        priceMoveThreshold = getDoubleOverride(overrides, "priceMoveThreshold", priceMoveThreshold);
        enableRangeTrading = getBooleanOverride(overrides, "enableRangeTrading", enableRangeTrading);
        rangeEntryDistance = getDoubleOverride(overrides, "rangeEntryDistance", rangeEntryDistance);
        rangeUseFilters = getBooleanOverride(overrides, "rangeUseFilters", rangeUseFilters);
        rangeBoundaryType = getStringOverride(overrides, "rangeBoundaryType", rangeBoundaryType);
        useSmcOrderBlockRange = getBooleanOverride(overrides, "useSmcOrderBlockRange", useSmcOrderBlockRange);
        smcRangeThresholdPercent = getDoubleOverride(overrides, "smcRangeThresholdPercent", smcRangeThresholdPercent);
    }

    // 通用参数提取方法（简化）
    private String getString(Map<String, Object> params, String key, String def) {
        Object v = params.get(key);
        return v == null ? def : String.valueOf(v);
    }
    private int getInt(Map<String, Object> params, String key, int def) {
        Object v = params.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return def; }
    }
    private double getDouble(Map<String, Object> params, String key, double def) {
        Object v = params.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (Exception e) { return def; }
    }
    private boolean getBoolean(Map<String, Object> params, String key, boolean def) {
        Object v = params.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        String s = String.valueOf(v).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) ? true : "false".equals(s) || "0".equals(s) || "no".equals(s) ? false : def;
    }

    private String getStringOverride(Map<String, String> ov, String key, String def) {
        String v = ov.get(key);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }
    private int getIntOverride(Map<String, String> ov, String key, int def) {
        String v = ov.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return def; }
    }
    private double getDoubleOverride(Map<String, String> ov, String key, double def) {
        String v = ov.get(key);
        if (v == null) return def;
        try { return Double.parseDouble(v.trim()); } catch (Exception e) { return def; }
    }
    private boolean getBooleanOverride(Map<String, String> ov, String key, boolean def) {
        String v = ov.get(key);
        if (v == null) return def;
        String s = v.trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) ? true : "false".equals(s) || "0".equals(s) || "no".equals(s) ? false : def;
    }

    private String normalizeEmaFilterMode(String mode) {
        if (mode == null) return emaFilterMode;
        String upper = mode.trim().toUpperCase();
        switch (upper) {
            case "ANY": return "EITHER";
            case "BASE": return "BASE_ONLY";
            case "MOMENTUM": return "MOMENTUM_ONLY";
            default: return upper;
        }
    }

    // ==================== 接口定义 ====================
    public interface PointValueProvider {
        double getPointValue(String symbol);
        double getMinTick(String symbol);
    }
}