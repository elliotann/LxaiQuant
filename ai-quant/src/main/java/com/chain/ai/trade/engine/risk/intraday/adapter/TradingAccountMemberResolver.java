package com.chain.ai.trade.engine.risk.intraday.adapter;

import com.chain.ai.trade.engine.risk.intraday.port.MemberResolverPort;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.ITradingAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradingAccountMemberResolver implements MemberResolverPort {

    private final ITradingAccountService tradingAccountService;

    @Override
    public Long resolveMemberId(String accountId, String robotId) {
        if (accountId == null || accountId.isBlank()) return null;
        TradingAccount account = tradingAccountService.getByAccountId(accountId);
        if (account == null || account.getMemberId() == null) return null;
        try {
            return Long.parseLong(account.getMemberId());
        } catch (Exception ignored) {
            return null;
        }
    }
}

