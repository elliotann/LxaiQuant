package com.chain.ai.trade.common.push;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalPushMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;

    private String source;

    private String symbol;

    private String direction;
    // 机器人 ID（可选）
    private String robotId;

    private Map<String, Object> data;

    private long timestamp;

    private long ttlMinutes;
}
