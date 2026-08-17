package com.chain.ai.trade.engine2.core.cost;

import com.chain.ai.trade.common.entity.dto.ContractSpec;

import java.math.BigDecimal;

public class MakerTakerCostModel implements CostModel {
    private final BigDecimal makerRate;
    private final BigDecimal takerRate;
    private final boolean useMaker;

    public MakerTakerCostModel(BigDecimal makerRate, BigDecimal takerRate, boolean useMaker) {
        this.makerRate = makerRate;
        this.takerRate = takerRate;
        this.useMaker = useMaker;
    }

    @Override
    public BigDecimal calcOpenCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
        return calcFee(price, quantity, contractSpec, useMaker ? makerRate : takerRate);
    }

    @Override
    public BigDecimal calcCloseCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
        return calcFee(price, quantity, contractSpec, useMaker ? makerRate : takerRate);
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
        return "MAKER_TAKER(maker=" + makerRate + ", taker=" + takerRate + ", useMaker=" + useMaker + ")";
    }
}