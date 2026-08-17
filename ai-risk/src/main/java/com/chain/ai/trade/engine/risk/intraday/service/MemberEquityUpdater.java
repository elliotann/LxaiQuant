package com.chain.ai.trade.engine.risk.intraday.service;

import com.chain.ai.trade.engine.risk.intraday.model.AccountEquitySnapshot;
import com.chain.ai.trade.engine.risk.intraday.model.MemberRiskConfig;
import com.chain.ai.trade.engine.risk.intraday.model.MemberRiskState;
import com.chain.ai.trade.engine.risk.intraday.port.AccountSnapshotPort;
import com.chain.ai.trade.engine.risk.intraday.port.MemberAccountsPort;
import com.chain.ai.trade.engine.risk.intraday.port.RiskStateStorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MemberEquityUpdater {

    private final MemberAccountsPort memberAccountsPort;
    private final AccountSnapshotPort accountSnapshotPort;
    private final RiskStateStorePort riskStateStorePort;

    public MemberRiskState updateState(Long memberId, MemberRiskConfig config) {
        if (memberId == null || config == null || !config.isEnabled()) return null;

        List<String> accounts = memberAccountsPort.listAccountIds(memberId);
        if (accounts == null || accounts.isEmpty()) return null;

        BigDecimal totalEquity = BigDecimal.ZERO;
        for (String accountId : accounts) {
            AccountEquitySnapshot snapshot = accountSnapshotPort.getAccountEquity(accountId);
            if (snapshot != null && snapshot.getEquity() != null) {
                totalEquity = totalEquity.add(snapshot.getEquity());
            }
        }

        MemberRiskState state = riskStateStorePort.getOrInit(memberId);
        if (state == null) return null;

        BigDecimal initialEquity = state.getInitialEquity() != null ? state.getInitialEquity() : BigDecimal.ZERO;
        if (initialEquity.compareTo(BigDecimal.ZERO) <= 0 && totalEquity.compareTo(BigDecimal.ZERO) > 0) {
            initialEquity = totalEquity;
        }

        BigDecimal dailyPnl = totalEquity.subtract(initialEquity);
        BigDecimal dailyPnlRatio = BigDecimal.ZERO;
        if (initialEquity.compareTo(BigDecimal.ZERO) > 0) {
            dailyPnlRatio = dailyPnl.divide(initialEquity, 8, RoundingMode.HALF_UP);
        }

        MemberRiskState updated = state.toBuilder()
                .initialEquity(initialEquity)
                .currentEquity(totalEquity)
                .dailyTotalPnl(dailyPnl)
                .dailyTotalPnlRatio(dailyPnlRatio)
                .lastUpdateTime(Instant.now())
                .build();
        riskStateStorePort.save(updated);
        return updated;
    }
}

