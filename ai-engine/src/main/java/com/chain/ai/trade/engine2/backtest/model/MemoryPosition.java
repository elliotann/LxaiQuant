package com.chain.ai.trade.engine2.backtest.model;

import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.extension.core.constants.ExitType;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 内存持仓模型 — 支持加减仓、止盈止损价、手续费。
 * <p>
 * 回测循环中使用的领域对象，不回测循环外不落库。
 * 开仓时生成 UUID 作为 positionId，↔ TradeOrder.orderSn。
 * <p>
 * 设计要点（方案 A）：
 * <ul>
 *   <li>totalQuantity：当前持仓量（随开/加/减仓变动）</li>
 *   <li>totalEntryQuantity：累计开仓总量（只增不减，用于 flush 聚合）</li>
 *   <li>entries：FIFO 队列，每个 EntryRecord 持有自己的出场记录列表 (closedDetails)</li>
 *   <li>平仓时：在对应的 EntryRecord 上追加 ClosedEntryDetail，实现 1:N 的入场→出场关联</li>
 * </ul>
 */
@Data
@Builder
@Slf4j
public class MemoryPosition {

    // ==================== 不可变字段（开仓时确定） ====================

    /** 持仓ID，UUID，开仓时生成 */
    private final String positionId;

    /** 交易对 */
    private final String symbol;

    /** 持仓方向：LONG / SHORT */
    private final SignalType direction;

    /** 首次开仓所在 K 线索引 */
    private final int entryBarIndex;

    /**
     * 🔥 首次开仓时间（加仓时不变）
     * <p>
     * 在开仓时设置，加仓时不修改。
     * 用于落库到 TradeOrder.orderTime / buyTime
     */
    private LocalDateTime entryTime;

    /**
     * 🔥 最后全平时间
     * <p>
     * 用于落库到 TradeOrder.sellTime
     */
    private final LocalDateTime exitTime;

    // ==================== 可变字段（持仓期间变化） ====================

    /** 当前加权平均开仓价（加仓时重算，部分平仓时不变） */
    private BigDecimal avgPrice;

    /** 最后平仓价格 */
    private BigDecimal exitPrice;

    /**
     * 当前持仓总量（开仓/加仓时增加，平仓时减少）
     * <p>
     * ⚠️ 这个值 = Σ entries 中每条记录的 quantity
     */
    private BigDecimal totalQuantity;

    /**
     * 累计开仓总量（只增不减，含所有开仓和加仓）
     * <p>
     * 用于 flush() 时聚合生成 TradeOrder.volume
     * ⚠️ 这个值不受平仓影响，永远等于 Σ initialQuantity
     */
    private BigDecimal totalEntryQuantity;

    /** 开仓手续费累计（含加仓） */
    private BigDecimal openFee;

    /** 平仓盈利 */
    private BigDecimal totalPnl;

    @Builder.Default
    private String status = "OPEN";  // OPEN / GAIN / LOSS

    /** 全局止盈价（可被 EntryRecord 覆盖） */
    private BigDecimal takeProfitPrice;

    /** 全局止损价（可被 EntryRecord 覆盖） */
    private BigDecimal stopLossPrice;

    // ==================== FIFO 队列 ====================

    /**
     * 当前剩余的开仓明细列表（FIFO 顺序）
     * <p>
     * 每个 EntryRecord 持有自己的出场记录列表 (closedDetails)
     */
    @Builder.Default
    private List<EntryRecord> entries = new ArrayList<>();

    // ================================================================

    /**
     * 添加一笔开仓明细（开仓或加仓时调用）
     * <p>
     * 1. 重算加权均价
     * 2. 累加 totalQuantity 和 totalEntryQuantity
     * 3. 追加到 FIFO 队列末尾
     *
     * @param record 开仓明细（必须已设置 initialQuantity 和 quantity）
     */
    public void addEntry(EntryRecord record) {
        if (record == null || record.getQuantity() == null || record.getQuantity().signum() <= 0) {
            log.warn("addEntry 忽略无效明细: {}", record);
            return;
        }

        // 1. 初始化空值保护
        if (totalQuantity == null) totalQuantity = BigDecimal.ZERO;
        if (totalEntryQuantity == null) totalEntryQuantity = BigDecimal.ZERO;
        if (avgPrice == null) avgPrice = BigDecimal.ZERO;
        if (openFee == null) openFee = BigDecimal.ZERO;

        // 2. 重算加权均价
        BigDecimal newTotalQty = totalQuantity.add(record.getQuantity());
        if (newTotalQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalCost = avgPrice.multiply(totalQuantity)
                    .add(record.getPrice().multiply(record.getQuantity()));
            this.avgPrice = totalCost.divide(newTotalQty, 8, RoundingMode.HALF_UP);
        } else {
            this.avgPrice = record.getPrice();
        }

        // 3. 更新数量（当前持仓量 + 累计开仓量）
        this.totalQuantity = newTotalQty;
        this.totalEntryQuantity = this.totalEntryQuantity.add(record.getQuantity());

        // 4. 累加手续费
        this.openFee = this.openFee.add(record.getFee());

        // 5. 追加到 FIFO 队列
        this.entries.add(record);

        log.debug("addEntry: posId={}, addQty={}, totalQty={}, totalEntryQty={}, avgPrice={}",
                positionId, record.getQuantity(), totalQuantity, totalEntryQuantity, avgPrice);
    }

    /**
     * 减少持仓数量（部分平仓或全平）— 方案 A
     * <p>
     * 按 FIFO 顺序从 entries 头部开始扣减，同时：
     * <ul>
     *   <li>在对应的 EntryRecord 上追加 ClosedEntryDetail（记录本次平仓明细）</li>
     *   <li>被完全平掉的 EntryRecord 从 entries 中移除</li>
     *   <li>被部分平掉的 EntryRecord 只修改 quantity（initialQuantity 不变）</li>
     *   <li>totalQuantity 减少，totalEntryQuantity 不变</li>
     * </ul>
     *
     * @param qty         需要减少的数量
     * @param exitPrice   平仓价格（含滑点）
     * @param pnlCalc     盈亏计算函数：(entryPrice, closeQty, exitPrice) → pnl
     * @param closeReason 平仓原因（STOP_LOSS / TAKE_PROFIT / AUTO 等）
     * @return 本次平仓的逐笔明细列表（用于生成 ActionRecord 和落库）
     */
    public List<ClosedEntryDetail> reduceQuantity(
            BigDecimal qty,
            BigDecimal exitPrice,
            BiFunction<BigDecimal, BigDecimal, BigDecimal> pnlCalc,
            String closeReason,
            LocalDateTime exitTime) {

        if (qty == null || qty.signum() <= 0) {
            return Collections.emptyList();
        }

        if (exitPrice == null) {
            log.warn("reduceQuantity: exitPrice 为 null，忽略扣减");
            return Collections.emptyList();
        }

        if (totalQuantity == null || totalQuantity.signum() <= 0) {
            log.warn("reduceQuantity: 当前无持仓，忽略扣减");
            return Collections.emptyList();
        }

        if (qty.compareTo(totalQuantity) > 0) {
            log.warn("reduceQuantity: 扣减数量 {} 超过总持仓 {}，将全部平仓", qty, totalQuantity);
            qty = totalQuantity;
        }

        List<ClosedEntryDetail> allClosedDetails = new ArrayList<>();
        BigDecimal remaining = qty;

        Iterator<EntryRecord> iterator = entries.iterator();
        while (iterator.hasNext() && remaining.signum() > 0) {
            EntryRecord entry = iterator.next();
            BigDecimal entryQty = entry.getQuantity();

            if (entryQty == null || entryQty.signum() <= 0) {
                // 🔥 不移除：保留 qty=0 的 entry，使其 closedDetails 在快照中可被深拷贝
                continue;
            }

            if (entryQty.compareTo(remaining) <= 0) {
                // ---- 该 Entry 全部平完 ----
                BigDecimal closeQty = entryQty;
                BigDecimal pnl = pnlCalc.apply(entry.getPrice(), closeQty);

                // 🔥 方案 A 核心：在 EntryRecord 上记录出场明细
                entry.addClosedDetail(exitPrice, closeQty, pnl, closeReason, exitTime);

                allClosedDetails.add(ClosedEntryDetail.builder()
                        .entryId(entry.getEntryId())
                        .quantity(closeQty)
                        .exitPrice(exitPrice)
                        .pnl(pnl)
                        .closeReason(closeReason)
                        .exitTime(exitTime)
                        .build());

                remaining = remaining.subtract(closeQty);
                entry.setQuantity(BigDecimal.ZERO);
                // 🔥 不移除：保留全平的 entry，使其 closedDetails 在后续 snapshot 中可被深拷贝

            } else {
                // ---- 该 Entry 部分平仓 ----
                BigDecimal closeQty = remaining;
                BigDecimal pnl = pnlCalc.apply(entry.getPrice(), closeQty);

                // 🔥 方案 A 核心：在 EntryRecord 上记录出场明细
                entry.addClosedDetail(exitPrice, closeQty, pnl, closeReason, exitTime);

                allClosedDetails.add(ClosedEntryDetail.builder()
                        .entryId(entry.getEntryId())
                        .quantity(closeQty)
                        .exitPrice(exitPrice)
                        .pnl(pnl)
                        .closeReason(closeReason)
                        .exitTime(exitTime)
                        .build());

                // 只修改 quantity，initialQuantity 保持不变
                entry.setQuantity(entryQty.subtract(closeQty));
                remaining = BigDecimal.ZERO;
            }
        }

        // 更新当前总持仓量（totalEntryQuantity 保持不变）
        this.totalQuantity = this.totalQuantity.subtract(qty);

        // 🔥 不再清空 entries：保留 qty=0 的 entry 及其 closedDetails，供后续 snapshot 深拷贝

        log.debug("reduceQuantity: posId={}, reduceQty={}, remainingQty={}, detailCount={}",
                positionId, qty, totalQuantity, allClosedDetails.size());

        return allClosedDetails;
    }

    /**
     * 按 entryId 精准扣减指定 Entry 的全部持仓（用于逐笔 TP/SL 平仓）。
     * <p>
     * 与 {@link #reduceQuantity} 不同，此方法不按 FIFO 顺序，
     * 而是直接定位到指定的 entry 执行扣减，避免平错仓位。
     *
     * @return 平仓明细，如果 entry 不存在或已平仓则返回空列表
     */
    public List<ClosedEntryDetail> reduceEntry(String entryId,
                                                BigDecimal exitPrice,
                                                BiFunction<BigDecimal, BigDecimal, BigDecimal> pnlCalc,
                                                String closeReason,
                                                LocalDateTime exitTime) {
        for (EntryRecord entry : entries) {
            if (!entryId.equals(entry.getEntryId())) continue;
            if (!entry.hasRemaining()) return Collections.emptyList();

            BigDecimal closeQty = entry.getQuantity();
            BigDecimal pnl = pnlCalc.apply(entry.getPrice(), closeQty);

            entry.addClosedDetail(exitPrice, closeQty, pnl, closeReason, exitTime);
            entry.setQuantity(BigDecimal.ZERO);
            this.totalQuantity = this.totalQuantity.subtract(closeQty);

            ClosedEntryDetail detail = ClosedEntryDetail.builder()
                    .entryId(entryId)
                    .quantity(closeQty)
                    .exitPrice(exitPrice)
                    .pnl(pnl)
                    .closeReason(closeReason)
                    .exitTime(exitTime)
                    .build();

            log.debug("reduceEntry: posId={}, entryId={}, closeQty={}, remainingQty={}",
                    positionId, entryId, closeQty, totalQuantity);
            return Collections.singletonList(detail);
        }
        return Collections.emptyList();
    }

    /**
     * 获取该仓位下所有出场明细（遍历所有 EntryRecord 的 closedDetails）
     * <p>
     * 用于 flush() 时展开落库到 ai_trade_exit_item
     */
    public List<ClosedEntryDetail> getAllClosedDetails() {
        List<ClosedEntryDetail> result = new ArrayList<>();
        for (EntryRecord entry : entries) {
            if (entry.getClosedDetails() != null) {
                result.addAll(entry.getClosedDetails());
            }
        }
        return result;
    }

    /**
     * 获取该仓位下所有已完全平仓的 EntryRecord 的出场明细
     * <p>
     * 用于统计该仓位的总盈亏
     */
    public List<ClosedEntryDetail> getFullyClosedDetails() {
        List<ClosedEntryDetail> result = new ArrayList<>();
        for (EntryRecord entry : entries) {
            if (entry.isFullyClosed() && entry.getClosedDetails() != null) {
                result.addAll(entry.getClosedDetails());
            }
        }
        return result;
    }

    /**
     * 计算该仓位的总已实现盈亏（遍历所有 EntryRecord 的 closedDetails）
     */
    public BigDecimal getTotalClosedPnl() {
        return getAllClosedDetails().stream()
                .map(ClosedEntryDetail::getPnl)
                .filter(pnl -> pnl != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断当前是否为空仓
     */
    public boolean isEmpty() {
        return totalQuantity == null || totalQuantity.signum() <= 0;
    }

    /**
     * 获取累计开仓总量（用于 flush 聚合）
     */
    public BigDecimal getTotalEntryQuantity() {
        return totalEntryQuantity != null ? totalEntryQuantity : BigDecimal.ZERO;
    }

    /**
     * 获取当前剩余明细数量
     */
    public int getEntryCount() {
        return entries != null ? entries.size() : 0;
    }

    /**
     * 获取所有尚未完全平仓的 EntryRecord
     */
    public List<EntryRecord> getActiveEntries() {
        List<EntryRecord> result = new ArrayList<>();
        for (EntryRecord entry : entries) {
            if (entry.hasRemaining()) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * 获取所有已完全平仓的 EntryRecord
     */
    public List<EntryRecord> getClosedEntries() {
        List<EntryRecord> result = new ArrayList<>();
        for (EntryRecord entry : entries) {
            if (entry.isFullyClosed()) {
                result.add(entry);
            }
        }
        return result;
    }

    // ==================== 🔥 新增：生成已平仓快照 ====================

    /**
     * 生成已平仓快照 — 在完全平仓时调用
     * <p>
     * ⚠️ 必须在 reduceQuantity 之后调用，此时 entries 已包含本次平仓的 closedDetails。
     * <p>
     * 深拷贝所有 EntryRecord（含 closedDetails），确保快照独立于原对象。
     *
     * @param exitPrice  加权平仓价
     * @param totalPnl   总盈亏（可先传 null，计算后通过 setter 补上）
     * @param exitType   出场原因
     * @param exitTime   平仓时间
     * @return 已平仓快照（status = "CLOSED"）
     */
    public MemoryPosition toClosedSnapshot(BigDecimal exitPrice, BigDecimal totalPnl,
                                           ExitType exitType, LocalDateTime exitTime) {
        // 深拷贝 entries（每个 EntryRecord 及其 closedDetails）
        List<EntryRecord> snapshotEntries = this.entries.stream()
                .map(EntryRecord::deepCopy)
                .collect(Collectors.toList());

        return MemoryPosition.builder()
                .positionId(this.positionId)
                .symbol(this.symbol)
                .direction(this.direction)
                .avgPrice(this.avgPrice)
                .totalQuantity(this.totalQuantity)
                .totalEntryQuantity(this.totalEntryQuantity)
                .entryBarIndex(this.entryBarIndex)
                .entryTime(this.entryTime)
                .takeProfitPrice(this.takeProfitPrice)
                .stopLossPrice(this.stopLossPrice)
                .openFee(this.openFee)
                .entries(snapshotEntries)
                // 平仓信息
                .exitPrice(exitPrice)
                .totalPnl(totalPnl)
                .exitTime(exitTime)
                .status(this.status)  // 🔥 携带状态
                .build();
    }

    // 2. 修正 markClosed
    public void markClosed(ExitType exitType, BigDecimal pnl) {
        if (exitType == null || exitType == ExitType.UNKNOWN) {
            this.status = pnl.compareTo(BigDecimal.ZERO) >= 0 ? "GAIN" : "LOSS";
        } else if (exitType == ExitType.TAKE_PROFIT) {
            this.status = "GAIN";
        } else if (exitType == ExitType.STOP_LOSS) {
            this.status = "LOSS";
        } else {
            this.status = pnl.compareTo(BigDecimal.ZERO) >= 0 ? "GAIN" : "LOSS";
        }
    }


}