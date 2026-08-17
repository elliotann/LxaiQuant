package com.chain.ai.trade.logs.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 系统错误日志实现类
 */
public class SystemErrorLog implements BusinessLog, Traceable {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private long timestamp;
    private String logType = "SYSTEM_ERROR";
    private String level = "ERROR";
    private String traceId;
    private String errorCode;
    private String errorMessage;
    private String component;
    private String stackTrace;
    
    public SystemErrorLog() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public SystemErrorLog(String level, String traceId, String errorCode, String errorMessage, 
                         String component, String stackTrace) {
        this();
        this.level = level;
        this.traceId = traceId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.component = component;
        this.stackTrace = stackTrace;
    }
    
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String getLogType() {
        return logType;
    }
    
    @Override
    public String getLevel() {
        return level;
    }
    
    @Override
    public String getTraceId() {
        return traceId;
    }
    
    @Override
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize SystemErrorLog to JSON", e);
        }
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getComponent() {
        return component;
    }
    
    public void setComponent(String component) {
        this.component = component;
    }
    
    public String getStackTrace() {
        return stackTrace;
    }
    
    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }
}
