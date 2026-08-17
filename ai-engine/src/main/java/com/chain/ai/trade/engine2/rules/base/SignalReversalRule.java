package com.chain.ai.trade.engine2.rules.base;

import com.chain.ai.trade.common.entity.dto.SignalInfo;
import com.chain.ai.trade.engine2.core.ExitSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import com.chain.ai.trade.engine2.rules.TradingRule;
import com.chain.ai.trade.engine.signal.service.impl.SignalCacheManager;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.common.entity.constants.SignalType;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * 信号反转出场规则。
 * <p>
 * 当信号缓存中出现与当前持仓方向相反的信号时触发平仓。
 * </p>
 */
public class SignalReversalRule implements TradingRule {

    private final SignalCacheManager signalCacheManager;

    public SignalReversalRule(SignalCacheManager signalCacheManager) {
        this.signalCacheManager = signalCacheManager;
    }

    @Override
    public ExitSignal evaluate(int index, Bar bar, BarSeries series, TradingContext context) {
        if (signalCacheManager == null || !context.hasPosition()) {
            return null;
        }
        SignalInfo signal = signalCacheManager.getSignal(index, series);
        if (signal == null) return null;

        String sigType = signal.getSignalType();

        // 持有多头且出现 SHORT 信号 → 平多
        if (context.hasLongPosition() && "SHORT".equalsIgnoreCase(sigType)) {
            return new ExitSignal(SignalType.CLOSE_LONG, ExitType.SIGNAL_REVERSAL);
        }
        // 持有空头且出现 LONG 信号 → 平空
        if (context.hasShortPosition() && "LONG".equalsIgnoreCase(sigType)) {
            return new ExitSignal(SignalType.CLOSE_SHORT, ExitType.SIGNAL_REVERSAL);
        }
        return null;
    }
}
