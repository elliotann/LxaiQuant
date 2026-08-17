package com.chain.ai.trade.engine.entity;

import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("ml_models")
public class MlModel extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private String symbol;

    private String modelType;

    private Integer version;

    private String filePath;

    private Long fileSize;

    private String md5Checksum;

    private BigDecimal accuracy;

    private BigDecimal recall;

    @TableField("`precision`")
    private BigDecimal precision;

    private BigDecimal f1Score;

    private String featureImportance;

    private String confusionMatrix;

    /**
     * 近30天每日准确率趋势 JSON
     */
    private String accuracyTrend;

    private String hyperparams;

    private String trainingDataRange;

    private Long trainingDurationMs;

    private Boolean isActive;

    private Date trainedAt;

    private String trainedBy;
}
