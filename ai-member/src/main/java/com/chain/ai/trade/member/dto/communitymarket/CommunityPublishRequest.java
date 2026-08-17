package com.chain.ai.trade.member.dto.communitymarket;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发布/编辑商品请求
 */
@Data
public class CommunityPublishRequest {
    private String productType;
    private String sourceId;
    private String name;
    private String description;
    private String previewImage;
    private String pricingType;
    private BigDecimal price;
    private Boolean vipFree;
    private List<String> tags;
}
