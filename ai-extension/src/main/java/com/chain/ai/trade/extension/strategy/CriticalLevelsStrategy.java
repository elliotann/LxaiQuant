package com.chain.ai.trade.extension.strategy;

import com.chain.ai.trade.engine.data.entity.dto.CriticalLevel;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;

import java.util.List;
import java.util.Map;

/**
 * 关键点位计算策略接口
 * 不同机器人可通过实现此接口来定制关键点位计算逻辑
 */
public interface CriticalLevelsStrategy {

    List<CriticalLevel> calculate(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String direction,
            double currentPrice
    );
}
