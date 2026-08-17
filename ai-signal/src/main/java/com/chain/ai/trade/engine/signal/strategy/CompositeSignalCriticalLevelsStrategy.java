package com.chain.ai.trade.engine.signal.strategy;

import com.chain.ai.trade.engine.data.entity.dto.CriticalLevel;
import com.chain.ai.trade.extension.strategy.CriticalLevelsConfig;
import com.chain.ai.trade.extension.strategy.SignalCriticalLevelsStrategy;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import com.chain.ai.trade.extension.ta4j.indicator.SmcCriticalLevelsCalculator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 基于21种复合状态(CompositeState)的信号驱动关键点位策略
 * 入场价覆盖为市价（currentPrice），OB过滤根据复合状态动态调整
 */
@Slf4j
@Component
public class CompositeSignalCriticalLevelsStrategy implements SignalCriticalLevelsStrategy {

    @PostConstruct
    public void init() {
        log.info("CompositeSignalCriticalLevelsStrategy 已加载（基于21种复合状态）");
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
        // 依据多周期结果动态决定OB过滤
        String filter = resolveFilterFromResults(results);
        List<CriticalLevel> levels = SmcCriticalLevelsCalculator.buildByDirection(results, direction, currentPrice, filter);
        // 信号场景：入场价覆盖为市价
        for (CriticalLevel level : levels) {
            if ("入场".equals(level.getAction())) {
                level.setPrice(currentPrice);
                level.setDistancePercent(0.0);
            }
        }
        return levels;
    }

    /**
     * 根据多周期Swing/Internal趋势方向一致性动态调整OB过滤
     */
    private String resolveFilterFromResults(Map<String, SmartMoneyConceptsIndicator.Result> results) {
        SmartMoneyConceptsIndicator.Result r4h = results.get("4H");
        SmartMoneyConceptsIndicator.Result r1h = results.get("1H");
        if (r4h == null || r1h == null) return "ALL";

        int swing4h = r4h.getSwingTrend();
        int internal4h = r4h.getInternalTrend();
        int swing1h = r1h.getSwingTrend();
        int internal1h = r1h.getInternalTrend();

        // 健康强趋势 → 内部OB
        if (swing4h == internal4h && swing1h == internal1h && swing4h == swing1h && swing4h != 0) {
            return "INTERNAL_ONLY";
        }
        // 大周期与小周期方向不一致 → 回调/反弹 → Swing OB
        if (swing4h != 0 && swing1h != 0 && swing4h != swing1h) {
            return "SWING_ONLY";
        }
        // 大周期内部趋势与小周期方向相反 → 预警阶段
        if (internal4h != 0 && swing1h == -internal4h) {
            return "SWING_ONLY";
        }
        return "ALL";
    }
}
