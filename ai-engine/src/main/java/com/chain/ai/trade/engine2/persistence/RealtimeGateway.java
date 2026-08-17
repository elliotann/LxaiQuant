package com.chain.ai.trade.engine2.persistence;

import com.chain.ai.trade.engine2.backtest.model.MemoryPosition;
import com.chain.ai.trade.engine2.core.execution.OrderIntent;
import com.chain.ai.trade.extension.core.constants.ExitType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实盘/模拟持久化门面 — @Async 异步写入，不阻塞主循环。
 * <p>
 * 区别于 BacktestBatchGateway 的批量 flush()，此处每笔交易实时落库。
 */
public interface RealtimeGateway {

    /** 订单提交时记录 */
    void onOrderSubmitted(OrderIntent intent);

    /** 订单成交时更新 */
    void onOrderFilled(OrderIntent intent, BigDecimal fillPrice, BigDecimal filledQuantity);

    /** 订单关闭（平仓）时记录 */
    void onOrderClosed(String symbol, String positionId, BigDecimal exitPrice, BigDecimal closeQty,
                       BigDecimal pnl, BigDecimal fee, ExitType exitType, LocalDateTime barTime);

    /** 持仓状态变更时更新（upsert TradeOrder） */
    void onPositionUpdated(MemoryPosition position);

    /** 权益曲线采样 */
    void onEquitySample(String symbol, int barIndex, long timestamp, BigDecimal equity);

    /**
     * 加载未平仓订单及其入场明细（用于引擎启动时仓位恢复）
     *
     * @param symbol 交易对
     * @param isTest 是否测试模式
     * @return [openOrders, orderSn → items 映射]
     */
    Object[] loadOpenPositions(String symbol, boolean isTest);
}
