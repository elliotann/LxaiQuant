package com.chain.ai.trade.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("recharge_address")
public class RechargeAddress {

    @TableId
    private String id;
    private String userId;
    private String rechargeAddress;
    private String privateKeyEnc;
    private String businessType;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
