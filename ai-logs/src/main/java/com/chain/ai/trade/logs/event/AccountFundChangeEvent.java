package com.chain.ai.trade.logs.event;

/**
 * 账户资金变更事件
 */
public class AccountFundChangeEvent extends BusinessEvent {
    
    private String accountId;
    private String currency;
    private double amount;
    private double balance;
    private String changeType; // DEPOSIT, WITHDRAW, FREEZE, UNFREEZE
    
    public AccountFundChangeEvent() {
        super("ACCOUNT_FUND_CHANGE", null);
    }
    
    public AccountFundChangeEvent(String traceId, String accountId, String currency, 
                                 double amount, double balance, String changeType) {
        super("ACCOUNT_FUND_CHANGE", traceId);
        this.accountId = accountId;
        this.currency = currency;
        this.amount = amount;
        this.balance = balance;
        this.changeType = changeType;
    }
    
    // Getters and Setters
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
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