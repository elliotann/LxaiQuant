package com.chain.ai.trade.logs.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("business_log_risk_control")
public class RiskControlLogEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String traceId;
    
    private String eventType;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;
    
    private Long userId;
    
    private Long accountId;
    
    private String strategyId;
    
    private String riskType;
    
    private String riskLevel;
    
    private String triggerValue;
    
    private String thresholdValue;
    
    private String actionTaken;
    
    private String relatedOrderId;
    
    private String relatedSymbol;
    
    private String description;
    
    private String remark;
    
    private String extraData;
    
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}