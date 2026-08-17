package com.chain.ai.trade.engine.signal.strategy;

import com.chain.ai.trade.common.entity.constants.CompositeState;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dto.CriticalLevel;
import com.chain.ai.trade.extension.strategy.CriticalLevelsConfig;
import com.chain.ai.trade.extension.strategy.SignalCriticalLevelsStrategy;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import com.chain.ai.trade.extension.ta4j.indicator.SmcCriticalLevelsCalculator;
import com.chain.ai.trade.extension.ta4j.indicator.trend.SmcTrendUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DefaultSignalCriticalLevelsStrategy implements SignalCriticalLevelsStrategy {

    @PostConstruct
    public void init() {
        log.info("DefaultSignalCriticalLevelsStrategy 已加载");
    }

    @Override
    public CriticalLevelsConfig getConfig() {
        return CriticalLevelsConfig.DEFAULT;
    }

    @Override
    public List<CriticalLevel> calculate(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String direction,
            double currentPrice) {
        CompositeState cs = resolveCompositeState(results, currentPrice);
        List<CriticalLevel> levels = SmcCriticalLevelsCalculator.buildByCompositeState(results, direction, currentPrice, "ALL", cs);
        for (CriticalLevel level : levels) {
            if ("入场".equals(level.getAction())) {
                level.setPrice(currentPrice);
                level.setDistancePercent(0.0);
            }
        }
        return levels;
    }

    /**
     * 从多周期SMC结果中解析复合状态
     */
    private static CompositeState resolveCompositeState(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            double currentPrice) {
        SmartMoneyConceptsIndicator.Result r4h = results.get("4H");
        SmartMoneyConceptsIndicator.Result r1h = results.get("1H");
        SmartMoneyConceptsIndicator.Result r15m = results.get("15M");
        if (r4h == null || r1h == null || r15m == null) {
            return null;
        }

        Map<CandlestickIntervalEnum, SmartMoneyConceptsIndicator.Result> map = new HashMap<>();
        map.put(CandlestickIntervalEnum.OKX4HOUR, r4h);
        map.put(CandlestickIntervalEnum.OKXMIN60, r1h);
        map.put(CandlestickIntervalEnum.OKXMIN15, r15m);

        boolean priceBrokenHigherLow = !Double.isNaN(r1h.getLastHigherLow())
                && currentPrice < r1h.getLastHigherLow();
        boolean priceBrokenLowerHigh = !Double.isNaN(r1h.getLastLowerHigh())
                && currentPrice > r1h.getLastLowerHigh();

        return SmcTrendUtils.getDetailedTrendState(
                map, currentPrice, priceBrokenHigherLow, priceBrokenLowerHigh);
    }
}
