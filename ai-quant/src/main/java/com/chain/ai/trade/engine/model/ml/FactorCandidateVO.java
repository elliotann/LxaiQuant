package com.chain.ai.trade.engine.model.ml;

import lombok.Data;
import java.util.Date;

@Data
public class FactorCandidateVO {
    private String id;
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
    private Date createTime;
}
