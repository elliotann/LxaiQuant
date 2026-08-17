package com.chain.ai.trade.engine2.backtest;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.common.utils.ProfitCalcUtils;
import com.chain.ai.trade.engine2.backtest.model.ActionRecord;
import com.chain.ai.trade.engine2.backtest.model.ClosedEntryDetail;
import com.chain.ai.trade.engine2.backtest.model.EntryRecord;
import com.chain.ai.trade.engine2.backtest.model.MemoryPosition;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import com.chain.ai.trade.engine2.core.cost.CostModel;
import com.chain.ai.trade.engine2.core.cost.ZeroCostModel;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.order.entity.SnowFlake;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.Bar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * TradingContext 的回测实现 — 引擎内更新，策略只读查询。
 * <p>
 * 使用 {@link MemoryPosition} 管理双向持仓，记录 {@link EntryRecord} 和 {@link ActionRecord}。
 */
@Slf4j
public class BacktestContext implements TradingContext {

    private final String symbol;
    private final BigDecimal initialCapital;

    /** 多头持仓 */
    private MemoryPosition longPosition;

    /** 空头持仓 */
    private MemoryPosition shortPosition;

    /** 已实现的盈亏总和（不含浮动盈亏） */
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    /** 当前可用余额 */
    private BigDecimal availableBalance;

    /** 合约规格（面值、乘数），用于收益计算 */
    private final ContractSpec contractSpec;

    /** 成本模型 */
    private final CostModel costModel;

    /** 滑点百分比（如 0.001 = 0.1%） */
    private final BigDecimal slippage;

    /** 杠杆倍数 */
    private final int leverage;

    /** 总手续费累计 */
    private BigDecimal totalCommissionPaid = BigDecimal.ZERO;

    /** 已平仓交易记录（兼容 BacktestResult 统计） */
    private final List<BacktestResult.TradeRecord> trades = new ArrayList<>();

    /** 权益曲线采样点 */
    private final List<BacktestResult.EquityPoint> equityCurve = new ArrayList<>();

    /** 开仓明细记录（全量日志，永不删除） */
    private final List<EntryRecord> entryRecords = new ArrayList<>();

    /** 交易信号记录 */
    private final List<ActionRecord> actionRecords = new ArrayList<>();

    /** 交易ID计数器，每次平仓生成唯一ID */
    private final AtomicInteger tradeIdCounter = new AtomicInteger(1);

    // ===== 🔥 已平仓快照列表（引擎内部维护，策略不可见） =====
    private final List<MemoryPosition> closedPositions = new ArrayList<>();

    // ==================== 构造方法 ====================

    public BacktestContext(String symbol, BigDecimal initialCapital, ContractSpec contractSpec,
                           BigDecimal slippage, int leverage, CostModel costModel) {
        this.symbol = symbol;
        this.initialCapital = initialCapital;
        this.availableBalance = initialCapital;
        this.contractSpec = contractSpec;
        this.slippage = slippage;
        this.leverage = leverage;
        this.costModel = costModel != null ? costModel : new ZeroCostModel();
    }

    // ==================== TradingContext 接口实现（策略只读） ====================

    @Override
    public String getSymbol() { return symbol; }
    @Override public boolean isBacktest() { return true; }

    @Override
    public boolean hasPosition() { return longPosition != null || shortPosition != null; }

    @Override
    public boolean hasLongPosition() { return longPosition != null; }

    @Override
    public boolean hasShortPosition() { return shortPosition != null; }

    @Override
    public SignalType getNetPositionSide() {
        BigDecimal longQty = getLongQuantity();
        BigDecimal shortQty = getShortQuantity();
        int cmp = longQty.compareTo(shortQty);
        if (cmp > 0) return SignalType.LONG;
        if (cmp < 0) return SignalType.SHORT;
        return SignalType.HOLD;  // 净持仓为 0（可能空仓或完全对冲）
    }

    @Override
    public boolean isFullyHedged() {
        return hasLongPosition() && hasShortPosition()
                && getLongQuantity().compareTo(getShortQuantity()) == 0;
    }

    @Override
    public BigDecimal getLongQuantity() {
        return longPosition != null ? longPosition.getTotalQuantity() : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getShortQuantity() {
        return shortPosition != null ? shortPosition.getTotalQuantity() : BigDecimal.ZERO;
    }

    @Override
    public MemoryPosition getLongPosition() {
        return longPosition;
    }

    @Override
    public MemoryPosition getShortPosition() {
        return shortPosition;
    }

    @Override
    public BigDecimal getLongAvgPrice() {
        return longPosition != null ? longPosition.getAvgPrice() : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getShortAvgPrice() {
        return shortPosition != null ? shortPosition.getAvgPrice() : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getEquity(BigDecimal currentPrice) {
        BigDecimal total = initialCapital.add(realizedPnl);
        if (longPosition != null) {
            BigDecimal unrealized = ProfitCalcUtils.getProfitByVolume(
                    Exchange.OKX, OrderSideEnum.BUY,
                    longPosition.getAvgPrice(), longPosition.getTotalQuantity(),
                    currentPrice, contractSpec);
            total = total.add(unrealized);
        }
        if (shortPosition != null) {
            BigDecimal unrealized = ProfitCalcUtils.getProfitByVolume(
                    Exchange.OKX, OrderSideEnum.SELL,
                    shortPosition.getAvgPrice(), shortPosition.getTotalQuantity(),
                    currentPrice, contractSpec);
            total = total.add(unrealized);
        }
        return total;
    }

    @Override
    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    @Override
    public List<MemoryPosition> getClosedPositions() {
        return new ArrayList<>(closedPositions);
    }

    @Override
    public int getClosedTradeCount() {
        return closedPositions.size();
    }

    @Override
    public BigDecimal getTotalRealizedPnl() {
        return realizedPnl;
    }

    @Override
    public List<EntryRecord> getAllEntryRecords() {
        return new ArrayList<>(entryRecords);
    }

    @Override
    public List<ActionRecord> getAllActionRecords() {
        return new ArrayList<>(actionRecords);
    }

    @Override
    public BigDecimal getAvailableBalance() { return availableBalance; }

    @Override
    public List<EntryRecord> getLongEntries() {
        if (longPosition == null) return Collections.emptyList();
        return longPosition.getActiveEntries();
    }

    @Override
    public List<EntryRecord> getShortEntries() {
        if (shortPosition == null) return Collections.emptyList();
        return shortPosition.getActiveEntries();
    }

    // ==================== 引擎内部更新方法 ====================

    /**
     * 首次开仓（支持逐笔 TP/SL）
     */
    public void openPosition(int index, SignalType side, BigDecimal price, BigDecimal quantity,
                             BigDecimal takeProfitPrice, BigDecimal stopLossPrice,
                             LocalDateTime time, Long signalId) {

        if (side != SignalType.LONG && side != SignalType.SHORT) {
            log.warn("openPosition 收到无效方向: {}", side);
            return;
        }

        if (quantity == null || quantity.signum() <= 0) {
            log.warn("openPosition 开仓数量无效或为 0: quantity={}", quantity);
            return;
        }

        if (side == SignalType.LONG && longPosition != null) {
            log.warn("尝试开多但已有多头持仓 (持仓量={})，忽略本次开仓", longPosition.getTotalQuantity());
            return;
        }
        if (side == SignalType.SHORT && shortPosition != null) {
            log.warn("尝试开空但已有空头持仓 (持仓量={})，忽略本次开仓", shortPosition.getTotalQuantity());
            return;
        }

        BigDecimal actualPrice = applySlippage(price, side == SignalType.LONG);
        BigDecimal fee = costModel.calcOpenCost(actualPrice, quantity, contractSpec);

        BigDecimal margin = actualPrice.multiply(quantity)
                .multiply(contractSpec.getContractSize())
                .multiply(contractSpec.getContractMult())
                .divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);

        String positionId = SnowFlake.getIdStr();
        MemoryPosition pos = MemoryPosition.builder()
                .positionId(positionId)
                .symbol(symbol)
                .direction(side)
                .avgPrice(actualPrice)
                .totalQuantity(quantity)
                .totalEntryQuantity(quantity)
                .entryBarIndex(index)
                .entryTime(time)
                .openFee(fee)
                .takeProfitPrice(takeProfitPrice)
                .stopLossPrice(stopLossPrice)
                .build();

        EntryRecord entry = EntryRecord.builder()
                .entryId(UUID.randomUUID().toString())
                .positionId(positionId)
                .symbol(symbol)
                .price(actualPrice)
                .quantity(quantity)
                .initialQuantity(quantity)
                .fee(fee)
                .barIndex(index)
                .time(time)
                .takeProfitPrice(takeProfitPrice)
                .stopLossPrice(stopLossPrice)
                .signalId(signalId)
                .side(side.name())
                .build();

        pos.getEntries().add(entry);
        entryRecords.add(entry);

        if (side == SignalType.LONG) {
            longPosition = pos;
        } else {
            shortPosition = pos;
        }

        BigDecimal equityBefore = getEquity(price);
        availableBalance = availableBalance.subtract(margin).subtract(fee);
        totalCommissionPaid = totalCommissionPaid.add(fee);
        BigDecimal equityAfter = getEquity(price);

        actionRecords.add(ActionRecord.builder()
                .barIndex(index)
                .action("ENTRY")
                .price(price)
                .quantity(quantity)
                .equityBefore(equityBefore)
                .equityAfter(equityAfter)
                .positionId(positionId)
                .build());
    }

    /**
     * 加仓 — 往已有持仓追加一笔开仓明细。
     */
    public void addToPosition(int index, SignalType side, BigDecimal price, BigDecimal quantity,
                              BigDecimal takeProfitPrice, BigDecimal stopLossPrice,
                              LocalDateTime time, Long signalId) {
        MemoryPosition pos = side == SignalType.LONG ? longPosition : shortPosition;
        if (pos == null) {
            log.warn("addToPosition 失败: 方向 {} 无对应持仓", side);
            return;
        }

        BigDecimal actualPrice = applySlippage(price, side == SignalType.LONG);
        BigDecimal fee = costModel.calcOpenCost(actualPrice, quantity, contractSpec);

        BigDecimal margin = actualPrice.multiply(quantity)
                .multiply(contractSpec.getContractSize())
                .multiply(contractSpec.getContractMult())
                .divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);

        EntryRecord entry = EntryRecord.builder()
                .entryId(UUID.randomUUID().toString())
                .positionId(pos.getPositionId())
                .symbol(symbol)
                .price(actualPrice)
                .quantity(quantity)
                .initialQuantity(quantity)
                .fee(fee)
                .barIndex(index)
                .time(time)
                .takeProfitPrice(takeProfitPrice)
                .stopLossPrice(stopLossPrice)
                .signalId(signalId)
                .side(side.name())
                .build();

        pos.addEntry(entry);
        entryRecords.add(entry);

        BigDecimal equityBefore = getEquity(price);
        availableBalance = availableBalance.subtract(margin).subtract(fee);
        totalCommissionPaid = totalCommissionPaid.add(fee);
        BigDecimal equityAfter = getEquity(price);

        actionRecords.add(ActionRecord.builder()
                .barIndex(index)
                .action("SCALE_IN")
                .price(price)
                .quantity(quantity)
                .equityBefore(equityBefore)
                .equityAfter(equityAfter)
                .positionId(pos.getPositionId())
                .build());
    }

    // ==================== 平仓方法 ====================

    public void closePosition(int index, BigDecimal exitPrice, SignalType exitDirection, ExitType exitType, LocalDateTime exitTime) {
        closePosition(index, exitPrice, exitDirection, exitType, null, exitTime);
    }

    /**
     * 平仓 — 支持分批出场。
     */
    public void closePosition(int index, BigDecimal exitPrice, SignalType exitDirection,
                              ExitType exitType, Integer closePercent, LocalDateTime exitTime) {
        MemoryPosition pos = null;
        String sideName = null;

        if (exitDirection == SignalType.CLOSE_LONG && longPosition != null) {
            pos = longPosition;
            sideName = "LONG";
        } else if (exitDirection == SignalType.CLOSE_SHORT && shortPosition != null) {
            pos = shortPosition;
            sideName = "SHORT";
        }
        if (pos == null) return;

        if (closePercent == null || closePercent >= 100) {
            closePositionFull(pos, index, exitPrice, exitDirection, exitType, sideName, exitTime);
        } else {
            closePositionPartial(pos, index, exitPrice, exitDirection, exitType, sideName, closePercent, exitTime);
        }
    }

    /**
     * 全量平仓 — 关闭整个持仓，生成快照 + 聚合 TradeRecord
     * <p>
     * 顺序很重要：
     * 1. 先生成快照（此时 entries 完整）
     * 2. 执行 FIFO 扣减（修改当前持仓）
     * 3. 计算总盈亏
     * 4. 标记状态
     * 5. 将 totalPnl 和 status 同步到快照
     * 6. 存入 closedPositions
     */
    private void closePositionFull(MemoryPosition pos, int index, BigDecimal exitPrice,
                                   SignalType exitDirection, ExitType exitType, String sideName,
                                   LocalDateTime exitTime) {

        boolean isLong = (exitDirection == SignalType.CLOSE_LONG);
        BigDecimal actualExitPrice = applySlippage(exitPrice, !isLong);
        BigDecimal totalQty = pos.getTotalQuantity();

        // ================================================================
        // 第 1 步：执行 FIFO 扣减（entries 中会包含本次平仓的 closedDetails）
        // ================================================================
        String closeReason = exitType != null ? exitType.getDescription() : "AUTO";
        OrderSideEnum orderSide = isLong ? OrderSideEnum.BUY : OrderSideEnum.SELL;

        List<ClosedEntryDetail> details = pos.reduceQuantity(
                totalQty,
                actualExitPrice,
                (entryPrice, closeQty) -> ProfitCalcUtils.getProfitByVolume(
                        Exchange.OKX, orderSide,
                        entryPrice, closeQty,
                        actualExitPrice, contractSpec),
                closeReason,
                exitTime
        );

        // ================================================================
        // 🔥 第 2 步：扣减后生成快照（此时 entries 已包含本次全平的 closedDetails）
        // ================================================================
        MemoryPosition snapshot = pos.toClosedSnapshot(
                actualExitPrice,
                null,  // totalPnl 稍后计算完再设置
                exitType,
                exitTime
        );
        // 🔥 reduceQuantity 后 pos.totalQuantity=0，快照的 totalQuantity 需使用原始总量
        snapshot.setTotalQuantity(totalQty);

        // ================================================================
        // 第 3 步：计算总盈亏
        // ================================================================
        // 从本次平仓新增的 closedDetails 计算新PnL（用于 realizedPnl 更新，避免与之前部分平仓重复累加）
        BigDecimal newPnl = details.stream()
                .map(ClosedEntryDetail::getPnl)
                .filter(pnl -> pnl != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 从所有 entries 的 closedDetails 汇总（包含之前部分平仓的PnL，用于 TradeRecord/快照）
        BigDecimal totalPnl = pos.getEntries().stream()
                .flatMap(e -> e.getClosedDetails().stream())
                .map(ClosedEntryDetail::getPnl)
                .filter(pnl -> pnl != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ================================================================
        // 第 4 步：标记当前持仓的状态（止盈/止损）
        // ================================================================
        pos.markClosed(exitType, totalPnl);

        // ================================================================
        // 🔥 第 5 步：将 totalPnl 和 status 同步到快照
        // ================================================================
        snapshot.setTotalPnl(totalPnl);
        snapshot.setStatus(pos.getStatus());

        // ================================================================
        // 第 6 步：更新资金
        // ================================================================
        BigDecimal margin = actualExitPrice.multiply(totalQty)
                .multiply(contractSpec.getContractSize())
                .multiply(contractSpec.getContractMult())
                .divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);

        BigDecimal closeFee = costModel.calcCloseCost(actualExitPrice, totalQty, contractSpec);

        BigDecimal equityBefore = getEquity(exitPrice);
        realizedPnl = realizedPnl.add(newPnl);
        availableBalance = availableBalance.add(margin).add(newPnl).subtract(closeFee);
        totalCommissionPaid = totalCommissionPaid.add(closeFee);
        BigDecimal equityAfter = getEquity(exitPrice);

        // ================================================================
        // 第 7 步：记录 ActionRecord
        // ================================================================
        actionRecords.add(ActionRecord.builder()
                .barIndex(index)
                .action(exitType == null ? "FORCE_CLOSE" : "EXIT_SIGNAL")
                .price(actualExitPrice)
                .quantity(totalQty)
                .pnl(totalPnl)
                .equityBefore(equityBefore)
                .equityAfter(equityAfter)
                .exitType(exitType)
                .positionId(pos.getPositionId())
                .closedDetails(details)
                .build());

        // ================================================================
        // 第 8 步：生成 TradeRecord（报表聚合）
        // ================================================================
        BacktestResult.TradeRecord tradeRecord = buildTradeRecord(
                pos, actualExitPrice, exitPrice,
                totalQty, totalPnl, exitType, index);
        trades.add(tradeRecord);

        // ================================================================
        // 第 9 步：🔥 存入已平仓快照列表（此时 snapshot.entries 是完整的）
        // ================================================================
        closedPositions.add(snapshot);

        // ================================================================
        // 第 10 步：清空当前持仓指针
        // ================================================================
        if (isLong) {
            longPosition = null;
        } else {
            shortPosition = null;
        }
    }

    /**
     * 部分平仓 — 按 closePercent FIFO 平掉最早的开仓明细，保留剩余仓位。
     */
    private void closePositionPartial(MemoryPosition pos, int index, BigDecimal exitPrice,
                                      SignalType exitDirection, ExitType exitType,
                                      String sideName, int closePercent, LocalDateTime exitTime) {

        BigDecimal totalQty = pos.getTotalQuantity();
        BigDecimal closeQty = totalQty.multiply(BigDecimal.valueOf(closePercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        if (closeQty.signum() <= 0) return;

        if (closeQty.compareTo(totalQty) >= 0) {
            closePositionFull(pos, index, exitPrice, exitDirection, exitType, sideName, exitTime);
            return;
        }

        boolean isLong = (exitDirection == SignalType.CLOSE_LONG);
        BigDecimal actualExitPrice = applySlippage(exitPrice, !isLong);
        OrderSideEnum orderSide = isLong ? OrderSideEnum.BUY : OrderSideEnum.SELL;

        // 🔥 方案 A：传入 exitPrice + pnlCalc + closeReason
        String closeReason = exitType != null ? exitType.getDescription() : "AUTO";
        List<ClosedEntryDetail> details = pos.reduceQuantity(
                closeQty,
                actualExitPrice,
                (entryPrice, qty) -> ProfitCalcUtils.getProfitByVolume(
                        Exchange.OKX, orderSide,
                        entryPrice, qty,
                        actualExitPrice, contractSpec),
                closeReason,
                exitTime
        );

        // 计算总盈亏
        BigDecimal totalPnl = details.stream()
                .map(ClosedEntryDetail::getPnl)
                .filter(pnl -> pnl != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 释放保证金
        BigDecimal margin = actualExitPrice.multiply(closeQty)
                .multiply(contractSpec.getContractSize())
                .multiply(contractSpec.getContractMult())
                .divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);

        BigDecimal closeFee = costModel.calcCloseCost(actualExitPrice, closeQty, contractSpec);

        BigDecimal equityBefore = getEquity(exitPrice);
        realizedPnl = realizedPnl.add(totalPnl);
        availableBalance = availableBalance.add(margin).add(totalPnl).subtract(closeFee);
        totalCommissionPaid = totalCommissionPaid.add(closeFee);
        BigDecimal equityAfter = getEquity(exitPrice);

        actionRecords.add(ActionRecord.builder()
                .barIndex(index)
                .action("EXIT_PARTIAL")
                .price(actualExitPrice)
                .quantity(closeQty)
                .pnl(totalPnl)
                .equityBefore(equityBefore)
                .equityAfter(equityAfter)
                .exitType(exitType)
                .positionId(pos.getPositionId())
                .closedDetails(details)
                .build());

        // 部分平仓不生成 TradeRecord（仓位未归零）
        log.debug("部分平仓: posId={}, closeQty={}, remainingQty={}, pnl={}",
                pos.getPositionId(), closeQty, pos.getTotalQuantity(), totalPnl);
    }

    /**
     * 逐笔止盈/止损。
     * <p>
     * 使用 K 线极值检测 TP/SL 触发，避免收盘价漏检：
     * LONG 止盈用 highPrice，止损用 lowPrice；
     * SHORT 止盈用 lowPrice，止损用 highPrice。
     * 实际成交价使用 closePrice（更贴近真实撮合）。
     */
    public boolean closeEntriesByTpSl(int index, BigDecimal highPrice, BigDecimal lowPrice,
                                      BigDecimal closePrice, LocalDateTime exitTime) {

        String targetTime = "2025-09-12 22:25:00";
        String currentTime = DateUtil.formatDateTime(exitTime.minusHours(8));
        boolean hitTarget = targetTime.equals(currentTime);
        if(hitTarget){
            System.out.println("here");
        }
        boolean anyClosed = false;
        if (longPosition != null) {
            anyClosed |= closeHitEntries(longPosition, SignalType.CLOSE_LONG, index,
                    highPrice, lowPrice, closePrice, exitTime);
        }
        if (shortPosition != null) {
            anyClosed |= closeHitEntries(shortPosition, SignalType.CLOSE_SHORT, index,
                    highPrice, lowPrice, closePrice, exitTime);
        }
        return anyClosed;
    }

    /**
     * 关闭触及 TP/SL 的条目（逐 entry 精准扣减，避免平错仓位）。
     * <p>
     * 如果触及 TP/SL 导致仓位完全平仓，会生成快照存入 closedPositions。
     *
     * @param highPrice  K线最高价（LONG止盈/SHORT止损检测用）
     * @param lowPrice   K线最低价（LONG止损/SHORT止盈检测用）
     * @param closePrice K线收盘价（实际成交价，更贴近真实撮合）
     */
    private boolean closeHitEntries(MemoryPosition pos, SignalType exitDirection, int index,
                                    BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice,
                                    LocalDateTime exitTime) {
        boolean isLong = (exitDirection == SignalType.CLOSE_LONG);
        // LONG: 止盈需涨价→用high, 止损需跌价→用low
        // SHORT: 止盈需跌价→用low, 止损需涨价→用high
        BigDecimal tpCheckPrice = isLong ? highPrice : lowPrice;
        BigDecimal slCheckPrice = isLong ? lowPrice : highPrice;

        // 收集触及 TP/SL 的 entry（跳过已平仓的 qty=0 entry）
        List<EntryRecord> hitEntries = new ArrayList<>();
        for (EntryRecord entry : pos.getEntries()) {
            if (!entry.hasRemaining()) continue;

            boolean tpHit = isHitTakeProfit(entry, exitDirection, tpCheckPrice);
            boolean slHit = isHitStopLoss(entry, exitDirection, slCheckPrice);
            log.debug("[逐笔TP/SL] index={}, dir={}, entryId={}, entryTp={}, entrySl={}, high={}, low={}, tpCheckPrice={}, slCheckPrice={}, tpHit={}, slHit={}, qty={}",
                    index, exitDirection, entry.getEntryId(),
                    entry.getTakeProfitPrice(), entry.getStopLossPrice(),
                    highPrice, lowPrice, tpCheckPrice, slCheckPrice, tpHit, slHit, entry.getQuantity());

            if (tpHit || slHit) {
                hitEntries.add(entry);
            }
        }
        if (hitEntries.isEmpty()) return false;

        OrderSideEnum orderSide = isLong ? OrderSideEnum.BUY : OrderSideEnum.SELL;

        // 判断退出类型（取第一个命中的 entry 的类型），并记录该 entry 用于确定成交价
        ExitType exitType = ExitType.UNKNOWN;
        EntryRecord exitPriceEntry = null;
        for (EntryRecord entry : hitEntries) {
            if (isHitTakeProfit(entry, exitDirection, tpCheckPrice)) {
                exitType = ExitType.BATCH_TAKE_PROFIT;
                exitPriceEntry = entry;
                break;
            } else if (isHitStopLoss(entry, exitDirection, slCheckPrice)) {
                exitType = ExitType.BATCH_STOP_LOSS;
                exitPriceEntry = entry;
                break;
            }
        }
        String closeReason = exitType != null ? exitType.getDescription() : "AUTO";

        // 分批止损按止损价成交，分批止盈按止盈价成交；滑点暂未实现，其余按收盘价成交
        BigDecimal actualExitPrice;
        if (exitType == ExitType.BATCH_STOP_LOSS && exitPriceEntry != null
                && exitPriceEntry.getStopLossPrice() != null) {
            actualExitPrice = exitPriceEntry.getStopLossPrice();
        } else if (exitType == ExitType.BATCH_TAKE_PROFIT && exitPriceEntry != null
                && exitPriceEntry.getTakeProfitPrice() != null) {
            actualExitPrice = exitPriceEntry.getTakeProfitPrice();
        } else {
            actualExitPrice = applySlippage(closePrice, !isLong);
        }

        BigDecimal actualExitPriceFinal = actualExitPrice;

        // ================================================================
        // 逐 entry 精准扣减（不使用 reduceQuantity 避免 FIFO 平错仓位）
        // ================================================================
        List<ClosedEntryDetail> allDetails = new ArrayList<>();
        BigDecimal totalCloseQty = BigDecimal.ZERO;
        BigDecimal newPnl = BigDecimal.ZERO;

        for (EntryRecord entry : hitEntries) {
            BigDecimal entryQty = entry.getQuantity();
            List<ClosedEntryDetail> details = pos.reduceEntry(
                    entry.getEntryId(),
                    actualExitPriceFinal,
                    (entryPrice, cq) -> ProfitCalcUtils.getProfitByVolume(
                            Exchange.OKX, orderSide, entryPrice, cq, actualExitPriceFinal, contractSpec),
                    closeReason,
                    exitTime
            );
            allDetails.addAll(details);
            totalCloseQty = totalCloseQty.add(entryQty);
            BigDecimal entryPnl = details.stream()
                    .map(ClosedEntryDetail::getPnl).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            newPnl = newPnl.add(entryPnl);
        }

        // ================================================================
        // 生成快照（扣减后 entries 已包含本次平仓的 closedDetails）
        // ================================================================
        MemoryPosition snapshot = pos.toClosedSnapshot(
                actualExitPriceFinal,
                null,
                exitType,
                exitTime
        );
        snapshot.setTotalQuantity(pos.getTotalEntryQuantity());

        // ================================================================
        // 计算总盈亏（从所有 entries 的 closedDetails 汇总）
        // ================================================================
        BigDecimal totalPnl = pos.getEntries().stream()
                .flatMap(e -> e.getClosedDetails().stream())
                .map(ClosedEntryDetail::getPnl)
                .filter(pnl -> pnl != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        pos.markClosed(exitType, totalPnl);
        snapshot.setTotalPnl(totalPnl);
        snapshot.setStatus(pos.getStatus());

        // ================================================================
        // 更新资金
        // ================================================================
        BigDecimal margin = actualExitPrice.multiply(totalCloseQty)
                .multiply(contractSpec.getContractSize())
                .multiply(contractSpec.getContractMult())
                .divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);

        BigDecimal closeFee = costModel.calcCloseCost(actualExitPrice, totalCloseQty, contractSpec);

        BigDecimal equityBefore = getEquity(closePrice);
        realizedPnl = realizedPnl.add(newPnl);
        availableBalance = availableBalance.add(margin).add(newPnl).subtract(closeFee);
        totalCommissionPaid = totalCommissionPaid.add(closeFee);
        BigDecimal equityAfter = getEquity(closePrice);

        actionRecords.add(ActionRecord.builder()
                .barIndex(index)
                .action(exitType == ExitType.TAKE_PROFIT ? "EXIT_TP" : "EXIT_SL")
                .price(actualExitPrice)
                .quantity(totalCloseQty)
                .pnl(totalPnl)
                .equityBefore(equityBefore)
                .equityAfter(equityAfter)
                .exitType(exitType)
                .positionId(pos.getPositionId())
                .closedDetails(allDetails)
                .build());

        BacktestResult.TradeRecord tradeRecord = buildTradeRecord(
                pos, actualExitPrice, actualExitPrice,
                totalCloseQty, totalPnl, exitType, index);
        trades.add(tradeRecord);

        // ================================================================
        // 全部平完 → 存入快照并清空指针
        // ================================================================
        if (pos.getTotalQuantity().signum() == 0) {
            closedPositions.add(snapshot);
            if (exitDirection == SignalType.CLOSE_LONG) {
                longPosition = null;
            } else {
                shortPosition = null;
            }
        } else {
            log.debug("逐笔止盈止损(部分): posId={}, 平仓数量={}, 剩余数量={}, exitType={}",
                    pos.getPositionId(), totalCloseQty, pos.getTotalQuantity(), exitType);
        }

        return true;
    }

    /** 价格比较精度（2位小数），避免 double→BigDecimal 浮点精度问题 */
    private static final int PRICE_SCALE = 2;

    private static boolean isHitTakeProfit(EntryRecord entry, SignalType exitDirection, BigDecimal currentPrice) {
        if (entry.getTakeProfitPrice() == null) return false;
        BigDecimal price = currentPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal tp = entry.getTakeProfitPrice().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        return exitDirection == SignalType.CLOSE_LONG
                ? price.compareTo(tp) >= 0
                : price.compareTo(tp) <= 0;
    }

    private static boolean isHitStopLoss(EntryRecord entry, SignalType exitDirection, BigDecimal currentPrice) {
        if (entry.getStopLossPrice() == null) return false;
        BigDecimal price = currentPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal sl = entry.getStopLossPrice().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        return exitDirection == SignalType.CLOSE_LONG
                ? price.compareTo(sl) <= 0
                : price.compareTo(sl) >= 0;
    }

    // ==================== 辅助方法 ====================

    private BigDecimal applySlippage(BigDecimal price, boolean isBuy) {
        if (slippage == null || slippage.compareTo(BigDecimal.ZERO) == 0) {
            return price;
        }
        if (isBuy) {
            return price.multiply(BigDecimal.ONE.add(slippage));
        } else {
            return price.multiply(BigDecimal.ONE.subtract(slippage));
        }
    }

    private EntryRecord findEntryById(String entryId) {
        return entryRecords.stream()
                .filter(e -> e.getEntryId().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("EntryRecord not found: " + entryId));
    }

    /**
     * 🔥 方案 A：buildTradeRecord 增加 exitIndex 参数
     */
    private BacktestResult.TradeRecord buildTradeRecord(MemoryPosition pos, BigDecimal actualExitPrice,
                                                        BigDecimal exitPrice, BigDecimal totalQty,
                                                        BigDecimal totalPnl, ExitType exitType,
                                                        int exitIndex) {

        List<EntryRecord> positionEntries = entryRecords.stream()
                .filter(e -> e.getPositionId().equals(pos.getPositionId()))
                .collect(Collectors.toList());

        BigDecimal totalEntryVolume = positionEntries.stream()
                .map(EntryRecord::getInitialQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal weightedEntryPrice = positionEntries.stream()
                .map(e -> e.getPrice().multiply(e.getInitialQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(totalEntryVolume, 8, RoundingMode.HALF_UP);

        LocalDateTime entryTime = positionEntries.stream()
                .map(EntryRecord::getTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        String tradeId = "TRADE_" + tradeIdCounter.getAndIncrement();
        BigDecimal totalFee = pos.getOpenFee().add(
                costModel.calcCloseCost(actualExitPrice, totalQty, contractSpec)
        );

        return new BacktestResult.TradeRecord(
                tradeId,
                pos.getPositionId(),
                null,
                pos.getEntryBarIndex(),
                exitIndex,  // 🔥 使用传入的 exitIndex
                pos.getDirection().name(),
                weightedEntryPrice,
                exitPrice,
                totalEntryVolume,
                totalPnl,
                totalFee,
                exitType
        );
    }

    // ==================== 权益曲线采样 ====================

    public void sampleEquity(int index, long timestamp, BigDecimal currentPrice) {
        BigDecimal currentEquity = getEquity(currentPrice);
        equityCurve.add(new BacktestResult.EquityPoint(index, timestamp, currentEquity));
    }

    // ==================== 结果获取 ====================

    public List<BacktestResult.TradeRecord> getTrades() {
        return new ArrayList<>(trades);
    }

    public List<BacktestResult.EquityPoint> getEquityCurve() {
        return new ArrayList<>(equityCurve);
    }

    public List<EntryRecord> getEntryRecords() {
        return new ArrayList<>(entryRecords);
    }

    public List<ActionRecord> getActionRecords() {
        return new ArrayList<>(actionRecords);
    }

    public BigDecimal getTotalCommissionPaid() {
        return totalCommissionPaid;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }
}