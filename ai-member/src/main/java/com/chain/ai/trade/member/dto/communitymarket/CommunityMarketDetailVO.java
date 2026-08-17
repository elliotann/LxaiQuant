package com.chain.ai.trade.member.dto.communitymarket;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 社区市场商品详情VO
 */
@Data
public class CommunityMarketDetailVO {
    private Long id;
    private String productType;
    private String name;
    private String description;
    private String previewImage;
    private String sourceId;
    private String configSnapshot;
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

    // 表现数据
    private CommunityPerformanceVO performance;

    // 评论列表
    private List<CommunityCommentVO> comments;
}
