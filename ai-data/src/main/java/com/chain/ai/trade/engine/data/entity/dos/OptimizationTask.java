package com.chain.ai.trade.engine.data.entity.dos;

import lombok.Data;
import java.util.Date;

@Data
public class OptimizationTask {
    private Long id;
    private String taskId;
    private String strategyId;
    private String coinId;
    private Long startTime;
    private Long endTime;
    private String paramRanges;
    private String objective;
    private String config;
    private String engineVersion;
    private String strategyVersion;
    private String numType;
    private String executionModel;
    private String feeModel;
    private String status;
    private Integer progress;
    private Integer totalCombinations;
    private Date createdAt;
    private Date updatedAt;
}
