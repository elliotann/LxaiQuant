package com.chain.ai.trade.member.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证码服务
 * 需要配置 spring.mail.host 才会生效，未配置时不加载以避免启动失败
 */
@Slf4j
@Service
@ConditionalOnProperty("spring.mail.host")
@RequiredArgsConstructor
public class EmailCodeService {

    private final JavaMailSender mailSender;

    private static final Map<String, CodeEntry> CODE_CACHE = new ConcurrentHashMap<>();
    private static final int CODE_LENGTH = 6;
    private static final long CODE_TTL_MS = 5 * 60 * 1000; // 5分钟
    private static final String FROM_EMAIL = "noreply@lenzeto.com";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成并发送验证码到指定邮箱
     */
    public void sendCode(String email) {
        // 清除旧验证码
        CODE_CACHE.remove(email);

        // 生成6位随机数字验证码
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(RANDOM.nextInt(10));
        }
        String codeStr = code.toString();

        // 缓存验证码
        CODE_CACHE.put(email, new CodeEntry(codeStr, System.currentTimeMillis() + CODE_TTL_MS));

        // 发送邮件
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(FROM_EMAIL);
            message.setTo(email);
            message.setSubject("灵猞AI - 邮箱验证码");
            message.setText("您的验证码是: " + codeStr + "\n\n验证码有效期为5分钟，请勿泄露给他人。\n\n如果这不是您本人的操作，请忽略此邮件。");
            mailSender.send(message);
            log.info("验证码已发送至: {}", email);
        } catch (Exception e) {
            log.error("发送验证码至 {} 失败", email, e);
            // 发送失败时清除缓存
            CODE_CACHE.remove(email);
            throw new RuntimeException("验证码发送失败，请稍后重试", e);
        }
    }

    /**
     * 校验验证码
     * @return true 校验通过
     */
    public boolean verifyCode(String email, String code) {
        CodeEntry entry = CODE_CACHE.get(email);
        if (entry == null) {
            return false;
        }
        // 检查是否过期
        if (System.currentTimeMillis() > entry.expiry) {
            CODE_CACHE.remove(email);
            return false;
        }
        boolean match = entry.code.equals(code);
        if (match) {
            CODE_CACHE.remove(email); // 校验成功清除
        }
        return match;
    }

    private record CodeEntry(String code, long expiry) {}
}
