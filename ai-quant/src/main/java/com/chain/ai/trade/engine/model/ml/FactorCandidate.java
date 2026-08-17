package com.chain.ai.trade.engine.model.ml;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("factor_candidate")
public class FactorCandidate extends BaseEntity {
    private String taskId;
    private String expression;
    private String expressionLatex;
    private Double fitness;
    private Double rankIc;
    private Double sharpe;
    private Double turnover;
    private Integer treeDepth;
    private Integer nodeCount;
    private Double corrWithLabel;
    private Double topRet;
    private Boolean selected;
    private String customFeatureName;
}
