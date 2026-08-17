package com.chain.ai.trade.order.entity.dos;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.order.entity.constants.OrderItemType;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.order.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 开仓入场明细
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_trade_entry")
public class TradeEntry extends BaseEntity {

    @TableField("position_id")
    private String positionId;

    @TableField("entry_sn")
    private String entrySn;

    private String platformOrderSn;

    private String robotId;

    private String symbol;


    @TableField("side")
    private OrderSideEnum orderSideEnum;

    @TableField("entry_price")
    private BigDecimal buyPrice;

    @TableField("exit_price")
    private BigDecimal sellPrice;

    /**
     * 第一止损目标金额
     */
    private BigDecimal lossPrice;

    /**
     * 最终止盈目标金额
     */
    private BigDecimal gainPrice;

    /**
     * 压力位
     */
    private BigDecimal supplyPrice;

    /**
     * 支撑位
     */
    private BigDecimal demandPrice;

    private BigDecimal amount;

    private BigDecimal volume;

    private BigDecimal charge;

    @TableField("entry_time")
    private Date orderTime;

    @TableField("exit_time")
    private Date sellTime;

    private int buyCount;

    /**
     * 买入时权重
     */
    @TableField("entry_weights")
    private double buyWeights;

    /**
     * 卖时权重
     */
    @TableField("exit_weights")
    private double sellWeights;

    private BigDecimal income;

    /**
     * 止盈点数
     */
    private BigDecimal takeProfitAmount;

    @TableField("status")
    private TradePosition.TradeOrderStatus tradeOrderItemStatus;

    @TableField("entry_type")
    private OrderItemType orderItemType;

    private Boolean syncVolumeFlag;

    private BigDecimal closedVolume;

    private String platformAlgoId;

    private int repair;

    private Long signalId;

    private BigDecimal usedMargin;

    private BigDecimal maxLoss;

    private BigDecimal maxProfit;

    public BigDecimal getCanCloseVolume() {
        // 处理空值情况：如果 volume 为 null，尝试使用 amount 作为替代
        BigDecimal effectiveVolume = volume;
        if (effectiveVolume == null) {
            effectiveVolume = amount; // 如果 volume 为空，使用 amount
        }
        if (effectiveVolume == null) {
            return BigDecimal.ZERO; // 如果两者都为空，返回0
        }
        if (closedVolume == null) {
            return effectiveVolume; // 如果 closedVolume 为空，返回全部可平仓数量
        }
        BigDecimal result = effectiveVolume.subtract(closedVolume);
        // 确保结果不为负数
        return volume;
    }
}
