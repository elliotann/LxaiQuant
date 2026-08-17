package com.chain.ai.trade.order.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 订单查询DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderQueryDTO {

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 会员ID
     */
    private String memberId;

    /**
     * 账户ID
     */
    private Long accountId;

    /**
     * 机器人ID
     */
    private String robotId;

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 订单状态
     */
    private String status;

    /**
     * 订单方向 (BUY/SELL)
     */
    private String orderSide;

    /**
     * 回测任务ID
     */
    private String testReportId;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    private Date closeStartTime;

    private Date closeEndTime;

    /**
     * 页码 (从1开始)
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 20;

    /**
     * 排序字段
     */
    private String sortField = "orderTime";

    /**
     * 排序方向 (asc/desc)
     */
    private String sortOrder = "desc";
}
