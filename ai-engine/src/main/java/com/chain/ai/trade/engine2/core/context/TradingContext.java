package com.chain.ai.trade.engine2.core.context;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.engine2.backtest.model.ActionRecord;
import com.chain.ai.trade.engine2.backtest.model.EntryRecord;
import com.chain.ai.trade.engine2.backtest.model.MemoryPosition;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交易上下文 — 策略只读查询接口。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>策略只能通过此接口查询数据，不能修改</li>
 *   <li>支持独立双向持仓（多空可同时存在）</li>
 *   <li>所有返回的集合均为不可变快照</li>
 * </ul>
 */
public interface TradingContext {

    // ================================================================
    // 1. 当前持仓查询（策略实时决策用）
    // ================================================================

    /**
     * 是否有任何持仓（多头或空头）
     */
    boolean hasPosition();

    /**
     * 是否有多头持仓（独立判断，不受空头影响）
     */
    boolean hasLongPosition();

    /**
     * 是否有空头持仓（独立判断，不受多头影响）
     */
    boolean hasShortPosition();

    /**
     * 获取净持仓方向（多头数量 - 空头数量）。
     * <p>
     * 返回值：
     * <ul>
     *   <li>净持仓 > 0 → {@link SignalType#LONG}</li>
     *   <li>净持仓 < 0 → {@link SignalType#SHORT}</li>
     *   <li>净持仓 == 0 → {@link SignalType#HOLD}</li>
     * </ul>
     * <p>
     * ⚠️ 净方向为 HOLD 时，可能是空仓或完全对冲，
     * 如需区分请使用 {@link #hasPosition()} 和 {@link #isFullyHedged()}
     */
    SignalType getNetPositionSide();

    /**
     * 判断当前是否处于完全对冲状态（多头数量 == 空头数量，且 > 0）。
     * <p>
     * 这是 getNetPositionSide() == HOLD 时的特殊状态。
     * <p>
     * 示例：
     * <ul>
     *   <li>long=1, short=1 → true</li>
     *   <li>long=0, short=0 → false</li>
     *   <li>long=1, short=0 → false</li>
     * </ul>
     */
    boolean isFullyHedged();

    /**
     * 获取多头持仓数量（即使有空头也返回多头数量）
     */
    BigDecimal getLongQuantity();

    /**
     * 获取空头持仓数量（即使有多头也返回空头数量）
     */
    BigDecimal getShortQuantity();

    /**
     * 获取当前多头持仓对象，无持仓时返回 null
     */
    MemoryPosition getLongPosition();

    /**
     * 获取当前空头持仓对象，无持仓时返回 null
     */
    MemoryPosition getShortPosition();

    /**
     * 获取多头持仓加权均价
     */
    BigDecimal getLongAvgPrice();

    /**
     * 获取空头持仓加权均价
     */
    BigDecimal getShortAvgPrice();

    /**
     * 获取多头持仓的所有开仓明细（当前剩余）
     */
    List<EntryRecord> getLongEntries();

    /**
     * 获取空头持仓的所有开仓明细（当前剩余）
     */
    List<EntryRecord> getShortEntries();

    // ================================================================
    // 2. 资金查询
    // ================================================================

    /**
     * 获取当前可用余额（可用于开仓的资金）
     */
    BigDecimal getAvailableBalance();

    /**
     * 获取当前总权益（含浮动盈亏）
     *
     * @param currentPrice 当前市场价格（用于计算浮动盈亏）
     */
    BigDecimal getEquity(BigDecimal currentPrice);

    /**
     * 获取初始资金
     */
    BigDecimal getInitialCapital();

    // ================================================================
    // 3. 历史平仓查询（借鉴 TA4J TradingRecord.closedPositions）
    // ================================================================

    /**
     * 获取所有已平仓的交易闭环（完整快照）
     * <p>
     * 用途：落库、报表、绩效统计。
     * <p>
     * ⚠️ 策略不应该依赖历史数据做交易决策，仅用于统计或调试。
     */
    List<MemoryPosition> getClosedPositions();

    /**
     * 获取已平仓交易数量（统计用）
     */
    int getClosedTradeCount();

    /**
     * 获取总已实现盈亏（所有已平仓交易的盈亏之和）
     */
    BigDecimal getTotalRealizedPnl();

    // ================================================================
    // 4. 交易明细查询（审计/调试用）
    // ================================================================

    /**
     * 获取全量开仓明细日志（含已平仓和持仓中）
     * <p>
     * 用途：审计追溯、调试
     */
    List<EntryRecord> getAllEntryRecords();

    /**
     * 获取全量动作日志（所有交易动作）
     * <p>
     * 用途：调试、复盘
     */
    List<ActionRecord> getAllActionRecords();

    // ================================================================
    // 5. 元数据
    // ================================================================

    /**
     * 获取交易标的
     */
    String getSymbol();

    // ================================================================
    // 6. 运行模式
    // ================================================================

    /**
     * 是否为回测模式。
     * <p>
     * 回测中 Bar 数据完整（含 OHLC），可使用高低点判断止损/止盈；<br>
     * 实盘/模拟中当前 Bar 未完成，只能用实时价。
     */
    boolean isBacktest();
}