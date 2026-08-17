package com.chain.ai.trade.logs.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 账户资金变动日志实现类
 */
public class AccountFundChangeLog implements BusinessLog, Traceable {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private long timestamp;
    private String logType = "ACCOUNT_FUND_CHANGE";
    private String level = "INFO";
    private String traceId;
    private String accountId;
    private String asset;
    private double amount;
    private double balance;
    private String changeType;

    public AccountFundChangeLog() {
        this.timestamp = System.currentTimeMillis();
    }

    public AccountFundChangeLog(String level, String traceId, String accountId, String asset, 
                              double amount, double balance, String changeType) {
        this();
        this.level = level;
        this.traceId = traceId;
        this.accountId = accountId;
        this.asset = asset;
        this.amount = amount;
        this.balance = balance;
        this.changeType = changeType;
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
            throw new RuntimeException("Failed to serialize AccountFundChangeLog to JSON", e);
        }
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
}
