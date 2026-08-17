package com.chain.ai.trade.engine.signal.rule;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.extension.ta4j.indicator.BearishAccumulationIndicator;
import com.chain.ai.trade.extension.ta4j.indicator.BullishAccumulationIndicator;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.candles.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * K线形态检测器 — 基于ta4j内置Indicator
 */
public class CandlestickPatternDetector {

    /** 把CandlestickSnapshot列表转为ta4j BarSeries，复用IndicatorWrapHelper确保时间正确 */
    private static BarSeries toBarSeries(List<WeightRuleContext.CandlestickSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return new BaseBarSeries("candle", new ArrayList<>());
        long gapMillis = snapshots.size() >= 2
                ? snapshots.get(1).getId() - snapshots.get(0).getId()
                : 3600000L;
        CandlestickIntervalEnum interval = resolveInterval(gapMillis);
        return IndicatorWrapHelper.buildSeries(snapshots.stream()
                .map(s -> Candlestick.builder()
                        .id(s.getId())
                        .openPrice(BigDecimal.valueOf(s.getOpen()))
                        .highPrice(BigDecimal.valueOf(s.getHigh()))
                        .lowPrice(BigDecimal.valueOf(s.getLow()))
                        .closePrice(BigDecimal.valueOf(s.getClose()))
                        .volume(BigDecimal.ZERO)
                        .candlestickIntervalEnum(interval)
                        .build())
                .collect(Collectors.toList()));
    }

    private static CandlestickIntervalEnum resolveInterval(long gapMillis) {
        long gapMinutes = gapMillis / 60_000;
        for (CandlestickIntervalEnum e : CandlestickIntervalEnum.values()) {
            if (e.getMinNum() != null && e.getMinNum() == (int) gapMinutes) {
                return e;
            }
        }
        return CandlestickIntervalEnum.MIN60;
    }

    /**
     * 检测最近K线的形态
     *
     * @param snapshots K线快照列表（按时间升序）
     * @return 检测到的形态集合
     */
    public static Set<String> detect(List<WeightRuleContext.CandlestickSnapshot> snapshots) {
        Set<String> patterns = new HashSet<>();
        if (snapshots == null || snapshots.size() < 2) return patterns;

        BarSeries series = toBarSeries(snapshots);
        int lastIdx = series.getEndIndex();

        // 创建所有ta4j形态Indicator
        BullishHaramiIndicator bullishHarami = new BullishHaramiIndicator(series);
        BearishHaramiIndicator bearishHarami = new BearishHaramiIndicator(series);
        BullishEngulfingIndicator bullishEngulfing = new BullishEngulfingIndicator(series);
        BearishEngulfingIndicator bearishEngulfing = new BearishEngulfingIndicator(series);
        HammerIndicator hammer = new HammerIndicator(series);
        ShootingStarIndicator shootingStar = new ShootingStarIndicator(series);
        DojiIndicator doji = new DojiIndicator(series, 10, 0.1);
        MorningStarIndicator morningStar = new MorningStarIndicator(series);
        EveningStarIndicator eveningStar = new EveningStarIndicator(series);
        ThreeWhiteSoldiersIndicator threeWhiteSoldiers = new ThreeWhiteSoldiersIndicator(series, 10, series.numFactory().numOf(3));
        ThreeBlackCrowsIndicator threeBlackCrows = new ThreeBlackCrowsIndicator(series, 10, 3);

        if (Boolean.TRUE.equals(bullishHarami.getValue(lastIdx)))
            patterns.add(CandlestickPattern.BULLISH_HARAMI.name());
        if (Boolean.TRUE.equals(bearishHarami.getValue(lastIdx)))
            patterns.add(CandlestickPattern.BEARISH_HARAMI.name());
        if (Boolean.TRUE.equals(bullishEngulfing.getValue(lastIdx)))
            patterns.add(CandlestickPattern.BULLISH_ENGULFING.name());
        if (Boolean.TRUE.equals(bearishEngulfing.getValue(lastIdx)))
            patterns.add(CandlestickPattern.BEARISH_ENGULFING.name());
        if (Boolean.TRUE.equals(hammer.getValue(lastIdx)))
            patterns.add(CandlestickPattern.BULLISH_PIN_BAR.name());
        if (Boolean.TRUE.equals(shootingStar.getValue(lastIdx)))
            patterns.add(CandlestickPattern.BEARISH_PIN_BAR.name());
        if (Boolean.TRUE.equals(doji.getValue(lastIdx)))
            patterns.add(CandlestickPattern.DOJI.name());
        if (Boolean.TRUE.equals(morningStar.getValue(lastIdx)))
            patterns.add(CandlestickPattern.MORNING_STAR.name());
        if (Boolean.TRUE.equals(eveningStar.getValue(lastIdx)))
            patterns.add(CandlestickPattern.EVENING_STAR.name());
        if (Boolean.TRUE.equals(threeWhiteSoldiers.getValue(lastIdx)))
            patterns.add(CandlestickPattern.THREE_WHITE_SOLDIERS.name());
        if (Boolean.TRUE.equals(threeBlackCrows.getValue(lastIdx)))
            patterns.add(CandlestickPattern.THREE_BLACK_CROWS.name());

        // 自定义形态：强阳吞没 / 强阴吞没
        if (lastIdx >= 2) {
            BullishAccumulationIndicator bullishAccum = new BullishAccumulationIndicator(series);
            BearishAccumulationIndicator bearishAccum = new BearishAccumulationIndicator(series);
            if (bullishAccum.getValue(lastIdx).doubleValue() >= 1.0)
                patterns.add(CandlestickPattern.BULLISH_ACCUMULATION.name());
            if (bearishAccum.getValue(lastIdx).doubleValue() >= 1.0)
                patterns.add(CandlestickPattern.BEARISH_ACCUMULATION.name());
        }

        return patterns;
    }
}
