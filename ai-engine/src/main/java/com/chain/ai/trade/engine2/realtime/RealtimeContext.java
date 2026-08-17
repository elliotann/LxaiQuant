package com.chain.ai.trade.engine2.realtime;

import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.common.utils.ProfitCalcUtils;
import com.chain.ai.trade.engine2.backtest.BacktestResult;
import com.chain.ai.trade.engine2.backtest.model.*;
import com.chain.ai.trade.engine2.core.context.TradingContext;
import com.chain.ai.trade.common.utils.PricePrecisionUtils;
import com.chain.ai.trade.engine2.core.cost.CostModel;
import com.chain.ai.trade.engine2.core.execution.*;
import com.chain.ai.trade.engine2.persistence.RealtimeGateway;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.order.entity.dos.TradePosition;
import com.chain.ai.trade.order.entity.dos.TradeEntry;
import com.chain.ai.trade.order.entity.SnowFlake;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实盘/模拟上下文 — 内存持仓 + 委托 ExecutionHandler 执行订单。
 */
@Slf4j
public class RealtimeContext implements TradingContext {

    /** 交易配置（包含 userId、accountId 等身份信息） */
    private final RealtimeConfig config;
    private final String symbol;
    private BigDecimal initialCapital;
    private final ExecutionHandler executionHandler;
    private final RealtimeGateway gateway;
    private final ContractSpec contractSpec;
    private final CostModel costModel;
    private final int leverage;
    private final BigDecimal slippage;

    private MemoryPosition longPosition;
    private MemoryPosition shortPosition;
    private BigDecimal availableBalance;
    private BigDecimal realizedPnl = BigDecimal.ZERO;
    private BigDecimal totalCommissionPaid = BigDecimal.ZERO;

    private final List<BacktestResult.EquityPoint> equityCurve = new ArrayList<>();
    private final List<MemoryPosition> closedPositions = new ArrayList<>();
    private final List<EntryRecord> entryRecords = new ArrayList<>();
    private final List<ActionRecord> actionRecords = new ArrayList<>();
    private final List<BacktestResult.TradeRecord> trades = new ArrayList<>();
    private final AtomicInteger tradeIdCounter = new AtomicInteger(1);

    public RealtimeContext(RealtimeConfig config, ExecutionHandler executionHandler, RealtimeGateway gateway) {
        this.config = config;
        this.symbol = config.getSymbol();
        this.initialCapital = config.getInitialCapital();
        this.availableBalance = config.getInitialCapital();
        this.executionHandler = executionHandler;
        this.gateway = gateway;
        this.contractSpec = config.getContractSpec();
        this.costModel = config.getCostModel();
        this.leverage = config.getLeverage();
        this.slippage = config.getSlippage();
    }

    // ==================== 持仓恢复 ====================

    public void recoverFromOrders(List<TradePosition> openOrders, Map<String, List<TradeEntry>> itemsMap) {
        if (openOrders == null || openOrders.isEmpty()) return;
        for (TradePosition order : openOrders) {
            SignalType dir = order.getOrderSideEnum() == OrderSideEnum.BUY ? SignalType.LONG : SignalType.SHORT;
            MemoryPosition pos = MemoryPosition.builder()
                    .positionId(order.getPositionId())
                    .symbol(order.getSymbol())
                    .direction(dir)
                    .avgPrice(order.getBuyAvgPrice())
                    .totalQuantity(order.getVolume())
                    .totalEntryQuantity(order.getVolume())
                    .entryBarIndex(0)
                    .stopLossPrice(order.getLossPrice())
                    .takeProfitPrice(order.getGainPrice())
                    .openFee(BigDecimal.ZERO)
                    .build();

            List<TradeEntry> items = itemsMap.get(order.getPositionId());
            if (items != null) {
                for (TradeEntry item : items) {
                    EntryRecord entry = EntryRecord.builder()
                            .entryId(item.getEntrySn())
                            .positionId(order.getPositionId())
                            .symbol(item.getSymbol())
                            .price(item.getBuyPrice())
                            .initialQuantity(item.getVolume())
                            .quantity(item.getVolume())
                            .fee(item.getCharge()==null?BigDecimal.ZERO:item.getCharge())
                            .barIndex(0)
                            .side(dir.name())
                            .takeProfitPrice(item.getGainPrice())
                            .stopLossPrice(item.getLossPrice())
                            .build();
                    pos.getEntries().add(entry);
                }
            }

            if (dir == SignalType.LONG) longPosition = pos;
            else shortPosition = pos;
        }
        log.info("持仓恢复完成: symbol={}, longQty={}, shortQty={}",
                symbol,
                longPosition != null ? longPosition.getTotalQuantity() : BigDecimal.ZERO,
                shortPosition != null ? shortPosition.getTotalQuantity() : BigDecimal.ZERO);
    }

    // ==================== 开仓 ====================

    public OrderIntentResult openPosition(SignalType side, BigDecimal price, BigDecimal quantity,
                                           BigDecimal takeProfitPrice, BigDecimal stopLossPrice,
                                           LocalDateTime barTime, Long signalId, int entryBarIndex) {
        // 开仓时生成仓位ID，全程不变
        String posId = SnowFlake.getIdStr();
        OrderIntent intent = buildEntryIntent(side, price, quantity, takeProfitPrice, stopLossPrice,
                barTime, posId, signalId, true);
        OrderIntentResult result = executionHandler.submitOrder(intent, this);

        if (result.isFilled()) {
            onEntryFilled(intent, result.getFillPrice(), result.getFilledQuantity(), barTime, entryBarIndex);
        } else if (result.isPending()) {
            gateway.onOrderSubmitted(intent);
        }
        return result;
    }

    public OrderIntentResult addToPosition(SignalType side, BigDecimal price, BigDecimal quantity,
                                            BigDecimal takeProfitPrice, BigDecimal stopLossPrice,
                                            LocalDateTime barTime, int entryBarIndex, Long signalId) {
        MemoryPosition pos = side == SignalType.LONG ? longPosition : shortPosition;
        if (pos == null) return OrderIntentResult.rejected("no_position", "无持仓");

        OrderIntent intent = buildEntryIntent(side, price, quantity, takeProfitPrice, stopLossPrice,
                barTime, pos.getPositionId(), signalId, false);
        OrderIntentResult result = executionHandler.submitOrder(intent, this);

        if (result.isFilled()) {
            onEntryFilled(intent, result.getFillPrice(), result.getFilledQuantity(), barTime, entryBarIndex);
        } else if (result.isPending()) {
            gateway.onOrderSubmitted(intent);
        }
        return result;
    }

    private OrderIntent buildEntryIntent(SignalType side, BigDecimal price, BigDecimal quantity,
                                          BigDecimal tp, BigDecimal sl, LocalDateTime barTime, String posId,
                                          Long signalId, boolean isNewPosition) {
        return OrderIntent.builder()
                .clientOrderId(UUID.randomUUID().toString())
                .symbol(symbol)
                .side(side)
                .orderType(OrderType.MARKET)
                .price(PricePrecisionUtils.normalizePrice(symbol, applySlippage(price, side)))
                .quantity(quantity)
                .leverage(leverage)
                .takeProfitPrice(tp)
                .stopLossPrice(sl)
                .positionId(posId)
                .isNewPosition(isNewPosition)
                .barTime(barTime)
                .signalId(signalId)
                .build();
    }

    // ==================== 平仓 ====================

    public void closePosition(int barIndex, BigDecimal price, SignalType direction,
                               ExitType exitType, Integer closePercent, LocalDateTime barTime) {
        MemoryPosition pos = direction == SignalType.CLOSE_LONG ? longPosition : shortPosition;
        if (pos == null) return;

        BigDecimal closeQty = (closePercent != null && closePercent < 100)
                ? pos.getTotalQuantity().multiply(BigDecimal.valueOf(closePercent))
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                : pos.getTotalQuantity();
        if (closeQty.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal exitPrice = applySlippage(price,
                direction == SignalType.CLOSE_LONG ? SignalType.SHORT : SignalType.LONG);

        // ★ 调用交易所平仓
        OrderIntent closeIntent = OrderIntent.builder()
                .positionId(pos.getPositionId())
                .symbol(symbol)
                .quantity(closeQty)
                .price(PricePrecisionUtils.normalizePrice(symbol, exitPrice))
                .barTime(barTime)
                .exitType(exitType)
                .build();
        CloseOrderResult closeResult = executionHandler.closeOrder(closeIntent, this);
        if (!closeResult.isSuccess()) {
            log.warn("交易所平仓失败: posId={}, qty={}, price={}", pos.getPositionId(), closeQty, exitPrice);
            return;
        }

        OrderSideEnum orderSide = pos.getDirection() == SignalType.LONG ? OrderSideEnum.BUY : OrderSideEnum.SELL;
        BigDecimal fee = costModel.calcCloseCost(exitPrice, closeQty, contractSpec);

        List<ClosedEntryDetail> details = pos.reduceQuantity(closeQty, exitPrice,
                (ep, cq) -> ProfitCalcUtils.getProfitByVolume(
                        Exchange.OKX, orderSide, ep, cq, exitPrice, contractSpec),
                exitType.name(), barTime);

        BigDecimal pnl = details.stream()
                .map(ClosedEntryDetail::getPnl).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        realizedPnl = realizedPnl.add(pnl);
        totalCommissionPaid = totalCommissionPaid.add(fee);

        if (pos.getTotalQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            MemoryPosition snapshot = pos.toClosedSnapshot(exitPrice, pnl.subtract(fee), exitType, barTime);
            closedPositions.add(snapshot);

            trades.add(new BacktestResult.TradeRecord(
                    "TRADE_" + tradeIdCounter.getAndIncrement(),
                    pos.getPositionId(), null,
                    pos.getEntryBarIndex(), barIndex,
                    pos.getDirection().name(), pos.getAvgPrice(),
                    exitPrice, pos.getTotalEntryQuantity(),
                    pnl, pos.getOpenFee().add(fee), exitType));

            if (direction == SignalType.CLOSE_LONG) longPosition = null;
            else shortPosition = null;
        }

        gateway.onOrderClosed(symbol, pos.getPositionId(), exitPrice, closeQty, pnl, fee, exitType, barTime);
    }

    /**
     * 逐笔止盈/止损。
     * <p>
     * 使用 K 线极值检测 TP/SL 触发：LONG 止盈用 highPrice，止损用 lowPrice；
     * SHORT 止盈用 lowPrice，止损用 highPrice。实际成交价使用 closePrice。
     */
    public boolean closeEntriesByTpSl(int barIndex, BigDecimal highPrice, BigDecimal lowPrice,
                                      BigDecimal closePrice, LocalDateTime barTime) {
        boolean hit = false;
        hit |= checkTpSlForSide(longPosition, barIndex, barTime, highPrice, lowPrice, closePrice, true);
        hit |= checkTpSlForSide(shortPosition, barIndex, barTime, highPrice, lowPrice, closePrice, false);
        return hit;
    }

    private boolean checkTpSlForSide(MemoryPosition pos, int barIndex, LocalDateTime barTime,
                                      BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice,
                                      boolean isLong) {
        if (pos == null || pos.getTotalQuantity().compareTo(BigDecimal.ZERO) <= 0) return false;
        // LONG: 止盈需涨价→用high, 止损需跌价→用low
        // SHORT: 止盈需跌价→用low, 止损需涨价→用high
        BigDecimal tpCheckPrice = isLong ? highPrice : lowPrice;
        BigDecimal slCheckPrice = isLong ? lowPrice : highPrice;

        boolean hit = false;
        List<EntryRecord> entries = new ArrayList<>(pos.getEntries());
        for (EntryRecord entry : entries) {
            if (entry.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            boolean tpHit = isLong
                    ? entry.getTakeProfitPrice() != null && tpCheckPrice.compareTo(entry.getTakeProfitPrice()) >= 0
                    : entry.getTakeProfitPrice() != null && tpCheckPrice.compareTo(entry.getTakeProfitPrice()) <= 0;
            boolean slHit = isLong
                    ? entry.getStopLossPrice() != null && slCheckPrice.compareTo(entry.getStopLossPrice()) <= 0
                    : entry.getStopLossPrice() != null && slCheckPrice.compareTo(entry.getStopLossPrice()) >= 0;

            if (tpHit || slHit) {
                ExitType exitType = tpHit ? ExitType.TAKE_PROFIT : ExitType.STOP_LOSS;
                BigDecimal closeQty = entry.getQuantity();

                // ★ 调用交易所平仓（使用收盘价作为实际成交价基准）
                BigDecimal exitPrice = applySlippage(closePrice,
                        isLong ? SignalType.SHORT : SignalType.LONG);
                OrderIntent closeIntent = OrderIntent.builder()
                        .positionId(pos.getPositionId())
                        .symbol(symbol)
                        .quantity(closeQty)
                        .price(PricePrecisionUtils.normalizePrice(symbol, exitPrice))
                        .barTime(barTime)
                        .exitType(exitType)
                        .build();
                CloseOrderResult closeResult = executionHandler.closeOrder(closeIntent, this);
                if (!closeResult.isSuccess()) {
                    log.warn("TP/SL 交易所平仓失败: posId={}, qty={}, price={}",
                            pos.getPositionId(), closeQty, exitPrice);
                    continue;
                }

                OrderSideEnum orderSide = isLong ? OrderSideEnum.BUY : OrderSideEnum.SELL;
                // 按 entryId 精准扣减，避免 FIFO 平错仓位（使用滑点调整后的 exitPrice）
                List<ClosedEntryDetail> details = pos.reduceEntry(entry.getEntryId(), exitPrice,
                        (ep, cq) -> ProfitCalcUtils.getProfitByVolume(
                                Exchange.OKX, orderSide, ep, cq, exitPrice, contractSpec),
                        exitType.name(), barTime);
                BigDecimal pnl = details.stream()
                        .map(ClosedEntryDetail::getPnl).filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal fee = costModel.calcCloseCost(exitPrice, entry.getQuantity(), contractSpec);
                realizedPnl = realizedPnl.add(pnl);
                totalCommissionPaid = totalCommissionPaid.add(fee);
                hit = true;

                if (pos.getTotalQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    MemoryPosition snapshot = pos.toClosedSnapshot(exitPrice, pnl.subtract(fee), exitType, barTime);
                    closedPositions.add(snapshot);
                    trades.add(new BacktestResult.TradeRecord(
                            "TRADE_" + tradeIdCounter.getAndIncrement(),
                            pos.getPositionId(), entry.getEntryId(),
                            pos.getEntryBarIndex(), barIndex,
                            pos.getDirection().name(), pos.getAvgPrice(),
                            exitPrice, pos.getTotalEntryQuantity(),
                            pnl, pos.getOpenFee().add(fee), exitType));

                    gateway.onOrderClosed(symbol, pos.getPositionId(), exitPrice,
                            pos.getTotalEntryQuantity(), pnl, fee, exitType, barTime);

                    if (isLong) longPosition = null;
                    else shortPosition = null;
                    break;
                }
            }
        }
        return hit;
    }

    // ==================== 成交回调 ====================

    public void onEntryFilled(OrderIntent intent, BigDecimal fillPrice, BigDecimal filledQuantity,
                               LocalDateTime barTime, int entryBarIndex) {
        BigDecimal margin = filledQuantity.multiply(fillPrice).multiply(contractSpec.getContractSize())
                .multiply(contractSpec.getContractMult())
                .divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);
        BigDecimal fee = costModel.calcOpenCost(fillPrice, filledQuantity, contractSpec);
        availableBalance = availableBalance.subtract(margin).subtract(fee);
        totalCommissionPaid = totalCommissionPaid.add(fee);

        if (!intent.isNewPosition()) {
            // 加仓
            MemoryPosition pos = intent.getSide() == SignalType.LONG ? longPosition : shortPosition;
            if (pos != null) {
                EntryRecord entry = EntryRecord.builder()
                        .entryId(intent.getClientOrderId())
                        .positionId(pos.getPositionId())
                        .symbol(symbol).price(fillPrice)
                        .initialQuantity(filledQuantity).quantity(filledQuantity)
                        .fee(fee).barIndex(entryBarIndex).side(pos.getDirection().name())
                        .takeProfitPrice(intent.getTakeProfitPrice())
                        .stopLossPrice(intent.getStopLossPrice())
                        .signalId(intent.getSignalId()).build();
                pos.getEntries().add(entry);
                entryRecords.add(entry);
            }
        } else {
            // 新开仓：positionId 在 openPosition 时已生成，全程一致
            String positionId = intent.getPositionId();
            MemoryPosition pos = MemoryPosition.builder()
                    .positionId(positionId)
                    .symbol(symbol)
                    .direction(intent.getSide())
                    .avgPrice(fillPrice)
                    .totalQuantity(filledQuantity)
                    .totalEntryQuantity(filledQuantity)
                    .entryBarIndex(entryBarIndex)
                    .entryTime(barTime)
                    .openFee(fee)
                    .stopLossPrice(intent.getStopLossPrice())
                    .takeProfitPrice(intent.getTakeProfitPrice())
                    .build();

            EntryRecord entry = EntryRecord.builder()
                    .entryId(intent.getClientOrderId())
                    .positionId(positionId)
                    .symbol(symbol).price(fillPrice)
                    .initialQuantity(filledQuantity).quantity(filledQuantity)
                    .fee(fee).barIndex(entryBarIndex).side(intent.getSide().name())
                    .takeProfitPrice(intent.getTakeProfitPrice())
                    .stopLossPrice(intent.getStopLossPrice())
                    .signalId(intent.getSignalId()).build();
            pos.getEntries().add(entry);
            entryRecords.add(entry);

            if (intent.getSide() == SignalType.LONG) longPosition = pos;
            else shortPosition = pos;
            log.info("开仓: posId={}, side={}, price={}, qty={}", positionId, intent.getSide(), fillPrice, filledQuantity);
        }

        gateway.onOrderFilled(intent, fillPrice, filledQuantity);
        gateway.onPositionUpdated(intent.getSide() == SignalType.LONG ? longPosition : shortPosition);
    }

    // ==================== 权益采样 ====================

    public void sampleEquity(int index, long timestamp, BigDecimal currentPrice) {
        equityCurve.add(new BacktestResult.EquityPoint(index, timestamp, getEquity(currentPrice)));
    }

    // ==================== TradingContext 接口 ====================

    @Override public boolean hasPosition() { return hasLongPosition() || hasShortPosition(); }
    @Override public boolean hasLongPosition() { return longPosition != null && longPosition.getTotalQuantity().signum() > 0; }
    @Override public boolean hasShortPosition() { return shortPosition != null && shortPosition.getTotalQuantity().signum() > 0; }

    /**
     * 清除指定方向的内存持仓（用于 DB 同步发现幽灵仓位时清理）
     */
    public void clearPosition(SignalType side) {
        if (side == SignalType.LONG) {
            log.warn("清除多头内存持仓: posId={}", longPosition != null ? longPosition.getPositionId() : "null");
            longPosition = null;
        } else if (side == SignalType.SHORT) {
            log.warn("清除空头内存持仓: posId={}", shortPosition != null ? shortPosition.getPositionId() : "null");
            shortPosition = null;
        }
    }

    @Override
    public SignalType getNetPositionSide() {
        BigDecimal longQty = hasLongPosition() ? longPosition.getTotalQuantity() : BigDecimal.ZERO;
        BigDecimal shortQty = hasShortPosition() ? shortPosition.getTotalQuantity() : BigDecimal.ZERO;
        int cmp = longQty.compareTo(shortQty);
        if (cmp > 0) return SignalType.LONG;
        if (cmp < 0) return SignalType.SHORT;
        return SignalType.HOLD;
    }

    @Override public boolean isFullyHedged() {
        return hasLongPosition() && hasShortPosition()
                && longPosition.getTotalQuantity().compareTo(shortPosition.getTotalQuantity()) == 0;
    }
    @Override public BigDecimal getLongQuantity() { return hasLongPosition() ? longPosition.getTotalQuantity() : BigDecimal.ZERO; }
    @Override public BigDecimal getShortQuantity() { return hasShortPosition() ? shortPosition.getTotalQuantity() : BigDecimal.ZERO; }
    @Override public MemoryPosition getLongPosition() { return longPosition; }
    @Override public MemoryPosition getShortPosition() { return shortPosition; }
    @Override public BigDecimal getLongAvgPrice() { return hasLongPosition() ? longPosition.getAvgPrice() : BigDecimal.ZERO; }
    @Override public BigDecimal getShortAvgPrice() { return hasShortPosition() ? shortPosition.getAvgPrice() : BigDecimal.ZERO; }
    @Override public List<EntryRecord> getLongEntries() { return hasLongPosition() ? Collections.unmodifiableList(longPosition.getEntries()) : Collections.emptyList(); }
    @Override public List<EntryRecord> getShortEntries() { return hasShortPosition() ? Collections.unmodifiableList(shortPosition.getEntries()) : Collections.emptyList(); }
    @Override public BigDecimal getAvailableBalance() { return availableBalance; }

    @Override
    public BigDecimal getEquity(BigDecimal currentPrice) {
        BigDecimal floatingPnl = BigDecimal.ZERO;
        if (hasLongPosition()) {
            floatingPnl = floatingPnl.add(ProfitCalcUtils.getProfitByVolume(
                    Exchange.OKX, OrderSideEnum.BUY, longPosition.getAvgPrice(),
                    longPosition.getTotalQuantity(), currentPrice, contractSpec));
        }
        if (hasShortPosition()) {
            floatingPnl = floatingPnl.add(ProfitCalcUtils.getProfitByVolume(
                    Exchange.OKX, OrderSideEnum.SELL, shortPosition.getAvgPrice(),
                    shortPosition.getTotalQuantity(), currentPrice, contractSpec));
        }
        return availableBalance.add(realizedPnl).add(floatingPnl);
    }

    @Override public BigDecimal getInitialCapital() { return initialCapital; }
    @Override public List<MemoryPosition> getClosedPositions() { return Collections.unmodifiableList(closedPositions); }
    @Override public int getClosedTradeCount() { return closedPositions.size(); }
    @Override public BigDecimal getTotalRealizedPnl() { return realizedPnl; }
    @Override public List<EntryRecord> getAllEntryRecords() { return Collections.unmodifiableList(entryRecords); }
    @Override public List<ActionRecord> getAllActionRecords() { return Collections.unmodifiableList(actionRecords); }
    @Override public String getSymbol() { return symbol; }
    @Override public boolean isBacktest() { return false; }

    private BigDecimal applySlippage(BigDecimal price, SignalType side) {
        if (slippage == null || slippage.compareTo(BigDecimal.ZERO) <= 0) return price;
        return side == SignalType.LONG
                ? price.multiply(BigDecimal.ONE.add(slippage))
                : price.multiply(BigDecimal.ONE.subtract(slippage));
    }

    public BigDecimal getTotalCommissionPaid() { return totalCommissionPaid; }
    public List<BacktestResult.EquityPoint> getEquityCurve() { return equityCurve; }
    public List<BacktestResult.TradeRecord> getTrades() { return trades; }
    public ExecutionHandler getExecutionHandler() { return executionHandler; }
    public ContractSpec getContractSpec() { return contractSpec; }
    public int getLeverage() { return leverage; }
    public RealtimeConfig getConfig() { return config; }
}
