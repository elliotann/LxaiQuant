package com.chain.ai.trade.engine.notifier.impl;

import com.chain.ai.trade.engine.notifier.Notifier;
import com.chain.ai.trade.engine.notifier.TelegramNotifier;
import com.chain.ai.trade.engine.notifier.model.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(TelegramNotifier.class)
@RequiredArgsConstructor
public class TelegramNotifierAdapter implements Notifier {

    private final TelegramNotifier telegramNotifier;

    @Override
    public String channel() {
        return "telegram";
    }

    @Override
    public boolean send(NotificationMessage message) {
        try {
            double price = 0.0;
            if (message.getMetadata() != null && message.getMetadata().containsKey("price")) {
                price = ((Number) message.getMetadata().get("price")).doubleValue();
            }
            telegramNotifier.sendSignal(
                    message.getSymbol(),
                    message.getTitle(),
                    price);
            return true;
        } catch (Exception e) {
            log.error("Telegram通知失败, userId={}, type={}", message.getUserId(), message.getType(), e);
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return telegramNotifier.isEnabled();
    }
}
