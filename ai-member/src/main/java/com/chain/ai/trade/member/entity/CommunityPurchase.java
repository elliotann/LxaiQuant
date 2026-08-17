package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 社区市场购买记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("community_purchases")
public class CommunityPurchase {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long listingId;
    private String userId;
    private LocalDateTime purchaseTime;
    private BigDecimal creditsSpent;
    private LocalDateTime lastSyncTime;
    private LocalDateTime createdAt;
}
