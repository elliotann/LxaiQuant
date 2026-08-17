package com.chain.ai.trade.engine.entity.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 价格目标类
 */
@Data
@Builder
public class PriceTarget {
    private int level;                // 目标级别（1,2,3...）
    private double price;             // 目标价格
    private double probability;       // 达成概率（0-1）
    private String description;       // 目标描述
    private String basedOn;           // 基于什么计算（斐波那契、波浪目标等）
    private double distanceFromCurrent; // 距离当前价格的距离
    private double riskRewardRatio;   // 该目标的风险收益比
}