package com.chain.ai.trade.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BacktestMCPTools implements McpTool {

    @Tool("运行策略回测")
    public String runBacktest(@P("策略代码或ID") String strategyCode,
                              @P("交易对") String symbol,
                              @P("开始日期") String startDate,
                              @P("结束日期") String endDate,
                              @P("初始资金") double initialCapital) {
        log.info("run_backtest: strategy={}, symbol={}, start={}, end={}, capital={}",
                strategyCode, symbol, startDate, endDate, initialCapital);
        return "{\"status\": \"pending\", \"message\": \"回测任务已提交\"}";
    }
}
