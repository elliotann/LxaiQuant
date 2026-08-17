package com.chain.ai.trade.engine2.rules;

import com.chain.ai.trade.engine2.core.ExitSignal;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * 交易规则接口。
 * <p>
 * 评估规则在指定时刻是否满足，返回 ExitSignal（含出场方向+原因），null 表示不触发。
 * 支持通过 OrTradingRule 等组合规则实现多规则合并。
 * </p>
 */
public interface TradingRule {

    /**
     * 评估规则是否满足
     *
     * @param index   当前 Bar 索引
     * @param bar     当前 Bar
     * @param series  K线序列
     * @param context 交易上下文（持仓、资金等状态）
     * @return ExitSignal 表示触发出场（含方向+原因），null 表示不触发
     */
    ExitSignal evaluate(int index, Bar bar, BarSeries series, TradingContext context);
}
