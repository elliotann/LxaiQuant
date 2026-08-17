package com.chain.ai.trade.engine.data.mtf;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.provider.KlineDataProvider;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * V1 兼容适配器 — 回测模式。
 * 将 KlineDataService 的查询接口适配为旧 KlineDataProvider 的迭代器模式。
 * <p>
 * 过渡期使用，Phase 4 废弃。
 */
public class TestKlineDataProviderAdapter implements KlineDataProvider {

    private final KlineDataService klineDataService;
    private final ICandlestickService candlestickService;
    private final TradingStrategyParams params;
    private final Exchange exchange;
    private final String symbol;
    private final CandlestickIntervalEnum interval;

    /** 回测所有K线缓存（fetchAllKlines 时加载） */
    private BarSeries allKlines;
    /** 当前游标位置 */
    private int currentIndex;

    public TestKlineDataProviderAdapter(KlineDataService klineDataService,
                                         ICandlestickService candlestickService,
                                         TradingStrategyParams params) {
        this.klineDataService = klineDataService;
        this.candlestickService = candlestickService;
        this.params = params;
        this.exchange = params.getMemberPlatform();
        this.symbol = params.getSymbol();
        this.interval = parseInterval(params.getInterval());
    }

    @Override
    public BarSeries fetchInitialKlines(TradingStrategyParams params, int count) {
        if (params.getStartTime() != null && params.getEndTime() != null) {
            this.allKlines = klineDataService.getSeries(exchange, symbol, interval,
                    Instant.ofEpochMilli(params.getStartTime()),
                    Instant.ofEpochMilli(params.getEndTime()));
        } else {
            this.allKlines = klineDataService.getSeries(exchange, symbol, interval, count);
        }
        this.currentIndex = 0;
        return this.allKlines;
    }

    @Override
    public Bar fetchNextKline(TradingStrategyParams params) {
        if (allKlines == null || currentIndex >= allKlines.getBarCount()) {
            return null;
        }
        return allKlines.getBar(currentIndex++);
    }

    @Override
    public BarSeries fetchAllKlines(TradingStrategyParams params) {
        if (allKlines == null) {
            fetchInitialKlines(params, 1000);
        }
        return allKlines;
    }

    @Override
    public boolean isTestMode() {
        return true;
    }

    @Override
    public void reset() {
        this.allKlines = null;
        this.currentIndex = 0;
    }

    @Override
    public List<com.chain.ai.trade.engine.data.entity.dos.Candlestick> listByLeId(KlineParam param) {
        return candlestickService.listByLeId(param);
    }

    /** OKX 周期字符串 → CandlestickIntervalEnum */
    private static CandlestickIntervalEnum parseInterval(String interval) {
        if (interval == null) return CandlestickIntervalEnum.OKXMIN60;
        return switch (interval.toLowerCase()) {
            case "1m" -> CandlestickIntervalEnum.OKXMIN1;
            case "3m" -> CandlestickIntervalEnum.OKXMIN3;
            case "5m" -> CandlestickIntervalEnum.OKXMIN5;
            case "15m" -> CandlestickIntervalEnum.OKXMIN15;
            case "30m" -> CandlestickIntervalEnum.OKXMIN30;
            case "1h", "60m" -> CandlestickIntervalEnum.OKXMIN60;
            case "4h" -> CandlestickIntervalEnum.OKX4HOUR;
            case "1d" -> CandlestickIntervalEnum.OKX1D;
            default -> CandlestickIntervalEnum.OKXMIN60;
        };
    }
}
