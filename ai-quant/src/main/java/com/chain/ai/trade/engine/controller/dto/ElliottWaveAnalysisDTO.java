package com.chain.ai.trade.engine.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class ElliottWaveAnalysisDTO implements Serializable {
    private String symbol;
    private String interval;
    private Integer barCount;
    private Integer endIndex;

    private String degree;
    private String currentPhase;
    private Boolean invalidation;
    private Double confluenceScore;
    private Integer waveCount;
    private Integer filteredWaveCount;

    private ChannelDTO channel;
    private ScenarioDTO baseScenario;
    private List<ScenarioDTO> alternativeScenarios;
    private List<PeriodAnalysisDTO> periodAnalyses;

    @Data
    @Builder
    public static class ChannelDTO implements Serializable {
        private Boolean valid;
        private String upper;
        private String lower;
        private String median;
    }

    @Data
    @Builder
    public static class ScenarioDTO implements Serializable {
        private String id;
        private String type;
        private String currentPhase;
        private String direction;
        private Double confidencePercent;
        private String invalidationPrice;
        private String primaryTarget;
    }

    @Data
    @Builder
    public static class PeriodAnalysisDTO implements Serializable {
        private String period;
        private String degree;
        private Integer barCount;
        private String currentPhase;
        private Boolean invalidation;
        private Double confluenceScore;
        private Integer waveCount;
        private ScenarioDTO baseScenario;
    }
}
