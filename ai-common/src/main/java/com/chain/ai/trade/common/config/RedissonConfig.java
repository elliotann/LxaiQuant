package com.chain.ai.trade.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置类
 * 提供RedissonClient实例，用于分布式锁和分布式对象
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.redis.timeout:3000}")
    private int redisTimeout;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        try {
            Config config = new Config();
            String redisAddress = String.format("redis://%s:%d", redisHost, redisPort);
            
            SingleServerConfig singleServerConfig = config.useSingleServer()
                    .setAddress(redisAddress)
                    .setDatabase(redisDatabase)
                    .setConnectTimeout(redisTimeout)
                    .setTimeout(redisTimeout)
                    .setRetryAttempts(3)
                    .setRetryInterval(1500)
                    .setConnectionPoolSize(64)
                    .setConnectionMinimumIdleSize(10)
                    .setSubscriptionConnectionPoolSize(50)
                    .setSubscriptionConnectionMinimumIdleSize(1);

            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                singleServerConfig.setPassword(redisPassword);
            }

            RedissonClient redissonClient = Redisson.create(config);
            log.info("✅ RedissonClient初始化成功，连接地址: {}", redisAddress);
            return redissonClient;
        } catch (Exception e) {
            log.error("❌ RedissonClient初始化失败", e);
            throw new RuntimeException("RedissonClient初始化失败", e);
        }
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
