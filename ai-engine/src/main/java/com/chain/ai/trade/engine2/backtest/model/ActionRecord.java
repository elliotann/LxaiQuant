package com.chain.ai.trade.engine2.backtest.model;

import com.chain.ai.trade.extension.core.constants.ExitType;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交易信号记录 — 供生成权益曲线和绩效指标。
 * <p>
 * 每次开仓/平仓操作都会生成一条 ActionRecord 记录。
 */
@Data
@Builder
public class ActionRecord {

    /** 所在 K 线索引 */
    private final int barIndex;

    /** 操作类型：ENTRY / EXIT_TP / EXIT_SL / EXIT_SIGNAL / FORCE_CLOSE */
    private final String action;

    /** 成交价格 */
    private final BigDecimal price;

    /** 成交数量 */
    private final BigDecimal quantity;

    /** 本次盈亏（平仓时非空） */
    private final BigDecimal pnl;

    /** 操作前总权益 */
    private final BigDecimal equityBefore;

    /** 操作后总权益 */
    private final BigDecimal equityAfter;

    /** 平仓原因（平仓时非空） */
    private final ExitType exitType;

    /** 关联持仓ID → MemoryPosition.positionId */
    private final String positionId;

    /**
     * 平仓明细列表 — 记录本次平仓涉及的每笔 EntryRecord 的明细盈亏。
     * <p>
     * 全平时记录所有被平的条目，部分平仓时记录本次平掉的每条 EntryRecord。
     * 每个 ClosedEntryDetail 对应一笔被平的 EntryRecord。
     */
    @Singular("closedDetail")
    private final List<ClosedEntryDetail> closedDetails;
}
