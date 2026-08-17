package com.chain.ai.trade.order.entity.dos;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.order.entity.BaseEntity;
import com.chain.ai.trade.order.entity.constants.TradeOrderEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Date;

/**
 *平仓明细
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_trade_exit_item")
public class TradeExitItem extends BaseEntity {

    private String batchId;

    private String positionId;

    private String entrySn;

    private BigDecimal closedVolume;

    private String status;

    private BigDecimal entryPrice;

    private BigDecimal exitPrice;

    private Date exitTime;

    private BigDecimal income;

    private BigDecimal charge;

    /** 平仓方式: AUTO-自动, MANUAL-手动 */
    private String closeMethod;

    public TradeExitItem(String batchId, String entrySn, BigDecimal closedVolume, TradeOrderEnum closeStatus, BigDecimal entryPrice) {
        this.batchId = batchId;
        this.entrySn = entrySn;
        this.closedVolume = closedVolume;
        this.status = closeStatus.getCode();
        this.entryPrice = entryPrice;
    }

    public TradeExitItem(String batchId, String entrySn, BigDecimal closedVolume, TradeOrderEnum closeStatus, BigDecimal entryPrice, BigDecimal exitPrice, Date exitTime, BigDecimal income, BigDecimal charge) {
        this.batchId = batchId;
        this.entrySn = entrySn;
        this.closedVolume = closedVolume;
        this.status = closeStatus.getCode();
        this.entryPrice = entryPrice;
        this.exitPrice = exitPrice;
        this.exitTime = exitTime;
        this.income = income;
        this.charge = charge;
    }
}
