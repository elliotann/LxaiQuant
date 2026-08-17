package com.chain.ai.trade.engine.strategy.entity.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("entry_rule_condition")
public class EntryRuleCondition implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleId;

    private Integer sequence;

    private String connector;

    private String indicatorType;

    private String indicatorParams;

    private String operator;

    private BigDecimal threshold;

    private LocalDateTime createdAt;
}
