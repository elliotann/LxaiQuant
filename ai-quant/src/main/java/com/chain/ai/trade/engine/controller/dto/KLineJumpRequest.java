package com.chain.ai.trade.engine.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * K线时间跳转请求DTO
 */
@Data
public class KLineJumpRequest {
    
    @NotBlank(message = "交易对不能为空")
    private String symbol;
    
    @NotBlank(message = "时间周期不能为空")
    private String interval;
    
    private String exchange;
    
    @NotNull(message = "跳转时间不能为空")
    private Long time;
    
    /**
     * 跳转时间点之前的数据条数（默认100）
     */
    private Integer before = 100;
    
    /**
     * 跳转时间点之后的数据条数（默认100）
     */
    private Integer after = 100;
    
    /**
     * 总数据条数限制（默认200）
     */
    private Integer limit = 200;
}

