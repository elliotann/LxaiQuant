package com.chain.ai.trade.engine.xchange;

import com.chain.ai.trade.engine.xchange.dto.MarketOrder;

import java.io.IOException;
import java.util.List;

public interface ITradeService {
    /**
     * 市价下单
     * @param marketOrder
     * @return
     * @throws IOException
     */
    public String placeMarketOrder(MarketOrder marketOrder) throws IOException;
}
