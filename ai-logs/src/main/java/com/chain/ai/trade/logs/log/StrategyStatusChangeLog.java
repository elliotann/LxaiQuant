package com.chain.ai.trade.logs.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 策略状态变更日志实现类
 */
public class StrategyStatusChangeLog implements BusinessLog, Traceable {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private long timestamp;
    private String logType = "STRATEGY_STATUS_CHANGE";
    private String level = "INFO";
    private String traceId;
    private String strategyId;
    private String strategyName;
    private String oldStatus;
    private String newStatus;
    private String reason;

    public StrategyStatusChangeLog() {
        this.timestamp = System.currentTimeMillis();
    }

    public StrategyStatusChangeLog(String level, String traceId, String strategyId, String strategyName, 
                                 String oldStatus, String newStatus, String reason) {
        this();
        this.level = level;
        this.traceId = traceId;
        this.strategyId = strategyId;
        this.strategyName = strategyName;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
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
            throw new RuntimeException("Failed to serialize StrategyStatusChangeLog to JSON", e);
        }
    }

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
