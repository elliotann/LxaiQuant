package com.chain.ai.trade.engine.strategy;

import com.chain.ai.trade.engine.data.entity.dto.CriticalLevel;
import com.chain.ai.trade.extension.strategy.IndicatorCriticalLevelsStrategy;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import com.chain.ai.trade.extension.ta4j.indicator.SmcCriticalLevelsCalculator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DefaultIndicatorCriticalLevelsStrategy implements IndicatorCriticalLevelsStrategy {

    @PostConstruct
    public void init() {
        log.info("DefaultIndicatorCriticalLevelsStrategy 已加载");
    }

    @Override
    public String resolveDirection(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String trendState) {
        boolean bearishContext = trendState != null
                && (trendState.equals("强下降") || trendState.equals("下降反弹") || trendState.equals("下降末端"));
        boolean bullishContext = trendState != null
                && (trendState.equals("强上升") || trendState.equals("上升回调") || trendState.equals("上升末端"));

        if (!bearishContext && !bullishContext) {
            SmartMoneyConceptsIndicator.Result r4h = results.get("4H");
            if (r4h != null) {
                int swingTrend = r4h.getSwingTrend();
                if (swingTrend == -1) {
                    bearishContext = true;
                } else if (swingTrend == 1) {
                    bullishContext = true;
                }
            }
        }

        if (!bearishContext && !bullishContext) {
            int bearishCount = 0, bullishCount = 0;
            for (Map.Entry<String, SmartMoneyConceptsIndicator.Result> entry : results.entrySet()) {
                SmartMoneyConceptsIndicator.Result r = entry.getValue();
                if (r == null) continue;
                int swing = r.getSwingTrend();
                if (swing == -1) bearishCount++;
                else if (swing == 1) bullishCount++;
            }
            if (bearishCount >= 2) bearishContext = true;
            else if (bullishCount >= 2) bullishContext = true;
        }

        if (!bearishContext && !bullishContext) {
            return null;
        }
        return bearishContext ? "sell" : "buy";
    }

    @Override
    public String resolveEntryObFilter(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String trendState) {
        if ("强上升".equals(trendState) || "强下降".equals(trendState)) {
            return "INTERNAL_ONLY";
        }
        if ("上升回调".equals(trendState) || "下降反弹".equals(trendState)) {
            return "SWING_ONLY";
        }
        SmartMoneyConceptsIndicator.Result r4h = results.get("4H");
        SmartMoneyConceptsIndicator.Result r1h = results.get("1H");
        if (r4h != null && r1h != null) {
            int trend4h = r4h.getSwingTrend();
            int trend1h = r1h.getSwingTrend();
            if (trend4h != 0 && trend1h != 0 && trend4h != trend1h) {
                return "SWING_ONLY";
            }
        }
        return "ALL";
    }

    @Override
    public List<CriticalLevel> calculate(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String direction,
            double currentPrice) {
        return SmcCriticalLevelsCalculator.buildByDirection(results, direction, currentPrice, "ALL");
    }
}
