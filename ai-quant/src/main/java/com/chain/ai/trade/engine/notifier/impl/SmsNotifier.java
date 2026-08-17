package com.chain.ai.trade.engine.notifier.impl;

import com.chain.ai.trade.engine.notifier.Notifier;
import com.chain.ai.trade.engine.notifier.model.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmsNotifier implements Notifier {

    @Override
    public String channel() {
        return "sms";
    }

    @Override
    public boolean send(NotificationMessage message) {
        log.warn("短信通知尚未对接服务商, userId={}, type={}", message.getUserId(), message.getType());
        return false;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
