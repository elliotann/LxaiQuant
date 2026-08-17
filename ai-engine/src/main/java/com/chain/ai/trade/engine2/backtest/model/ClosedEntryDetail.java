package com.chain.ai.trade.engine2.backtest.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 平仓明细 — 记录本次平仓涉及的单笔 EntryRecord 的盈亏明细。
 * <p>
 * 部分平仓时，一个 ActionRecord 可能对应多个 ClosedEntryDetail，
 * 每个 ClosedEntryDetail 记录了被平掉的某笔 EntryRecord 的明细数据。
 * 全平时通常只有一条明细（汇总），或与 entries 数量一致。
 */
@Data
@Builder
public class ClosedEntryDetail {

    /** 被平的 EntryRecord.entryId */
    private String entryId;

    /** 本次平仓数量（该笔明细被平掉的数量） */
    private BigDecimal quantity;

    /** 该笔明细的盈亏 */
    private BigDecimal pnl;

    /** 该笔明细的平仓价 */
    private BigDecimal exitPrice;

    /** 平仓原因：STOP_LOSS / TAKE_PROFIT / AUTO 等 */
    private String closeReason;

    /** 平仓时间（对应K线的结束时间） */
    private LocalDateTime exitTime;
}
