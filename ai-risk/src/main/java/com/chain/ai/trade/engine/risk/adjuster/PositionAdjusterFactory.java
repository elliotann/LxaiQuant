package com.chain.ai.trade.engine.risk.adjuster;

import com.chain.ai.trade.common.utils.SpringContextUtil;
import org.springframework.beans.BeansException;

import java.util.Optional;

/**
 * 仓位调节器工厂
 * 用于管理和获取PositionAdjuster实例
 * 提供静态访问方法
 */
public class PositionAdjusterFactory {

    /**
     * 根据枚举类型获取仓位调节器
     * @param type 调节器类型
     * @return 调节器实例
     */
    public static Optional<PositionAdjuster> getAdjuster(PositionAdjusterType type) {
        if (type == null) {
            return Optional.empty();
        }
        try {
            PositionAdjuster adjuster = SpringContextUtil.getBean(type.getBeanName(), PositionAdjuster.class);
            return Optional.ofNullable(adjuster);
        } catch (BeansException e) {
            return Optional.empty();
        }
    }
}
