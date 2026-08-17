package com.chain.ai.trade.common.entity.constants;

/**
 * 订单操作（业务层面）
 */
public enum OrderAction {

    // 开仓操作
    OPEN_LONG("开多"),
    OPEN_SHORT("开空"),

    // 平仓操作
    CLOSE_LONG("平多"),
    CLOSE_SHORT("平空"),

    // 仓位调整
    LBAP("加多仓"),//加多仓
    LBSP("减多仓"),//减多仓
    SBAP("加空仓"),//加空仓
    SBSP("减空仓"),//减空仓

    // 止盈止损
    LONG_GAIN("多止盈"),
    LONG_LOSS("多止损"),
    SHORT_GAIN("空止盈"),
    SHORT_LOSS("空止损"),

    // 其他操作
    ADJUST_LEVERAGE("调整杠杆"),
    CANCEL_ORDER("取消订单");

    private final String description;

    OrderAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    // 兼容旧版本的 getLabel 方法
    @Deprecated
    public String getLabel() {
        return description;
    }

    /**
     * 获取枚举的字符串值
     */
    public String getValue() {
        return this.name();
    }

    /**
     * 根据字符串获取枚举值
     */
    public static OrderAction fromValue(String value) {
        for (OrderAction action : OrderAction.values()) {
            if (action.name().equalsIgnoreCase(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown OrderAction value: " + value);
    }
}
