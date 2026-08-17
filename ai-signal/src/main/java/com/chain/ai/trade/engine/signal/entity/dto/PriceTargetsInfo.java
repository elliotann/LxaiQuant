package com.chain.ai.trade.engine.signal.entity.dto;

import com.chain.ai.trade.engine.entity.dto.PriceTarget;
import com.chain.ai.trade.engine.entity.dto.StopLossLevel;
import com.chain.ai.trade.engine.risk.evaluator.impl.ElliottWaveEvaluator;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 价格目标信息封装类
 */
@Data
public class PriceTargetsInfo {
    private List<PriceTarget> priceTargets;          // 价格目标列表
    private List<StopLossLevel> stopLossLevels;      // 止损水平列表
    private Double optimalStopLoss;                 // 最优止损位
    private Double optimalTakeProfit;               // 最优止盈位

    public PriceTargetsInfo() {
        this.priceTargets = new ArrayList<>();
        this.stopLossLevels = new ArrayList<>();
    }

    public PriceTargetsInfo(List<PriceTarget> priceTargets, List<StopLossLevel> stopLossLevels,
                            Double optimalStopLoss, Double optimalTakeProfit) {
        this.priceTargets = priceTargets != null ? priceTargets : new ArrayList<>();
        this.stopLossLevels = stopLossLevels != null ? stopLossLevels : new ArrayList<>();
        this.optimalStopLoss = optimalStopLoss;
        this.optimalTakeProfit = optimalTakeProfit;
    }
}
