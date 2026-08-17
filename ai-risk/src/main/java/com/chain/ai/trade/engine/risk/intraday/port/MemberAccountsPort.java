package com.chain.ai.trade.engine.risk.intraday.port;

import java.util.List;

public interface MemberAccountsPort {
    List<String> listAccountIds(Long memberId);
}

