package com.chain.ai.trade.engine.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import com.chain.ai.trade.engine.model.PerformanceMetrics;

/**
 * 回测响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 回测结果列表
     */
    private List<StrategyResult> results;

    /**
     * 执行时间（毫秒）
     */
    private long executionTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StrategyResult {

        /**
         * 策略名称
         */
        private String strategyName;

        /**
         * 策略描述
         */
        private String strategyDescription;

        /**
         * 绩效指标
         */
        private PerformanceMetrics performanceMetrics;

        /**
         * 交易记录摘要
         */
        private TradeSummary tradeSummary;

        /**
         * 出场类型统计（出场原因 → 次数）
         */
        private Map<String, Integer> exitTypeStats;

        /**
         * 多批次持仓明细
         */
        private List<Map<String, Object>> positionDetails;

        /**
         * 权益曲线数据（JSON格式）
         */
        private String equityCurve;

        /**
         * 回撤序列数据（JSON格式）
         */
        private String drawdownSeries;

        /**
         * 是否成功
         */
        private boolean success;

        /**
         * 错误消息
         */
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeSummary {

        /**
         * 总交易次数
         */
        private int totalTrades;

        /**
         * 买入次数
         */
        private int buyTrades;

        /**
         * 卖出次数
         */
        private int sellTrades;

        /**
         * 平均持仓时间
         */
        private double averageHoldingPeriod;

        /**
         * 最大单笔盈利
         */
        private double maxProfit;

        /**
         * 最大单笔亏损
         */
        private double maxLoss;
    }
}
