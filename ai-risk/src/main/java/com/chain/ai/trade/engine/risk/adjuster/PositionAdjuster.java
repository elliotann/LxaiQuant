package com.chain.ai.trade.engine.risk.adjuster;

import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;

/**
 * 仓位调节器接口
 */
public interface PositionAdjuster {

    /**
     * 调节器ID
     */
    String getId();

    /**
     * 基于信号质量调节仓位
     * @param signal 交易信号
     * @param qualityScore 质量得分（0-1）
     * @param basePosition 基础仓位
     * @param context 调节上下文
     * @return 调节后的仓位权重
     */
    AdjustmentResult adjust(
            TradingSignalDto signal,
            double qualityScore,
            double basePosition,
            AdjustmentContext context);

    /**
     * 简单仓位计算
     * @param initialCapital
     * @param symbol
     * @param signalStrength 信号权重
     * @return 返回张数，只能是整数
     */
    default BigDecimal adjust(Double initialCapital, String symbol, double leverage ,BigDecimal signalStrength, Num nowPrice){
        return BigDecimal.ZERO;
    }
}

