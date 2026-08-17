package com.chain.ai.trade.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * 回测默认值配置
 * 管理回测结果保存时的默认值
 */
@Configuration
@ConfigurationProperties(prefix = "backtest.defaults")
@Data
public class BacktestDefaultsConfig {

    /**
     * 默认胜率（当绩效指标为空时使用）
     */
    private BigDecimal defaultWinRate = BigDecimal.valueOf(0.5);

    /**
     * 默认盈亏比
     */
    private BigDecimal defaultProfitFactor = BigDecimal.ONE;

    /**
     * 默认初始资金
     */
    private BigDecimal defaultInitialAmount = BigDecimal.valueOf(10000);
}

