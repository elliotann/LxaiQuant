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
@TableName("membership_benefit")
public class MembershipBenefit {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String level;
    private Integer monthlyCredits;
    private Integer maxBots;
    private Integer maxStrategies;
    private Integer maxBacktestsPerDay;
    private Integer maxAiAnalysisPerDay;
    private Boolean allowCustomFactor;
    private Boolean allowMlTraining;
    private Boolean allowApiAccess;
    private Boolean prioritySupport;
    private BigDecimal priceMonthlyUsdt;
    private BigDecimal priceYearlyUsdt;
    private LocalDateTime createdAt;
}
