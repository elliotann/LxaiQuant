package com.chain.ai.trade.engine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.engine.mapper.NotificationConfigMapper;
import com.chain.ai.trade.engine.notifier.entity.NotificationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConfigService {

    private final NotificationConfigMapper configMapper;

    public List<NotificationConfig> getUserConfigs(String userId) {
        return configMapper.selectList(
                new LambdaQueryWrapper<NotificationConfig>()
                        .eq(NotificationConfig::getUserId, userId));
    }

    public NotificationConfig saveOrUpdate(String userId, String channel, Boolean enabled, String configJson) {
        NotificationConfig existing = configMapper.selectOne(
                new LambdaQueryWrapper<NotificationConfig>()
                        .eq(NotificationConfig::getUserId, userId)
                        .eq(NotificationConfig::getChannel, channel));
        if (existing != null) {
            existing.setEnabled(enabled);
            if (configJson != null) {
                existing.setConfigJson(configJson);
            }
            configMapper.updateById(existing);
            return existing;
        }
        NotificationConfig config = NotificationConfig.builder()
                .userId(userId)
                .channel(channel)
                .enabled(enabled)
                .configJson(configJson)
                .build();
        configMapper.insert(config);
        return config;
    }

    public boolean isChannelEnabled(String userId, String channel) {
        NotificationConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<NotificationConfig>()
                        .eq(NotificationConfig::getUserId, userId)
                        .eq(NotificationConfig::getChannel, channel));
        return config != null && Boolean.TRUE.equals(config.getEnabled());
    }
}
