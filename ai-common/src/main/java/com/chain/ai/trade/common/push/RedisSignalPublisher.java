package com.chain.ai.trade.common.push;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "ai.push.enabled", havingValue = "true", matchIfMissing = true)
public class RedisSignalPublisher implements SignalMessagePublisher {

    private final StringRedisTemplate stringRedisTemplate;

    private String channel = "signal:push";

    public void setChannel(String channel) {
        this.channel = channel;
    }

    @Override
    public void push(SignalPushMessage message) {
        try {
            if (message.getTimestamp() == 0) {
                message.setTimestamp(System.currentTimeMillis());
            }
            String json = JSON.toJSONString(message);
            stringRedisTemplate.convertAndSend(channel, json);
            log.debug("信号已发布到Redis频道 {}: type={}, symbol={}", channel, message.getType(), message.getSymbol());
        } catch (Exception e) {
            log.error("发布信号到Redis频道 {} 失败: type={}", channel, message.getType(), e);
        }
    }

    @Override
    public void pushSignal(String symbol, String direction, Map<String, Object> data) {
        SignalPushMessage message = SignalPushMessage.builder()
                .type("NEW_SIGNAL")
                .source("AI_LIVE_ADVICE_SCHEDULED")
                .symbol(symbol)
                .direction(direction)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .ttlMinutes(15)
                .build();
        push(message);
    }

    @Override
    public void pushNotification(String type, String source, String title, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("content", content);
        SignalPushMessage message = SignalPushMessage.builder()
                .type(type)
                .source(source)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
        push(message);
    }
}
