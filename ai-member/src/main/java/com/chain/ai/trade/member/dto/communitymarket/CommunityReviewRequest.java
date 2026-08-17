package com.chain.ai.trade.member.dto.communitymarket;

import lombok.Data;

/**
 * 审核请求
 */
@Data
public class CommunityReviewRequest {
    private Long id;
    private String action;
    private String note;
}
