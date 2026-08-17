package com.chain.ai.trade.logs.event;

/**
 * 系统异常事件
 */
public class SystemErrorEvent extends BusinessEvent {
    
    private String errorCode;
    private String errorMessage;
    private String component; // 发生异常的组件
    private String stackTrace;
    
    public SystemErrorEvent() {
        super("SYSTEM_ERROR", null);
    }
    
    public SystemErrorEvent(String traceId, String errorCode, String errorMessage, 
                          String component, String stackTrace) {
        super("SYSTEM_ERROR", traceId);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.component = component;
        this.stackTrace = stackTrace;
    }
    
    // Getters and Setters
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