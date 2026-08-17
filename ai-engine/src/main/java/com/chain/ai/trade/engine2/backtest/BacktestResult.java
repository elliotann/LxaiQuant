package com.chain.ai.trade.engine2.backtest;

import com.chain.ai.trade.engine2.backtest.model.ActionRecord;
import com.chain.ai.trade.engine2.backtest.model.EntryRecord;
import com.chain.ai.trade.engine2.backtest.model.MemoryPosition;
import com.chain.ai.trade.extension.core.constants.ExitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 回测结果聚合对象
 * <p>
 * 由 BacktestEngine.buildResult() 生成，包含：
 * <ul>
 *   <li>绩效指标（胜率、夏普、回撤等）</li>
 *   <li>交易记录列表（每笔完整交易的聚合）</li>
 *   <li>权益曲线采样点</li>
 *   <li>开仓明细全量日志</li>
 *   <li>动作日志全量记录</li>
 * </ul>
 */
@Builder
@Value
public class BacktestResult {

    // ==================== 基础指标 ====================

    /** 交易标的 */
    String symbol;

    /** K 线总数量 */
    int totalBars;

    /** 总交易次数（已平仓的完整交易笔数） */
    int totalTrades;

    /** 最终权益（含浮动盈亏） */
    BigDecimal finalEquity;

    /** 总盈亏（最终权益 - 初始权益） */
    BigDecimal totalPnl;

    /** 最大回撤（百分比） */
    BigDecimal maxDrawdown;

    /** 胜率（盈利交易数 / 总交易数） */
    double winRate;

    /** 总手续费（含开仓+加仓+平仓） */
    @Builder.Default
    BigDecimal totalCommission = BigDecimal.ZERO;

    // ==================== 进阶绩效指标 ====================

    /** 年化收益率 */
    @Builder.Default
    double annualReturn = 0;

    /** 夏普比率（年化） */
    @Builder.Default
    double sharpeRatio = 0;

    /** 索提诺比率（年化，仅考虑下行波动） */
    @Builder.Default
    double sortinoRatio = 0;

    /** 年化波动率 */
    @Builder.Default
    double volatility = 0;

    /** 盈利交易笔数 */
    @Builder.Default
    int profitableTrades = 0;

    /** 盈亏比（平均盈利 / 平均亏损的绝对值） */
    @Builder.Default
    double profitLossRatio = 0;

    /** 平均盈利 */
    @Builder.Default
    double averageWin = 0;

    /** 平均亏损 */
    @Builder.Default
    double averageLoss = 0;

    /** 最大单笔盈利 */
    @Builder.Default
    double largestWinTrade = 0;

    /** 最大单笔亏损 */
    @Builder.Default
    double largestLossTrade = 0;

    // ==================== 明细数据列表 ====================

    /**
     * 已平仓交易聚合记录
     * <p>
     * 每笔完整交易（从首次开仓到完全平仓）生成 1 条 TradeRecord。
     * 聚合使用 EntryRecord.initialQuantity，不受部分平仓影响。
     */
    List<TradeRecord> trades;

    /** 权益曲线采样点 (barIndex → equity) */
    List<EquityPoint> equityCurve;

    /** 开仓明细全量日志（永不删除，用于审计追溯） */
    @Builder.Default
    List<EntryRecord> entryRecords = new ArrayList<>();

    /** 交易动作日志全量记录（开/加/平/减仓） */
    @Builder.Default
    List<ActionRecord> actionRecords = new ArrayList<>();

    // ================================================================
    // 🔥 新增：已平仓快照列表（供 Gateway 落库使用）
    // ================================================================

    /**
     * 已平仓仓位快照列表
     * <p>
     * 每个 MemoryPosition 包含完整的开仓明细（EntryRecord）和平仓明细（ClosedEntryDetail）。
     * 供 BacktestBatchGateway 直接落库到 ai_trade_position + ai_trade_entry + ai_trade_exit_item。
     * <p>
     * ⚠️ 此字段仅供持久化使用，不参与绩效指标计算。
     */
    @Builder.Default
    List<MemoryPosition> closedPositions = new ArrayList<>();

    // ==================== 内部类：TradeRecord ====================

    /**
     * 已平仓交易聚合记录
     * <p>
     * 由 BacktestContext.buildTradeRecord() 生成，使用 EntryRecord.initialQuantity 聚合。
     */
    @Value
    @AllArgsConstructor
    public static class TradeRecord {

        /** 唯一交易ID，用于去重和关联 */
        String tradeId;

        /** V2 引擎内存仓位ID → 对应 TradeOrder.orderSn */
        String positionId;

        /**
         * 开仓明细ID
         * <p>
         * 聚合记录（全平）时为 null，表示这是一笔汇总交易；
         * 逐笔止盈止损时非 null，关联到具体的 EntryRecord.entryId。
         */
        String entryId;

        /** 首次开仓所在的 K 线索引（用于获取开仓时间） */
        int entryIndex;

        /** 完全平仓所在的 K 线索引（用于获取平仓时间） */
        int exitIndex;

        /** 交易方向：LONG / SHORT */
        String side;

        /** 加权平均开仓价 = Σ(price × initialQuantity) / Σ initialQuantity */
        BigDecimal entryPrice;

        /** 加权平均平仓价（全平时的成交价） */
        BigDecimal exitPrice;

        /** 累计开仓总量 = Σ initialQuantity（含加仓） */
        BigDecimal quantity;

        /** 总盈亏（含所有开仓明细的盈亏之和） */
        BigDecimal pnl;

        /** 总手续费 = 开仓手续费累计 + 平仓手续费 */
        BigDecimal fee;

        /** 出场原因：TAKE_PROFIT / STOP_LOSS / EXIT / FORCE_CLOSE / null */
        ExitType exitType;
    }

    // ==================== 内部类：EquityPoint ====================

    /** 权益曲线采样点 */
    @Value
    @AllArgsConstructor
    public static class EquityPoint {
        /** K 线索引 */
        int index;

        /** K 线开始时间（毫秒时间戳） */
        long timestamp;

        /** 该时刻的总权益（含浮动盈亏） */
        BigDecimal equity;
    }
}