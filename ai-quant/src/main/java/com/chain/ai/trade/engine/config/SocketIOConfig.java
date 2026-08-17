package com.chain.ai.trade.engine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Socket.IO配置
 * 注意：需要添加socket.io-server依赖
 * 如果使用Netty Socket.IO，在pom.xml中添加依赖：
 * <dependency>
 *     <groupId>com.corundumstudio.socketio</groupId>
 *     <artifactId>netty-socketio</artifactId>
 *     <version>2.0.3</version>
 * </dependency>
 * 
 * 如果不需要Socket.IO支持，可以跳过此配置
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "com.corundumstudio.socketio.SocketIOServer")
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = false)
public class SocketIOConfig {
    
    // 注意：这里需要根据实际使用的Socket.IO库来配置
    // 如果使用netty-socketio，取消下面的注释
    
    /*
    @Value("${socketio.host:0.0.0.0}")
    private String host;
    
    @Value("${socketio.port:8000}")
    private Integer port;
    
    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);
        config.setAllowCustomRequests(true);
        config.setOrigin("*");
        
        SocketIOServer server = new SocketIOServer(config);
        
        log.info("Socket.IO服务器配置完成: host={}, port={}", host, port);
        
        return server;
    }
    */
    
    // 如果使用其他Socket.IO实现，请在此处配置
}

