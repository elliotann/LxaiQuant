package com.chain.ai.trade.engine.notifier.impl;

import com.chain.ai.trade.engine.notifier.Notifier;
import com.chain.ai.trade.engine.notifier.model.NotificationMessage;
import com.chain.ai.trade.engine.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppNotifier implements Notifier {

    private final WebSocketNotificationService webSocketNotificationService;

    @Override
    public String channel() {
        return "app";
    }

    @Override
    public boolean send(NotificationMessage message) {
        try {
            if (message.getUserId() != null) {
                webSocketNotificationService.sendToUser(
                        message.getUserId(),
                        "/topic/notification",
                        message);
            }
            return true;
        } catch (Exception e) {
            log.error("App推送失败, userId={}, type={}", message.getUserId(), message.getType(), e);
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
