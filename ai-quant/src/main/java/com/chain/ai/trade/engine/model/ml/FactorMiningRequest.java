package com.chain.ai.trade.engine.model.ml;

import lombok.Data;
import java.util.List;

@Data
public class FactorMiningRequest {
    private String taskName;
    private String symbol;
    private String interval = "1H";
    private List<String> operatorSet;
    private List<String> terminalSet;
    private Integer populationSize;
    private Integer generations;
    private Integer tournamentSize;
    private Double crossoverProb;
    private Double mutationProb;
    private Double parsimonyCoefficient;
    private String fitnessMetric;
    private Integer lookbackBars;
}
