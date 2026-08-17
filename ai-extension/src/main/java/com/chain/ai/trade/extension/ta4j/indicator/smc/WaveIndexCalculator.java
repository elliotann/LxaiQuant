package com.chain.ai.trade.extension.ta4j.indicator.smc;

import com.chain.ai.trade.extension.ta4j.indicator.SmartMoneyConceptsIndicator;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 波次计算器 — 基于 swingTrend 变化和 BOS 事件计算当前波次
 * <p>波次定义：CHOCH 启动计数为 1，每次 Swing BOS 递增，趋势反转归零</p>
 */
@Slf4j
public class WaveIndexCalculator {

    /**
     * 计算当前波次（自动适配多空方向）
     *
     * @param results    历史结果列表（按时间正序）
     * @param currentIdx 当前索引
     * @param isBuy      多头 true / 空头 false
     * @return 多头返回 0~4+，空头返回 0~-4+
     */
    public static int calculate(List<SmartMoneyConceptsIndicator.Result> results, int currentIdx, boolean isBuy) {
        int wave = 0;
        Integer prevTrend = null;

        for (int i = 0; i <= currentIdx; i++) {
            SmartMoneyConceptsIndicator.Result bar = results.get(i);
            int currentTrend = bar.getSwingTrend();

            if (isBuy) {
                // 多头：CHOCH 启动 → BOS 递增
                if (prevTrend != null && prevTrend == -1 && currentTrend == 1) {
                    wave = 1; // 试盘（CHOCH 确认转多）
                } else if (currentTrend == 1 && bar.isSwingBullishBOS()) {
                    wave = (wave == 0) ? 1 : wave + 1; // 确认 → 加速
                } else if (currentTrend == -1 && wave > 0) {
                    wave = 0; // 趋势反转，归零
                }
            } else {
                // 空头：CHoCH 启动 → BOS 递增
                if (prevTrend != null && prevTrend == 1 && currentTrend == -1) {
                    wave = -1; // 试盘
                } else if (currentTrend == -1 && bar.isSwingBearishBOS()) {
                    wave = (wave == 0) ? -1 : wave - 1;
                } else if (currentTrend == 1 && wave < 0) {
                    wave = 0; // 趋势反转，归零
                }
            }
            prevTrend = currentTrend;
        }
        return wave;
    }

    /**
     * 波次 → 阶段名称映射
     *
     * @param wave  波次值
     * @param isBuy 方向
     * @return 中文阶段名：混沌 / 试盘 / 确认 / 加速 / 赶顶 / 赶底
     */
    public static String getWavePhase(int wave, boolean isBuy) {
        if (isBuy) {
            switch (wave) {
                case 0: return "混沌";
                case 1: return "试盘";
                case 2: return "确认";
                case 3: return "加速";
                default: return "赶顶";
            }
        } else {
            switch (wave) {
                case 0: return "混沌";
                case -1: return "试盘";
                case -2: return "确认";
                case -3: return "加速";
                default: return "赶底";
            }
        }
    }

    /**
     * 计算 swingTrend 翻转频率（过去 N 根 K 线）
     *
     * @param results    历史结果列表
     * @param currentIdx 当前索引
     * @param lookback   回溯根数
     * @return 翻转次数
     */
    public static int calculateFlipCount(List<SmartMoneyConceptsIndicator.Result> results, int currentIdx, int lookback) {
        int flips = 0;
        Integer prevTrend = null;
        int start = Math.max(0, currentIdx - lookback + 1);

        for (int i = start; i <= currentIdx; i++) {
            int trend = results.get(i).getSwingTrend();
            if (prevTrend != null && prevTrend != 0 && trend != 0 && trend != prevTrend) {
                flips++;
            }
            prevTrend = trend;
        }
        return flips;
    }

    /**
     * 计算结构年龄 — 当前结构形成后的 K 线根数
     *
     * @param results    历史结果列表
     * @param currentIdx 当前索引
     * @return 结构年龄（最小为 0）
     */
    public static int calculateStructureAge(List<SmartMoneyConceptsIndicator.Result> results, int currentIdx) {
        if (currentIdx < 0 || results.isEmpty()) return 0;

        int currentTrend = results.get(currentIdx).getSwingTrend();
        for (int i = currentIdx; i >= 0; i--) {
            if (results.get(i).getSwingTrend() != currentTrend) {
                return currentIdx - i;
            }
        }
        return currentIdx + 1;
    }
}
