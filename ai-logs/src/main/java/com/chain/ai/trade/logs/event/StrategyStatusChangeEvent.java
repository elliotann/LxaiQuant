package com.chain.ai.trade.logs.event;

/**
 * 策略状态变更事件
 */
public class StrategyStatusChangeEvent extends BusinessEvent {
    
    private String strategyId;
    private String strategyName;
    private String oldStatus;
    private String newStatus; // RUNNING, STOPPED, PAUSED, ERROR
    private String reason;
    
    public StrategyStatusChangeEvent() {
        super("STRATEGY_STATUS_CHANGE", null);
    }
    
    public StrategyStatusChangeEvent(String traceId, String strategyId, String strategyName, 
                                   String oldStatus, String newStatus, String reason) {
        super("STRATEGY_STATUS_CHANGE", traceId);
        this.strategyId = strategyId;
        this.strategyName = strategyName;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }
    
    // Getters and Setters
    public String getStrategyId() {
        return strategyId;
    }
    
    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }
    
    public String getStrategyName() {
        return strategyName;
    }
    
    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }
    
    public String getOldStatus() {
        return oldStatus;
    }
    
    public void setOldStatus(String oldStatus) {
        this.oldStatus = oldStatus;
    }
    
    public String getNewStatus() {
        return newStatus;
    }
    
    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
}