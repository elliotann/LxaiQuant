package com.chain.ai.trade.engine2.realtime;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine2.core.execution.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 模拟订单执行 — 内存订单簿撮合。
 * <p>
 * 市价单立即成交，限价单挂单等待 K 线价格触及后模拟成交。
 */
@Slf4j
@Component
@Profile("paper")
public class PaperExecutionHandler implements ExecutionHandler {

    /** 模拟订单簿：Key = clientOrderId, Value = OrderIntent */
    private final ConcurrentHashMap<String, OrderIntent> pendingOrders = new ConcurrentHashMap<>();

    /** 成交回调监听 */
    private final List<Consumer<FillResult>> fillListeners = new CopyOnWriteArrayList<>();

    public void addFillListener(Consumer<FillResult> listener) {
        fillListeners.add(listener);
    }

    @Override
    public OrderIntentResult submitOrder(OrderIntent intent, RealtimeContext context) {
        log.debug("Paper 提交订单: clientOrderId={}, side={}, type={}, price={}, qty={}",
                intent.getClientOrderId(), intent.getSide(), intent.getOrderType(), intent.getPrice(), intent.getQuantity());

        if (intent.getOrderType() == OrderType.MARKET) {
            BigDecimal fillPrice = intent.getPrice();
            FillResult fill = FillResult.builder()
                    .clientOrderId(intent.getClientOrderId())
                    .filled(true)
                    .fillPrice(fillPrice)
                    .filledQuantity(intent.getQuantity())
                    .fillTime(LocalDateTime.now())
                    .build();
            notifyFilled(fill);
            return OrderIntentResult.filled(intent.getClientOrderId(), fillPrice, intent.getQuantity());
        }

        pendingOrders.put(intent.getClientOrderId(), intent);
        log.info("Paper 挂单: clientOrderId={}, side={}, limitPrice={}, qty={}",
                intent.getClientOrderId(), intent.getSide(), intent.getPrice(), intent.getQuantity());
        return OrderIntentResult.pending(intent.getClientOrderId());
    }

    @Override
    public CloseOrderResult closeOrder(OrderIntent intent, RealtimeContext context) {
        log.debug("Paper 平仓: positionId={}, side={}, qty={}",
                intent.getPositionId(), intent.getSide(), intent.getQuantity());

        BigDecimal fillPrice = intent.getPrice();
        BigDecimal fee = BigDecimal.ZERO;

        if (context != null && context.getContractSpec() != null) {
            fee = context.getContractSpec().getContractSize()
                    .multiply(intent.getQuantity()).multiply(fillPrice)
                    .multiply(BigDecimal.valueOf(0.0005));
        }

        return CloseOrderResult.success(fillPrice, intent.getQuantity(), fee);
    }

    @Override
    public CancelResult cancelOrder(String clientOrderId) {
        OrderIntent removed = pendingOrders.remove(clientOrderId);
        if (removed != null) {
            log.info("Paper 撤单成功: clientOrderId={}", clientOrderId);
            return CancelResult.ok();
        }
        return CancelResult.fail("订单不存在或已成交");
    }

    @Override
    public FillResult checkFill(String clientOrderId) {
        // Paper 不主动轮询，由 processPendingOrdersOnBar 触发
        OrderIntent intent = pendingOrders.get(clientOrderId);
        if (intent == null) {
            // 可能已成交（被 remove 了）
            return FillResult.builder()
                    .clientOrderId(clientOrderId)
                    .filled(true)
                    .build();
        }
        return null;
    }

    @Override
    public List<OrderIntent> getPendingOrders(String symbol) {
        return pendingOrders.values().stream()
                .filter(o -> o.getSymbol().equals(symbol))
                .collect(Collectors.toList());
    }

    @Override
    public List<FillResult> processPendingOrdersOnBar(String symbol, double currentHigh, double currentLow) {
        List<FillResult> fills = new ArrayList<>();
        Iterator<Map.Entry<String, OrderIntent>> it = pendingOrders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, OrderIntent> entry = it.next();
            OrderIntent intent = entry.getValue();
            if (!intent.getSymbol().equals(symbol)) continue;

            boolean reached = isLongBuy(intent)
                    ? currentLow <= intent.getPrice().doubleValue()
                    : currentHigh >= intent.getPrice().doubleValue();

            if (reached) {
                it.remove();
                FillResult fill = FillResult.builder()
                        .clientOrderId(intent.getClientOrderId())
                        .filled(true)
                        .fillPrice(intent.getPrice())
                        .filledQuantity(intent.getQuantity())
                        .fillTime(LocalDateTime.now())
                        .build();
                fills.add(fill);
                notifyFilled(fill);
                log.info("Paper 成交: clientOrderId={}, price={}, qty={}",
                        intent.getClientOrderId(), intent.getPrice(), intent.getQuantity());
            }
        }
        return fills;
    }

    private boolean isLongBuy(OrderIntent intent) {
        return intent.getSide() == SignalType.LONG;
    }

    private void notifyFilled(FillResult fill) {
        for (Consumer<FillResult> listener : fillListeners) {
            try { listener.accept(fill); } catch (Exception e) { log.error("成交回调异常", e); }
        }
    }
}
