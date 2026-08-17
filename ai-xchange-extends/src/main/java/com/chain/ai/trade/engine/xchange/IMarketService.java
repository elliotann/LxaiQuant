package com.chain.ai.trade.engine.xchange;



import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.xchange.utils.Options;

import java.util.List;

/**
 * 市场数据
 */
public interface IMarketService {

    static IMarketService create(Options options) {
        throw new RuntimeException("Unsupport Exchange.");
    }



    /**
     * @param request
     * @return
     */
    List<Candlestick> getHistoryCandlestick(CandlestickRequest request);

}
