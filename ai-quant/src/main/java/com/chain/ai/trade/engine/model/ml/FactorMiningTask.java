package com.chain.ai.trade.engine.model.ml;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("factor_mining_task")
public class FactorMiningTask extends BaseEntity {
    private String taskName;
    private String symbol;
    @TableField("`interval`")
    private String interval;
    private String operatorSet;
    private String terminalSet;
    private Integer populationSize;
    private Integer generations;
    private Integer tournamentSize;
    private Double crossoverProb;
    private Double mutationProb;
    private Double parsimonyCoefficient;
    private String fitnessMetric;
    private Integer lookbackBars;
    private String status;
    private Double progress;
    private Double bestFitness;
    private String bestExpression;
    private String bestExpressionLatex;
    private String errorMsg;
    private Date startTime;
    private Date endTime;
}
