package com.chain.ai.trade.engine.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标的信息 DTO，含 symbol code 和 name
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymbolInfoDTO {
    private String symbol;
    private String name;
    private String exchange;
}
