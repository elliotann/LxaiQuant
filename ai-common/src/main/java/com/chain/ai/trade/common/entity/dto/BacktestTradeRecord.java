package com.chain.ai.trade.common.entity.dto;


import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 回测交易记录DTO
 * 用于保存回测引擎生成的交易记录到订单系统中
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BacktestTradeRecord {

    /**
     * 交易时间
     */
    private Date tradeTime;

    /**
     * 交易方向：LONG（多单）, SHORT（空单）
     */
    private String direction;

    /**
     * 操作类型：开仓(OPEN), 平仓(CLOSE)
     */
    private String actionType;

    /**
     * 具体操作：开多(OPEN_LONG), 开空(OPEN_SHORT), 平多(CLOSE_LONG), 平空(CLOSE_SHORT)
     */
    private String action;

    /**
     * 交易价格
     */
    private BigDecimal price;

    /**
     * 交易数量
     */
    private BigDecimal amount;

    /**
     * 盈亏金额（平仓时填写）
     */
    private BigDecimal pnl;

    /**
     * 盈亏百分比（平仓时填写）
     */
    private String pnlPercent;

    /**
     * 手续费成本（平仓时填写，开仓+平仓合计）
     */
    private BigDecimal charge;

    private String closeReason;

    /**
     * 交易ID，用于关联开仓和平仓
     */
    private String tradeId;

    /**
     * V2引擎仓位ID，作为 orderSn 持久化
     */
    private String positionId;

    /**
     * 开仓明细ID，对应EntryRecord.entryId，用于平仓时精准匹配
     */
    private String entryId;

    /**
     * 对应的OrderSideEnum
     */
    public OrderSideEnum getOrderSide() {
        if ("LONG".equals(direction)) {
            return OrderSideEnum.BUY;
        } else if ("SHORT".equals(direction)) {
            return OrderSideEnum.SELL;
        }
        return OrderSideEnum.BUY; // 默认多单
    }

    /**
     * 是否为开仓操作
     */
    public boolean isOpenAction() {
        return "OPEN".equals(actionType) ||
               "开多".equals(action) ||
               "开空".equals(action) ||
               "开仓".equals(action) ||
               action != null && action.startsWith("开");
    }

    /**
     * 是否为平仓操作
     */
    public boolean isCloseAction() {
        return "CLOSE".equals(actionType) ||
               "平多".equals(action) ||
               "平空".equals(action) ||
               action != null && action.startsWith("平");
    }
}
