package com.chain.ai.trade.engine.controller.logs;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TradeLogTestController {

    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/test/logs/send")
    public String sendTestLog() {
        Map<String, Object> payload = Map.of(
                "type", "business_log",
                "timestamp", System.currentTimeMillis(),
                "logType", "TRADE",
                "level", "INFO",
                "traceId", "TEST-" + System.nanoTime(),
                "data", Map.of(
                        "symbol", "BTCUSDT",
                        "side", "buy",
                        "price", 26888.0,
                        "qty", 0.15,
                        "pnl", 2.68,
                        "strategy", "demo-strategy"
                )
        );
        messagingTemplate.convertAndSend("/topic/logs", (Object) payload);
        return "ok";
    }
}
