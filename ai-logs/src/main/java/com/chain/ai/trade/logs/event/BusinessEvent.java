package com.chain.ai.trade.logs.event;

/**
 * 业务事件基类
 * 所有业务事件都需要继承此类
 */
public abstract class BusinessEvent {
    
    private long timestamp;      // 事件发生时间（毫秒）
    private String eventType;    // 事件类型标识
    private String traceId;      // 全局追踪ID
    
    public BusinessEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public BusinessEvent(String eventType, String traceId) {
        this();
        this.eventType = eventType;
        this.traceId = traceId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}