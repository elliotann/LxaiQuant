package com.chain.ai.trade.engine.notifier.impl;

import com.chain.ai.trade.engine.notifier.Notifier;
import com.chain.ai.trade.engine.notifier.entity.SiteMessage;
import com.chain.ai.trade.engine.notifier.model.NotificationMessage;
import com.chain.ai.trade.engine.mapper.SiteMessageMapper;
import com.chain.ai.trade.engine.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SiteMsgNotifier implements Notifier {

    private final SiteMessageMapper siteMessageMapper;
    private final WebSocketNotificationService webSocketNotificationService;

    @Override
    public String channel() {
        return "site_msg";
    }

    @Override
    public boolean send(NotificationMessage message) {
        try {
            SiteMessage entity = SiteMessage.builder()
                    .userId(message.getUserId())
                    .type(message.getType())
                    .title(message.getTitle())
                    .content(message.getContent())
                    .severity(message.getSeverity())
                    .isRead(false)
                    .build();
            siteMessageMapper.insert(entity);

            webSocketNotificationService.sendToUser(
                    message.getUserId(),
                    "/topic/site-message/unread", "1");
            return true;
        } catch (Exception e) {
            log.error("站内信发送失败, userId={}, type={}", message.getUserId(), message.getType(), e);
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
