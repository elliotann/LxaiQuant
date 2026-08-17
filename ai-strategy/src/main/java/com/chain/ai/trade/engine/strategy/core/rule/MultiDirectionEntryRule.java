package com.chain.ai.trade.engine.strategy.core.rule;

import com.chain.ai.trade.common.entity.dto.SignalInfo;
import com.chain.ai.trade.engine.signal.service.impl.SignalCacheManager;
import com.chain.ai.trade.extension.ta4j.core.rule.ExitSignal;
import com.chain.ai.trade.extension.ta4j.core.rule.DirectionalRule;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Trade.TradeType;

/**
 * 基于信号缓存的多空入场规则。
 * 当信号为 "LONG" 时返回 BUY（开多），为 "SHORT" 时返回 SELL（开空）。
 */
public class MultiDirectionEntryRule implements DirectionalRule {
    private final BarSeries series;
    private final SignalCacheManager signalCache;

    public MultiDirectionEntryRule(BarSeries series, SignalCacheManager signalCache) {
        this.series = series;
        this.signalCache = signalCache;
    }

    @Override
    public TradeType getDirection(int index, TradingRecord tradingRecord) {
        SignalInfo signal = signalCache.getSignal(index, series);
        if (signal == null) return null;
        String signalType = signal.getSignalType();
        if (signalType == null) return null;

        switch (signalType) {
            case "LONG":
                return signal.getWeight() > 0 ? TradeType.BUY : null;
            case "SHORT":
                return signal.getWeight() > 0 ? TradeType.SELL : null;
            default:
                return null;
        }
    }

    @Override
    public ExitSignal getSignal(int index, TradingRecord tradingRecord) {
        return null;
    }

}
