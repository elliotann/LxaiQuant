package com.chain.ai.trade.common.push;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPushMessage {

    /** trade / risk / system / strategy */
    private String type;
    private String userId;
    private String title;
    private String content;
    private String symbol;
    /** info / warning / critical */
    private String severity;
    private Map<String, Object> metadata;
    private long timestamp;
    /** 测试/回测模式通知，true 时跳过实际推送 */
    private Boolean isTest;
}
