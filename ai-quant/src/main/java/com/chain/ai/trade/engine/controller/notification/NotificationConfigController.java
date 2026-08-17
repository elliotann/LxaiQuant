package com.chain.ai.trade.engine.controller.notification;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.notifier.MailNotifier;
import com.chain.ai.trade.engine.notifier.entity.NotificationConfig;
import com.chain.ai.trade.engine.notifier.entity.NotificationLog;
import com.chain.ai.trade.engine.notifier.entity.SiteMessage;
import com.chain.ai.trade.engine.notifier.entity.SmtpConfig;
import com.chain.ai.trade.engine.service.NotificationConfigService;
import com.chain.ai.trade.engine.service.NotificationLogService;
import com.chain.ai.trade.engine.service.SiteMessageService;
import com.chain.ai.trade.engine.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationConfigController {

    private final NotificationConfigService configService;
    private final SiteMessageService siteMessageService;
    private final NotificationLogService notificationLogService;
    private final MailNotifier mailNotifier;

    @GetMapping("/configs")
    public ApiResponse<List<NotificationConfig>> getConfigs() {
        String userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(configService.getUserConfigs(userId));
    }

    @PutMapping("/configs/{channel}")
    public ApiResponse<NotificationConfig> updateConfig(
            @PathVariable String channel,
            @RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.getCurrentUserId();
        Boolean enabled = (Boolean) body.get("enabled");
        String configJson = (String) body.get("configJson");
        return ApiResponse.success(configService.saveOrUpdate(userId, channel, enabled, configJson));
    }

    @GetMapping("/messages")
    public ApiResponse<IPage<SiteMessage>> listMessages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        String userId = SecurityUtils.getCurrentUserId();
        if (unreadOnly) {
            return ApiResponse.success(siteMessageService.listUnreadMessages(userId, page, size));
        }
        return ApiResponse.success(siteMessageService.listMessages(userId, page, size));
    }

    @GetMapping("/messages/unread-count")
    public ApiResponse<Long> unreadCount() {
        String userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(siteMessageService.countUnread(userId));
    }

    @PostMapping("/messages/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable String id) {
        siteMessageService.markAsRead(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/messages/read-all")
    public ApiResponse<Void> markAllAsRead() {
        String userId = SecurityUtils.getCurrentUserId();
        siteMessageService.markAllAsRead(userId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/messages/{id}")
    public ApiResponse<Void> deleteMessage(@PathVariable String id) {
        siteMessageService.deleteMessage(id);
        return ApiResponse.success(null);
    }

    // ========== 通知日志（notification_log）API ==========

    /**
     * 分页查询通知日志
     */
    @GetMapping("/logs")
    public ApiResponse<IPage<NotificationLog>> listNotificationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        String userId = SecurityUtils.getCurrentUserId();
        if (unreadOnly) {
            return ApiResponse.success(notificationLogService.listUnreadMessages(userId, page, size));
        }
        return ApiResponse.success(notificationLogService.listMessages(userId, page, size));
    }

    /**
     * 未读通知数量
     */
    @GetMapping("/logs/unread-count")
    public ApiResponse<Long> unreadLogsCount() {
        String userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(notificationLogService.countUnread(userId));
    }

    /**
     * 标记单条通知为已读
     */
    @PostMapping("/logs/{id}/read")
    public ApiResponse<Void> markLogAsRead(@PathVariable String id) {
        notificationLogService.markAsRead(id);
        return ApiResponse.success(null);
    }

    /**
     * 标记所有通知为已读
     */
    @PostMapping("/logs/read-all")
    public ApiResponse<Void> markAllLogsAsRead() {
        String userId = SecurityUtils.getCurrentUserId();
        notificationLogService.markAllAsRead(userId);
        return ApiResponse.success(null);
    }

    /**
     * 删除单条通知
     */
    @DeleteMapping("/logs/{id}")
    public ApiResponse<Void> deleteLog(@PathVariable String id) {
        notificationLogService.deleteMessage(id);
        return ApiResponse.success(null);
    }

    // ========== 测试邮件 ==========

    @PostMapping("/test/email")
    public ApiResponse<String> testEmail(@RequestBody Map<String, Object> body) {
        String to = (String) body.getOrDefault("to", "13713587424@163.com");
        String subject = (String) body.getOrDefault("subject", "测试邮件");
        String content = (String) body.getOrDefault("content", "这是一封来自量化交易系统的测试邮件");

        SmtpConfig config = new SmtpConfig();
        config.setSmtpHost((String) body.getOrDefault("smtpHost", "smtp.gmail.com"));
        config.setSmtpPort(body.get("smtpPort") != null ? ((Number) body.get("smtpPort")).intValue() : 587);
        config.setEmailUser((String) body.getOrDefault("emailUser", ""));
        config.setEmailPassword((String) body.getOrDefault("emailPassword", ""));
        config.setProxyEnabled(body.get("proxyEnabled") != null && (Boolean) body.get("proxyEnabled"));
        config.setProxyHost((String) body.getOrDefault("proxyHost", ""));
        config.setProxyPort(body.get("proxyPort") != null ? ((Number) body.get("proxyPort")).intValue() : 7890);

        try {
            mailNotifier.sendWithConfig(config, to, subject, content);
            log.info("测试邮件发送成功 to={}", to);
            return ApiResponse.success("测试邮件发送成功");
        } catch (Exception e) {
            log.error("测试邮件发送失败", e);
            return ApiResponse.error(500, "发送失败: " + e.getMessage());
        }
    }
}
