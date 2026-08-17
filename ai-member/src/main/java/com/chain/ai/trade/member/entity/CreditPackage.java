package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("credit_package")
public class CreditPackage {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private Integer credits;
    private BigDecimal priceUsdt;
    private Integer bonusCredits;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
