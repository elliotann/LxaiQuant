package com.chain.ai.trade.engine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("trading_advice")
public class TradingAdvice {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("advice_id")
    private String adviceId;

    private String symbol;

    @TableField("natural_report")
    private String naturalReport;

    @TableField("tradeplan_json")
    private String tradeplanJson;

    @TableField("created_at")
    private Date createdAt;
}
