package com.chain.ai.trade.engine2.strategy;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine2.core.EntrySignal;
import com.chain.ai.trade.engine2.core.ExitSignal;
import com.chain.ai.trade.engine2.core.OrderIntent;
import com.chain.ai.trade.engine2.core.ScaleInSignal;
import com.chain.ai.trade.engine2.core.context.StrategyContext;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * 统一策略抽象接口 — 引擎唯一调用的策略契约。
 * <p>
 * 入场/出场通过独立接口承载，对应规则配置化驱动的<strong>入场规则集</strong>和<strong>出场规则集</strong>。
 * 回测/实盘共用同一套 ScriptStrategy 接口，仅执行引擎不同。
 * 完全脱离 TA4J 的 Strategy/Rule/TradingRecord 依赖。
 */
public interface ScriptStrategy {

    /**
     * 初始化（加载参数、计算指标）
     */
    default void init(StrategyContext ctx) {}

    /**
     * 入场决策 — 引擎每Bar调用一次。
     *
     * @return EntrySignal（含方向 + 信号强度），null 表示不入场
     */
    default EntrySignal shouldEntry(int index, Bar bar, TradingContext context) {
        return null;
    }

    /**
     * 出场决策 — 引擎每Bar调用一次
     *
     * @return ExitSignal（包含方向 + 原因），null 表示不出场
     */
    default ExitSignal shouldExit(int index, Bar bar, TradingContext context) {
        return null;
    }

    /**
     * 加仓决策 — 有持仓时引擎每 Bar 调用一次。
     * <p>
     * 返回 ScaleInSignal 表示执行加仓，null 表示不加仓。
     * 默认返回 null（不触发加仓），策略按需覆盖。
     *
     * @return ScaleInSignal（含方向 + 原因 + 可选的止盈止损价），null 表示不加仓
     */
    default ScaleInSignal shouldScaleIn(int index, Bar bar, BarSeries series, TradingContext context) {
        return null;
    }

    /**
     * 订单成交回调（实盘模式使用）
     */
    default void onOrderFilled(OrderIntent intent) {}

    /**
     * 策略销毁
     */
    default void destroy() {}
}
