package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * K线历史数据响应
 */
@Data
public class KLineHistoryResponse {
    
    private String symbol;
    private String interval;
    private List<KLineDataDTO> klines;
    private Long currentTime;
}

