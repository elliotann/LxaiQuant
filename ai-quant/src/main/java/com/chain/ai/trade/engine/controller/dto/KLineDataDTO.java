package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * K线数据DTO
 */
@Data
public class KLineDataDTO {
    
    private Long time;              // 时间戳（秒）
    private BigDecimal open;        // 开盘价
    private BigDecimal high;        // 最高价
    private BigDecimal low;         // 最低价
    private BigDecimal close;       // 收盘价
    private BigDecimal volume;      // 成交量
    private BigDecimal quoteVolume; // 成交额（可选）
    private Integer tradeCount;     // 成交笔数（可选）
}

