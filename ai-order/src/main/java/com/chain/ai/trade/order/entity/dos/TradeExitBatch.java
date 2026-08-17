package com.chain.ai.trade.order.entity.dos;

/**
 * @Description 平仓操作主表
 * @Author liangchen
 * @Date 2024/12/10 15:21
 **/

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.order.entity.constants.TradeOrderEnum;
import com.chain.ai.trade.order.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Date;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_trade_exit_batch")
public class TradeExitBatch extends BaseEntity {

    @TableField("position_id")
    private String positionId;

    @TableField("batch_platform_order_sn")
    private String closePlatformOrderSn;

    /**
     * @see TradeOrderEnum CLOSE_METHOD
     */
    @TableField(exist = false)
    private String closeMethod;

    private BigDecimal closedVolume;

    /**
     * @see TradeOrderEnum CLOSE_ORDER_STATUS
     */
    private String status;

    @TableField("exit_price")
    private BigDecimal sellPrice;

    @TableField("exit_time")
    private Date sellTime;

    private BigDecimal income;

    private BigDecimal charge;

    public TradeExitBatch(String positionId, BigDecimal closedVolume, TradeOrderEnum closeStatus) {
        this.positionId = positionId;
        this.closedVolume = closedVolume;
        this.status = closeStatus.getCode();
        this.closeMethod = TradeOrderEnum.CLOSE_METHOD_MANUAL.getCode();
    }

    public TradeExitBatch(String positionId, BigDecimal closedVolume, TradeOrderEnum closeStatus, BigDecimal sellPrice, Date sellTime, BigDecimal income, BigDecimal charge) {
        this.positionId = positionId;
        this.closedVolume = closedVolume;
        this.status = closeStatus.getCode();
        this.closeMethod = TradeOrderEnum.CLOSE_METHOD_AUTO.getCode();
        this.sellPrice = sellPrice;
        this.sellTime = sellTime;
        this.income = income;
        this.charge = charge;
    }
}
