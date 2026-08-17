package com.chain.ai.trade.engine2.realtime;

import com.chain.ai.trade.engine2.persistence.RealtimeGateway;
import com.chain.ai.trade.engine2.strategy.ScriptStrategy;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import com.chain.ai.trade.order.entity.dos.TradeEntry;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.BarSeries;

import java.util.List;
import java.util.Map;

/**
 * 实盘引擎 — 通过 LiveEngineFactory 创建，不由 Spring 直接管理。
 * <p>
 * 继承 RealtimeEngine，实现 beforeRun() 从 DB 恢复持仓。
 * executeLoop() 使用父类的事件循环（wait/notify）。
 * </p>
 */
@Slf4j
public class LiveEngine extends RealtimeEngine {

    public LiveEngine(BarSeries series, ScriptStrategy strategy,
                      RealtimeConfig config, RealtimeContext context,
                      RealtimeGateway gateway) {
        super(series, strategy, config, context, gateway);
    }

    @Override
    protected void beforeRun() {
        super.beforeRun();

        // 仓位恢复：从 DB 加载未平仓订单，重建 MemoryPosition
        Object[] result = gateway.loadOpenPositions(config.getSymbol(), false);
        if (result != null && result[0] != null) {
            @SuppressWarnings("unchecked")
            List<TradePosition> openOrders = (List<TradePosition>) result[0];
            @SuppressWarnings("unchecked")
            Map<String, List<TradeEntry>> itemsMap = (Map<String, List<TradeEntry>>) result[1];
            context.recoverFromOrders(openOrders, itemsMap);
            log.info("仓位恢复完成: symbol={}, 恢复 {} 个持仓", config.getSymbol(), openOrders.size());
        }
    }
}