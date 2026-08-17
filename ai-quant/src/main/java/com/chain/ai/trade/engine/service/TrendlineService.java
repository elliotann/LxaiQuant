package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.engine.controller.dto.TrendlineData;
import com.chain.ai.trade.engine.controller.dto.TrendlineParams;
import org.ta4j.core.BarSeries;

import java.util.List;

public interface TrendlineService {
    TrendlineData calculateTrendlines(BarSeries series, List<String> indicators, TrendlineParams params);
}
