package com.chain.ai.trade.order.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 权益曲线点（前端图表展示用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquityCurvePoint {

    /** 日期（yyyy-MM-dd 或 yyyy-MM，取决于 granularity） */
    private String date;

    /** 净值 */
    private BigDecimal equity;

    /** 回撤（正数表示回撤幅度，百分比值如 5.2 表示 5.2%） */
    private BigDecimal drawdown;

    /** 实际取值日期（月粒度时标识该月最后一条数据的实际日期） */
    private String actualDate;
}
