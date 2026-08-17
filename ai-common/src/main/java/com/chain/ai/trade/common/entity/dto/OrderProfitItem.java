package com.chain.ai.trade.common.entity.dto;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 用于汇总多笔订单收益的单项数据
 * 由业务层从 TradeOrder 等实体转换后传入通用收益工具
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderProfitItem {

    /** 平台 */
    private Exchange platform;

    /** 交易对 */
    private String symbol;

    /** 方向 */
    private OrderSideEnum orderSide;

    /** 开仓价 */
    private BigDecimal openPrice;

    /** 可平仓数量（张/张数） */
    private BigDecimal remainingVolume;

    /** 已实现收益（部分平仓已产生的 income） */
    private BigDecimal income;

    /** 已产生手续费 */
    private BigDecimal charge;

    /** 是否已完全平仓（CLOSE/GAIN/LOSS 等） */
    private boolean closed;

    /** 已平仓时的收益（仅当 closed 时有效，用于汇总） */
    private BigDecimal closedProfit;

    /** 杠杆倍数（用于保证金等计算，可选） */
    private Integer leverRate;

    /** 是否跳过不参与汇总（如挂单 OPEN、待成交 PENDING、已撤销 CLOSE） */
    private boolean skip;
}
