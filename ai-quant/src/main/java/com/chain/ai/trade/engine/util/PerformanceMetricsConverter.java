package com.chain.ai.trade.engine.util;

import com.chain.ai.trade.engine.model.PerformanceMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 绩效指标转换器
 * 安全地从 PerformanceMetrics 获取值，提供默认值
 */
@Component
@Slf4j
public class PerformanceMetricsConverter {

    /**
     * 默认胜率（当绩效指标为空时使用）
     */
    private static final BigDecimal DEFAULT_WIN_RATE = BigDecimal.valueOf(0.5);

    /**
     * 默认盈亏比
     */
    private static final BigDecimal DEFAULT_PROFIT_FACTOR = BigDecimal.ONE;

    /**
     * 获取总收益率
     */
    public BigDecimal getTotalReturn(PerformanceMetrics metrics) {
        if (metrics == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(metrics.getTotalReturn());
    }

    /**
     * 获取最大回撤
     */
    public BigDecimal getMaxDrawdown(PerformanceMetrics metrics) {
        if (metrics == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(metrics.getMaxDrawdown());
    }

    /**
     * 获取胜率
     */
    public BigDecimal getWinRate(PerformanceMetrics metrics) {
        if (metrics == null) {
            return DEFAULT_WIN_RATE;
        }
        return BigDecimal.valueOf(metrics.getWinRate());
    }

    /**
     * 获取总交易次数
     */
    public int getTotalTrades(PerformanceMetrics metrics) {
        if (metrics == null) {
            return 0;
        }
        return (int) metrics.getTotalTrades();
    }

    /**
     * 获取盈利交易次数
     */
    public int getProfitableTrades(PerformanceMetrics metrics) {
        if (metrics == null) {
            return 0;
        }
        return (int) metrics.getProfitableTrades();
    }

    /**
     * 获取盈亏比
     */
    public BigDecimal getProfitFactor(PerformanceMetrics metrics) {
        if (metrics == null) {
            return DEFAULT_PROFIT_FACTOR;
        }
        return BigDecimal.valueOf(metrics.getProfitLossRatio());
    }

    /**
     * 计算最终价值
     * @param metrics 绩效指标
     * @param initialAmount 初始资金
     * @return 最终价值
     */
    public BigDecimal calculateFinalValue(PerformanceMetrics metrics, double initialAmount) {
        if (metrics == null) {
            return BigDecimal.valueOf(initialAmount);
        }
        double finalValue = initialAmount * (1 + metrics.getTotalReturn());
        return BigDecimal.valueOf(finalValue);
    }

    /**
     * 计算夏普比率
     * 优先使用 metrics 中通过 ta4j 精准计算的 sharpeRatio 值，
     * 仅在无法获取时回退到简化公式（总收益率 / 最大回撤）
     * @param metrics 绩效指标
     * @return 夏普比率
     */
    public BigDecimal getSharpeRatio(PerformanceMetrics metrics) {
        if (metrics == null) {
            log.warn("PerformanceMetrics为null，返回夏普比率为0");
            return BigDecimal.ZERO;
        }
        // 优先使用 ta4j 精准计算的 sharpeRatio
        double realSharpe = metrics.getSharpeRatio();
        if (!Double.isNaN(realSharpe) && !Double.isInfinite(realSharpe) && Math.abs(realSharpe) > 1e-10) {
            return toScaled4(realSharpe);
        }
        // 回退：简化公式 sharpeRatio = totalReturn / maxDrawdown
        double maxDrawdown = metrics.getMaxDrawdown();
        double totalReturn = metrics.getTotalReturn();
        log.debug("使用简化公式计算夏普比率: totalReturn={}, maxDrawdown={}", totalReturn, maxDrawdown);
        
        if (maxDrawdown == 0 || Double.isNaN(maxDrawdown) || Double.isInfinite(maxDrawdown)) {
            log.warn("最大回撤无效(maxDrawdown={})，返回夏普比率为0", maxDrawdown);
            return BigDecimal.ZERO;
        }
        if (Double.isNaN(totalReturn) || Double.isInfinite(totalReturn)) {
            log.warn("总收益率无效(totalReturn={})，返回夏普比率为0", totalReturn);
            return BigDecimal.ZERO;
        }
        // 简化计算：总收益率 / 最大回撤
        // 注意：maxDrawdown通常是0到1之间的正数（表示回撤百分比）
        double sharpeRatio = totalReturn / maxDrawdown;
        log.debug("计算得到的夏普比率: {}", sharpeRatio);
        return toScaled4(sharpeRatio);
    }

    /**
     * 获取卡玛比率
     * 优先使用年化收益率 / 最大回撤，仅在年化收益率不可用时回退到总收益率 / 最大回撤
     * @param metrics 绩效指标
     * @return 卡玛比率
     */
    public BigDecimal getCalmarRatio(PerformanceMetrics metrics) {
        if (metrics == null) {
            log.warn("PerformanceMetrics为null，返回卡玛比率为0");
            return BigDecimal.ZERO;
        }
        double maxDrawdown = metrics.getMaxDrawdown();
        log.debug("计算卡玛比率: maxDrawdown={}", maxDrawdown);
        
        if (maxDrawdown == 0 || Double.isNaN(maxDrawdown) || Double.isInfinite(maxDrawdown)) {
            log.warn("最大回撤无效(maxDrawdown={})，返回卡玛比率为0", maxDrawdown);
            return BigDecimal.ZERO;
        }
        // 优先使用年化收益率计算卡玛比率
        double annualReturn = metrics.getAnnualReturn();
        if (!Double.isNaN(annualReturn) && !Double.isInfinite(annualReturn) && Math.abs(annualReturn) > 1e-10) {
            double calmarRatio = annualReturn / maxDrawdown;
            log.debug("使用年化收益率计算卡玛比率: {}", calmarRatio);
            return toScaled4(calmarRatio);
        }
        // 回退：总收益率 / 最大回撤
        double totalReturn = metrics.getTotalReturn();
        if (Double.isNaN(totalReturn) || Double.isInfinite(totalReturn)) {
            log.warn("总收益率无效(totalReturn={})，返回卡玛比率为0", totalReturn);
            return BigDecimal.ZERO;
        }
        double calmarRatio = totalReturn / maxDrawdown;
        log.debug("使用简化公式计算卡玛比率: {}", calmarRatio);
        return toScaled4(calmarRatio);
    }

    /**
     * 获取总成本
     */
    public BigDecimal getTotalCost(PerformanceMetrics metrics) {
        if (metrics == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(metrics.getTotalCost());
    }

    private static final BigDecimal MAX_DECIMAL_10_6 = new BigDecimal("9999.999999");
    private static final BigDecimal MIN_DECIMAL_10_6 = new BigDecimal("-9999.999999");
    private static final BigDecimal MAX_DECIMAL_10_4 = new BigDecimal("999999.9999");
    private static final BigDecimal MIN_DECIMAL_10_4 = new BigDecimal("-999999.9999");

    private BigDecimal toSafeDecimal(double value, BigDecimal max, BigDecimal min, int scale) {
        BigDecimal bd = BigDecimal.valueOf(value);
        if (bd.compareTo(max) > 0) {
            log.warn("数值异常过大: {}, 已截断至 {}", value, max);
            return max;
        }
        if (bd.compareTo(min) < 0) {
            log.warn("数值异常过小: {}, 已截断至 {}", value, min);
            return min;
        }
        return bd.setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal toScaled6(double value) {
        return toSafeDecimal(value, MAX_DECIMAL_10_6, MIN_DECIMAL_10_6, 6);
    }

    private BigDecimal toScaled4(double value) {
        return toSafeDecimal(value, MAX_DECIMAL_10_4, MIN_DECIMAL_10_4, 4);
    }

    /**
     * 获取年化收益率
     */
    public BigDecimal getAnnualReturn(PerformanceMetrics metrics) {
        if (metrics == null) {
            return BigDecimal.ZERO;
        }
        double value = metrics.getAnnualReturn();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO;
        }
        return toScaled6(value);
    }

    /**
     * 获取年化波动率
     */
    public BigDecimal getVolatility(PerformanceMetrics metrics) {
        if (metrics == null) {
            return BigDecimal.ZERO;
        }
        double value = metrics.getVolatility();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO;
        }
        return toScaled6(value);
    }

    /**
     * 获取索提诺比率
     */
    public BigDecimal getSortinoRatio(PerformanceMetrics metrics) {
        if (metrics == null) {
            return BigDecimal.ZERO;
        }
        double value = metrics.getSortinoRatio();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO;
        }
        return toScaled4(value);
    }

    /**
     * 获取平均盈利额
     */
    public BigDecimal getAverageWin(PerformanceMetrics metrics) {
        if (metrics == null) {
            return BigDecimal.ZERO;
        }
        double value = metrics.getAverageWin();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value);
    }

    /**
     * 获取平均亏损额
     */
    public BigDecimal getAverageLoss(PerformanceMetrics metrics) {
        if (metrics == null) {
            return BigDecimal.ZERO;
        }
        double value = metrics.getAverageLoss();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value);
    }

    /**
     * 获取最大单笔盈利
     */
    public BigDecimal getLargestWinTrade(PerformanceMetrics metrics) {
        if (metrics == null) {
            return BigDecimal.ZERO;
        }
        double value = metrics.getLargestWinTrade();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value);
    }

    /**
     * 获取最大单笔亏损
     */
    public BigDecimal getLargestLossTrade(PerformanceMetrics metrics) {
        if (metrics == null) {
            return BigDecimal.ZERO;
        }
        double value = metrics.getLargestLossTrade();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value);
    }
}

