package com.chain.ai.trade.engine.strategy;

import com.chain.ai.trade.common.entity.constants.CompositeState;
import com.chain.ai.trade.engine.data.entity.dto.CriticalLevel;
import com.chain.ai.trade.extension.strategy.IndicatorCriticalLevelsStrategy;
import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import com.chain.ai.trade.extension.ta4j.indicator.SmcCriticalLevelsCalculator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于21种复合状态(CompositeState)的关键点位策略
 * 使用大周期方向 + 小周期内部趋势做精细化判断，替代旧7种趋势状态的equals匹配
 */
@Slf4j
@Component
public class CompositeIndicatorCriticalLevelsStrategy implements IndicatorCriticalLevelsStrategy {

    /** 中文名 → CompositeState 映射 */
    private static final Map<String, CompositeState> CN_TO_STATE = new HashMap<>();

    static {
        CN_TO_STATE.put("强上升·健康", CompositeState.STRONG_BULLISH_HEALTHY);
        CN_TO_STATE.put("强上升·浅回调", CompositeState.STRONG_BULLISH_SHALLOW_PULLBACK);
        CN_TO_STATE.put("强上升·预警回调(1H)", CompositeState.STRONG_BULLISH_WARNING_1H);
        CN_TO_STATE.put("强上升·预警回调(4H)", CompositeState.STRONG_BULLISH_WARNING_4H);
        CN_TO_STATE.put("强上升·确认回调", CompositeState.STRONG_BULLISH_CONFIRMED_PULLBACK);
        CN_TO_STATE.put("上升回调·进行中", CompositeState.BULLISH_PULLBACK_ONGOING);
        CN_TO_STATE.put("上升回调·筑底", CompositeState.BULLISH_PULLBACK_BOTTOMING);
        CN_TO_STATE.put("上升回调·失败", CompositeState.BULLISH_PULLBACK_FAILURE);
        CN_TO_STATE.put("上升末端·延续下跌", CompositeState.BULLISH_ENDING_CONTINUE_DOWN);
        CN_TO_STATE.put("上升末端·转势确认", CompositeState.BULLISH_ENDING_CONFIRM);
        CN_TO_STATE.put("强下降·健康", CompositeState.STRONG_BEARISH_HEALTHY);
        CN_TO_STATE.put("强下降·浅反弹", CompositeState.STRONG_BEARISH_SHALLOW_BOUNCE);
        CN_TO_STATE.put("强下降·预警反弹(1H)", CompositeState.STRONG_BEARISH_WARNING_1H);
        CN_TO_STATE.put("强下降·预警反弹(4H)", CompositeState.STRONG_BEARISH_WARNING_4H);
        CN_TO_STATE.put("强下降·确认反弹", CompositeState.STRONG_BEARISH_CONFIRMED_BOUNCE);
        CN_TO_STATE.put("下降反弹·进行中", CompositeState.BEARISH_PULLBACK_ONGOING);
        CN_TO_STATE.put("下降反弹·筑顶", CompositeState.BEARISH_PULLBACK_TOPPING);
        CN_TO_STATE.put("下降反弹·失败", CompositeState.BEARISH_PULLBACK_FAILURE);
        CN_TO_STATE.put("下降末端·延续反弹", CompositeState.BEARISH_ENDING_CONTINUE_UP);
        CN_TO_STATE.put("下降末端·转势确认", CompositeState.BEARISH_ENDING_CONFIRM);
        CN_TO_STATE.put("完全震荡", CompositeState.RANGING_NO_DIRECTION);
    }

    @PostConstruct
    public void init() {
        log.info("CompositeIndicatorCriticalLevelsStrategy 已加载（基于21种复合状态）");
    }

    @Override
    public String resolveDirection(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String trendState) {
        CompositeState cs = tryParseCompositeState(trendState);
        if (cs != null) {
            return resolveDirectionByState(cs);
        }
        // fallback: 基于results自行推导
        return resolveDirectionFromResults(results);
    }

    @Override
    public String resolveEntryObFilter(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String trendState) {
        CompositeState cs = tryParseCompositeState(trendState);
        if (cs != null) {
            return resolveFilterByState(cs);
        }
        // fallback: 基于results自行推导
        return resolveFilterFromResults(results);
    }

    @Override
    public List<CriticalLevel> calculate(
            Map<String, SmartMoneyConceptsIndicator.Result> results,
            String direction,
            double currentPrice) {
        return SmcCriticalLevelsCalculator.buildByDirection(results, direction, currentPrice, "ALL");
    }

    // ==================== CompositeState → 方向 ====================

    private String resolveDirectionByState(CompositeState cs) {
        switch (cs) {
            // 多头方向
            case STRONG_BULLISH_HEALTHY:
            case STRONG_BULLISH_SHALLOW_PULLBACK:
            case STRONG_BULLISH_WARNING_1H:
            case STRONG_BULLISH_WARNING_4H:
            case STRONG_BULLISH_CONFIRMED_PULLBACK:
            case BULLISH_PULLBACK_ONGOING:
            case BULLISH_PULLBACK_BOTTOMING:
            case BULLISH_PULLBACK_FAILURE:       // 回调失败 → 转空失败 → 延续多头
            case BULLISH_ENDING_CONTINUE_DOWN:   // 上升末端延续下跌 → 空头
            case BULLISH_ENDING_CONFIRM:         // 上升末端转势确认 → 空头
                return "buy";

            // 空头方向
            case STRONG_BEARISH_HEALTHY:
            case STRONG_BEARISH_SHALLOW_BOUNCE:
            case STRONG_BEARISH_WARNING_1H:
            case STRONG_BEARISH_WARNING_4H:
            case STRONG_BEARISH_CONFIRMED_BOUNCE:
            case BEARISH_PULLBACK_ONGOING:
            case BEARISH_PULLBACK_TOPPING:
            case BEARISH_PULLBACK_FAILURE:       // 反弹失败 → 转多失败 → 延续空头
            case BEARISH_ENDING_CONTINUE_UP:     // 下降末端延续反弹 → 多头
            case BEARISH_ENDING_CONFIRM:         // 下降末端转势确认 → 多头
                return "sell";

            default:
                return null;
        }
    }

    // ==================== CompositeState → OB过滤 ====================

    private String resolveFilterByState(CompositeState cs) {
        switch (cs) {
            // 健康强趋势 → 只取内部OB（严格）
            case STRONG_BULLISH_HEALTHY:
            case STRONG_BEARISH_HEALTHY:
                return "INTERNAL_ONLY";

            // 浅回调/反弹 → 严格
            case STRONG_BULLISH_SHALLOW_PULLBACK:
            case STRONG_BEARISH_SHALLOW_BOUNCE:
                return "INTERNAL_ONLY";

            // 预警回调 → 放宽到Swing
            case STRONG_BULLISH_WARNING_1H:
            case STRONG_BULLISH_WARNING_4H:
            case STRONG_BULLISH_CONFIRMED_PULLBACK:
            case STRONG_BEARISH_WARNING_1H:
            case STRONG_BEARISH_WARNING_4H:
            case STRONG_BEARISH_CONFIRMED_BOUNCE:
                return "SWING_ONLY";

            // 回调/反弹进行中 → Swing
            case BULLISH_PULLBACK_ONGOING:
            case BEARISH_PULLBACK_ONGOING:
            case BULLISH_PULLBACK_FAILURE:
            case BEARISH_PULLBACK_FAILURE:
                return "SWING_ONLY";

            // 筑底/筑顶 → 全部OB
            case BULLISH_PULLBACK_BOTTOMING:
            case BEARISH_PULLBACK_TOPPING:
                return "ALL";

            // 末端阶段 → 全部OB
            case BULLISH_ENDING_CONTINUE_DOWN:
            case BULLISH_ENDING_CONFIRM:
            case BEARISH_ENDING_CONTINUE_UP:
            case BEARISH_ENDING_CONFIRM:
                return "ALL";

            default:
                return "ALL";
        }
    }

    // ==================== tryParseCompositeState ====================

    private CompositeState tryParseCompositeState(String s) {
        if (s == null || s.isBlank()) return null;
        CompositeState cs = CN_TO_STATE.get(s);
        if (cs != null) {
            log.debug("trendState解析为CompositeState: {} -> {}", s, cs);
            return cs;
        }
        return null;
    }

    // ==================== fallback: 基于results自行推导 ====================

    private String resolveDirectionFromResults(Map<String, SmartMoneyConceptsIndicator.Result> results) {
        // 4H swing决定大方向
        SmartMoneyConceptsIndicator.Result r4h = results.get("4H");
        if (r4h != null) {
            int swing4h = r4h.getSwingTrend();
            int internal4h = r4h.getInternalTrend();
            if (swing4h == 1 && internal4h == 1) return "buy";
            if (swing4h == -1 && internal4h == -1) return "sell";
        }
        // 多周期投票
        int bullish = 0, bearish = 0;
        for (Map.Entry<String, SmartMoneyConceptsIndicator.Result> e : results.entrySet()) {
            SmartMoneyConceptsIndicator.Result r = e.getValue();
            if (r == null) continue;
            int st = r.getSwingTrend();
            int it = r.getInternalTrend();
            if (st == 1 && it == 1) bullish++;
            else if (st == -1 && it == -1) bearish++;
        }
        if (bullish >= 2) return "buy";
        if (bearish >= 2) return "sell";
        return null;
    }

    private String resolveFilterFromResults(Map<String, SmartMoneyConceptsIndicator.Result> results) {
        SmartMoneyConceptsIndicator.Result r4h = results.get("4H");
        SmartMoneyConceptsIndicator.Result r1h = results.get("1H");
        if (r4h != null && r1h != null) {
            int trend4h = r4h.getSwingTrend();
            int trend1h = r1h.getSwingTrend();
            // 大周期与小周期方向不一致 → 回调/反弹阶段 → Swing OB
            if (trend4h != 0 && trend1h != 0 && trend4h != trend1h) {
                return "SWING_ONLY";
            }
            // 大周期内部趋势与小周期内部趋势一致 → 健康趋势 → 内部OB
            if (r4h.getInternalTrend() == r1h.getInternalTrend() && r4h.getInternalTrend() != 0) {
                return "INTERNAL_ONLY";
            }
        }
        return "ALL";
    }
}
