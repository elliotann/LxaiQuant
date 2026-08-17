package com.chain.ai.trade.engine.data.mtf;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.provider.KlineDataProvider;
import org.ta4j.core.BarSeries;

import java.time.Instant;

/**
 * K 线数据服务 — 唯一对外入口。
 * 调用方通过 Exchange + Symbol + Interval + 时间范围 获取数据。
 * <p>
 * 职责单一：只负责数据查询，不关心回测/实盘。
 * MTF 由引擎层创建（谁用谁知道）。
 */
public interface KlineDataService {

    /** 获取最新 N 根 K 线的 BarSeries */
    BarSeries getSeries(Exchange exchange, String symbol,
                        CandlestickIntervalEnum interval,
                        int barCount);

    /** 获取指定时间范围内的 BarSeries */
    BarSeries getSeries(Exchange exchange, String symbol,
                        CandlestickIntervalEnum interval,
                        Instant start, Instant end);

    /** V1 兼容（过渡期）：适配旧 KlineDataProvider 接口 */
    KlineDataProvider getProvider(TradingStrategyParams params);
}
