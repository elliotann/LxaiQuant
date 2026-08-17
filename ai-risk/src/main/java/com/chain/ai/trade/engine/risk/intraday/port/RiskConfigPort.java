package com.chain.ai.trade.engine.risk.intraday.port;

import com.chain.ai.trade.engine.risk.intraday.model.MemberRiskConfig;

public interface RiskConfigPort {
    MemberRiskConfig getConfig(Long memberId);
}

