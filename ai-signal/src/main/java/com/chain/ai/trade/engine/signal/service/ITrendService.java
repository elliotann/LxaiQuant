package com.chain.ai.trade.engine.signal.service;


import com.chain.ai.trade.engine.signal.entity.constants.Trend;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;

public interface ITrendService {
    public Trend getTrend(IndicatorCalcDto calcDto);

    default Trend getTrendChange(IndicatorCalcDto calcDto){
        return Trend.UN_KNOW;
    }
}
