package com.chain.ai.trade.engine.notifier.event;

import com.chain.ai.trade.engine.notifier.NotificationRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRouter notificationRouter;

    @EventListener
    public void handleNotification(NotificationEvent event) {
        log.debug("收到通知事件: type={}, userId={}",
                event.getMessage().getType(), event.getMessage().getUserId());
        notificationRouter.send(event.getMessage());
    }
}
