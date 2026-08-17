package com.chain.ai.trade.engine2.core.cost;

import com.chain.ai.trade.common.entity.dto.ContractSpec;

import java.math.BigDecimal;

public class PerShareCostModel implements CostModel {
    private final BigDecimal perShareRate;
    private final BigDecimal minFee;
    private final BigDecimal maxFee;

    public PerShareCostModel(BigDecimal perShareRate) {
        this(perShareRate, null, null);
    }

    public PerShareCostModel(BigDecimal perShareRate, BigDecimal minFee, BigDecimal maxFee) {
        this.perShareRate = perShareRate;
        this.minFee = minFee;
        this.maxFee = maxFee;
    }

    @Override
    public BigDecimal calcOpenCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
        return calcFee(quantity);
    }

    @Override
    public BigDecimal calcCloseCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
        return calcFee(quantity);
    }

    private BigDecimal calcFee(BigDecimal quantity) {
        BigDecimal fee = perShareRate.multiply(quantity);
        if (minFee != null) fee = fee.max(minFee);
        if (maxFee != null) fee = fee.min(maxFee);
        return fee;
    }

    @Override
    public String getDescription() {
        return "PER_SHARE(rate=" + perShareRate + ", min=" + minFee + ", max=" + maxFee + ")";
    }
}