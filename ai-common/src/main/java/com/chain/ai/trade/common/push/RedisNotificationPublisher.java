package com.chain.ai.trade.common.push;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "ai.push.enabled", havingValue = "true", matchIfMissing = true)
public class RedisNotificationPublisher {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String CHANNEL = "notification:push";

    public void publish(NotificationPushMessage message) {
        try {
            if (Boolean.TRUE.equals(message.getIsTest())) {
                log.debug("测试/回测模式通知已跳过: type={}, title={}", message.getType(), message.getTitle());
                return;
            }
            if (message.getTimestamp() == 0) {
                message.setTimestamp(System.currentTimeMillis());
            }
            String json = JSON.toJSONString(message);
            stringRedisTemplate.convertAndSend(CHANNEL, json);
            log.debug("通知已发布到Redis频道 {}: type={}, title={}", CHANNEL, message.getType(), message.getTitle());
        } catch (Exception e) {
            log.error("发布通知到Redis频道 {} 失败: type={}", CHANNEL, message.getType(), e);
        }
    }
}
