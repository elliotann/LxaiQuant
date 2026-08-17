package com.chain.ai.trade.engine.strategy.core.rule;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.entity.dto.SignalInfo;
import com.chain.ai.trade.engine.signal.service.impl.SignalCacheManager;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * 基于信号缓存的空头出场规则。
 * 当持有空头仓位且信号不再是 "SHORT"（信号为空、反转或明确退出）时，isSatisfied() 返回 true。
 * 支持 mustWin 模式：只有当持仓盈利（扣除手续费）时才允许信号反转出场。
 */
public class SignalShortExitRule implements Rule {

    private final BarSeries series;
    private final SignalCacheManager signalCache;
    private final boolean mustWin;
    private final double commissionRate;

    public SignalShortExitRule(BarSeries series, SignalCacheManager signalCache) {
        this(series, signalCache, false, 0.001);
    }

    public SignalShortExitRule(BarSeries series, SignalCacheManager signalCache,
                               boolean mustWin, double commissionRate) {
        this.series = series;
        this.signalCache = signalCache;
        this.mustWin = mustWin;
        this.commissionRate = commissionRate;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        // 没有持仓时不触发出场
        if (tradingRecord == null || !tradingRecord.getCurrentPosition().isOpened()) {
            return false;
        }
        SignalInfo signal = signalCache.getSignal(index, series);
        if (signal == null) return false;
        String signalType = signal.getSignalType();
        if (signalType == null) return false;
        // 不是多头信号就不出场
        if (!SignalType.LONG.name().equals(signalType)) {
            return false;
        }
        // 信号触发了反转出场
        if (!mustWin) return true;

        // mustWin 模式：检查当前持仓是否盈利（扣除手续费），用开盘价与出场价一致
        double entryPrice = tradingRecord.getCurrentPosition().getEntry().getNetPrice().doubleValue();
        Bar currentBar = series.getBar(index);
        double currentPrice = currentBar.getOpenPrice().doubleValue();
        double totalFee = commissionRate * 2 * entryPrice; // 开仓+平仓手续费
        return (entryPrice - currentPrice) > totalFee;
    }
}
