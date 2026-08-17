package com.chain.ai.trade.engine.signal.rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorMetadata {

    private String id;
    private String name;
    private String category;
    private String description;
    private String valueType;
    private ValueRange valueRange;
    private boolean hasParams;
    private List<ParamDef> params;
    private List<String> operators;
    private List<String> enumValues;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValueRange {
        private Double min;
        private Double max;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParamDef {
        private String key;
        private String label;
        private String type;
        private String defaultValue;
        private Double min;
        private Double max;
        private List<String> options;
    }
}
