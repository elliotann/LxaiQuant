package com.chain.ai.trade.engine.risk.evaluator;

import com.chain.ai.trade.engine.entity.dto.TradingSignalDto;
import com.chain.ai.trade.engine.risk.common.TimeFrame;

import java.util.Collections;
import java.util.List;

/**
 * 质量评估器接口
 */
public interface QualityEvaluator {

    /**
     * 评估器唯一标识
     */
    String getId();

    /**
     * 评估器名称
     */
    String getName();

    /**
     * 评估器描述
     */
    String getDescription();

    /**
     * 评估信号质量
     * @param signal 待评估信号
     * @param context 评估上下文（包含K线、指标等）
     * @return 质量评估结果
     */
    QualityEvaluationResult evaluate(TradingSignalDto signal, EvaluationContext context);

    /**
     * 评估器权重（影响最终置信度的权重）
     */
    default double getWeight() {
        return 1.0;
    }

    /**
     * 是否需要多周期数据
     */
    default boolean requiresMultiTimeFrame() {
        return false;
    }

    /**
     * 如果需要多周期数据，指定哪些时间框架
     */
    default List<TimeFrame> getRequiredTimeFrames() {
        return Collections.emptyList();
    }
}

