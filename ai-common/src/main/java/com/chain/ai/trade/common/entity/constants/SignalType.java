package com.chain.ai.trade.common.entity.constants;

/**
 * 策略信号类型
 */
public enum SignalType {
    LONG("做多"),
    SHORT("做空"),
    CLOSE_LONG("平多"),
    CLOSE_SHORT("平空"),
    CALLBACK_LONG("回调做多"),
    CALLBACK_SHORT("反弹做空"),
    HOLD("持有");

    private final String description;

    SignalType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
