package com.chain.ai.trade.engine.model.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelMetrics {
    private BigDecimal accuracy;
    private BigDecimal recall;
    private BigDecimal precision;
    private BigDecimal f1Score;
    private int totalSamples;
    private int correctPredictions;
    private Map<String, Double> featureImportance;
    private Map<String, Integer> confusionMatrix;
}
