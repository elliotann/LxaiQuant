package com.chain.ai.trade.engine.risk.intraday.adapter;

import com.chain.ai.trade.engine.risk.intraday.port.MemberAccountsPort;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.ITradingAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TradingAccountMemberAccountsAdapter implements MemberAccountsPort {

    private final ITradingAccountService tradingAccountService;

    @Override
    public List<String> listAccountIds(Long memberId) {
        if (memberId == null) return List.of();
        List<TradingAccount> accounts = tradingAccountService.getAllAccounts();
        if (accounts == null || accounts.isEmpty()) return List.of();
        String mid = String.valueOf(memberId);
        return accounts.stream()
                .filter(a -> Objects.equals(mid, a.getMemberId()))
                .map(TradingAccount::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
}
