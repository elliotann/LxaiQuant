package com.chain.ai.trade.engine.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 从交易所导入K线响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KLineImportFromExchangeResponse {

    private boolean success;
    private String message;
    /** 成功导入的K线条数 */
    private int importedCount;
}
