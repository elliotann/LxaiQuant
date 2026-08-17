package com.chain.ai.trade.member.dto.communitymarket;

import lombok.Data;

/**
 * 社区市场列表查询参数
 */
@Data
public class CommunityMarketQuery {
    private int page = 1;
    private int pageSize = 20;
    private String productType;
    private String keyword;
    private String pricingType;
    private String sortBy;
    private String tag;
}
