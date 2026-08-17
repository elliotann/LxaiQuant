package com.chain.ai.trade.engine2.rules.base;

import com.chain.ai.trade.engine2.core.ExitSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import com.chain.ai.trade.engine2.rules.TradingRule;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.common.entity.constants.SignalType;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;

/**
 * 固定百分比止盈规则。
 * <p>
 * 回测模式：使用 Bar 高低点判断（盘中触及即触发）；<br>
 * 实盘/模拟模式：使用收盘价判断（当前 Bar 未完成，无完整 OHLC）。
 * </p>
 */
public class FixedTakeProfitRule implements TradingRule {

    private final double profitPercent; // 0.03 = 3%

    public FixedTakeProfitRule(double profitPercent) {
        this.profitPercent = profitPercent;
    }

    @Override
    public ExitSignal evaluate(int index, Bar bar, BarSeries series, TradingContext context) {
        if (!context.hasPosition()) return null;

        boolean isBacktest = context.isBacktest();

        // 检查多头止盈
        if (context.hasLongPosition()) {
            BigDecimal avgPrice = context.getLongAvgPrice();
            BigDecimal threshold = avgPrice.multiply(BigDecimal.valueOf(1 + profitPercent));
            if (isBacktest
                    ? bar.getHighPrice().bigDecimalValue().compareTo(threshold) >= 0
                    : bar.getClosePrice().bigDecimalValue().compareTo(threshold) >= 0) {
                return new ExitSignal(SignalType.CLOSE_LONG, ExitType.TAKE_PROFIT);
            }
        }

        // 检查空头止盈
        if (context.hasShortPosition()) {
            BigDecimal avgPrice = context.getShortAvgPrice();
            BigDecimal threshold = avgPrice.multiply(BigDecimal.valueOf(1 - profitPercent));
            if (isBacktest
                    ? bar.getLowPrice().bigDecimalValue().compareTo(threshold) <= 0
                    : bar.getClosePrice().bigDecimalValue().compareTo(threshold) <= 0) {
                return new ExitSignal(SignalType.CLOSE_SHORT, ExitType.TAKE_PROFIT);
            }
        }
        return null;
    }
}
