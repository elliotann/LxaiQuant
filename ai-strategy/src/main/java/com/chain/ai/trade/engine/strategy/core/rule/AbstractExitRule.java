package com.chain.ai.trade.engine.strategy.core.rule;

import com.chain.ai.trade.engine.strategy.enums.ExitRuleType;
import com.chain.ai.trade.extension.core.constants.ExitType;
import org.ta4j.core.TradingRecord;
import java.util.HashMap;
import java.util.Map;

/**
 * 抽象出场规则基类
 * 提供通用的规则实现，子类只需实现评估逻辑
 */
public abstract class AbstractExitRule implements ExitRule {

    protected final ExitRuleType ruleType;
    protected final ExitType exitType;
    protected final String description;
    protected final Map<String, Object> parameters;

    protected boolean enabled = true;
    protected int priority = 0;

    /**
     * 构造函数
     *
     * @param ruleType 规则类型
     * @param exitType 出场类型
     * @param description 规则描述
     */
    protected AbstractExitRule(ExitRuleType ruleType, ExitType exitType, String description) {
        this.ruleType = ruleType;
        this.exitType = exitType;
        this.description = description;
        this.parameters = new HashMap<>();

        // 添加基本参数
        this.parameters.put("ruleType", ruleType.name());
        this.parameters.put("exitType", exitType.name());
        this.parameters.put("enabled", enabled);
        this.parameters.put("priority", priority);
    }

    /**
     * 带参数的构造函数
     */
    protected AbstractExitRule(ExitRuleType ruleType, ExitType exitType,
                               String description, Map<String, Object> parameters) {
        this.ruleType = ruleType;
        this.exitType = exitType;
        this.description = description;
        this.parameters = new HashMap<>(parameters);

        // 确保基本参数存在
        this.parameters.putIfAbsent("ruleType", ruleType.name());
        this.parameters.putIfAbsent("exitType", exitType.name());
        this.parameters.putIfAbsent("enabled", enabled);
        this.parameters.putIfAbsent("priority", priority);
    }

    @Override
    public ExitRuleType getRuleType() {
        return ruleType;
    }

    @Override
    public ExitType getExitType() {
        return exitType;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParameters() {
        return new HashMap<>(parameters);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.parameters.put("enabled", enabled);
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
        this.parameters.put("priority", priority);
    }

    /**
     * 添加参数
     */
    protected void addParameter(String key, Object value) {
        this.parameters.put(key, value);
    }

    /**
     * 获取参数值
     */
    protected <T> T getParameter(String key, Class<T> type) {
        Object value = parameters.get(key);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }

    /**
     * 获取参数值，带默认值
     */
    protected <T> T getParameter(String key, Class<T> type, T defaultValue) {
        T value = getParameter(key, type);
        return value != null ? value : defaultValue;
    }

    /**
     * 规则评估逻辑（子类实现）
     */
    @Override
    public abstract boolean evaluate(int index, TradingRecord tradingRecord);

    /**
     * 获取规则唯一标识
     */
    @Override
    public String getRuleId() {
        return String.format("%s_%s_%s",
                ruleType.name(),
                exitType.name(),
                Integer.toHexString(hashCode()));
    }

    @Override
    public String toString() {
        return String.format("ExitRule[type=%s, exit=%s, desc=%s, enabled=%s]",
                ruleType.getDisplayName(),
                exitType.getDescription(),
                description,
                enabled);
    }
}