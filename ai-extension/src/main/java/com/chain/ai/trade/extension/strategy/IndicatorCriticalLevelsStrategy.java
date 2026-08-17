package com.chain.ai.trade.extension.strategy;

import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;

import java.util.Map;

/**
 * 指标驱动型关键点位策略
 * 行情看板使用，方向由趋势状态推导，OB过滤可动态调整
 */
public interface IndicatorCriticalLevelsStrategy extends CriticalLevelsStrategy {

    String resolveDirection(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String trendState
    );

    String resolveEntryObFilter(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String trendState
    );
}
