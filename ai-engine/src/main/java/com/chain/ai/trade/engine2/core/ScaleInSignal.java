package com.chain.ai.trade.engine2.core;

import com.chain.ai.trade.common.entity.constants.SignalType;

import java.math.BigDecimal;

/**
 * 加仓信号 — shouldScaleIn() 返回的加仓决策结果。
 * <p>
 * direction 标识加仓方向：LONG（加多）/ SHORT（加空）。
 * signalStrength 为信号强度权重，默认 1.0，用于仓位调节器动态调整加仓大小。
 * takeProfitPrice / stopLossPrice 可空，用于逐笔止盈止损。
 * price 可空，指定加仓价，null 表示使用当前 K 线开盘价。
 */
public class ScaleInSignal {

    private final SignalType direction;
    private final ScaleInReason reason;
    private final double signalStrength;
    private final BigDecimal takeProfitPrice;
    private final BigDecimal stopLossPrice;
    private final BigDecimal price;
    private final Long signalId;

    public ScaleInSignal(SignalType direction, ScaleInReason reason) {
        this(direction, reason, 1.0, null, null, null, null);
    }

    public ScaleInSignal(SignalType direction, ScaleInReason reason,
                         BigDecimal takeProfitPrice, BigDecimal stopLossPrice) {
        this(direction, reason, 1.0, takeProfitPrice, stopLossPrice, null, null);
    }

    public ScaleInSignal(SignalType direction, ScaleInReason reason,
                         BigDecimal takeProfitPrice, BigDecimal stopLossPrice, BigDecimal price) {
        this(direction, reason, 1.0, takeProfitPrice, stopLossPrice, price, null);
    }

    public ScaleInSignal(SignalType direction, ScaleInReason reason, double signalStrength,
                         BigDecimal takeProfitPrice, BigDecimal stopLossPrice, BigDecimal price) {
        this(direction, reason, signalStrength, takeProfitPrice, stopLossPrice, price, null);
    }

    public ScaleInSignal(SignalType direction, ScaleInReason reason, double signalStrength,
                         BigDecimal takeProfitPrice, BigDecimal stopLossPrice, BigDecimal price,
                         Long signalId) {
        this.direction = direction;
        this.reason = reason;
        this.signalStrength = signalStrength;
        this.takeProfitPrice = takeProfitPrice;
        this.stopLossPrice = stopLossPrice;
        this.price = price;
        this.signalId = signalId;
    }

    public SignalType getDirection() { return direction; }
    public ScaleInReason getReason() { return reason; }
    public double getSignalStrength() { return signalStrength; }
    public BigDecimal getTakeProfitPrice() { return takeProfitPrice; }
    public BigDecimal getStopLossPrice() { return stopLossPrice; }
    public BigDecimal getPrice() { return price; }
    public Long getSignalId() { return signalId; }
}
