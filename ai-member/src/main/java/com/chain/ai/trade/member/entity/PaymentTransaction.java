package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("payment_transaction")
public class PaymentTransaction {

    @TableId
    private String id;
    private String userId;
    private String type;
    private Integer planId;
    private BigDecimal amountUsdt;
    private String paymentCurrency;
    private String paymentAddress;
    private String memo;
    private String txId;
    private String status;
    private LocalDateTime expireAt;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
