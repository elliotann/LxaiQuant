package com.chain.ai.trade.engine.risk.intraday.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RiskCheckResult {
    private boolean passed;
    private String rejectReason;
    private RiskStatus status;

    public static RiskCheckResult pass(RiskStatus status) {
        return new RiskCheckResult(true, null, status);
    }

    public static RiskCheckResult reject(String reason, RiskStatus status) {
        return new RiskCheckResult(false, reason, status);
    }
}

