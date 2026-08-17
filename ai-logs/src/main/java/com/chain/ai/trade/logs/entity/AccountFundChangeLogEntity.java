package com.chain.ai.trade.logs.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("business_log_account_fund")
public class AccountFundChangeLogEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String traceId;
    
    private String eventType;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;
    
    private Long userId;
    
    private Long accountId;
    
    private String strategyId;
    
    private String currency;
    
    private String changeType;
    
    private BigDecimal amount;
    
    private BigDecimal balanceBefore;
    
    private BigDecimal balanceAfter;
    
    private BigDecimal availableBefore;
    
    private BigDecimal availableAfter;
    
    private BigDecimal frozenBefore;
    
    private BigDecimal frozenAfter;
    
    private String relatedOrderId;
    
    private String relatedTradeId;
    
    private String remark;
    
    private String extraData;
    
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}