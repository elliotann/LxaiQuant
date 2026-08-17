package com.chain.ai.trade.member.dto.communitymarket;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社区市场评论VO
 */
@Data
public class CommunityCommentVO {
    private Long id;
    private String userId;
    private String userNickname;
    private String userAvatar;
    private Integer rating;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
