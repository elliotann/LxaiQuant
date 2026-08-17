package com.chain.ai.trade.common.push;

import java.util.Map;

public interface SignalMessagePublisher {

    void push(SignalPushMessage message);

    void pushSignal(String symbol, String direction, Map<String, Object> data);

    void pushNotification(String type, String source, String title, String content);
}
