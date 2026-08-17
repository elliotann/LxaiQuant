package com.chain.ai.trade.engine2.core.execution;

/**
 * 订单类型
 */
public enum OrderType {

    /** 市价单：立即以当前最优价成交 */
    MARKET,

    /** 限价单：挂单等待指定价格成交 */
    LIMIT
}
