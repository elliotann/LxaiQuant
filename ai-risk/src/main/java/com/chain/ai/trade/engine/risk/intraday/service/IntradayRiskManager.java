package com.chain.ai.trade.engine.risk.intraday.service;

import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.engine.risk.intraday.model.MemberRiskConfig;
import com.chain.ai.trade.engine.risk.intraday.model.MemberRiskState;
import com.chain.ai.trade.engine.risk.intraday.model.RiskCheckResult;
import com.chain.ai.trade.engine.risk.intraday.model.RiskStatus;
import com.chain.ai.trade.engine.risk.intraday.port.MemberResolverPort;
import com.chain.ai.trade.engine.risk.intraday.port.RiskConfigPort;
import com.chain.ai.trade.engine.risk.intraday.port.RiskStateStorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class IntradayRiskManager {

    private final RiskConfigPort riskConfigPort;
    private final RiskStateStorePort riskStateStorePort;
    private final IntradayRiskStateMachine stateMachine;
    private final MemberEquityUpdater memberEquityUpdater;
    private final IntradayForceStopCoordinator forceStopCoordinator;

    @Autowired(required = false)
    private MemberResolverPort memberResolverPort;

    public RiskCheckResult preOpenCheck(TradingStrategyParams params) {
        Long memberId = resolveMemberId(params);
        if (memberId == null) {
            return RiskCheckResult.pass(RiskStatus.ACTIVE);
        }

        MemberRiskConfig config = riskConfigPort.getConfig(memberId);
        if (config == null || !config.isEnabled()) {
            return RiskCheckResult.pass(RiskStatus.ACTIVE);
        }

        MemberRiskState updated = memberEquityUpdater.updateState(memberId, config);
        MemberRiskState state = updated != null ? updated : riskStateStorePort.getOrInit(memberId);
        RiskStatus before = state != null ? state.getStatus() : RiskStatus.ACTIVE;
        state = stateMachine.transition(state, config);
        riskStateStorePort.save(state);

        RiskStatus status = state.getStatus();
        if (status == RiskStatus.STOP) {
            if (before != RiskStatus.STOP) {
                forceStopCoordinator.forceStop(memberId);
            }
            return RiskCheckResult.reject("日内风控已熔断，禁止交易", status);
        }
        if (status == RiskStatus.WARNING) {
            return RiskCheckResult.reject("日内风控预警，禁止开仓", status);
        }
        if (status == RiskStatus.PROFIT_LOCKED) {
            return RiskCheckResult.reject("日内风控已止盈锁定，禁止开仓", status);
        }
        return RiskCheckResult.pass(status);
    }



    private Long resolveMemberId(TradingStrategyParams params) {
        if (params == null) return null;
        Map<String, Object> additional = params.getAdditionalParams();
        if (additional != null) {
            Object v = additional.get("memberId");
            if (v instanceof Number) {
                return ((Number) v).longValue();
            }
            if (v instanceof String s) {
                try {
                    return Long.parseLong(s);
                } catch (Exception ignored) {
                }
            }
        }
        if (memberResolverPort == null) return null;
        return memberResolverPort.resolveMemberId(params.getAccountId(), params.getRobotId());
    }
}
