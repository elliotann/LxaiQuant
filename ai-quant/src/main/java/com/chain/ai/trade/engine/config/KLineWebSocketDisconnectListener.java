package com.chain.ai.trade.engine.config;

import com.chain.ai.trade.engine.service.KLineWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket 断开事件监听器
 * 客户端断开连接时清理其K线订阅记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KLineWebSocketDisconnectListener {

    private final KLineWebSocketService kLineWebSocketService;

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        log.debug("WebSocket连接断开，清理订阅: sessionId={}", sessionId);
        kLineWebSocketService.removeSession(sessionId);
    }
}
