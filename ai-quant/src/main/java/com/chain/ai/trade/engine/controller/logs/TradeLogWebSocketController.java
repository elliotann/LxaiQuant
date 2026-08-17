package com.chain.ai.trade.engine.controller.logs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 交易日志WebSocket控制器
 * 处理交易日志的STOMP消息映射
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class TradeLogWebSocketController {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * 订阅交易日志
     * 客户端发送SUBSCRIBE帧到 /topic/logs
     */
    @SubscribeMapping("/topic/logs")
    public void handleLogsSubscription(StompHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("收到交易日志订阅请求: sessionId={}", sessionId);
        
        // 发送订阅确认消息
        Map<String, Object> ackMessage = Map.of(
            "type", "ack",
            "status", "subscribed",
            "topic", "/topic/logs",
            "message", "已订阅交易日志"
        );
        
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/logs", ackMessage);
    }
    
    /**
     * 处理客户端发送到 /app/logs 的消息（如果需要）
     */
    @MessageMapping("/logs")
    public void handleLogsMessage(@Payload Map<String, Object> payload, StompHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("收到交易日志消息: sessionId={}, payload={}", sessionId, payload);
        
        // 可以在这里处理客户端发送的日志相关消息
        // 例如：客户端请求特定类型的日志等
    }
}