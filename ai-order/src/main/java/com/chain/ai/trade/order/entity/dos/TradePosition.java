package com.chain.ai.trade.order.entity.dos;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.OrderPriceType;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.order.entity.BaseEntity;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.order.entity.constants.StopType;
import com.chain.ai.trade.order.entity.constants.Trend;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 仓位主表
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_trade_position")
public class TradePosition extends BaseEntity {
    public enum TradeOrderStatus {
        //挂单
        OPEN,
        PENDING,//代表价格已经确认，待成交
        DEAL,//成交运行中
        CLOSE,//撤销
        LOSS,//止损
        GAIN//止
    }

    @TableField("position_id")
    private String positionId;

    private Exchange memberPlatform;

    private String platformOrderSn;

    private String memberId;

    private String memberName;


    private String symbol;

    private String accountId;

    private String robotId;

    private String goodsId;

    private OrderSideEnum orderSideEnum;

    /**
     * 价格类型：LIMIT / MARKET
     */
    private OrderPriceType priceType;

    private Date orderTime;

    private int trend;


    private Trend colorTrend;

    private BigDecimal openPrice;

    private BigDecimal buyPrice;

    private BigDecimal sellPrice;

    private BigDecimal amount;

    private BigDecimal volume;


    private BigDecimal charge;

    private Date sellTime;

    private Date buyTime;

    private BigDecimal orderAmount;


    @TableField("position_status")
    private TradeOrderStatus tradeOrderStatus;

    /**
     * 赢利目标百分比
     */
    private float profitPercent;

    private BigDecimal income;

    private BigDecimal hasIncome;

    private BigDecimal closeAmount;
    /**
     * 止盈点数
     */
    private BigDecimal takeProfitAmount;

    /**
     * 原始止盈止损价格，因为会移动
     */
    private BigDecimal oriLossPrice;

    /**
     * 第一止损目标金额
     */
    private BigDecimal lossPrice;

    /**
     * 第二止损目标金额
     */
    private BigDecimal secLossPrice;

    /**
     * 原始止盈止损价格，因为会移动
     */
    private BigDecimal oriGainPrice;

    /**
     * 最终止盈目标金额
     */
    private BigDecimal gainPrice;

    /**
     * 第一止盈目标金额
     */
    private BigDecimal firstGainPrice;

    /**
     * 第二止盈目标金额
     */
    private BigDecimal secGainPrice;

    /**
     * 买入时权重
     */
    private double buyWeights;

    /**
     * 卖时权重
     */
    private double sellWeights;

    //开单类型，手动还是系统开的
    private String openType;

    //是否测试单
    private boolean test;

    private String remark;

    //策略委托单ID,主要用于止盈止损
    private String algoId;

    //客户自定义策略订单ID，主要用于止盈止损
    private String algoClOrdId;

    //交易所订单ID
    private String exchangeOrderId;

    //止损类型
    private StopType stopType;

    private CandlestickIntervalEnum klineInterval;
    /**
     * 杠杆倍数
     */
    private int leverRate;

    private com.chain.ai.trade.order.entity.MemberRobotConfig.ConfigType configType;

    private String testReportId;

    private BigDecimal buyAvgPrice;

    private Long signalId;

    /**
     * 信号权重
     */
    private String signalTrend;

    private BigDecimal usedMargin;

    private BigDecimal maxLoss;

    private BigDecimal maxProfit;

    // 计算亏损点数的方法，根据买入方向和现价计算
    public BigDecimal calcTakeProfit(BigDecimal nowPrice) {
        if (orderSideEnum.equals(OrderSideEnum.BUY)) {
            return nowPrice.subtract(buyPrice);
        }
        {
            return buyPrice.subtract(nowPrice);
        }
    }
}
