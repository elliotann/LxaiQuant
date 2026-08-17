package com.chain.ai.trade.engine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.engine.mapper.NotificationLogMapper;
import com.chain.ai.trade.engine.notifier.entity.NotificationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationLogService {

    private final NotificationLogMapper notificationLogMapper;

    /**
     * 分页查询用户通知日志
     */
    public IPage<NotificationLog> listMessages(String userId, int page, int size) {
        return notificationLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<NotificationLog>()
                        .eq(NotificationLog::getUserId, userId)
                        .orderByDesc(NotificationLog::getSentAt));
    }

    /**
     * 分页查询未读通知
     */
    public IPage<NotificationLog> listUnreadMessages(String userId, int page, int size) {
        return notificationLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<NotificationLog>()
                        .eq(NotificationLog::getUserId, userId)
                        .eq(NotificationLog::getIsRead, false)
                        .orderByDesc(NotificationLog::getSentAt));
    }

    /**
     * 查询未读通知数量
     */
    public long countUnread(String userId) {
        return notificationLogMapper.selectCount(
                new LambdaQueryWrapper<NotificationLog>()
                        .eq(NotificationLog::getUserId, userId)
                        .eq(NotificationLog::getIsRead, false));
    }

    /**
     * 标记单条通知为已读
     */
    @Transactional
    public void markAsRead(String messageId) {
        NotificationLog log = notificationLogMapper.selectById(messageId);
        if (log != null) {
            log.setIsRead(true);
            log.setReadAt(new Date());
            notificationLogMapper.updateById(log);
        }
    }

    /**
     * 标记用户所有通知为已读
     */
    @Transactional
    public void markAllAsRead(String userId) {
        notificationLogMapper.update(
                NotificationLog.builder().isRead(true).readAt(new Date()).build(),
                new LambdaQueryWrapper<NotificationLog>()
                        .eq(NotificationLog::getUserId, userId)
                        .eq(NotificationLog::getIsRead, false));
    }

    /**
     * 删除单条通知
     */
    @Transactional
    public void deleteMessage(String messageId) {
        notificationLogMapper.deleteById(messageId);
    }
}
