package com.chain.ai.trade.engine.signal.rule;

import lombok.Data;

/**
 * 通用指标值类型容器，支持 NUMERIC / STRING / BOOLEAN 三种类型
 * 对应 v5.2 设计文档 §4.1.5
 */
@Data
public class IndicatorValue {

    public enum Type { NUMERIC, STRING, BOOLEAN }

    private Type type;
    private Double numericValue;
    private String stringValue;
    private Boolean booleanValue;

    public IndicatorValue() {
    }

    public IndicatorValue(Type type, Double numericValue, String stringValue, Boolean booleanValue) {
        this.type = type;
        this.numericValue = numericValue;
        this.stringValue = stringValue;
        this.booleanValue = booleanValue;
    }

    public static IndicatorValue of(Double val) {
        return new IndicatorValue(Type.NUMERIC, val, null, null);
    }

    public static IndicatorValue of(String val) {
        return new IndicatorValue(Type.STRING, null, val, null);
    }

    public static IndicatorValue of(Boolean val) {
        return new IndicatorValue(Type.BOOLEAN, null, null, val);
    }

    /** 便捷获取数值，非 NUMERIC 类型返回 null */
    public Double getNumericValue() {
        return type == Type.NUMERIC ? numericValue : null;
    }

    /** 便捷获取字符串，非 STRING 类型返回 null */
    public String getStringValue() {
        return type == Type.STRING ? stringValue : null;
    }

    /** 便捷获取布尔值，非 BOOLEAN 类型返回 null */
    public Boolean getBooleanValue() {
        return type == Type.BOOLEAN ? booleanValue : null;
    }
}
