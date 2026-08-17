package com.chain.ai.trade.member.dto.communitymarket;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社区市场表现数据VO（JSON字段，按商品类型不同）
 */
@Data
public class CommunityPerformanceVO {
    private Integer usageCount;
    private String performanceData;
    private LocalDateTime updatedAt;
}
