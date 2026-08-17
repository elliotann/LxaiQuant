package com.chain.ai.trade.engine2.core.cost;

import com.chain.ai.trade.common.entity.dto.ContractSpec;

import java.math.BigDecimal;

public class ZeroCostModel implements CostModel {
    @Override
    public BigDecimal calcOpenCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal calcCloseCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
        return BigDecimal.ZERO;
    }

    @Override
    public String getDescription() {
        return "ZERO";
    }
}