package com.chain.ai.trade.engine.notifier.event;

import com.chain.ai.trade.engine.notifier.model.NotificationMessage;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NotificationEvent extends ApplicationEvent {

    private final NotificationMessage message;

    public NotificationEvent(Object source, NotificationMessage message) {
        super(source);
        this.message = message;
    }
}
