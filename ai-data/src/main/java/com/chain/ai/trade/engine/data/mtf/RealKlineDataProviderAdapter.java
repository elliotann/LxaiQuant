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
import java.util.List;

/**
 * V1 兼容适配器 — 实盘模式。
 * 将 KlineDataService 的查询接口适配为旧 KlineDataProvider 的迭代器模式。
 * <p>
 * 过渡期使用，Phase 4 废弃。
 */
public class RealKlineDataProviderAdapter implements KlineDataProvider {

    private final KlineDataService klineDataService;
    private final ICandlestickService candlestickService;
    private final TradingStrategyParams params;
    private final Exchange exchange;
    private final String symbol;
    private final CandlestickIntervalEnum interval;

    public RealKlineDataProviderAdapter(KlineDataService klineDataService,
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
        return klineDataService.getSeries(exchange, symbol, interval, count);
    }

    @Override
    public Bar fetchNextKline(TradingStrategyParams params) {
        // 实盘模式：查询最新一根K线
        BarSeries series = klineDataService.getSeries(exchange, symbol, interval, 1);
        return series.getBarCount() > 0 ? series.getLastBar() : null;
    }

    @Override
    public BarSeries fetchAllKlines(TradingStrategyParams params) {
        if (params.getStartTime() != null && params.getEndTime() != null) {
            return klineDataService.getSeries(exchange, symbol, interval,
                    Instant.ofEpochMilli(params.getStartTime()),
                    Instant.ofEpochMilli(params.getEndTime()));
        }
        return klineDataService.getSeries(exchange, symbol, interval, 1000);
    }

    @Override
    public boolean isTestMode() {
        return false;
    }

    @Override
    public void reset() {
        // 实盘模式无状态，无需重置
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
