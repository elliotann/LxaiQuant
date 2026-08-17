package com.chain.ai.trade.engine2.backtest.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 开仓明细记录 — 记录单次开仓的详细信息。
 * <p>
 * 每个 MemoryPosition 包含多个 EntryRecord（支持加减仓）。
 * <p>
 * 设计要点（方案 A）：
 * <ul>
 *   <li>quantity：当前剩余数量（可变，FIFO 部分平仓时扣减）</li>
 *   <li>initialQuantity：原始开仓数量（不可变，用于持久化聚合统计）</li>
 *   <li>closedDetails：出场明细列表（每次平仓时追加）</li>
 *   <li>1 个 EntryRecord → N 个 ClosedEntryDetail</li>
 * </ul>
 * <p>
 * 对应数据库：
 * <ul>
 *   <li>EntryRecord → TradeOrderItem（ai_trade_entry 入场明细表）</li>
 *   <li>EntryRecord.closedDetails → TradeOrderCloseItem（ai_trade_exit_item 平仓明细表）</li>
 * </ul>
 */
@Data
@Builder
public class EntryRecord {

    // ==================== 不可变字段（开仓时确定，永不改变） ====================

    /** 开仓明细唯一标识 → TradeOrderItem.orderItemSn */
    @NonNull
    private String entryId;

    /** 归属持仓ID → MemoryPosition.positionId → TradeOrder.orderSn */
    @NonNull
    private String positionId;

    /** 交易对 */
    @NonNull
    private String symbol;

    /** 开仓价格（含滑点） */
    @NonNull
    private BigDecimal price;

    /**
     * 原始开仓数量（不可变，用于持久化聚合统计）
     * <p>
     * 对应 TradeOrderItem.volume
     */
    @NonNull
    private BigDecimal initialQuantity;

    /** 开仓手续费 → TradeOrderItem.charge */
    @NonNull
    private BigDecimal fee;

    /** 开仓所在的 K 线索引 */
    private int barIndex;

    /** K 线结束时间（用于聚合排序） */
    private LocalDateTime time;

    /** 可空 — 逐笔止盈价（本笔加仓的独立止盈价） */
    private BigDecimal takeProfitPrice;

    /** 可空 — 逐笔止损价（本笔加仓的独立止损价） */
    private BigDecimal stopLossPrice;

    /** 可空 — 触发入场的信号ID，关联到 TechnicalSignal */
    private Long signalId;

    /** 方向：LONG / SHORT → TradeOrderItem.orderSideEnum */
    @NonNull
    private String side;

    // ==================== 可变字段（持仓期间变化） ====================

    /**
     * 当前剩余数量（可变，FIFO 部分平仓时扣减）
     * <p>
     * ⚠️ 不是 final，因为部分平仓时需要修改此字段
     * <p>
     * 初始值 = initialQuantity，完全平仓后 = 0
     */
    @NonNull
    private BigDecimal quantity;

    /**
     * 出场明细列表（每次平仓时追加）
     * <p>
     * 🔥 方案 A 核心：每个 EntryRecord 持有自己的出场记录
     * <p>
     * 对应数据库：TradeOrderCloseItem（平仓明细表）
     * <p>
     * 一个 EntryRecord 可以被平多次（分批平仓），每次追加一条 ClosedEntryDetail
     */
    @Builder.Default
    private List<ClosedEntryDetail> closedDetails = new ArrayList<>();

    // ==================== 辅助方法 ====================

    /**
     * 追加一笔出场明细（部分平仓或全平时调用）
     *
     * @param exitPrice   平仓价格（含滑点）
     * @param quantity    本次平仓数量
     * @param pnl         本次盈亏
     * @param closeReason 平仓原因（STOP_LOSS / TAKE_PROFIT / AUTO 等）
     */
    public void addClosedDetail(BigDecimal exitPrice, BigDecimal quantity, BigDecimal pnl, String closeReason, LocalDateTime exitTime) {
        if (quantity == null || quantity.signum() <= 0) {
            return;
        }
        if (exitPrice == null) {
            return;
        }

        ClosedEntryDetail detail = ClosedEntryDetail.builder()
                .entryId(this.entryId)
                .exitPrice(exitPrice)
                .quantity(quantity)
                .pnl(pnl != null ? pnl : BigDecimal.ZERO)
                .closeReason(closeReason)
                .exitTime(exitTime)
                .build();

        this.closedDetails.add(detail);
    }

    /**
     * 判断是否已全部平完
     */
    public boolean isFullyClosed() {
        return quantity == null || quantity.signum() <= 0;
    }

    /**
     * 判断是否还有剩余
     */
    public boolean hasRemaining() {
        return quantity != null && quantity.signum() > 0;
    }

    /**
     * 获取已平仓总量
     */
    public BigDecimal getClosedTotalQuantity() {
        return initialQuantity.subtract(quantity);
    }

    /**
     * 获取该笔开仓的总已实现盈亏
     */
    public BigDecimal getTotalPnl() {
        return closedDetails.stream()
                .map(ClosedEntryDetail::getPnl)
                .filter(pnl -> pnl != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 获取该笔开仓的出场次数（被平了几次）
     */
    public int getExitCount() {
        return closedDetails != null ? closedDetails.size() : 0;
    }

    // ================================================================
    // 工厂方法
    // ================================================================

    /**
     * 创建开仓明细的便捷方法（不含逐笔 TP/SL）
     */
    public static EntryRecord of(String entryId, String positionId, String symbol,
                                 BigDecimal price, BigDecimal quantity,
                                 BigDecimal fee, int barIndex, LocalDateTime time,
                                 String side) {
        return EntryRecord.builder()
                .entryId(entryId)
                .positionId(positionId)
                .symbol(symbol)
                .price(price)
                .quantity(quantity)
                .initialQuantity(quantity)
                .fee(fee)
                .barIndex(barIndex)
                .time(time)
                .side(side)
                .closedDetails(new ArrayList<>())
                .build();
    }

    /**
     * 创建开仓明细的完整方法（含逐笔 TP/SL）
     */
    public static EntryRecord ofFull(String entryId, String positionId, String symbol,
                                     BigDecimal price, BigDecimal quantity,
                                     BigDecimal fee, int barIndex, LocalDateTime time,
                                     String side,
                                     BigDecimal takeProfitPrice, BigDecimal stopLossPrice) {
        return EntryRecord.builder()
                .entryId(entryId)
                .positionId(positionId)
                .symbol(symbol)
                .price(price)
                .quantity(quantity)
                .initialQuantity(quantity)
                .fee(fee)
                .barIndex(barIndex)
                .time(time)
                .side(side)
                .takeProfitPrice(takeProfitPrice)
                .stopLossPrice(stopLossPrice)
                .closedDetails(new ArrayList<>())
                .build();
    }

    // ==================== 深拷贝 ====================

    /**
     * 深拷贝 — 用于生成已平仓快照
     * <p>
     * 拷贝所有字段，包括 closedDetails 列表的完整副本。
     * 保证快照独立于原对象，后续修改不影响快照。
     *
     * @return 深拷贝的 EntryRecord 实例
     */
    public EntryRecord deepCopy() {
        EntryRecord copy = EntryRecord.builder()
                .entryId(this.entryId)
                .positionId(this.positionId)
                .symbol(this.symbol)
                .price(this.price)
                .quantity(this.quantity)
                .initialQuantity(this.initialQuantity)
                .fee(this.fee)
                .barIndex(this.barIndex)
                .time(this.time)
                .takeProfitPrice(this.takeProfitPrice)
                .stopLossPrice(this.stopLossPrice)
                .signalId(this.signalId)
                .side(this.side)
                .build();

        // 深拷贝 closedDetails
        if (this.closedDetails != null && !this.closedDetails.isEmpty()) {
            copy.setClosedDetails(new ArrayList<>(this.closedDetails));
        }

        return copy;
    }
}