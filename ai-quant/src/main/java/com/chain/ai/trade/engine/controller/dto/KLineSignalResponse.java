package com.chain.ai.trade.engine.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * K线信号标注响应DTO
 */
@Data
@Builder
public class KLineSignalResponse {
    
    private String symbol;
    
    private String interval;
    
    private Long from;
    
    private Long to;
    
    /**
     * 信号标注列表
     */
    private List<KLineSignalDTO> signals;
    
    /**
     * 信号总数
     */
    private Integer total;
}
