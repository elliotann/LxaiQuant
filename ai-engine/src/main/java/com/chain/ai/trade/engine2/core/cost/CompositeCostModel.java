package com.chain.ai.trade.engine2.core.cost;

import com.chain.ai.trade.common.entity.dto.ContractSpec;

import java.math.BigDecimal;
import java.util.List;

/**
 * 复合成本模型 — 支持 A 股等多费率叠加场景
 * <p>
 * 典型 A 股卖出成本：佣金 0.03% + 印花税 0.1% + 过户费 1元/笔
 */
public class CompositeCostModel implements CostModel {
    private final List<CostComponent> openComponents;
    private final List<CostComponent> closeComponents;

    public CompositeCostModel(List<CostComponent> openComponents, List<CostComponent> closeComponents) {
        this.openComponents = openComponents;
        this.closeComponents = closeComponents;
    }

    @Override
    public BigDecimal calcOpenCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
        return calcTotal(openComponents, price, quantity, contractSpec);
    }

    @Override
    public BigDecimal calcCloseCost(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
        return calcTotal(closeComponents, price, quantity, contractSpec);
    }

    private BigDecimal calcTotal(List<CostComponent> components, BigDecimal price,
                                 BigDecimal quantity, ContractSpec contractSpec) {
        BigDecimal total = BigDecimal.ZERO;
        for (CostComponent comp : components) {
            total = total.add(comp.calculate(price, quantity, contractSpec));
        }
        return total;
    }

    @Override
    public String getDescription() {
        return "COMPOSITE(open=" + openComponents.size() + " comps, close=" + closeComponents.size() + " comps)";
    }

    public interface CostComponent {
        BigDecimal calculate(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec);
    }

    public static class PercentageComponent implements CostComponent {
        private final BigDecimal rate;
        public PercentageComponent(BigDecimal rate) { this.rate = rate; }
        @Override
        public BigDecimal calculate(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
            return price.multiply(quantity).multiply(contractSpec.getContractSize())
                    .multiply(contractSpec.getContractMult()).multiply(rate);
        }
    }

    public static class FixedComponent implements CostComponent {
        private final BigDecimal fixedAmount;
        public FixedComponent(BigDecimal fixedAmount) { this.fixedAmount = fixedAmount; }
        @Override
        public BigDecimal calculate(BigDecimal price, BigDecimal quantity, ContractSpec contractSpec) {
            return fixedAmount;
        }
    }
}