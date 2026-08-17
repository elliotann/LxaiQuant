package com.chain.ai.trade.extension.ta4j.core;

import org.ta4j.core.ExecutionSide;
import org.ta4j.core.LiveTrade;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

import java.time.Instant;
import java.util.List;

/**
 * 实盘交易记录适配器（完全兼容 ta4j 0.22）。
 */
public class LiveTradingRecordAdapter implements TradingRecord {

    private static final CostModel ZERO_COST_MODEL = new ZeroCostModel();

    private final LiveTradingRecord tradingRecord;
    private final Trade.TradeType startingType;

    public LiveTradingRecordAdapter(RuleDto order) {
        this.startingType = order.isBuy() ? Trade.TradeType.BUY : Trade.TradeType.SELL;
        this.tradingRecord = new LiveTradingRecord(startingType);
        ExecutionSide side = order.isBuy() ? ExecutionSide.BUY : ExecutionSide.SELL;
        LiveTrade trade = new LiveTrade(0, Instant.EPOCH, order.getEntryPrice(), order.getAmount(), null, side, null,
                null);
        this.tradingRecord.recordFill(0, trade);
    }

    @Override
    public Position getCurrentPosition() {
        return tradingRecord.getCurrentPosition();
    }

    @Override
    public Trade.TradeType getStartingType() {
        return startingType;
    }

    @Override
    public String getName() {
        return "LiveTradingRecordAdapter";
    }

    @Override
    public void operate(int index, Num price, Num amount) {
        tradingRecord.operate(index, price, amount);
    }

    @Override
    public boolean enter(int index, Num price, Num amount) {
        return tradingRecord.enter(index, price, amount);
    }

    @Override
    public boolean exit(int index, Num price, Num amount) {
        return tradingRecord.exit(index, price, amount);
    }

    @Override
    public List<Position> getPositions() {
        return tradingRecord.getPositions();
    }

    @Override
    public List<Trade> getTrades() {
        return tradingRecord.getTrades();
    }

    @Override
    public Trade getLastTrade() {
        return tradingRecord.getLastTrade();
    }

    @Override
    public Trade getLastTrade(Trade.TradeType tradeType) {
        return tradingRecord.getLastTrade(tradeType);
    }

    @Override
    public Trade getLastEntry() {
        return tradingRecord.getLastEntry();
    }

    @Override
    public Trade getLastExit() {
        return tradingRecord.getLastExit();
    }

    @Override
    public Integer getStartIndex() {
        return tradingRecord.getStartIndex();
    }

    @Override
    public Integer getEndIndex() {
        return tradingRecord.getEndIndex();
    }

    @Override
    public CostModel getTransactionCostModel() {
        return ZERO_COST_MODEL;
    }

    @Override
    public CostModel getHoldingCostModel() {
        return ZERO_COST_MODEL;
    }

    @Override
    public Position getLastPosition() {
        return tradingRecord.getLastPosition();
    }

    @Override
    public int getPositionCount() {
        return tradingRecord.getPositionCount();
    }

    @Override
    public boolean isClosed() {
        return tradingRecord.isClosed();
    }
}
