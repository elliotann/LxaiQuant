package com.chain.ai.trade.engine.notifier;

import com.chain.ai.trade.engine.notifier.model.NotificationMessage;

public interface Notifier {

    String channel();

    boolean send(NotificationMessage message);

    boolean isAvailable();
}
