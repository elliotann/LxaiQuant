package com.chain.ai.trade.engine.controller.vo;

import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 用户统计响应VO
 */
@Data
@Builder
public class UserStatsResponse {
    private Long totalUsers;
    private Long activeUsers;
    private Long newUsersToday;
    private Long newUsersThisWeek;
    private Long newUsersThisMonth;
    private List<Map<String, Object>> userGrowth;
    private List<Map<String, Object>> roleDistribution;
}
