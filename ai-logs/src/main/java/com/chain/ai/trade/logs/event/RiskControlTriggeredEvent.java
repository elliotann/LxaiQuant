package com.chain.ai.trade.logs.event;

/**
 * 风控规则触发事件
 */
public class RiskControlTriggeredEvent extends BusinessEvent {
    
    private String ruleId;
    private String ruleName;
    private String symbol;
    private String action; // BLOCK, WARN, LIMIT
    private String reason;
    
    public RiskControlTriggeredEvent() {
        super("RISK_CONTROL_TRIGGERED", null);
    }
    
    public RiskControlTriggeredEvent(String traceId, String ruleId, String ruleName, 
                                    String symbol, String action, String reason) {
        super("RISK_CONTROL_TRIGGERED", traceId);
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.symbol = symbol;
        this.action = action;
        this.reason = reason;
    }
    
    // Getters and Setters
    public String getRuleId() {
        return ruleId;
    }
    
    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }
    
    public String getRuleName() {
        return ruleName;
    }
    
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
}