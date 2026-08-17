package com.chain.ai.trade.engine.risk.intraday.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder(toBuilder = true)
public class MemberRiskState implements Serializable {
    private Long memberId;

    @Builder.Default
    private RiskStatus status = RiskStatus.ACTIVE;

    @Builder.Default
    private BigDecimal initialEquity = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal currentEquity = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal dailyTotalPnl = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal dailyTotalPnlRatio = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal remainingLossBudget = BigDecimal.ZERO;

    @Builder.Default
    private Instant lastUpdateTime = Instant.now();
}
