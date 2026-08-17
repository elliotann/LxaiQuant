package com.chain.ai.trade.engine.strategy.core.rule;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.entity.dto.SignalInfo;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.signal.service.impl.SignalCacheManager;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * 基于信号缓存的多头出场规则。
 * 当持有多头仓位且信号不再是 "LONG"（信号为空、反转或明确退出）时，isSatisfied() 返回 true。
 * 支持 mustWin 模式：只有当持仓盈利（扣除手续费）时才允许信号反转出场。
 */
public class SignalLongExitRule implements Rule {
    private static final long TIMEZONE_OFFSET_MS = 8 * 60 * 60 * 1000L;
    private final BarSeries series;
    private final SignalCacheManager signalCache;
    private final boolean mustWin;
    private final double commissionRate;

    public SignalLongExitRule(BarSeries series, SignalCacheManager signalCache) {
        this(series, signalCache, false, 0.00045);
    }

    public SignalLongExitRule(BarSeries series, SignalCacheManager signalCache,
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
        // 不是空头信号就不出场
        if (!SignalType.SHORT.name().equals(signalType)) {
            return false;
        }
        // 信号触发了反转出场
        if (!mustWin) return true;

        // mustWin 模式：检查当前持仓是否盈利（扣除手续费），用开盘价与出场价一致
        double entryPrice = tradingRecord.getCurrentPosition().getEntry().getNetPrice().doubleValue();
        Bar currentBar = series.getBar(index);
        double currentPrice = currentBar.getOpenPrice().doubleValue();
        double totalFee = commissionRate * 2 * entryPrice; // 开仓+平仓手续费
        String targetTime = "2026-05-15 04:51:00";
        String currentTime = DateUtil.formatDateTime(new java.util.Date(currentBar.getBeginTime().toEpochMilli() - TIMEZONE_OFFSET_MS));
        boolean hitTarget = targetTime.equals(currentTime);
        if(hitTarget||(currentPrice - entryPrice) > totalFee){
            System.out.printf("here");
        }
        return (currentPrice - entryPrice) > totalFee;
    }
}
