package com.chain.ai.trade.engine2.core.cost;

import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.engine2.backtest.model.MemoryPosition;

import java.math.BigDecimal;

/**
 * 成本模型 — 计算交易过程中的所有成本。
 * <p>
 * 借鉴 TA4J CostModel 的设计思想，适配自有内存模型。
 * <p>
 * 成本包括但不限于：
 * <ul>
 *   <li>手续费（开仓/平仓）</li>
 *   <li>滑点成本</li>
 *   <li>资金费率（持仓费）</li>
 *   <li>印花税、过户费（A股）</li>
 * </ul>
 */
public interface CostModel {

    /**
     * 计算开仓成本
     */
    BigDecimal calcOpenCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec);

    /**
     * 计算平仓成本
     */
    BigDecimal calcCloseCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec);

    /**
     * 计算持仓成本（资金费率等）
     */
    default BigDecimal calcHoldingCost(MemoryPosition position, BigDecimal currentPrice) {
        return BigDecimal.ZERO;
    }

    /**
     * 获取成本描述
     */
    String getDescription();
}