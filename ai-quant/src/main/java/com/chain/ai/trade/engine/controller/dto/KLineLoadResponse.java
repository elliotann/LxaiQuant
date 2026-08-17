package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * K线数据加载响应
 */
@Data
public class KLineLoadResponse {
    
    private String symbol;
    private String interval;
    private String direction;
    private List<KLineDataDTO> data;
    private Boolean hasMore;
    private Long nextAnchorTime;
}

