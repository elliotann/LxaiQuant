package com.chain.ai.trade.engine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.engine.mapper.SiteMessageMapper;
import com.chain.ai.trade.engine.notifier.entity.SiteMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteMessageService {

    private final SiteMessageMapper siteMessageMapper;

    public IPage<SiteMessage> listMessages(String userId, int page, int size) {
        return siteMessageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SiteMessage>()
                        .eq(SiteMessage::getUserId, userId)
                        .orderByDesc(SiteMessage::getCreateTime));
    }

    public IPage<SiteMessage> listUnreadMessages(String userId, int page, int size) {
        return siteMessageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SiteMessage>()
                        .eq(SiteMessage::getUserId, userId)
                        .eq(SiteMessage::getIsRead, false)
                        .orderByDesc(SiteMessage::getCreateTime));
    }

    public long countUnread(String userId) {
        return siteMessageMapper.selectCount(
                new LambdaQueryWrapper<SiteMessage>()
                        .eq(SiteMessage::getUserId, userId)
                        .eq(SiteMessage::getIsRead, false));
    }

    @Transactional
    public void markAsRead(String messageId) {
        SiteMessage msg = siteMessageMapper.selectById(messageId);
        if (msg != null) {
            msg.setIsRead(true);
            msg.setReadAt(new Date());
            siteMessageMapper.updateById(msg);
        }
    }

    @Transactional
    public void markAllAsRead(String userId) {
        siteMessageMapper.update(
                SiteMessage.builder().isRead(true).readAt(new Date()).build(),
                new LambdaQueryWrapper<SiteMessage>()
                        .eq(SiteMessage::getUserId, userId)
                        .eq(SiteMessage::getIsRead, false));
    }

    @Transactional
    public void deleteMessage(String messageId) {
        siteMessageMapper.deleteById(messageId);
    }
}
