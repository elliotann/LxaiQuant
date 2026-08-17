package com.chain.ai.trade.engine.risk.intraday.service;

import com.chain.ai.trade.engine.risk.intraday.model.MemberRiskConfig;
import com.chain.ai.trade.engine.risk.intraday.model.MemberRiskState;
import com.chain.ai.trade.engine.risk.intraday.model.RiskStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class IntradayRiskStateMachine {

    public MemberRiskState transition(MemberRiskState state, MemberRiskConfig config) {
        if (state == null) return null;
        if (config == null || !config.isEnabled()) return state;

        RiskStatus oldStatus = state.getStatus();
        RiskStatus newStatus = oldStatus;

        BigDecimal pnlRatio = state.getDailyTotalPnlRatio() != null ? state.getDailyTotalPnlRatio() : BigDecimal.ZERO;
        BigDecimal lossRatio = pnlRatio;
        BigDecimal profitRatio = pnlRatio;

        if (oldStatus == RiskStatus.ACTIVE) {
            if (lossRatio.compareTo(config.getStopRatio().negate()) <= 0) {
                newStatus = RiskStatus.STOP;
            } else if (lossRatio.compareTo(config.getWarningRatio().negate()) <= 0) {
                newStatus = RiskStatus.WARNING;
            } else if (profitRatio.compareTo(config.getProfitTargetRatio()) >= 0) {
                newStatus = RiskStatus.PROFIT_LOCKED;
            }
        } else if (oldStatus == RiskStatus.WARNING) {
            if (lossRatio.compareTo(config.getStopRatio().negate()) <= 0) {
                newStatus = RiskStatus.STOP;
            } else if (profitRatio.compareTo(config.getProfitTargetRatio()) >= 0) {
                newStatus = RiskStatus.PROFIT_LOCKED;
            }
        } else if (oldStatus == RiskStatus.PROFIT_LOCKED) {
            newStatus = RiskStatus.PROFIT_LOCKED;
        } else if (oldStatus == RiskStatus.STOP) {
            newStatus = RiskStatus.STOP;
        }

        if (newStatus != oldStatus) {
            return state.toBuilder().status(newStatus).lastUpdateTime(Instant.now()).build();
        }
        return state.toBuilder().lastUpdateTime(Instant.now()).build();
    }
}

