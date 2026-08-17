package com.chain.ai.trade.engine.notifier;

import com.chain.ai.trade.engine.notifier.entity.SmtpConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Slf4j
@Component
public class MailNotifier {

    public void sendWithConfig(SmtpConfig config, String to, String subject, String content) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getSmtpHost());
        sender.setPort(config.getSmtpPort());
        sender.setUsername(config.getEmailUser());
        sender.setPassword(config.getEmailPassword());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        if (config.getSmtpPort() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.trust", config.getSmtpHost());
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", config.getSmtpHost());
        }

        if (config.isProxyEnabled()) {
            props.put("mail.smtp.socks.host", config.getProxyHost());
            props.put("mail.smtp.socks.port", String.valueOf(config.getProxyPort()));
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(config.getEmailUser());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);

        sender.send(message);
        log.info("邮件发送成功 {} -> {}", subject, to);
    }
}
