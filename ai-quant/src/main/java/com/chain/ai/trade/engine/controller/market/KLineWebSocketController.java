package com.chain.ai.trade.engine.controller.market;

import com.chain.ai.trade.engine.service.KLineWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * K线数据WebSocket控制器
 * 处理K线数据的订阅和取消订阅
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class KLineWebSocketController {
    
    private final KLineWebSocketService kLineWebSocketService;
    
    /**
     * 订阅K线数据
     * 客户端发送消息到 /app/kline/subscribe
     */
    @MessageMapping("/kline/subscribe")
    public void subscribe(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String symbol = (String) payload.get("symbol");
        String interval = (String) payload.get("interval");
        
        log.info("收到K线订阅请求: sessionId={}, symbol={}, interval={}", sessionId, symbol, interval);
        
        if (symbol == null || interval == null) {
            log.warn("订阅参数不完整: symbol={}, interval={}", symbol, interval);
            return;
        }
        
        // 记录订阅（定时任务据此推送数据）
        kLineWebSocketService.addSubscription(sessionId, symbol, interval);
        
        // 发送订阅成功响应
        kLineWebSocketService.sendSubscribed(sessionId, symbol, interval);
    }
    
    /**
     * 取消订阅K线数据
     * 客户端发送消息到 /app/kline/unsubscribe
     */
    @MessageMapping("/kline/unsubscribe")
    public void unsubscribe(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String symbol = (String) payload.get("symbol");
        String interval = (String) payload.get("interval");
        
        log.info("收到K线取消订阅请求: sessionId={}, symbol={}, interval={}", sessionId, symbol, interval);
        
        if (symbol == null || interval == null) {
            log.warn("取消订阅参数不完整: symbol={}, interval={}", symbol, interval);
            return;
        }
        
        // 移除订阅记录
        kLineWebSocketService.removeSubscription(sessionId, symbol, interval);
        
        // 发送取消订阅成功响应
        kLineWebSocketService.sendUnsubscribed(sessionId, symbol, interval);
    }
    
    /**
     * 上报图表状态
     * 客户端发送消息到 /app/kline/chart-state
     */
    @MessageMapping("/kline/chart-state")
    public void reportChartState(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Map<String, Object> state = (Map<String, Object>) payload.get("state");
        
        log.debug("收到图表状态上报: sessionId={}, state={}", sessionId, state);
        
        // 可以根据图表状态调整推送策略
        // 例如：用户正在查看历史数据时，暂停实时推送
    }
}

