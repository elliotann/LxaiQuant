package com.chain.ai.trade.engine.entity.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 止损水平类
 */
@Data
@Builder
public class StopLossLevel {
    private int level;                // 止损级别（1,2,3...）
    private double price;             // 止损价格
    private String type;              // 止损类型（固定、移动、时间止损等）
    private String description;       // 止损描述
    private String basedOn;           // 基于什么设置（波浪失效价、通道、ATR等）
    private double riskPercentage;    // 风险百分比
    private boolean isPrimary;        // 是否为主要止损
}