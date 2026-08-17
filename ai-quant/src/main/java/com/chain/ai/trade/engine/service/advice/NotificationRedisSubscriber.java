package com.chain.ai.trade.engine.service.advice;

import com.alibaba.fastjson2.JSON;
import com.chain.ai.trade.common.push.NotificationPushMessage;
import com.chain.ai.trade.engine.notifier.NotificationRouter;
import com.chain.ai.trade.engine.notifier.model.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRedisSubscriber implements MessageListener {

    private final NotificationRouter notificationRouter;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            NotificationPushMessage pushMsg = JSON.parseObject(body, NotificationPushMessage.class);
            if (pushMsg == null) {
                log.warn("解析通知推送消息失败, body={}", body);
                return;
            }

            NotificationMessage notificationMessage = NotificationMessage.builder()
                    .userId(pushMsg.getUserId())
                    .type(pushMsg.getType())
                    .title(pushMsg.getTitle())
                    .content(pushMsg.getContent())
                    .symbol(pushMsg.getSymbol())
                    .severity(pushMsg.getSeverity())
                    .metadata(pushMsg.getMetadata())
                    .createdAt(Instant.now())
                    .build();

            // 根据通知类型决定推送渠道：基础交易通知+邮件推送
            List<String> channels = List.of("site_msg", "app", "email");
            notificationRouter.send(notificationMessage, channels);
            log.info("交易通知已转发至路由中心: type={}, title={}, channels={}", pushMsg.getType(), pushMsg.getTitle(), channels);
        } catch (Exception e) {
            log.error("处理通知推送消息异常", e);
        }
    }
}
