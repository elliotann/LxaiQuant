package com.chain.ai.trade.engine2.persistence;

import com.chain.ai.trade.engine2.backtest.BacktestResult;
import org.ta4j.core.BarSeries;

/**
 * V2 引擎持久化门面 — 与 BacktestEngine 深度集成，buffer 批量写入。
 * <p>
 * 三种 Profile 实现：
 * <ul>
 *   <li>{@code @Profile("backtest")} → {@link BacktestBatchGateway}（buffer 批量落库）</li>
 *   <li>{@code @Profile("paper")} → PaperBatchGateway（实时模拟写入）</li>
 *   <li>{@code @Profile("live")} → LiveAsyncGateway（异步实盘写入）</li>
 * </ul>
 */
public interface PersistenceGateway {

    /** 开始一批持久化（引擎在 executeLoop 前调用，初始化 buffer） */
    default void openBatch() {}

    /** 批量刷写 — 引擎结束后调用，将 buffer 写入 DB */
    default void flush(String taskId, String symbol, BacktestResult result, BarSeries series) {}
}
