package com.chain.ai.trade.engine2.backtest;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** 回测配置 */
@Data
@Builder
public class BacktestConfig {
    String symbol;

    /** 跳过前 N 根 K 线（用于指标预热） */
    @Builder.Default
    int warmupPeriod = 50;

    /** 初始总资产（USDT），对应机器人当前资金 TradingBot.currentCapital */
    @Builder.Default
    BigDecimal initialCapital = BigDecimal.valueOf(10000);

    /** 每笔开仓的分配资金（USDT），对应 TradingStrategyParams.amount / 基础仓位资金 */
    @Builder.Default
    BigDecimal positionAmount = BigDecimal.valueOf(1000);

    /** 杠杆倍数 */
    @Builder.Default
    int leverage = 1;

    /** 滑点百分比（如 0.001 = 0.1%） */
    @Builder.Default
    double slippage = 0.0;

    /** 手续费率（回测计算使用），如 0.001 = 0.1%，从 TradingStrategyParams.commissionRate 传入 */
    @Builder.Default
    BigDecimal commissionRate = BigDecimal.ZERO;

    /** 合约面值（如 BTC-USDT-SWAP 为 0.001），从 ContractSpec 获取 */
    BigDecimal contractSize;

    /** 交易所平台，用于收益/保证金计算 */
    Exchange platform;

    /** 合约规格（面值、乘数），用于收益/保证金计算 */
    @Builder.Default
    ContractSpec contractSpec = ContractSpec.defaultSpec();

    // ====== 加减仓配置 ======

    /** 最大加仓次数（含首次开仓），0 或 1 表示不加仓，3 表示最多开 3 笔 */
    @Builder.Default
    int maxAddPositions = 0;

    /** 盈利加仓幅度阈值（如 0.05 = 5%） */
    @Builder.Default
    double addPosOnProfitPct = 0;

    /** 亏损补仓幅度阈值 */
    @Builder.Default
    double addPosOnLossPct = 0;

    /** 盈利加仓间距 — 距上一笔加仓价最小涨幅 */
    @Builder.Default
    double addPosOnProfitGapPct = 0;

    /** 亏损补仓间距 — 距上一笔加仓价最小跌幅 */
    @Builder.Default
    double addPosOnLossGapPct = 0;

    /** EMA 趋势过滤开关 */
    @Builder.Default
    boolean profitAddEmaTrendEnabled = false;

    /** 快 EMA 周期 */
    @Builder.Default
    int profitAddEmaFastPeriod = 9;

    /** 慢 EMA 周期 */
    @Builder.Default
    int profitAddEmaSlowPeriod = 21;

    /** 趋势连续满足 K 线数 */
    @Builder.Default
    int profitAddEmaMinConsecutiveBars = 3;

    /** 仓位模式：QUALITY=固定比例(默认) / RISK=以损定量 */
    private String positionMode = "QUALITY";

    /** 账户总权益（USDT），以损定量模式必需 */
    private BigDecimal accountBalance = BigDecimal.ZERO;

    /** 日常止损缓冲（%），如 0.08 = 0.08%，用于以损定量计算止损距离 */
    @Builder.Default
    double dailyStopLossBuffer = 0.08;

    /** 单笔风险比例（%），如 1.0 = 1%，以损定量模式使用 */
    @Builder.Default
    double singleTradeRiskPct = 1.0;

    /** 启用同向信号频率限制 */
    @Builder.Default
    boolean signalFrequencyEnabled = false;

    /** 限制粒度: 3min / 15min / 1hour */
    @Builder.Default
    String signalFrequencyGranularity = "15min";

    /** 限制模式: strict_lock / structure_upgrade_exempt / unlimited */
    @Builder.Default
    String signalFrequencyMode = "structure_upgrade_exempt";
}
