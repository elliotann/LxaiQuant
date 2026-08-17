package com.chain.ai.trade.extension.strategy;

import java.math.BigDecimal;
import java.util.List;

/**
 * 关键点位策略参数配置
 * 用于信号驱动型策略的差异化参数
 */
public class CriticalLevelsConfig {

    public static final CriticalLevelsConfig DEFAULT = new CriticalLevelsConfig();

    private String entryObTypeFilter = "ALL";
    private BigDecimal maxEntryDistancePct = BigDecimal.valueOf(3.0);
    private BigDecimal stopLossFactor = BigDecimal.valueOf(0.002);
    private BigDecimal dedupPriceTolerance = BigDecimal.valueOf(0.0001);
    private boolean tp1PreferSwing = true;
    private boolean tp2PreferLiquidity = true;
    private List<String> enabledPeriods = List.of("15M", "1H", "4H", "1D");

    public String getEntryObTypeFilter() {
        return entryObTypeFilter;
    }

    public BigDecimal getMaxEntryDistancePct() {
        return maxEntryDistancePct;
    }

    public BigDecimal getStopLossFactor() {
        return stopLossFactor;
    }

    public BigDecimal getDedupPriceTolerance() {
        return dedupPriceTolerance;
    }

    public boolean isTp1PreferSwing() {
        return tp1PreferSwing;
    }

    public boolean isTp2PreferLiquidity() {
        return tp2PreferLiquidity;
    }

    public List<String> getEnabledPeriods() {
        return enabledPeriods;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final CriticalLevelsConfig config = new CriticalLevelsConfig();

        public Builder entryObTypeFilter(String entryObTypeFilter) {
            config.entryObTypeFilter = entryObTypeFilter;
            return this;
        }

        public Builder maxEntryDistancePct(BigDecimal maxEntryDistancePct) {
            config.maxEntryDistancePct = maxEntryDistancePct;
            return this;
        }

        public Builder stopLossFactor(BigDecimal stopLossFactor) {
            config.stopLossFactor = stopLossFactor;
            return this;
        }

        public Builder dedupPriceTolerance(BigDecimal dedupPriceTolerance) {
            config.dedupPriceTolerance = dedupPriceTolerance;
            return this;
        }

        public Builder tp1PreferSwing(boolean tp1PreferSwing) {
            config.tp1PreferSwing = tp1PreferSwing;
            return this;
        }

        public Builder tp2PreferLiquidity(boolean tp2PreferLiquidity) {
            config.tp2PreferLiquidity = tp2PreferLiquidity;
            return this;
        }

        public Builder enabledPeriods(List<String> enabledPeriods) {
            config.enabledPeriods = enabledPeriods;
            return this;
        }

        public CriticalLevelsConfig build() {
            return config;
        }
    }
}
