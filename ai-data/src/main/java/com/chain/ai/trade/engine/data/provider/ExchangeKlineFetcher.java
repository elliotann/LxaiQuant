package com.chain.ai.trade.engine.data.provider;

import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;

import java.util.List;

/**
 * 从交易所获取历史K线数据的接口
 * 由 ai-quant 等模块提供实现（如 OKX、Gate.io 公开 API）
 */
public interface ExchangeKlineFetcher {

    /**
     * 当前 fetcher 是否支持该交易所
     *
     * @param exchange 交易所标识，如 "OKX"、"GATEIO"
     * @return true 表示支持
     */
    default boolean supports(String exchange) {
        return false;
    }

    /**
     * 从交易所拉取指定时间范围内的K线数据（单次请求，最多 limit 条）
     *
     * @param exchange    交易所标识，如 "OKX"
     * @param symbol      交易对，如 "BTC-USDT"
     * @param interval    K线周期
     * @param startTimeSec 开始时间（秒级时间戳）
     * @param endTimeSec   结束时间（秒级时间戳）
     * @param limit       单次最多条数，如 300
     * @return K线列表，时间从旧到新；不支持该交易所或失败时返回空列表
     */
    List<Candlestick> fetchKlines(String exchange, String symbol, CandlestickIntervalEnum interval,
                                  long startTimeSec, long endTimeSec, int limit);
}
