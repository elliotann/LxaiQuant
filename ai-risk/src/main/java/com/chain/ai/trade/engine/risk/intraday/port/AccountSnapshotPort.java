package com.chain.ai.trade.engine.risk.intraday.port;

import com.chain.ai.trade.engine.risk.intraday.model.AccountEquitySnapshot;

public interface AccountSnapshotPort {
    AccountEquitySnapshot getAccountEquity(String accountId);
}

