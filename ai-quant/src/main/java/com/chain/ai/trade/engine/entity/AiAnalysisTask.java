package com.chain.ai.trade.engine.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_analysis_tasks")
public class AiAnalysisTask extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private String userId;

    private String symbols;

    private Integer intervalMin;

    private String notifyChannels;

    private Boolean enabled;

    private Date lastRunAt;

    private Date nextRunAt;

    private Integer xxlJobId;

    private String xxlJobGroupId;
}
