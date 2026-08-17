package com.chain.ai.trade.engine.data.entity.dos;

import lombok.Data;
import java.util.Date;

@Data
public class OptimizationResult {
    private Long id;
    private String taskId;
    private String paramValues;
    private Double totalReturn;
    private Double maxDrawdown;
    private Double winRate;
    private Double sharpeRatio;
    private Double score;
    private Date createdAt;
}
