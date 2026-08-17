package com.chain.ai.trade.engine.service.advice;

import com.chain.ai.trade.engine.signal.entity.dto.TechnicalSignalDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public void pushSignal(TechnicalSignalDTO signal) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "AI_SIGNAL");
            payload.put("symbol", signal.getSymbol());
            payload.put("direction", signal.getTechnicalDirection());
            payload.put("robotId", signal.getIndicator());
            payload.put("strength", signal.getSignalStrength());
            payload.put("entryType", signal.getEntryType());
            payload.put("price", signal.getCurrentPrice());
            payload.put("timeframe", signal.getTimeframe());
            payload.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/signals", payload, new HashMap<String, Object>());
            log.info("信号已推送至WebSocket: symbol={}, direction={}, strength={}",
                    signal.getSymbol(), signal.getTechnicalDirection(), signal.getSignalStrength());
        } catch (Exception e) {
            log.error("推送信号至WebSocket失败: symbol={}", signal.getSymbol(), e);
        }
    }
}
