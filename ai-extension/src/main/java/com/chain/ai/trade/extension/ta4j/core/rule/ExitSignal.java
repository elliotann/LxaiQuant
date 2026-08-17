package com.chain.ai.trade.extension.ta4j.core.rule;

import com.chain.ai.trade.extension.core.constants.ExitType;
import org.ta4j.core.Trade;

public class ExitSignal {
    private final Trade.TradeType direction; // 平仓方向
    private final ExitType exitType;          // 具体原因
    private final Double price;               // 出场价格 (可选)
    private final String orderItemSn;
    private final Integer closePercent;       // 分批出场百分比（主动止盈用，0~100）

    public ExitSignal(Trade.TradeType direction, ExitType exitType) {
        this(direction, exitType, (Double) null, null, null);
    }

    public ExitSignal(Trade.TradeType direction, ExitType exitType, Double price) {
        this(direction, exitType, price, null, null);
    }

    public ExitSignal(Trade.TradeType direction, ExitType exitType, String orderItemSn) {
        this(direction, exitType, (Double) null, orderItemSn, null);
    }

    public ExitSignal(Trade.TradeType direction, ExitType exitType, Double price, String orderItemSn) {
        this(direction, exitType, price, orderItemSn, null);
    }

    public ExitSignal(Trade.TradeType direction, ExitType exitType, Double price, Integer closePercent) {
        this(direction, exitType, price, null, closePercent);
    }

    public ExitSignal(Trade.TradeType direction, ExitType exitType, Double price, String orderItemSn, Integer closePercent) {
        this.direction = direction;
        this.exitType = exitType;
        this.price = price;
        this.orderItemSn = orderItemSn;
        this.closePercent = closePercent;
    }

    public Trade.TradeType getDirection() { return direction; }
    public ExitType getExitType() { return exitType; }
    public Double getPrice() { return price; }
    public String getOrderItemSn() { return orderItemSn; }
    public Integer getClosePercent() { return closePercent; }
}
