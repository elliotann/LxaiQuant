package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * K线数据加载请求（按方向）
 */
@Data
public class KLineLoadRequest {
    
    @NotBlank(message = "交易对不能为空")
    private String symbol;
    
    @NotBlank(message = "时间周期不能为空")
    private String interval;
    
    @NotBlank(message = "方向不能为空")
    @Pattern(regexp = "forward|backward", message = "方向必须是forward或backward")
    private String direction;
    
    private String exchange;
    
    private Long anchorTime;
    
    private Integer limit = 100;
    
    private String requestId;
    
    // 可选边界条件
    private Long minTime;
    private Long maxTime;
}

