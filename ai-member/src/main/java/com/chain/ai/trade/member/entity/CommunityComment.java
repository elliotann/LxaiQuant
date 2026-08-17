package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 社区市场评论
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("community_comments")
public class CommunityComment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long listingId;
    private String userId;
    private Integer rating;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
