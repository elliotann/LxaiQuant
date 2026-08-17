package com.chain.ai.trade.backtest.entity.dos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 回测任务表 - 存储任务配置和状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("backtest_task")
public class BacktestTask {

    /**
     * 核心标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    /**
     * 策略信息
     */
    private String strategyName;
    private String strategyCode;
    private String strategyVersion;

    /**
     * 时间范围
     */
    private LocalDate startDate;
    private LocalDate endDate;

    /**
     * 资金配置
     */
    private BigDecimal initialCapital;
    private String currency;

    /**
     * 基准和股票池
     */
    private String benchmark;
    private String universe;  // JSON字符串存储股票池数组

    /**
     * 参数配置（JSON格式）
     */
    private String config;  // JSON字符串

    /**
     * 交易相关字段（回测落库到订单系统使用）
     */
    private String robotId;
    private String memberId;
    private Long accountId;
    private Integer leverage;

    /**
     * 状态管理
     */
    private String status;
    private Integer progress;
    private String errorMessage;

    /**
     * 执行时间追踪
     */
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer durationSeconds;

    /**
     * 分区和优化
     */
    private LocalDate partitionKey;
}
