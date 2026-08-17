package com.chain.ai.trade.engine.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * K线信号标注查询请求DTO
 */
@Data
public class KLineSignalRequest {
    
    /**
     * 交易对
     */
    @NotBlank(message = "交易对不能为空")
    private String symbol;
    
    /**
     * 时间周期（如：3m, 5m, 1h等）
     */
    @NotBlank(message = "时间周期不能为空")
    private String interval;
    
    private String exchange;
    
    /**
     * 开始时间（UTC时间戳，秒）
     */
    @NotNull(message = "开始时间不能为空")
    private Long from;
    
    /**
     * 结束时间（UTC时间戳，秒）
     */
    @NotNull(message = "结束时间不能为空")
    private Long to;
    
    /**
     * 机器人ID（可选，用于关联交易信号）
     */
    private String robotId;
    
    /**
     * 信号类型/策略标识（可选，如 MACD、RANGE_FILTER，对应 technical_signal.indicator）
     */
    private String indicator;
    
    /**
     * 会员ID（可选）
     */
    private String memberId;
    
    /**
     * 账户ID（可选）
     */
    private String accountId;
    
    /**
     * 信号类型过滤（可选，如：LB, SB, LS, SS等）
     */
    private String signalType;
    
    /**
     * 最大返回数量（可选，默认100）
     */
    private Integer limit;
}
