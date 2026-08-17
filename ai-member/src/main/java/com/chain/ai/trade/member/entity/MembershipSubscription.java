package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("membership_subscription")
public class MembershipSubscription {

    @TableId
    private String id;
    private String userId;
    private String planLevel;
    private String billingCycle;
    private String status;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime nextBillingAt;
    private Boolean autoRenew;
    private Boolean cancelAtPeriodEnd;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
