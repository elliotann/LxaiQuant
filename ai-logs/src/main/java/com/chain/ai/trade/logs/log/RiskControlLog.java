package com.chain.ai.trade.logs.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 风控日志实现类
 */
public class RiskControlLog implements BusinessLog, Traceable {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private long timestamp;
    private String logType = "RISK_CONTROL";
    private String level = "WARN";
    private String traceId;
    private String ruleId;
    private String ruleName;
    private String subject;
    private String action;
    private String reason;

    public RiskControlLog() {
        this.timestamp = System.currentTimeMillis();
    }

    public RiskControlLog(String level, String traceId, String ruleId, String ruleName, String subject, 
                          String action, String reason) {
        this();
        this.level = level;
        this.traceId = traceId;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.subject = subject;
        this.action = action;
        this.reason = reason;
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
            throw new RuntimeException("Failed to serialize RiskControlLog to JSON", e);
        }
    }

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

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
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
