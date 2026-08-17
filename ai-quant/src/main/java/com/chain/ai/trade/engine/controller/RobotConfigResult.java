package com.chain.ai.trade.engine.controller;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 机器人配置查询结果
 */
@Getter
public class RobotConfigResult {
    private String accountId;
    private String strategyBeanName;
    private String interval;
    private String strategyId;
    private BigDecimal initialCapital;
    private Double leverage;

    public RobotConfigResult(String accountId, String strategyBeanName, String interval, String strategyId, BigDecimal initialCapital, Double leverage) {
        this.accountId = accountId;
        this.strategyBeanName = strategyBeanName;
        this.interval = interval;
        this.strategyId = strategyId;
        this.initialCapital = initialCapital;
        this.leverage = leverage;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getStrategyBeanName() {
        return strategyBeanName;
    }

    public void setStrategyBeanName(String strategyBeanName) {
        this.strategyBeanName = strategyBeanName;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    public void setInitialCapital(BigDecimal initialCapital) {
        this.initialCapital = initialCapital;
    }

    public Double getLeverage() {
        return leverage;
    }

    public void setLeverage(Double leverage) {
        this.leverage = leverage;
    }
}
