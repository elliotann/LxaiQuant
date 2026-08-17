package com.chain.ai.trade.engine.risk.evaluator;

import com.chain.ai.trade.engine.entity.dto.PriceAnalysisDto;
import lombok.AllArgsConstructor;
import com.chain.ai.trade.engine.risk.evaluator.impl.ElliottWaveEvaluator;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 质量评估结果
 */
@Data
@Builder
public class QualityEvaluationResult {
    private String evaluatorId;           // 评估器ID
    private String signalId;              // 信号ID
    private double score;                 // 评估得分（0-1）
    private double weight;                // 评估器权重
    @Builder.Default
    private Map<String, Object> factors = new HashMap<>();  // 评估因子详细数据
    private String summary;               // 评估摘要
    @Builder.Default
    private List<String> warnings = new ArrayList<>();        // 警告信息
    @Builder.Default
    private List<String> recommendations = new ArrayList<>(); // 建议


    // 新增：详细交易目标信息
    private PriceAnalysisDto priceAnalysisDto;
}

