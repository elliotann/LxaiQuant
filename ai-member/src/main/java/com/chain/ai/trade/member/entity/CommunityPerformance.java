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
 * 社区市场表现数据（JSON字段支持多类型差异化指标）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("community_performance")
public class CommunityPerformance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long listingId;
    private Integer usageCount;
    private String performanceData;
    private LocalDateTime updatedAt;
}
