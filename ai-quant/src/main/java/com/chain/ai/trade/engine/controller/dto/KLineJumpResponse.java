package com.chain.ai.trade.engine.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * K线时间跳转响应DTO
 */
@Data
@Builder
public class KLineJumpResponse {
    
    private String symbol;
    
    private String interval;
    
    private Long targetTime;
    
    private List<KLineDataDTO> klines;
    
    private Long currentTime;
    
    /**
     * 跳转时间点之前是否还有更多数据
     */
    private Boolean hasMoreBefore;
    
    /**
     * 跳转时间点之后是否还有更多数据
     */
    private Boolean hasMoreAfter;
}

