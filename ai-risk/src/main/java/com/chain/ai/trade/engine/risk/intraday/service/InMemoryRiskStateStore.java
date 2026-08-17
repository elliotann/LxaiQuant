package com.chain.ai.trade.engine.risk.intraday.service;

import com.chain.ai.trade.engine.risk.intraday.model.MemberRiskState;
import com.chain.ai.trade.engine.risk.intraday.model.RiskStatus;
import com.chain.ai.trade.engine.risk.intraday.port.RiskStateStorePort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRiskStateStore implements RiskStateStorePort {

    private final Map<Long, MemberRiskState> states = new ConcurrentHashMap<>();

    @Override
    public MemberRiskState getOrInit(Long memberId) {
        if (memberId == null) return null;
        return states.computeIfAbsent(memberId, id -> MemberRiskState.builder()
                .memberId(id)
                .status(RiskStatus.ACTIVE)
                .initialEquity(BigDecimal.ZERO)
                .currentEquity(BigDecimal.ZERO)
                .dailyTotalPnl(BigDecimal.ZERO)
                .dailyTotalPnlRatio(BigDecimal.ZERO)
                .remainingLossBudget(BigDecimal.ZERO)
                .lastUpdateTime(Instant.now())
                .build());
    }

    @Override
    public void save(MemberRiskState state) {
        if (state == null || state.getMemberId() == null) return;
        states.put(state.getMemberId(), state);
    }

    @Override
    public void reset(Long memberId) {
        if (memberId == null) return;
        states.remove(memberId);
    }
}
