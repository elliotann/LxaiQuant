package com.chain.ai.trade.engine.notifier;

import com.chain.ai.trade.engine.mapper.NotificationLogMapper;
import com.chain.ai.trade.engine.notifier.entity.NotificationLog;
import com.chain.ai.trade.engine.notifier.model.NotificationMessage;
import com.chain.ai.trade.engine.service.NotificationConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRouter {

    private final List<Notifier> notifiers;
    private final NotificationConfigService configService;
    private final NotificationLogMapper logMapper;

    private Map<String, Notifier> notifierMap;

    private Map<String, Notifier> getNotifierMap() {
        if (notifierMap == null) {
            notifierMap = new ConcurrentHashMap<>();
            for (Notifier n : notifiers) {
                notifierMap.put(n.channel(), n);
            }
        }
        return notifierMap;
    }

    @Async("notificationExecutor")
    public void send(NotificationMessage message) {
        send(message, null);
    }

    @Async("notificationExecutor")
    public void send(NotificationMessage message, List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            channels = List.of("site_msg", "app");
        }
        for (String channel : channels) {
            Notifier notifier = getNotifierMap().get(channel);
            if (notifier == null) {
                log.warn("未知通知渠道: {}", channel);
                continue;
            }
            if (!notifier.isAvailable()) {
                log.warn("通知渠道不可用: {}", channel);
                continue;
            }
            if (message.getUserId() != null
                    && !configService.isChannelEnabled(message.getUserId(), channel)) {
                log.debug("用户{}未开启{}通知", message.getUserId(), channel);
                continue;
            }
            boolean success = notifier.send(message);
            saveLog(message, channel, success);
        }
    }

    private void saveLog(NotificationMessage message, String channel, boolean success) {
        try {
            NotificationLog log = NotificationLog.builder()
                    .userId(message.getUserId())
                    .channel(channel)
                    .type(message.getType())
                    .title(message.getTitle())
                    .content(message.getContent())
                    .status(success ? "SUCCESS" : "FAILED")
                    .sentAt(new Date())
                    .build();
            logMapper.insert(log);
        } catch (Exception e) {
            log.error("保存通知日志失败, channel={}", channel, e);
        }
    }
}
