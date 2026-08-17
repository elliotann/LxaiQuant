package com.chain.ai.trade.engine2.core.execution;

import com.chain.ai.trade.engine2.realtime.RealtimeContext;

import java.util.List;

/**
 * 订单执行处理器 — Paper/Live 的唯一差异点。
 * <p>
 * Paper：内存订单簿模拟撮合<br>
 * Live：封装 OkxDirectTradeService 真实下单
 */
public interface ExecutionHandler {

    /**
     * 提交订单（开仓/加仓）
     */
    OrderIntentResult submitOrder(OrderIntent intent, RealtimeContext context);

    /**
     * 平仓（全平或部分平仓）
     */
    CloseOrderResult closeOrder(OrderIntent intent, RealtimeContext context);

    /**
     * 取消挂单
     */
    CancelResult cancelOrder(String clientOrderId);

    /**
     * 查询订单成交状态（PendingOrderWorker 调用）
     * @return null = 未成交, 非null = 已成交或已拒绝
     */
    FillResult checkFill(String clientOrderId);

    /**
     * 获取所有挂单列表
     */
    List<OrderIntent> getPendingOrders(String symbol);

    /**
     * 每根 K 线到达时处理挂单（Paper 用价格比较触发模拟成交）
     */
    List<FillResult> processPendingOrdersOnBar(String symbol, double currentHigh, double currentLow);
}
