package com.chain.ai.trade.engine2.core.context;

import org.ta4j.core.BarSeries;

import java.util.Map;

/**
 * 策略初始化上下文 — 引擎在策略启动时传入。
 * <p>
 * 包含策略所需的全局参数、K线序列、交易对等信息。
 */
public class StrategyContext {

    private final String strategyId;
    private final String symbol;
    private final String interval;
    private final BarSeries barSeries;
    private final Map<String, Object> params;

    public StrategyContext(String strategyId, String symbol, String interval,
                           BarSeries barSeries, Map<String, Object> params) {
        this.strategyId = strategyId;
        this.symbol = symbol;
        this.interval = interval;
        this.barSeries = barSeries;
        this.params = params;
    }

    public String getStrategyId() { return strategyId; }
    public String getSymbol() { return symbol; }
    public String getInterval() { return interval; }
    public BarSeries getBarSeries() { return barSeries; }
    public Map<String, Object> getParams() { return params; }
}
