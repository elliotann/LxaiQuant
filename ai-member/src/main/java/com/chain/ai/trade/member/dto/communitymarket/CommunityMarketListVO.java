package com.chain.ai.trade.member.dto.communitymarket;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 社区市场列表项VO
 */
@Data
public class CommunityMarketListVO {
    private Long id;
    private String productType;
    private String name;
    private String description;
    private String sourceId;
    private String previewImage;
    private String pricingType;
    private BigDecimal price;
    private Boolean vipFree;
    private String authorId;
    private String authorNickname;
    private String authorAvatar;
    private BigDecimal avgRating;
    private Integer ratingCount;
    private Integer purchaseCount;
    private Integer viewCount;
    private Boolean isOwn;
    private Boolean isPurchased;
    private Boolean hasUpdate;
    private String status;
    private String tags;
    private LocalDateTime createdAt;
}
