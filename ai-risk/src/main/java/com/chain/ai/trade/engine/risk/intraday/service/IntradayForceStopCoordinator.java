package com.chain.ai.trade.engine.risk.intraday.service;

import com.chain.ai.trade.engine.risk.intraday.model.OpenPositionRef;
import com.chain.ai.trade.engine.risk.intraday.port.MemberAccountsPort;
import com.chain.ai.trade.engine.risk.intraday.port.OrderExecutionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntradayForceStopCoordinator {

    private final MemberAccountsPort memberAccountsPort;
    private final OrderExecutionPort orderExecutionPort;

    public void forceStop(Long memberId) {
        if (memberId == null) return;

        List<String> accountIds = memberAccountsPort.listAccountIds(memberId);
        if (accountIds == null || accountIds.isEmpty()) return;

        for (String accountId : accountIds) {
            List<OpenPositionRef> positions = orderExecutionPort.listOpenPositions(accountId);
            if (positions == null || positions.isEmpty()) continue;
            for (OpenPositionRef pos : positions) {
                if (pos == null || pos.getOrderSn() == null) continue;
                boolean ok = orderExecutionPort.closePosition(pos.getOrderSn());
                log.info("日内风控强平: memberId={}, accountId={}, orderSn={}, ok={}", memberId, accountId, pos.getOrderSn(), ok);
            }
        }
    }
}

