package com.chain.ai.trade.order.entity.dos;

import com.baomidou.mybatisplus.annotation.TableName;
import com.chain.ai.trade.order.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 移动明细
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("vdr_trailing_detail")
public class TrailingDetail extends BaseEntity {
    //第多少次
    private int count;

    private BigDecimal stopLossPrice;

    private BigDecimal stopGainPrice;

    private BigDecimal price;

    private String changeTime;

    private String orderSn;
}
