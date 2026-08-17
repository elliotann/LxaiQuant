package com.chain.ai.trade.logs.logger;

import com.chain.ai.trade.logs.log.TradeLog;
import com.chain.ai.trade.logs.log.BusinessLog;
import java.util.UUID;

/**
 * WebSocket日志推送测试类
 * 用于验证日志是否能通过WebSocket推送到前端
 */
public class WebSocketLogPushTest {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== WebSocket日志推送测试开始 ===");
        
        // 创建一条模拟的交易开仓日志
        TradeLog tradeLog = new TradeLog(
            "INFO",                    // level
            UUID.randomUUID().toString(), // traceId
            "TRADE_" + System.currentTimeMillis(), // tradeId
            "ORDER_" + System.currentTimeMillis(), // orderId
            "demo-strategy",           // strategyId
            "BTCUSDT",                 // symbol
            "buy",                     // side
            26800.1234,                // price
            0.15,                      // quantity
            2.68                       // fee
        );

        System.out.println("创建交易日志:");
        System.out.println("  类型: " + tradeLog.getLogType());
        System.out.println("  级别: " + tradeLog.getLevel());
        System.out.println("  交易对: " + tradeLog.getSymbol());
        System.out.println("  方向: " + tradeLog.getSide());
        System.out.println("  价格: " + tradeLog.getPrice());
        System.out.println("  数量: " + tradeLog.getQuantity());
        System.out.println("  JSON: " + tradeLog.toJson());
        
        // 创建WebSocket日志处理器并发送日志
        System.out.println("\n启动WebSocket日志处理器...");
        AsyncWebSocketLogger logger = new AsyncWebSocketLogger();
        logger.start();
        
        System.out.println("发送日志到WebSocket...");
        logger.handle(tradeLog);
        
        // 等待日志处理完成
        System.out.println("等待日志处理...");
        Thread.sleep(1500);
        
        logger.stop();
        System.out.println("\n✅ 测试完成！");
        System.out.println("请在前端“交易日志”标签页查看是否接收到日志");
        System.out.println("预期内容: BTCUSDT 买入 价格:26800.1234 数量:0.15");
        
        System.out.println("\n=== WebSocket日志推送测试结束 ===");
    }
}