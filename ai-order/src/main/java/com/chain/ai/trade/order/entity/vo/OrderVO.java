package com.chain.ai.trade.order.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 订单VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {

    /**
     * 订单ID
     */
    private String id;

    /**
     * 订单编号
     */
    private String orderSn;

    /**
     * 主订单ID
     */
    private String mainOrderId;

    /**
     * 会员ID
     */
    private String memberId;

    /**
     * 会员名称
     */
    private String memberName;

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 账户ID（字符串，与 TradeOrder.accountId 类型保持一致）
     */
    private String accountId;

    /**
     * 机器人ID
     */
    private String robotId;

    /**
     * 订单方向 (BUY/SELL)
     */
    private String orderSide;

    /**
     * 价格类型（LIMIT / MARKET）
     */
    private String priceType;

    private int leverRate;

    /**
     * 订单时间
     */
    private Date orderTime;

    /**
     * 开仓价格
     */
    private BigDecimal openPrice;

    /**
     * 买入均价
     */
    private BigDecimal buyAvgPrice;

    /**
     * 买入价格
     */
    private BigDecimal buyPrice;

    /**
     * 卖出价格
     */
    private BigDecimal sellPrice;
    private BigDecimal lossPrice;
    private BigDecimal gainPrice;

    private String batchExitType;
    private Integer currentBatchIndex;
    private List<BatchExitPlanVO> batchExitPlans;
    private List<BatchExitRecordVO> batchExits;
    private BigDecimal trailingGainLockedPrice;
    private BigDecimal trailingLossLockedPrice;

    /**
     * 数量
     */
    private BigDecimal amount;

    /**
     * 剩余持仓数量（原数量减去已平仓数量）
     */
    private BigDecimal remainingAmount;

    /**
     * 成交量
     */
    private BigDecimal volume;

    /**
     * 手续费
     */
    private BigDecimal charge;

    /**
     * 卖出时间
     */
    private Date sellTime;

    /**
     * 买入时间
     */
    private Date buyTime;

    /**
     * 订单金额
     */
    private BigDecimal orderAmount;

    /**
     * 订单状态
     */
    private String status;

    private String closeReason;

    /**
     * 利润百分比
     */
    private Float profitPercent;

    /**
     * 收益
     */
    private BigDecimal income;

    /**
     * 已实现收益
     */
    private BigDecimal hasIncome;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 回测任务ID
     */
    private String testReportId;

    /**
     * 平台订单编号
     */
    private String platformOrderSn;

    /**
     * 交易平台
     */
    private String memberPlatform;
}
