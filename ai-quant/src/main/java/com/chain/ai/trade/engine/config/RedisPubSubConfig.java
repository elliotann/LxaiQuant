package com.chain.ai.trade.engine.config;

import com.chain.ai.trade.engine.service.advice.NotificationRedisSubscriber;
import com.chain.ai.trade.engine.service.advice.SignalRedisSubscriber;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
@AllArgsConstructor
public class RedisPubSubConfig {

    private final SignalRedisSubscriber signalSubscriber;
    private final NotificationRedisSubscriber notificationSubscriber;

    @Bean
    public MessageListenerAdapter signalListenerAdapter() {
        return new MessageListenerAdapter(signalSubscriber);
    }

    @Bean
    public MessageListenerAdapter notificationListenerAdapter() {
        return new MessageListenerAdapter(notificationSubscriber);
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(signalListenerAdapter(), new ChannelTopic("signal:push"));
        container.addMessageListener(notificationListenerAdapter(), new ChannelTopic("notification:push"));
        return container;
    }
}
