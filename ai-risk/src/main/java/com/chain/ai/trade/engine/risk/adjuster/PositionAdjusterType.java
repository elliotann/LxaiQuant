package com.chain.ai.trade.engine.risk.adjuster;

import lombok.Getter;
import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * 仓位调节器类型枚举
 * 定义系统中可用的仓位调节器及其Bean名称
 */
@Getter
@AllArgsConstructor
public enum PositionAdjusterType {
    
    /**
     * 基于信号质量权重的调节器 (默认)
     */
    QUALITY("quality-weight-adjuster", "基于信号质量权重的调节器"),
    
    /**
     * 基于风险的调节器
     */
    RISK("risk-based", "基于风险的调节器"),
    
    /**
     * 基于组合的调节器
     */
    PORTFOLIO("portfolio-aware", "基于组合的调节器"),
    
    /**
     * 基于市场状态的调节器
     */
    MARKET("market-state", "基于市场状态的调节器"),
    
    /**
     * 基于绩效自适应的调节器
     */
    PERFORMANCE("performance-adaptive", "基于绩效自适应的调节器"),

    /**
     * 加仓专用调节器
     */
    SCALE_IN("scale-in-adjuster", "加仓专用调节器");

    private final String beanName;
    private final String description;

    /**
     * 根据Bean名称查找枚举
     * @param beanName Bean名称
     * @return 对应的枚举，如果未找到返回Empty
     */
    public static Optional<PositionAdjusterType> fromBeanName(String beanName) {
        if (beanName == null || beanName.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.getBeanName().equals(beanName))
                .findFirst();
    }
}
