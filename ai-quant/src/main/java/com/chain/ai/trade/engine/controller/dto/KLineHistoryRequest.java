package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * K线历史数据查询请求
 */
@Data
public class KLineHistoryRequest {
    
    @NotBlank(message = "交易对不能为空")
    private String symbol;
    
    @NotBlank(message = "时间周期不能为空")
    private String interval;
    
    private String exchange;
    
    private Long startTime;
    
    private Long endTime;
    
    @Min(value = 1, message = "数据条数最少1条")
    @Max(value = 2000, message = "数据条数最多2000条")
    private Integer limit = 500;
}

