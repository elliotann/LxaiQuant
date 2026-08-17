package com.chain.ai.trade.extension.ta4j.indicator.smc;

import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;

/**
 * 位置比计算器 — 价格在结构区间内的相对位置
 * <p>做多：(price - HL) / (HH - HL)，HH = lastSwingHigh, HL = lastHigherLow → fallback lastSwingLow</p>
 * <p>做空：(price - LL) / (LH - LL)，LH = lastLowerHigh → fallback lastSwingHigh, LL = lastSwingLow</p>
 */
public class PositionRatioCalculator {

    /**
     * 计算位置比（自动适配多空方向）
     *
     * @param result       SMC 指标结果
     * @param isBuy        多头 true / 空头 false
     * @param currentPrice 当前价格
     * @return 位置比 0.00 ~ 1.00，数据不足时返回 0.5
     */
    public static double calculate(SmartMoneyConceptsIndicator.Result result, boolean isBuy, double currentPrice) {
        if (isBuy) {
            // 做多：基于 HH - HL
            double hh = result.getLastSwingHigh();
            double hl = result.getLastHigherLow();
            if (Double.isNaN(hl)) {
                hl = result.getLastSwingLow(); // fallback
            }
            if (Double.isNaN(hh) || Double.isNaN(hl) || hh <= hl) return 0.5;
            return clamp((currentPrice - hl) / (hh - hl));
        } else {
            // 做空：基于 LH - LL
            double lh = result.getLastLowerHigh();
            if (Double.isNaN(lh)) {
                lh = result.getLastSwingHigh(); // fallback
            }
            double ll = result.getLastSwingLow();
            if (Double.isNaN(lh) || Double.isNaN(ll) || lh <= ll) return 0.5;
            return clamp((currentPrice - ll) / (lh - ll));
        }
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
