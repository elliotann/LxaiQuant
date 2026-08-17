package com.chain.ai.trade.logs.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("business_log_order")
public class OrderLogEntity {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String traceId;
    
    private String eventType;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;
    
    private Long userId;
    
    private Long accountId;
    
    private String strategyId;
    
    private String orderId;
    
    private String symbol;
    
    private String orderSide;
    
    private String orderType;
    
    private BigDecimal price;
    
    private BigDecimal quantity;
    
    private BigDecimal amount;
    
    private String status;
    
    private String clientOrderId;
    
    private String remark;
    
    private String extraData;
    
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}