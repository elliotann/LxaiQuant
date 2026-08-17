package com.chain.ai.trade.engine.service;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;

/**
 * 图表服务接口
 * 提供策略回测结果的可视化图表生成功能
 */
public interface IChartService {

    /**
     * 生成策略回测图表
     */
    ChartService.ChartResult generateStrategyChart(BarSeries series, String strategyName, TradingRecord record);


}

