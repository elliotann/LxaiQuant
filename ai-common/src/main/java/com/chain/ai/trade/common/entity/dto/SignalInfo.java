package com.chain.ai.trade.common.entity.dto;

/**
 * 信号信息类，包含信号类型、权重和技术信号ID
 */
public class SignalInfo {
    private final Long id;
    private final String signalType;
    private final double weight;
    private final String extraParams;

    public SignalInfo(Long id, String signalType, double weight, String extraParams) {
        this.id = id;
        this.signalType = signalType;
        this.weight = weight;
        this.extraParams = extraParams;
    }

    public Long getId() {
        return id;
    }

    public String getSignalType() {
        return signalType;
    }

    public double getWeight() {
        return weight;
    }

    public String getExtraParams() {
        return extraParams;
    }

    @Override
    public String toString() {
        return "SignalInfo{id=" + id + ", signalType='" + signalType + "', weight=" + weight + ", extraParams='" + extraParams + "'}";
    }
}
