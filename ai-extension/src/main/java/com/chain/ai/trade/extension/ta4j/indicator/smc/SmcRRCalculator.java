package com.chain.ai.trade.extension.ta4j.indicator.smc;

import com.chain.ai.trade.engine.data.entity.dto.smc.SmcStructureDTO;

/**
 * SMC 结构盈亏比计算器 — 基于结构位的止损止盈计算
 * <p>做多：SL 设在 lastSwingLow 下方，TP 取最近 Bearish OB/FVG 下沿</p>
 * <p>做空：SL 设在 lastLowerHigh 上方，TP 取最近 Bullish OB/FVG 上沿</p>
 */
public class SmcRRCalculator {

    /**
     * 计算 SMC 结构驱动的盈亏比
     *
     * @param dto         SMC 结构数据
     * @param entryPrice  上游信号入场价
     * @param isBuy       多头 true / 空头 false
     * @param isChaosMode 混沌特例模式（止损收紧）
     * @return 盈亏比（≥0），数据不足返回 0
     */
    public static double calculateNetRR(SmcStructureDTO dto, double entryPrice, boolean isBuy, boolean isChaosMode) {
        double stopLoss;
        double takeProfit;

        if (isBuy) {
            // 止损：最近 HL 下方
            double recentHL = dto.getLastSwingLow();
            if (Double.isNaN(recentHL)) return 0;
            double buffer = isChaosMode ? 0.002 : 0.001;
            stopLoss = recentHL * (1 - buffer);

            // 止盈：最近 Bearish OB 或 Bearish FVG 下沿（取更近的目标 = 更高价格）
            double nearestTp = dto.getSwingOrderBlocks().stream()
                    .filter(ob -> ob.getBias() == -1)
                    .findFirst()
                    .map(SmcStructureDTO.OrderBlockDTO::getLow)
                    .orElse(entryPrice * 1.05);

            if (!Double.isNaN(dto.getLastBearishFVGBottom())) {
                nearestTp = Math.max(nearestTp, dto.getLastBearishFVGBottom());
            }
            takeProfit = nearestTp;

        } else {
            // 止损：最近 LH 上方
            double recentLH = dto.getLastLowerHigh();
            if (Double.isNaN(recentLH)) {
                // fallback
                if (Double.isNaN(dto.getLastSwingHigh())) return 0;
                recentLH = dto.getLastSwingHigh();
            }
            double buffer = isChaosMode ? 0.002 : 0.001;
            stopLoss = recentLH * (1 + buffer);

            // 止盈：最近 Bullish OB 或 Bullish FVG 上沿（取更近的目标 = 更高价格，离入场近）
            double nearestTp = dto.getSwingOrderBlocks().stream()
                    .filter(ob -> ob.getBias() == 1)
                    .findFirst()
                    .map(SmcStructureDTO.OrderBlockDTO::getHigh)
                    .orElse(entryPrice * 0.95);

            if (!Double.isNaN(dto.getLastBullishFVGTop())) {
                nearestTp = Math.max(nearestTp, dto.getLastBullishFVGTop());
            }
            takeProfit = nearestTp;
        }

        double risk = Math.abs(entryPrice - stopLoss);
        double reward = Math.abs(takeProfit - entryPrice);
        if (risk == 0) return 0;
        return reward / risk;
    }
}
