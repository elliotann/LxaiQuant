package com.chain.ai.trade.engine.risk.intraday.port;

public interface MemberResolverPort {
    Long resolveMemberId(String accountId, String robotId);
}

