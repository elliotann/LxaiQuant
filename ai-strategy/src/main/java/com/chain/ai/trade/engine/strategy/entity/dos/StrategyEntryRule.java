package com.chain.ai.trade.engine.strategy.entity.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("strategy_entry_rule")
public class StrategyEntryRule implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleId;

    private String strategyId;

    private String direction;

    private Boolean disabled;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
