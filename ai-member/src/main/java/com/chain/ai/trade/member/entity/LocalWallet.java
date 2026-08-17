package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.common.entity.dos.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 本地钱包表 - 维护每个账户的币种余额信息
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("local_wallet")
public class LocalWallet extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 关联账户ID（外键，指向账户表id）
     */
    private Long accountId;

    /**
     * 用户ID（可选冗余，用于快速按用户查询）
     */
    private String userId;

    /**
     * 币种（BTC, USDT...）
     */
    private String asset;

    /**
     * 可用余额
     */
    private BigDecimal available;

    /**
     * 冻结余额
     */
    private BigDecimal frozen;

    /**
     * 总余额（可用+冻结）
     */
    private BigDecimal total;

    /**
     * 最后更新时间
     */
    private Date updateTime;
}
