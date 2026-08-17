package com.chain.ai.trade.member.dto.communitymarket;

import lombok.Data;

/**
 * 评论请求
 */
@Data
public class CommunityCommentRequest {
    private Integer rating;
    private String content;
}
