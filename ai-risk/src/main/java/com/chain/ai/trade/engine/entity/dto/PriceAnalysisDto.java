package com.chain.ai.trade.engine.entity.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 价格分析DTO
 * 包含价格目标、止损水平、趋势信息等完整的交易分析数据
 */
@Data
@Builder
public class PriceAnalysisDto {
    // 基础价格信息
    private Double currentPrice;           // 当前价格
    private String currentTrend;           // 当前趋势（BULLISH/BEARISH/NEUTRAL）
    private String trendDirection;         // 趋势方向描述
    
    // 评估指标
    private Integer agreementLevel;        // 一致性等级 1-5
    private Double compositeScore;         // 综合得分
    
    // 价格目标和止损
    private List<PriceTarget> priceTargets;          // 价格目标列表
    private List<StopLossLevel> stopLossLevels;      // 止损水平列表
    private Double optimalStopLoss;                 // 最优止损位
    private Double optimalTakeProfit;               // 最优止盈位
    private Double riskRewardRatio;                 // 风险收益比
    private Double breakevenPrice;                  // 盈亏平衡点
    
    // 策略描述
    private String targetStrategy;                  // 目标策略描述
    private String stopLossStrategy;                // 止损策略描述
    
    // 建议和警告
    private List<String> warnings;                  // 警告信息
    private List<String> recommendations;           // 建议
    private String tradingAdvice;                  // 交易建议（AVOID/CAUTIOUS/CONFIRMED）
}
