package com.chain.ai.trade.engine.risk.intraday.adapter;

import com.chain.ai.trade.engine.risk.intraday.model.AccountEquitySnapshot;
import com.chain.ai.trade.engine.risk.intraday.port.AccountSnapshotPort;
import com.chain.ai.trade.order.entity.vo.OrderVO;
import com.chain.ai.trade.order.service.ITradeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TradeOrderAccountSnapshotAdapter implements AccountSnapshotPort {

    private final ITradeOrderService tradeOrderService;

    @Override
    public AccountEquitySnapshot getAccountEquity(String accountId) {
        BigDecimal equity = BigDecimal.ZERO;
        if (accountId != null && !accountId.isBlank()) {
            equity = equity.add(BigDecimal.valueOf(tradeOrderService.getAvailableBalance(accountId)));
            List<OrderVO> positions = tradeOrderService.getPositionOrders(accountId, null);
            if (positions != null) {
                for (OrderVO o : positions) {
                    if (o == null) continue;
                    if (o.getIncome() != null) {
                        equity = equity.add(o.getIncome());
                    }
                }
            }
        }
        return AccountEquitySnapshot.builder().accountId(accountId).equity(equity).build();
    }
}
