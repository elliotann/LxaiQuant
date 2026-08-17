package com.chain.ai.trade.backtest.entity.dos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 回测报告表 - 存储分析报告和用户交互
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("backtest_report")
public class BacktestReport {

    /**
     * 关联标识
     */
    @TableId
    private String taskId;

    /**
     * 报告基本信息
     */
    private String title;
    private Integer version;
    private String reportType;  // AUTO/MANUAL/TEMPLATE

    /**
     * 文字分析和总结
     */
    private String summary;     // 策略表现文字总结
    private String analysis;    // 深度分析结果JSON

    /**
     * 图表和可视化
     */
    private String metrics;     // 关键指标JSON

    /**
     * 用户交互和标记
     */
    private String tags;        // 标签JSON数组
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