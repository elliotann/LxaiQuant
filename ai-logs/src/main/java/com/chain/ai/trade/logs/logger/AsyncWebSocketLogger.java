package com.chain.ai.trade.logs.logger;

import com.chain.ai.trade.logs.log.BusinessLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异步WebSocket日志处理器
 * 将业务日志推送到WebSocket客户端，使用STOMP消息代理
 */
@Component
public class AsyncWebSocketLogger extends AbstractBusinessLogHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncWebSocketLogger.class);
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;
    
    public AsyncWebSocketLogger() {
        super("AsyncWebSocketLogger");
    }
    
    @Override
    protected void processLog(BusinessLog log) {
        try {
            Map<String, Object> payload = formatWebSocketPayload(log);
            messagingTemplate.convertAndSend("/topic/logs", (Object) payload);
            
            logger.debug("Broadcasted business log via STOMP: type={}, level={}, traceId={}",
                    log.getLogType(), log.getLevel(), log.getTraceId());
            
        } catch (Exception e) {
            logger.error("Error pushing log to WebSocket via STOMP: {}", log.getLogType(), e);
        }
    }
    
    /**
     * 格式化WebSocket消息
     * 
     * @param log 业务日志
     * @return WebSocket消息
     */
    private Map<String, Object> formatWebSocketPayload(BusinessLog log) {
        Object data;
        try {
            data = objectMapper.readValue(log.toJson(), Object.class);
        } catch (Exception e) {
            data = log.toJson();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "business_log");
        payload.put("timestamp", log.getTimestamp());
        payload.put("logType", log.getLogType());
        payload.put("level", log.getLevel());
        payload.put("traceId", log.getTraceId());
        payload.put("data", data);
        return payload;
    }
}
