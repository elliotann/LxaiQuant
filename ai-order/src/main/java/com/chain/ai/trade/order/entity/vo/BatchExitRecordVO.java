package com.chain.ai.trade.order.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchExitRecordVO {

    private Integer batchIndex;
    private BigDecimal triggerPrice;
    private BigDecimal closeVolume;
    private BigDecimal closeAvgPrice;
    private BigDecimal income;
    private Date closeTime;
}
