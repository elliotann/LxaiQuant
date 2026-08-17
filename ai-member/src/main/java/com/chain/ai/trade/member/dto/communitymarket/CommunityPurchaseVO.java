package com.chain.ai.trade.member.dto.communitymarket;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 社区市场已购商品VO
 */
@Data
public class CommunityPurchaseVO {
    private Long id;
    private Long listingId;
    private String productType;
    private String listingName;
    private String listingDescription;
    private String previewImage;
    private String authorId;
    private String authorNickname;
    private String sourceId;
    private String configSnapshot;
    private LocalDateTime purchaseTime;
    private BigDecimal creditsSpent;
    private LocalDateTime lastSyncTime;
    private Boolean hasUpdate;
}
