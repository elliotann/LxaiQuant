package com.chain.ai.trade.engine.signal.service.support;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.service.ISignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

@Slf4j
@Service
public class AiSignService implements ISignService {

    @Override
    public BuyAndSellWeightDto execute(IndicatorCalcDto calcDto) {
        String strategyType = calcDto.getStrategyType();
        if (strategyType == null) {
            return holdResult();
        }
        try {
            switch (strategyType) {
                case "TREND":
                    return calcTrend(calcDto);
                case "GRID":
                    return calcGrid(calcDto);
                case "MEAN_REVERSION":
                    return calcMeanReversion(calcDto);
                case "BREAKOUT":
                    return calcBreakout(calcDto);
                case "SCALPING":
                    return calcScalping(calcDto);
                default:
                    return holdResult();
            }
        } catch (Exception e) {
            log.warn("AiSignService error, strategyType={}", strategyType, e);
            return holdResult();
        }
    }

    private BuyAndSellWeightDto calcTrend(IndicatorCalcDto calcDto) {
        BarSeries series = calcDto.getSeries();
        if (series == null || series.getBarCount() < 30) {
            return holdResult();
        }
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator fast = new EMAIndicator(close, 9);
        EMAIndicator slow = new EMAIndicator(close, 26);

        int end = series.getBarCount() - 1;
        Num fastVal = fast.getValue(end);
        Num slowVal = slow.getValue(end);
        Num prevFast = fast.getValue(Math.max(0, end - 1));
        Num prevSlow = slow.getValue(Math.max(0, end - 1));

        boolean crossUp = prevFast.isLessThan(prevSlow) && fastVal.isGreaterThan(slowVal);
        boolean crossDown = prevFast.isGreaterThan(prevSlow) && fastVal.isLessThan(slowVal);

        if (crossUp) {
            return buyResult();
        }
        if (crossDown) {
            return sellResult();
        }
        if (fastVal.isGreaterThan(slowVal)) {
            return buyResult();
        }
        return sellResult();
    }

    private BuyAndSellWeightDto calcGrid(IndicatorCalcDto calcDto) {
        BarSeries series = calcDto.getSeries();
        if (series == null || series.getBarCount() < 20) {
            return holdResult();
        }
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int end = series.getBarCount() - 1;
        Num price = close.getValue(end);

        SMAIndicator sma = new SMAIndicator(close, 20);
        StandardDeviationIndicator sd = new StandardDeviationIndicator(close, 20);
        Num mid = sma.getValue(end);
        Num std = sd.getValue(end);
        Num upper = mid.plus(std.multipliedBy(series.numFactory().numOf(2.0)));
        Num lower = mid.minus(std.multipliedBy(series.numFactory().numOf(2.0)));

        double rangePct = price.minus(lower).doubleValue() / upper.minus(lower).doubleValue();

        if (rangePct < 0.25) {
            return buyResult();
        }
        if (rangePct > 0.75) {
            return sellResult();
        }
        return holdResult();
    }

    private BuyAndSellWeightDto calcMeanReversion(IndicatorCalcDto calcDto) {
        BarSeries series = calcDto.getSeries();
        if (series == null || series.getBarCount() < 15) {
            return holdResult();
        }
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(close, 14);

        int end = series.getBarCount() - 1;
        double rsiVal = rsi.getValue(end).doubleValue();

        if (rsiVal < 30) {
            return buyResult();
        }
        if (rsiVal > 70) {
            return sellResult();
        }
        if (rsiVal < 40) {
            return buyResult();
        }
        if (rsiVal > 60) {
            return sellResult();
        }
        return holdResult();
    }

    private BuyAndSellWeightDto calcBreakout(IndicatorCalcDto calcDto) {
        BarSeries series = calcDto.getSeries();
        if (series == null || series.getBarCount() < 20) {
            return holdResult();
        }
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        HighPriceIndicator high = new HighPriceIndicator(series);
        LowPriceIndicator low = new LowPriceIndicator(series);

        int end = series.getBarCount() - 1;
        Num currentClose = close.getValue(end);
        Num currentHigh = high.getValue(end);
        Num currentLow = low.getValue(end);

        Num recentHigh = high.getValue(end - 1);
        Num recentLow = low.getValue(end - 1);
        for (int i = end - 2; i >= Math.max(0, end - 20); i--) {
            Num h = high.getValue(i);
            Num l = low.getValue(i);
            if (h.isGreaterThan(recentHigh)) recentHigh = h;
            if (l.isLessThan(recentLow)) recentLow = l;
        }

        if (currentClose.isGreaterThan(recentHigh) && currentHigh.isGreaterThan(recentHigh)) {
            return buyResult();
        }
        if (currentClose.isLessThan(recentLow) && currentLow.isLessThan(recentLow)) {
            return sellResult();
        }
        return holdResult();
    }

    private BuyAndSellWeightDto calcScalping(IndicatorCalcDto calcDto) {
        BarSeries series = calcDto.getSeries();
        if (series == null || series.getBarCount() < 10) {
            return holdResult();
        }
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator fast = new EMAIndicator(close, 5);
        EMAIndicator slow = new EMAIndicator(close, 13);

        int end = series.getBarCount() - 1;
        Num fastVal = fast.getValue(end);
        Num slowVal = slow.getValue(end);
        Num prevFast = fast.getValue(Math.max(0, end - 1));
        Num prevSlow = slow.getValue(Math.max(0, end - 1));

        boolean crossUp = prevFast.isLessThan(prevSlow) && fastVal.isGreaterThan(slowVal);
        boolean crossDown = prevFast.isGreaterThan(prevSlow) && fastVal.isLessThan(slowVal);

        if (crossUp) {
            return buyResult();
        }
        if (crossDown) {
            return sellResult();
        }
        double gapPct = fastVal.minus(slowVal).doubleValue() / slowVal.doubleValue();
        if (gapPct > 0.005) {
            return buyResult();
        }
        if (gapPct < -0.005) {
            return sellResult();
        }
        return holdResult();
    }

    private BuyAndSellWeightDto buyResult() {
        BuyAndSellWeightDto result = new BuyAndSellWeightDto();
        result.setSignalType(SignalType.LONG);
        return result;
    }

    private BuyAndSellWeightDto sellResult() {
        BuyAndSellWeightDto result = new BuyAndSellWeightDto();
        result.setSignalType(SignalType.SHORT);
        return result;
    }

    private BuyAndSellWeightDto holdResult() {
        return new BuyAndSellWeightDto();
    }
}
