package com.chain.ai.trade.engine.risk.intraday.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberRiskConfig implements Serializable {
    @Builder.Default
    private boolean enabled = false;

    @Builder.Default
    private BigDecimal warningRatio = BigDecimal.valueOf(0.02);

    @Builder.Default
    private BigDecimal stopRatio = BigDecimal.valueOf(0.04);

    @Builder.Default
    private BigDecimal profitTargetRatio = BigDecimal.valueOf(0.06);

    @Builder.Default
    private BigDecimal defaultStopLossPercent = BigDecimal.valueOf(0.01);

    @Builder.Default
    private BigDecimal slippagePercent = BigDecimal.valueOf(0.001);

    @Builder.Default
    private BigDecimal riskPerExposure = BigDecimal.valueOf(0.01);
}
