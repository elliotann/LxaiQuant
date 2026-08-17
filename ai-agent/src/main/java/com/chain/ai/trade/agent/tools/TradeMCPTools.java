package com.chain.ai.trade.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class TradeMCPTools implements McpTool {

    @Tool("下单")
    public OrderResult placeOrder(@P("交易对") String symbol,
                                  @P("BUY/SELL") String side,
                                  @P("数量") double quantity,
                                  @P("价格类型，MARKET/LIMIT") String type,
                                  @P("限价价格（可选）") Double price) {
        log.info("place_order: {} {} {} {} price={}", symbol, side, quantity, type, price);
        return OrderResult.builder()
                .orderId("mock-" + System.currentTimeMillis())
                .status("PENDING")
                .build();
    }

    @Tool("查询订单历史")
    public List<String> getOrderHistory(@P("用户ID") String userId,
                                        @P("开始时间") String start,
                                        @P("结束时间") String end,
                                        @P("机器人ID（可选）") String robotId) {
        log.debug("get_order_history: userId={}, start={}, end={}, robotId={}", userId, start, end, robotId);
        return List.of();
    }

    @Tool("查询持仓历史")
    public List<String> getPositionHistory(@P("用户ID") String userId,
                                           @P("开始时间") String start,
                                           @P("结束时间") String end) {
        log.debug("get_position_history: userId={}, start={}, end={}", userId, start, end);
        return List.of();
    }
}
