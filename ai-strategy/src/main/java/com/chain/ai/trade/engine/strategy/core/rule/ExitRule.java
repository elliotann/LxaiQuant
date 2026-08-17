package com.chain.ai.trade.engine.strategy.core.rule;

import com.chain.ai.trade.engine.strategy.enums.ExitRuleType;
import com.chain.ai.trade.extension.core.constants.ExitType;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import java.util.Map;

/**
 * 出场规则接口（基于ta4j Rule）
 * 所有出场规则都实现此接口，无论是技术指标、信号还是业务规则
 *
 * 设计原则：
 * 1. 统一基于ta4j Rule接口，便于集成到ta4j策略中
 * 2. 同时包含规则类型（ExitRuleType）和出场类型（ExitType）
 * 3. 支持参数配置和规则描述
 */
public interface ExitRule extends Rule {

    /**
     * 获取规则类型（技术分类）
     * 用于规则管理、配置和分组
     */
    ExitRuleType getRuleType();

    /**
     * 获取出场类型（业务分类）
     * 用于防重检查、日志记录和业务操作
     */
    ExitType getExitType();

    /**
     * 获取规则描述
     */
    String getDescription();

    /**
     * 获取规则参数
     */
    Map<String, Object> getParameters();

    /**
     * 是否启用该规则
     */
    boolean isEnabled();

    /**
     * 设置启用状态
     */
    void setEnabled(boolean enabled);

    /**
     * 获取规则优先级（数值越小优先级越高）
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 设置规则优先级
     */
    default void setPriority(int priority) {
        // 默认实现：忽略优先级设置
    }

    /**
     * 判断规则是否满足条件
     * 重写Rule接口的方法，提供默认实现
     */
    @Override
    default boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (!isEnabled()) {
            return false;
        }
        return evaluate(index, tradingRecord);
    }

    /**
     * 规则评估逻辑（子类实现）
     */
    boolean evaluate(int index, TradingRecord tradingRecord);

    /**
     * 获取规则唯一标识
     */
    default String getRuleId() {
        return getRuleType().name() + "_" + getExitType().name();
    }
}