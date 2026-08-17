package com.chain.ai.trade.engine.model;

/**
 * 出场规则配置
 */
public class ExitRuleConfig {

    public boolean fixedPercentStopLossEnabled;
    public Double fixedPercentStopLossPercent;
    public boolean takeProfitEnabled;
    public String takeProfitType;
    public Double takeProfitPercent;
    public boolean signalReversalExitEnabled = false;

}
