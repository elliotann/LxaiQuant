package com.chain.ai.trade.engine.signal.service;


import com.chain.ai.trade.engine.signal.entity.constants.TradeSignal;
import com.chain.ai.trade.engine.signal.entity.dto.BuyAndSellWeightDto;
import com.chain.ai.trade.engine.signal.entity.dto.IndicatorCalcDto;
import com.chain.ai.trade.engine.signal.entity.dto.WeightAndConfidenceDto;

import java.math.BigDecimal;

/**
 * 信号接口
 */
public interface ISignService {
    public BuyAndSellWeightDto execute(IndicatorCalcDto calcDto);

    /**
     * 风险权重
     * @param calcDto
     * @param index
     * @return
     */
    default double calculateRisk(IndicatorCalcDto calcDto){
        return 1.0;
    }
    /**
     * 补仓风险权重
     * @param calcDto
     * @param index
     * @return
     */
    default double calculateRiskRepair(IndicatorCalcDto calcDto){
        return 1.0;
    }

    /**
     * 平仓
     * @param calcDto
     * @return
     */
    default BuyAndSellWeightDto executeClose(IndicatorCalcDto calcDto){
        return null;
    }

    default TradeSignal generateTradeSignal(int index){
        return null;
    }

    default Double getWeight(IndicatorCalcDto calcDto){
        return 0d;
    }

    /**
     * 获取权重和置信度
     * @param calcDto 计算DTO
     * @return 权重和置信度DTO，如果未实现则返回默认值（权重0，置信度0.5）
     */
    default WeightAndConfidenceDto getWeightAndConfidence(IndicatorCalcDto calcDto){
        Double weight = getWeight(calcDto);
        return new WeightAndConfidenceDto(
            BigDecimal.valueOf(weight),
            null, null, null
        );
    }
}
