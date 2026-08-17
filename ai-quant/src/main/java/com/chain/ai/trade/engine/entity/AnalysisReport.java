package com.chain.ai.trade.engine.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("analysis_reports")
public class AnalysisReport extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private String taskId;

    private String symbol;

    private String decision;

    private Integer confidence;

    private String summary;

    private String analysis;

    private String risks;

    private String triggerType;

    private String reportJson;
}
