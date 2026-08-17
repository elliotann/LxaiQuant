package com.chain.ai.trade.engine2.core.cost;

import com.chain.ai.trade.common.entity.dto.ContractSpec;

import java.math.BigDecimal;

public class PercentageCostModel implements CostModel {
    private final BigDecimal openRate;
    private final BigDecimal closeRate;

    public PercentageCostModel(BigDecimal rate) {
        this(rate, rate);
    }

    public PercentageCostModel(BigDecimal openRate, BigDecimal closeRate) {
        this.openRate = openRate;
        this.closeRate = closeRate;
    }

    @Override
    public BigDecimal calcOpenCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
        return calcFee(price, quantity, contractSpec, openRate);
    }

    @Override
    public BigDecimal calcCloseCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
        return calcFee(price, quantity, contractSpec, closeRate);
    }

    private BigDecimal calcFee(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec, BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return price.multiply(quantity)
                .multiply(contractSpec.getContractSize())
                .multiply(contractSpec.getContractMult())
                .multiply(rate);
    }

    @Override
    public String getDescription() {
        return "PERCENTAGE(open=" + openRate + ", close=" + closeRate + ")";
    }
}