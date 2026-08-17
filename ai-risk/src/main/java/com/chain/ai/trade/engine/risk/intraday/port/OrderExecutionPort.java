package com.chain.ai.trade.engine.risk.intraday.port;

import com.chain.ai.trade.engine.risk.intraday.model.OpenPositionRef;

import java.util.List;

public interface OrderExecutionPort {
    List<OpenPositionRef> listOpenPositions(String accountId);
    boolean closePosition(String orderSn);
}

