package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 资金流水表 - 记录所有资金变动
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("fund_flow")
public class FundFlow extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 关联账户ID
     */
    private Long accountId;

    /**
     * 币种
     */
    private String asset;

    /**
     * 变动类型：1-交易，2-资金费，3-划转，4-手续费，5-其他
     */
    private Integer flowType;

    /**
     * 变动金额（正为入，负为出）
     */
    private BigDecimal amount;

    /**
     * 变动前余额（total）
     */
    private BigDecimal balanceBefore;

    /**
     * 变动后余额（total）
     */
    private BigDecimal balanceAfter;

    /**
     * 关联ID（如订单ID）
     */
    private String refId;
}