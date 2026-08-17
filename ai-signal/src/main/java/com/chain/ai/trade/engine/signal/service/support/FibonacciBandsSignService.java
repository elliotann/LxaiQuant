package com.chain.ai.trade.engine.signal.service.support;


import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;

import static com.chain.ai.trade.common.entity.constants.SignalType.*;
import com.chain.ai.trade.engine.signal.service.DefaultSignService;
import com.chain.ai.trade.extension.ta4j.indicator.HLCAvgIndicator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.math.BigDecimal;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

import java.util.function.BiFunction;

/**
 * 斐波那契入场带策略服务
 *
 * 完全对应TradingView Pine Script: "Fibonacci Entry Bands [AlgoAlpha]"
 * 实现了完整的斐波那契轨道系统，包括：
 * - 双重EMA作为基础线
 * - ATR/标准差波动率计算
 * - 6条斐波那契轨道（0.618, 1.0, 1.618, 2.618倍）
 * - 趋势判断和多种信号类型
 * - 可调节的止盈 aggressiveness
 */
@Slf4j
@Service
public class FibonacciBandsSignService extends DefaultSignService {

    /** 信号服务标识，用于关联配置和权重规则 */
    public static final String SERVICE_KEY = "FibonacciBandsSignService";

    public FibonacciBandsSignService() {
        this.signalServiceKey = SERVICE_KEY;
    }

    // 默认参数值 - 完全对应Pine Script输入参数
    private static final int DEFAULT_LENGTH = 21;          // len = input.int(21, "Length")
    private static final int DEFAULT_ATR_LENGTH = 14;      // atrLen = input.int(14, "ATR Length")
    private static final String DEFAULT_TP_AGGRESSIVENESS = "low"; // tpAggressiveness = input.string("low", ...)
    private static final boolean USE_ATR = true;           // useATR = input.bool(true, "Use ATR")

    @Override
    public BuyAndSellWeightDto execute(IndicatorCalcDto calcDto) {
        // 获取参数或使用默认值 - 完全对应Pine Script输入
        int len = calcDto.getLengthOpen() > 0 ? calcDto.getLengthOpen() : DEFAULT_LENGTH;
        int atrLen = calcDto.getVolFactor() > 0 ? Integer.parseInt(calcDto.getVolFactor()+"") : DEFAULT_ATR_LENGTH;
        String tpAggressiveness = StringUtils.isNotEmpty(calcDto.getTpAggressiveness()) ?
                calcDto.getTpAggressiveness() : DEFAULT_TP_AGGRESSIVENESS;

        // 构建时间序列
        BarSeries series = IndicatorWrapHelper.buildSeries(calcDto.getKLines());
        // 使用上一根已完成K线，而不是当前未完成的K线
        int endIndex = series.getEndIndex() - 1;

        // 确保有足够的数据
        if (endIndex < len * 2 + 5) {
            log.warn("数据不足，无法计算策略。需要至少{}根K线，当前只有{}根", len * 2 + 5, endIndex + 1);
            return new BuyAndSellWeightDto();
        }

        // 1. 使用HLC3作为源数据 - 对应Pine Script: src = input.source(hlc3, ...)
        HLCAvgIndicator hlc3 = new HLCAvgIndicator(series);

        // 2. 计算双EMA作为basis - 对应Pine Script: basis = ta.ema(ta.ema(src, len), len)
        EMAIndicator firstEMA = new EMAIndicator(hlc3, len);
        EMAIndicator basis = new EMAIndicator(firstEMA, len);

        // 3. 计算波动率 - 完全对应Pine Script: vol = useATR ? ta.atr(atrLen) : ta.stdev(src, len)
        Indicator<Num> vol = USE_ATR ?
                new ATRIndicator(series, atrLen) :
                new StandardDeviationIndicator(hlc3, len);

        // 4. 斐波那契乘数 - 完全对应Pine Script
        Num mult1 = series.numFactory().numOf(0.618);
        Num mult2 = series.numFactory().numOf(1.0);
        Num mult3 = series.numFactory().numOf(1.618);
        Num mult4 = series.numFactory().numOf(2.618);

        // 5. 计算斐波那契轨道 - 对应Pine Script的upper1-4, lower1-4
        CombineIndicator upper1 = new CombineIndicator(basis, vol, (b, v) -> b.plus(v.multipliedBy(mult1)));
        CombineIndicator upper2 = new CombineIndicator(basis, vol, (b, v) -> b.plus(v.multipliedBy(mult2)));
        CombineIndicator upper3 = new CombineIndicator(basis, vol, (b, v) -> b.plus(v.multipliedBy(mult3)));
        CombineIndicator upper4 = new CombineIndicator(basis, vol, (b, v) -> b.plus(v.multipliedBy(mult4)));
        CombineIndicator lower1 = new CombineIndicator(basis, vol, (b, v) -> b.minus(v.multipliedBy(mult1)));
        CombineIndicator lower2 = new CombineIndicator(basis, vol, (b, v) -> b.minus(v.multipliedBy(mult2)));
        CombineIndicator lower3 = new CombineIndicator(basis, vol, (b, v) -> b.minus(v.multipliedBy(mult3)));
        CombineIndicator lower4 = new CombineIndicator(basis, vol, (b, v) -> b.minus(v.multipliedBy(mult4)));

        // 6. 趋势判断指标 - 完全对应Pine Script逻辑
        // 用数组顺序计算，避免 CachedIndicator 内部递归调用
        int seriesLength = endIndex + 1;
        Num[] trendValues = new Num[seriesLength];
        for (int i = 0; i < seriesLength; i++) {
            if (i < 1) {
                trendValues[i] = series.numFactory().numOf(0);
                continue;
            }
            Num basisCurrent = basis.getValue(i);
            Num basisPrevious = basis.getValue(i - 1);
            Num previousTrend = i > 0 ? trendValues[i - 1] : series.numFactory().numOf(0);

            if (basisCurrent.isGreaterThan(basisPrevious)) {
                trendValues[i] = series.numFactory().numOf(1);
            } else if (basisCurrent.isLessThan(basisPrevious)) {
                trendValues[i] = series.numFactory().numOf(-1);
            } else {
                trendValues[i] = previousTrend;
            }
        }

        // 将趋势数组封装为 Indicator，方便后续规则使用
        Indicator<Num> trend = new AbstractIndicator<Num>(series) {
            @Override
            public Num getValue(int index) {
                return index >= 0 && index < trendValues.length ? trendValues[index] : series.numFactory().numOf(0);
            }
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };

        // 7. 选择止盈轨道 - 对应Pine Script的switch语句
        Indicator<Num> tpLongBand;
        Indicator<Num> tpShortBand;

        switch (tpAggressiveness) {
            case "low":
                tpLongBand = lower4;
                tpShortBand = upper4;
                break;
            case "medium":
                tpLongBand = lower2;
                tpShortBand = upper2;
                break;
            case "high":
                tpLongBand = lower1;
                tpShortBand = upper1;
                break;
            default:
                tpLongBand = lower4;
                tpShortBand = upper4;
        }

        // 8. 入场信号规则 - 完全对应Pine Script逻辑

        // 基础入场信号 - 对应Pine Script: longSignal = ta.crossover(basis, basis[1])
        PreviousValueIndicator basisPrev = new PreviousValueIndicator(basis, 1);
        Rule longEntrySignal = new CrossedUpIndicatorRule(basis, basisPrev);
        Rule shortEntrySignal = new CrossedDownIndicatorRule(basis, basisPrev);

        // 反弹信号 - 完全对应Pine Script逻辑
        // longBounce = showBounce and trend == 1 and low < basis and close > basis and not longSignal
        Rule longBounceSignal = new Rule() {
            @Override
            public boolean isSatisfied(int index, TradingRecord tradingRecord) {
                if (index < 1) return false;

                Num trendVal = trend.getValue(index);
                Bar bar = series.getBar(index);
                Num low = bar.getLowPrice();
                Num close = bar.getClosePrice();
                Num basisVal = basis.getValue(index);

                // 完全对应Pine Script条件
                return trendVal.isEqual(series.numFactory().numOf(1)) &&           // trend == 1 (上升趋势)
                        low.isLessThan(basisVal) &&                   // low < basis
                        close.isGreaterThan(basisVal) &&              // close > basis
                        !longEntrySignal.isSatisfied(index, tradingRecord); // not longSignal
            }
        };

        // shortBounce = showBounce and trend == -1 and high > basis and close < basis and not shortSignal
        Rule shortBounceSignal = new Rule() {
            @Override
            public boolean isSatisfied(int index, TradingRecord tradingRecord) {
                if (index < 1) return false;

                Num trendVal = trend.getValue(index);
                Bar bar = series.getBar(index);
                Num high = bar.getHighPrice();
                Num close = bar.getClosePrice();
                Num basisVal = basis.getValue(index);

                // 完全对应Pine Script条件
                return trendVal.isEqual(series.numFactory().numOf(-1)) &&          // trend == -1 (下降趋势)
                        high.isGreaterThan(basisVal) &&               // high > basis
                        close.isLessThan(basisVal) &&                 // close < basis
                        !shortEntrySignal.isSatisfied(index, tradingRecord); // not shortSignal
            }
        };

        // 9. 止盈信号规则 - 完全对应Pine Script的rejectionLong/rejectionShort
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // rejectionLong = ta.crossunder(close, tpLongBand) and trend == -1
        Rule rejectionLong = new CrossedDownIndicatorRule(closePrice, tpLongBand)
                .and(new EqualIndicator(trend, series.numFactory().numOf(-1)));

        // rejectionShort = ta.crossover(close, tpShortBand) and trend == 1
        Rule rejectionShort = new CrossedUpIndicatorRule(closePrice, tpShortBand)
                .and(new EqualIndicator(trend, series.numFactory().numOf(1)));

        // 10. 检测信号 - 完全按照Pine Script逻辑，使用上一根已完成K线
        BuyAndSellWeightDto result = new BuyAndSellWeightDto();

        // 检测信号 - 完全按照Pine Script alertcondition顺序
        // 1. alertcondition(longSignal, title="Long Entry")
        if (longEntrySignal.isSatisfied(endIndex)) {
            log.info("斐波那契入场带策略-基础买入信号(basis上穿) K线时间:{}",
                    DateUtil.longConvertDateTime(calcDto.getKLines().get(endIndex).getId()));
            result.setSignalType(LONG);
        }
        // 2. alertcondition(shortSignal, title="Short Entry")
        else if (shortEntrySignal.isSatisfied(endIndex)) {
            log.info("斐波那契入场带策略-基础卖出信号(basis下穿) K线时间:{}",
                    DateUtil.longConvertDateTime(calcDto.getKLines().get(endIndex).getId()));
            result.setSignalType(SHORT);
        }
        // 3. alertcondition(rejectionLong, title="Rejection Long (TP for Short)")
        else if (rejectionLong.isSatisfied(endIndex)) {
            log.info("斐波那契入场带策略-空头止盈信号（平空） K线时间:{}",
                    DateUtil.longConvertDateTime(calcDto.getKLines().get(endIndex).getId()));
            result.setSignalType(CLOSE_SHORT);
        }
        // 4. alertcondition(rejectionShort, title="Rejection Short (TP for Long)")
        else if (rejectionShort.isSatisfied(endIndex)) {
            log.info("斐波那契入场带策略-多头止盈信号（平多） K线时间:{}",
                    DateUtil.longConvertDateTime(calcDto.getKLines().get(endIndex).getId()));
            result.setSignalType(CLOSE_LONG);
        }
        // 5. alertcondition(longBounce, title="Long Bounce")
        else if (longBounceSignal.isSatisfied(endIndex)) {
            log.info("斐波那契入场带策略-反弹买入信号 K线时间:{}",
                    DateUtil.longConvertDateTime(calcDto.getKLines().get(endIndex).getId()));
            result.setSignalType(LONG);
        }
        // 6. alertcondition(shortBounce, title="Short Bounce")
        else if (shortBounceSignal.isSatisfied(endIndex)) {
            log.info("斐波那契入场带策略-反弹卖出信号 K线时间:{}",
                    DateUtil.longConvertDateTime(calcDto.getKLines().get(endIndex).getId()));
            result.setSignalType(SHORT);
        }
        else {
            log.debug("斐波那契入场带策略-无信号 K线时间:{}",
                    DateUtil.longConvertDateTime(calcDto.getKLines().get(endIndex).getId()));
        }

        // 发送信号
        if (null != result.getSignalType()) {
            Long signalId = saveSign(calcDto, result.getSignalType());
            result.setSignalId(signalId);
        }

        return result;
    }

    @Override
    public BuyAndSellWeightDto executeClose(IndicatorCalcDto calcDto) {
        // 平仓逻辑使用相同的信号检测逻辑
        BuyAndSellWeightDto openSignal = execute(calcDto);

        if (openSignal.getSignalType() != null) {
            switch (openSignal.getSignalType()) {
                case LONG:
                    openSignal.setSignalType(CLOSE_LONG);
                    break;
                case SHORT:
                    openSignal.setSignalType(CLOSE_SHORT);
                    break;
                default:
                    // 保持原样
                    break;
            }
        }

        return openSignal;
    }


    // ===== 辅助类：组合两个指标 =====
    static class CombineIndicator extends AbstractIndicator<Num> {
        private final Indicator<Num> first;
        private final Indicator<Num> second;
        private final BiFunction<Num, Num, Num> function;

        public CombineIndicator(Indicator<Num> first, Indicator<Num> second,
                                BiFunction<Num, Num, Num> function) {
            super(first.getBarSeries());
            this.first = first;
            this.second = second;
            this.function = function;
        }

        @Override
        public Num getValue(int index) {
            return function.apply(first.getValue(index), second.getValue(index));
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

    }

    // ===== 辅助类：指标相等规则 =====
    static class EqualIndicator extends AbstractRule {
        private final Indicator<Num> indicator;
        private final Num value;

        public EqualIndicator(Indicator<Num> indicator, Num value) {
            this.indicator = indicator;
            this.value = value;
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            return indicator.getValue(index).isEqual(value);
        }
    }
}
