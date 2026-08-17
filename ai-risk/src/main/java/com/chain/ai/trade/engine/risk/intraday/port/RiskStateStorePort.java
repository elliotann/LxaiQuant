package com.chain.ai.trade.engine.risk.intraday.port;

import com.chain.ai.trade.engine.risk.intraday.model.MemberRiskState;

public interface RiskStateStorePort {
    MemberRiskState getOrInit(Long memberId);
    void save(MemberRiskState state);
    void reset(Long memberId);
}

