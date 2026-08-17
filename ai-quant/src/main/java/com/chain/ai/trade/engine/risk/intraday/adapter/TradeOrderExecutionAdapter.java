package com.chain.ai.trade.engine.risk.intraday.adapter;

import com.chain.ai.trade.engine.risk.intraday.model.OpenPositionRef;
import com.chain.ai.trade.engine.risk.intraday.port.OrderExecutionPort;
import com.chain.ai.trade.order.entity.vo.OrderVO;
import com.chain.ai.trade.order.service.ITradeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TradeOrderExecutionAdapter implements OrderExecutionPort {

    private final ITradeOrderService tradeOrderService;

    @Override
    public List<OpenPositionRef> listOpenPositions(String accountId) {
        List<OrderVO> orders = tradeOrderService.getPositionOrders(accountId, null);
        if (orders == null || orders.isEmpty()) return List.of();
        return orders.stream()
                .filter(Objects::nonNull)
                .filter(o -> o.getOrderSn() != null)
                .map(o -> OpenPositionRef.builder()
                        .orderSn(o.getOrderSn())
                        .symbol(o.getSymbol())
                        .accountId(accountId)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public boolean closePosition(String orderSn) {
        try {
            tradeOrderService.closeOrderByOrderSn(orderSn);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}

