package com.chain.ai.trade.backtest.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 回测报告DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestReportDTO {

    /**
     * 关联标识
     */
    private String taskId;

    /**
     * 报告基本信息
     */
    private String title;
    private Integer version;
    private String reportType;

    /**
     * 文字分析和总结
     */
    private String summary;
    private Map<String, Object> analysis;  // 深度分析结果

    /**
     * 图表和可视化
     */
    private Map<String, Object> metrics;   // 关键指标

    /**
     * 用户交互和标记
     */
    private List<String> tags;             // 标签列表
    private Boolean isFavorite;
    private Boolean isArchived;
    private Integer rating;

    /**
     * 用户笔记
     */
    private String notes;

    /**
     * 时间戳
     */
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 审计信息
     */
    private String createdBy;
    private String updatedBy;
}