package com.chain.ai.trade.common.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 合约规格（面值、乘数）
 * 用于非火币等标准合约的收益/保证金计算
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 合约面值（如 BTC 0.01, ETH 0.01） */
    private BigDecimal contractSize;

    /** 合约乘数，通常为 1 */
    private BigDecimal contractMult;

    public static final BigDecimal DEFAULT_CONTRACT_SIZE = new BigDecimal("0.1");
    public static final BigDecimal DEFAULT_CONTRACT_MULT = BigDecimal.ONE;

    /** 默认规格（面值 0.01，乘数 1） */
    public static ContractSpec defaultSpec() {
        return ContractSpec.builder()
                .contractSize(DEFAULT_CONTRACT_SIZE)
                .contractMult(DEFAULT_CONTRACT_MULT)
                .build();
    }
}
