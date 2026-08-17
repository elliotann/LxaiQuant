package com.chain.ai.trade.common.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 收益结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitDto {

    /** 总收益（含已实现 + 持仓浮盈） */
    private BigDecimal totalProfit;

    /** 持仓浮盈 */
    private BigDecimal positionProfit;
}
