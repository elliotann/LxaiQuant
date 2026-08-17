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
 * 社区市场商品
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("community_market_listings")
public class CommunityMarketListing {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String productType;
    private String sourceId;
    private String name;
    private String description;
    private String previewImage;
    private String configSnapshot;
    private String pricingType;
    private BigDecimal price;
    private Boolean vipFree;
    private String authorId;
    private String status;
    private String reviewNote;
    private String reviewerId;
    private LocalDateTime reviewedAt;
    private Integer viewCount;
    private Integer purchaseCount;
    private BigDecimal avgRating;
    private Integer ratingCount;
    private Boolean hasUpdate;
    private String tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
