package com.chain.ai.trade.engine.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Date;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("ml_training_jobs")
public class MlTrainingJob extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private String symbol;

    private String modelType;

    private String status;

    private BigDecimal accuracy;

    private String errorMsg;

    private Date startTime;

    private Date endTime;
}
