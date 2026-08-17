package com.chain.ai.trade.engine.data.provider.impl;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.provider.KlineDataProvider;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.util.List;

/**
 * 实盘模式K线数据提供者
 * 从数据库获取最新K线数据
 */
@Slf4j
@Component
@org.springframework.context.annotation.Primary
@RequiredArgsConstructor
public class RealKlineDataProvider implements KlineDataProvider {

    private final ICandlestickService candlestickService;

    @Override
    public BarSeries fetchInitialKlines(TradingStrategyParams params, int count) {
        try {
            CandlestickIntervalEnum interval = parseInterval(params.getInterval());
            long lastTime = System.currentTimeMillis();
            // 从最新K线往前推，获取指定数量的历史K线（不包含最新K线本身）
            // 使用 latestKline.getId() - 1 来排除最新K线
            List<Candlestick> historicalKlines = candlestickService.listByLeId(
                    lastTime,
                    params.getSymbol(),
                    interval,
                    count
            );

            if (historicalKlines == null || historicalKlines.isEmpty()) {
                log.warn("未获取到历史K线数据: symbol={}, interval={}, count={}",
                        params.getSymbol(), params.getInterval(), count);
                return new BaseBarSeriesBuilder()
                        .withName(params.getSymbol() != null ? params.getSymbol() : "BTC-USD")
                        .build();
            }

            log.info("实盘模式：成功加载 {} 根初始K线数据", historicalKlines.size());

            // 转换为BarSeries
            return IndicatorWrapHelper.buildSeries(historicalKlines);

        } catch (Exception e) {
            log.error("获取初始K线数据失败", e);
            return new BaseBarSeriesBuilder()
                    .withName(params.getSymbol() != null ? params.getSymbol() : "BTC-USD")
                    .build();
        }
    }

    @Override
    public Bar fetchNextKline(TradingStrategyParams params) {
        try {
            // 从数据库获取最新K线数据
            CandlestickIntervalEnum interval = parseInterval(params.getInterval());
            Candlestick latestKline = candlestickService.getLastKline(
                    params.getSymbol(),
                    interval
            );

            if (latestKline != null) {
                // 转换为Bar对象
                return convertToBar(latestKline, interval);
            }

            log.warn("未获取到最新K线数据: symbol={}, interval={}", params.getSymbol(), params.getInterval());
            return null;

        } catch (Exception e) {
            log.error("获取最新K线数据失败", e);
            return null;
        }
    }

    @Override
    public boolean isTestMode() {
        return false;
    }

    @Override
    public List<Candlestick> listByLeId(KlineParam param) {
        return candlestickService.listByLeId(param);
    }

    /**
     * 将Candlestick转换为Bar对象
     */
    private Bar convertToBar(Candlestick kline, CandlestickIntervalEnum interval) {
        List<Candlestick> singleKline = List.of(kline);
        BarSeries series = IndicatorWrapHelper.buildSeries(singleKline);
        return series.getLastBar();
    }

    /**
     * 解析时间间隔字符串为枚举
     */
    private CandlestickIntervalEnum parseInterval(String interval) {
        if (interval == null) {
            return CandlestickIntervalEnum.OKXMIN60; // 默认1小时
        }

        return switch (interval.toLowerCase()) {
            case "1m" -> CandlestickIntervalEnum.OKXMIN1;
            case "3m" -> CandlestickIntervalEnum.OKXMIN3;
            case "5m" -> CandlestickIntervalEnum.OKXMIN5;
            case "15m" -> CandlestickIntervalEnum.OKXMIN15;
            case "30m" -> CandlestickIntervalEnum.OKXMIN30;
            case "1h", "60m" -> CandlestickIntervalEnum.OKXMIN60;
            case "4h" -> CandlestickIntervalEnum.OKX4HOUR;
            case "1d" -> CandlestickIntervalEnum.OKX1D;
            default -> CandlestickIntervalEnum.OKXMIN60; // 默认1小时
        };
    }
}
