package com.chain.ai.trade.engine.service.advice;

import com.alibaba.fastjson2.JSON;
import com.chain.ai.trade.common.push.SignalPushMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalRedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            SignalPushMessage pushMsg = JSON.parseObject(body, SignalPushMessage.class);
            if (pushMsg == null) {
                log.warn("解析Redis推送消息失败，body={}", body);
                return;
            }
            Map<String, Object> payload = buildPayload(pushMsg);
            messagingTemplate.convertAndSend("/topic/signals", (Object) payload);
            log.info("Redis信号已转发至STOMP: type={}, source={}, symbol={}",
                    pushMsg.getType(), pushMsg.getSource(), pushMsg.getSymbol());
        } catch (Exception e) {
            log.error("处理Redis推送消息异常", e);
        }
    }

    private Map<String, Object> buildPayload(SignalPushMessage msg) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", msg.getType() != null ? msg.getType() : "UNKNOWN");
        payload.put("source", msg.getSource() != null ? msg.getSource() : "SYSTEM");
        payload.put("symbol", msg.getSymbol());
        payload.put("direction", msg.getDirection());
        payload.put("robotId", msg.getRobotId());
        payload.put("timestamp", msg.getTimestamp());
        payload.put("ttlMinutes", msg.getTtlMinutes());
        if (msg.getData() != null) {
            payload.putAll(msg.getData());
        }
        return payload;
    }
}
