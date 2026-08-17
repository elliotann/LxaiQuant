package com.chain.ai.trade.common.entity.dto;


import com.chain.ai.trade.common.entity.constants.DataSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * 回测请求模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestRequest {

    /**
     * 策略类型
     */
    private String strategyType;

    /**
     * 货币ID (用于CoinGecko数据源)
     */
    private String coinId;

    /**
     * 数据天数
     */
    private Integer days;

    /**
     * 初始资金
     */
    private Double initialAmount;

    /**
     * 机器人ID（用于传统回测）
     */
    private String robotId;

    private String strategyId;

    /**
     * 账户ID（传统回测/测试模式需要提供）
     */
    private String accountId;

    @Builder.Default
    private BacktestType backtestType = BacktestType.TRADITIONAL_BACKTEST_NEW;

    /**
     * 杠杆倍数 (用于合约交易)
     */
    @Builder.Default
    private Double leverage = 1.0;

    /**
     * 是否为合约交易
     */
    @Builder.Default
    private Boolean isContractTrading = false;

    /**
     * 手续费率 (例如: 0.00045 表示万4.5，即0.045%)，默认 0.00045
     */
    @Builder.Default
    private Double commissionRate = 0.00045;

    /**
     * 滑点率 (例如: 0.0001 表示万分之一，即0.01%)，默认 0
     */
    @Builder.Default
    private Double slippageRate = 0.0;

    /**
     * K线周期 (例如: 3m, 15m, 1h, 4h, 1d)
     * 不传时使用默认周期
     */
    private String interval;

    @Builder.Default
    private String executionMatchPolicy = "FIFO";

    /**
     * 信号数据源 (用于信号策略)
     */
    private String signalDataFrom;

    /**
     * 信号交易对 (用于信号策略)
     */
    private String signalSymbol;

    /**
     * 信号指标类型 (用于信号策略)
     */
    private String signalIndicatorType;

    /**
     * 仓位调节器ID (用于动态仓位控制)
     * 对应 PositionAdjusterType 的 beanName
     */
    private String positionAdjusterId;

    /**
     * 回测开始时间（毫秒时间戳，UTC）
     * 仅在测试/回测模式下使用，用于限制一次性加载信号的时间范围
     */
    private Long startTime;

    /**
     * 回测结束时间（毫秒时间戳，UTC）
     * 仅在测试/回测模式下使用，用于限制一次性加载信号的时间范围
     */
    private Long endTime;

    /**
     * RSI策略参数
     */
    private RSIConfig rsiConfig;

    /**
     * 支撑阻力策略参数
     */
    private SRConfig srConfig;

    private Map<String, String> parameterOverrides;

    private Map<String, SignalInfo> inMemorySignalCache;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RSIConfig {
        @Builder.Default
        private Integer rsiPeriod = 14;
        @Builder.Default
        private Integer overboughtLevel = 70;
        @Builder.Default
        private Integer oversoldLevel = 30;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SRConfig {
        @Builder.Default
        private Integer barCount = 50;
        @Builder.Default
        private Integer surroundingBars = 3;
    }


    /**
     * 回测类型枚举
     */
    public enum BacktestType {
        /**
         * 快速回测 - 基于信号的简单回测(使用V2自研引擎)
         */
        TRADITIONAL_BACKTEST_NEW("快速回测", "基于K线步进的回测方法(使用V2自研引擎)"),

        /**
         * 模拟实盘 - 逐根K线推送模拟实时行情，验证事件驱动引擎
         */
        PAPER_TRADING("模拟实盘", "逐根K线推送模拟实时行情，使用PaperEngine验证事件驱动链路");

        private final String displayName;
        private final String description;

        BacktestType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }


}
