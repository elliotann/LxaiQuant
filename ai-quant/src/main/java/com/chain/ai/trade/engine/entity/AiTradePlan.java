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
@TableName("ai_trade_plan")
public class AiTradePlan extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private String planUuid;

    private String previewId;

    private String previewType;

    private String name;

    private String description;

    private String status;

    private String planContent;

    private String trace;

    private String executionResult;
}

