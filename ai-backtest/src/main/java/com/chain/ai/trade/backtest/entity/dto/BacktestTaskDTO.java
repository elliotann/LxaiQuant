package com.chain.ai.trade.backtest.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 回测任务DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestTaskDTO {

    private Long id;
    private String taskId;

    // 策略信息
    private String strategyName;
    private String strategyCode;
    private String strategyVersion;

    // 时间范围
    private LocalDate startDate;
    private LocalDate endDate;

    // 资金配置
    private BigDecimal initialCapital;
    private String currency;

    // 基准和股票池
    private String benchmark;
    private String[] universe;  // 应用层使用数组，在服务层处理JSON转换

    // 参数配置
    private Map<String, Object> config;

    // 交易相关字段（回测落库到订单系统使用）
    private String robotId;
    private String memberId;
    private Long accountId;
    private Integer leverage;

    // 状态管理
    private String status;
    private Integer progress;
    private String errorMessage;

    // 执行时间追踪
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer durationSeconds;

    // 分区和优化
    private LocalDate partitionKey;

}
