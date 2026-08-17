package com.chain.ai.trade.engine2.core;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.extension.core.constants.ExitType;

import java.math.BigDecimal;

/**
 * 出场信号 — shouldExit() 返回的出场决策结果。
 * <p>
 * direction 标识平仓方向：CLOSE_LONG（平多）/ CLOSE_SHORT（平空）。
 * exitType 标识具体出场原因，用于防重复出场和出场日志。
 */
public class ExitSignal {

    private final SignalType direction;   // CLOSE_LONG / CLOSE_SHORT
    private final ExitType exitType;       // 出场原因
    private final BigDecimal price;        // 出场价格（可选）
    private final Integer closePercent;    // 分批出场百分比，null 表示全平

    public ExitSignal(SignalType direction, ExitType exitType) {
        this(direction, exitType, null, null);
    }

    public ExitSignal(SignalType direction, ExitType exitType, BigDecimal price) {
        this(direction, exitType, price, null);
    }

    public ExitSignal(SignalType direction, ExitType exitType, BigDecimal price, Integer closePercent) {
        this.direction = direction;
        this.exitType = exitType;
        this.price = price;
        this.closePercent = closePercent;
    }

    public SignalType getDirection() { return direction; }
    public ExitType getExitType() { return exitType; }
    public BigDecimal getPrice() { return price; }
    public Integer getClosePercent() { return closePercent; }
}
