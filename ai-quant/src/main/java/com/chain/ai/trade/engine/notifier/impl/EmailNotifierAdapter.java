package com.chain.ai.trade.engine.notifier.impl;

import com.alibaba.fastjson2.JSON;
import com.chain.ai.trade.engine.notifier.MailNotifier;
import com.chain.ai.trade.engine.notifier.Notifier;
import com.chain.ai.trade.engine.notifier.entity.NotificationConfig;
import com.chain.ai.trade.engine.notifier.entity.SmtpConfig;
import com.chain.ai.trade.engine.notifier.model.NotificationMessage;
import com.chain.ai.trade.engine.service.NotificationConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotifierAdapter implements Notifier {

    private final MailNotifier mailNotifier;
    private final NotificationConfigService configService;

    @Override
    public String channel() {
        return "email";
    }

    @Override
    public boolean send(NotificationMessage message) {
        try {
            List<NotificationConfig> configs = configService.getUserConfigs(message.getUserId());
            NotificationConfig emailConfig = configs.stream()
                    .filter(c -> "email".equals(c.getChannel()))
                    .filter(NotificationConfig::getEnabled)
                    .findFirst().orElse(null);
            if (emailConfig == null || emailConfig.getConfigJson() == null) {
                log.warn("邮件未配置或未启用, userId={}", message.getUserId());
                return false;
            }
            SmtpConfig smtpConfig = JSON.parseObject(emailConfig.getConfigJson(), SmtpConfig.class);
            String emailContent = message.getContent() != null ? message.getContent() : message.getTitle();
            String subject = message.getTitle();
            String fullContent = emailContent + "\n\n时间: " + java.time.LocalDateTime.now();
            if (message.getSymbol() != null) {
                fullContent = "交易对: " + message.getSymbol() + "\n" + fullContent;
            }
            mailNotifier.sendWithConfig(smtpConfig, smtpConfig.getTo(), subject, fullContent);
            return true;
        } catch (Exception e) {
            log.error("邮件通知失败, userId={}, type={}", message.getUserId(), message.getType(), e);
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
