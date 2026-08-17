package com.chain.ai.trade.engine2.core;

import com.chain.ai.trade.common.entity.constants.SignalType;

/**
 * 入场信号 — shouldEntry() 返回的入场决策结果。
 * <p>
 * direction 标识入场方向：LONG / SHORT。
 * signalStrength 为信号强度权重（0-3），默认 1.0，用于仓位调节器动态调整开仓大小。
 */
public class EntrySignal {

    private final SignalType direction;
    private final double signalStrength;
    private final Long signalId;

    public EntrySignal(SignalType direction) {
        this(direction, 1.0, null);
    }

    public EntrySignal(SignalType direction, double signalStrength) {
        this(direction, signalStrength, null);
    }

    public EntrySignal(SignalType direction, double signalStrength, Long signalId) {
        this.direction = direction;
        this.signalStrength = signalStrength;
        this.signalId = signalId;
    }

    public SignalType getDirection() { return direction; }
    public double getSignalStrength() { return signalStrength; }
    public Long getSignalId() { return signalId; }
}
