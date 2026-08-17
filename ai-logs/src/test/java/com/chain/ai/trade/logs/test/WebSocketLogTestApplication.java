package com.chain.ai.trade.logs.test;

import com.chain.ai.trade.logs.log.TradeLog;
import com.chain.ai.trade.logs.logger.AsyncWebSocketLogger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

/**
 * WebSocket日志测试应用
 * 用于验证交易日志的WebSocket推送功能
 */
@SpringBootApplication
public class WebSocketLogTestApplication {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== 启动WebSocket日志测试应用 ===");
        
        // 启动Spring Boot应用
        ConfigurableApplicationContext context = SpringApplication.run(WebSocketLogTestApplication.class, args);
        
        // 获取SimpMessagingTemplate
        SimpMessagingTemplate messagingTemplate = context.getBean(SimpMessagingTemplate.class);
        
        // 创建AsyncWebSocketLogger并设置messagingTemplate
        AsyncWebSocketLogger logger = context.getBean(AsyncWebSocketLogger.class);
        
        System.out.println("等待应用启动完成...");
        Thread.sleep(3000);
        
        System.out.println("\n=== 开始发送测试日志 ===");
        
        // 发送5条测试日志
        for (int i = 0; i < 5; i++) {
            TradeLog tradeLog = new TradeLog(
                "INFO",
                UUID.randomUUID().toString(),
                "TRADE_" + System.currentTimeMillis(),
                "ORDER_" + System.currentTimeMillis(),
                "demo-strategy",
                "BTCUSDT",
                i % 2 == 0 ? "buy" : "sell",
                26800.0 + i * 100,
                0.15,
                2.68
            );
            
            System.out.println("发送第" + (i + 1) + "条日志: " + tradeLog.getSymbol() + " " + tradeLog.getSide() + " 价格:" + tradeLog.getPrice());
            logger.handle(tradeLog);
            
            Thread.sleep(1000);
        }
        
        System.out.println("\n✅ 测试日志发送完成！");
        System.out.println("请在前端“交易日志”标签页查看是否接收到日志");
        System.out.println("按任意键退出...");
        
        // 等待用户输入
        System.in.read();
        
        context.close();
        System.out.println("应用已关闭");
    }
}
