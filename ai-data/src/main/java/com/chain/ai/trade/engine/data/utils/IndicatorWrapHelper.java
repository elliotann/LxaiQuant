package com.chain.ai.trade.engine.data.utils;


import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;

import java.time.*;
import java.util.List;


public class IndicatorWrapHelper {
    /**
     * 构建series
     *
     * @param lines
     * @return
     */
    public static BarSeries buildSeries(List<Candlestick> lines) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withName("indicator_series")
                .build();

        if (lines == null || lines.isEmpty()) {
            return series;
        }

        // 使用上海时区
        ZoneId shanghaiZone = ZoneId.of("Asia/Shanghai");

        Duration duration = Duration.ofMinutes(1);
        if (CandlestickIntervalEnum.OKXMIN60.equals(lines.get(0).getCandlestickIntervalEnum())) {
            duration = Duration.ofMinutes(60);
        }
        if (CandlestickIntervalEnum.OKXMIN15.equals(lines.get(0).getCandlestickIntervalEnum())) {
            duration = Duration.ofMinutes(15);
        }
        if (CandlestickIntervalEnum.OKXMIN5.equals(lines.get(0).getCandlestickIntervalEnum())) {
            duration = Duration.ofMinutes(5);
        }
        if (CandlestickIntervalEnum.OKXMIN3.equals(lines.get(0).getCandlestickIntervalEnum())) {
            duration = Duration.ofMinutes(3);
        }

        for (Candlestick kline : lines) {
            // 关键：直接将时间戳视为上海时区时间
            // 创建上海时区的 ZonedDateTime
            ZonedDateTime startTime = ZonedDateTime
                    .ofInstant(Instant.ofEpochMilli(kline.getId()), shanghaiZone);

            ZonedDateTime endTime = startTime.plus(duration);

            // 使用 LocalDateTime 创建 Instant，避免时区转换
            // 这将把上海时间当作 UTC 时间来处理
            Instant endInstant = endTime.toLocalDateTime().atOffset(ZoneOffset.UTC).toInstant();


            Bar bar = series.barBuilder()
                    .timePeriod(duration)
                    .endTime(endInstant)
                    .openPrice(series.numFactory().numOf(kline.getOpenPrice()))
                    .highPrice(series.numFactory().numOf(kline.getHighPrice()))
                    .lowPrice(series.numFactory().numOf(kline.getLowPrice()))
                    .closePrice(series.numFactory().numOf(kline.getClosePrice()))
                    .volume(series.numFactory().numOf(kline.getVolume()))
                    .build();
            series.addBar(bar);
        }
        return series;
    }

    /**
     * Candlestick → TA4J Bar 转换（单条）
     * 时区处理与 buildSeries 一致：用 id（毫秒时间戳）→ 上海时区 → 本地时间当 UTC 处理
     */
    public static Bar buildBar(Candlestick kline) {
        CandlestickIntervalEnum interval = kline.getCandlestickIntervalEnum();
        if (interval == null) {
            interval = CandlestickIntervalEnum.OKXMIN3;
        }

        Duration duration = Duration.ofMinutes(interval.getMinNum());
        ZoneId shanghaiZone = ZoneId.of("Asia/Shanghai");

        ZonedDateTime startTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(kline.getId()), shanghaiZone);
        ZonedDateTime endTime = startTime.plus(duration);
        Instant endInstant = endTime.toLocalDateTime().atOffset(ZoneOffset.UTC).toInstant();
        Instant beginInstant = endInstant.minus(duration);

        return new BaseBar(
                duration,
                beginInstant,
                endInstant,
                DecimalNum.valueOf(kline.getOpenPrice()),
                DecimalNum.valueOf(kline.getHighPrice()),
                DecimalNum.valueOf(kline.getLowPrice()),
                DecimalNum.valueOf(kline.getClosePrice()),
                DecimalNum.valueOf(kline.getVolume()),
                DecimalNum.valueOf(0),
                0
        );
    }

    /**
     * 安全获取Bar，避免索引越界
     */
    public static Bar getBarSafe(BarSeries series, int index) {
        if (series != null && index >= 0 && index < series.getBarCount()) {
            return series.getBar(index);
        }
        return null;
    }

}
