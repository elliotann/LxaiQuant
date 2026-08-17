package com.chain.ai.trade.extension.ta4j.indicator.smc;

import com.chain.ai.trade.engine.data.entity.dto.smc.ChaosExceptionResult;

/**
 * 混沌特例评估器 — 判断是否命中混沌特例
 * <p>四条件 AND：Wave=0 + RR≥3:1 + 风险≤0.5% + flipCount＜3</p>
 */
public class ChaosExceptionEvaluator {

    /**
     * 判断是否命中混沌特例
     *
     * @param waveIndex       波次
     * @param riskRewardRatio 盈亏比
     * @param riskPercent     单笔风险占比（%）
     * @param flipCount       翻转频率
     * @return 评估结果
     */
    public static ChaosExceptionResult evaluate(int waveIndex, double riskRewardRatio, double riskPercent, int flipCount) {
        // 条件1：波次必须为混沌（Wave 0）
        if (waveIndex != 0) {
            return new ChaosExceptionResult(false, "波次非混沌（当前: " + waveIndex + "）", 1.0);
        }

        // 条件2：翻转频率 < 3（极端混沌不可豁免）
        if (flipCount >= 3) {
            return new ChaosExceptionResult(false, "翻转频率过高（" + flipCount + " ≥ 3），极端混沌不可豁免", 1.0);
        }

        // 条件3：盈亏比 ≥ 3:1
        if (riskRewardRatio < 3.0) {
            return new ChaosExceptionResult(false,
                    "盈亏比不足（" + String.format("%.2f", riskRewardRatio) + " < 3:1）", 1.0);
        }

        // 条件4：单笔风险 ≤ 0.5%
        if (riskPercent > 0.5) {
            return new ChaosExceptionResult(false,
                    "单笔风险过高（" + String.format("%.2f", riskPercent) + "% > 0.5%）", 1.0);
        }

        // 全部满足
        String reason = "混沌特例触发：RR=" + String.format("%.2f", riskRewardRatio)
                + ", 风险=" + String.format("%.2f", riskPercent) + "%";
        return new ChaosExceptionResult(true, reason, 0.2);
    }
}
