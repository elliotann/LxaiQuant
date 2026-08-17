package com.chain.ai.trade.engine.notifier.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    private String id;
    private String userId;
    private String type;
    private String title;
    private String content;
    private String symbol;
    private String severity;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
