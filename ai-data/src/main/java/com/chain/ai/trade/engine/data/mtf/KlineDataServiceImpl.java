package com.chain.ai.trade.engine.data.mtf;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.provider.KlineDataProvider;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.time.Instant;

/**
 * KlineDataService 核心实现，封装 ICandlestickService + IndicatorWrapHelper。
 */
@Service
@RequiredArgsConstructor
public class KlineDataServiceImpl implements KlineDataService {

    private final ICandlestickService candlestickService;

    @Override
    public BarSeries getSeries(Exchange exchange, String symbol,
                               CandlestickIntervalEnum interval, int barCount) {
        KlineParam param = KlineParam.builder()
                .exchange(exchange)
                .symbol(symbol)
                .klineInterval(interval)
                .size(barCount)
                .build();
        return IndicatorWrapHelper.buildSeries(candlestickService.getLastKlines(param));
    }

    @Override
    public BarSeries getSeries(Exchange exchange, String symbol,
                               CandlestickIntervalEnum interval,
                               Instant start, Instant end) {
        KlineParam param = KlineParam.builder()
                .exchange(exchange)
                .symbol(symbol)
                .klineInterval(interval)
                .startTime(start.toEpochMilli())
                .endTime(end.toEpochMilli())
                .build();
        return IndicatorWrapHelper.buildSeries(candlestickService.getKlines(param));
    }

    @Override
    public KlineDataProvider getProvider(TradingStrategyParams params) {
        boolean isTest = params.getTestMode() != null && params.getTestMode();
        return isTest
                ? new TestKlineDataProviderAdapter(this, candlestickService, params)
                : new RealKlineDataProviderAdapter(this, candlestickService, params);
    }
}
