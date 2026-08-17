package com.chain.ai.trade.engine2.persistence;

import com.chain.ai.trade.backtest.entity.dos.BacktestTask;
import com.chain.ai.trade.backtest.entity.dto.BacktestResultDTO;
import com.chain.ai.trade.backtest.service.BacktestTaskService;
import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.signal.entity.constants.TradeStatus;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import com.chain.ai.trade.engine.signal.service.ITradeSignalService;
import com.chain.ai.trade.engine2.backtest.BacktestResult;
import com.chain.ai.trade.engine2.backtest.model.ClosedEntryDetail;
import com.chain.ai.trade.engine2.backtest.model.EntryRecord;
import com.chain.ai.trade.engine2.backtest.model.MemoryPosition;
import com.chain.ai.trade.order.entity.dos.*;
import com.chain.ai.trade.order.mapper.TradeOrderCloseItemMapper;
import com.chain.ai.trade.order.mapper.TradeOrderCloseMapper;
import com.chain.ai.trade.order.mapper.TradeOrderItemMapper;
import com.chain.ai.trade.order.mapper.TradeOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 回测持久化批量写入门面 — 方案 A（基于 closedPositions 快照）
 * <p>
 * 数据模型：1个仓位 → N个入场记录 → N个平仓记录
 * <p>
 * 数据来源：result.getClosedPositions()（已平仓快照列表）
 * 每个快照已包含完整的开仓明细（entries）和平仓明细（closedDetails）
 * <p>
 * 落库目标：
 * <ul>
 *   <li>ai_trade_position（仓位主表）</li>
 *   <li>ai_trade_entry（入场明细）</li>
 *   <li>ai_trade_exit_batch（平仓批次汇总）</li>
 *   <li>ai_trade_exit_item（平仓明细）</li>
 *   <li>trade_signal（业务信号表）</li>
 * </ul>
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class BacktestBatchGateway implements PersistenceGateway {

    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final TradeOrderCloseMapper tradeOrderCloseMapper;
    private final TradeOrderCloseItemMapper tradeOrderCloseItemMapper;
    private final BacktestTaskService backtestTaskService;
    private final ITradeSignalService tradeSignalService;

    @Override
    public void openBatch() {
        log.debug("BacktestBatchGateway.openBatch 调用");
    }

    @Override
    public void flush(String taskId, String symbol, BacktestResult result, BarSeries series) {
        flushInternal(taskId, symbol, result, series);
    }

    /**
     * 核心业务方法 — 使用 closedPositions 快照直接落库
     * <p>
     * 数据流：
     * <ol>
     *   <li>从 result 获取 closedPositions（已平仓快照列表）</li>
     *   <li>遍历每个快照，生成 1 条 TradeOrder（仓位主表）</li>
     *   <li>遍历快照中的 entries，每个 EntryRecord 生成 1 条 TradeOrderItem（入场明细）</li>
     *   <li>遍历每个 EntryRecord 的 closedDetails，每个 ClosedEntryDetail 生成 1 条 TradeOrderCloseItem（平仓明细）</li>
     *   <li>每个快照生成 1 条 TradeOrderClose（平仓批次汇总）</li>
     *   <li>保存绩效指标到 backtest_result</li>
     * </ol>
     */
    @Transactional
    public void flushInternal(String taskId, String symbol, BacktestResult result, BarSeries series) {
        if (result == null) {
            log.warn("flush 跳过：result 为 null, taskId={}", taskId);
            return;
        }

        // 🔥 使用 closedPositions（已平仓快照）
        List<MemoryPosition> closedPositions = result.getClosedPositions();
        if (closedPositions == null || closedPositions.isEmpty()) {
            log.warn("flush 跳过：closedPositions 为空, taskId={}", taskId);
            return;
        }

        // 查询回测任务
        BacktestTask task = backtestTaskService.getById(taskId);
        if (task == null) {
            log.warn("flush 跳过：未找到回测任务, taskId={}", taskId);
            return;
        }

        String robotId = task.getRobotId() != null ? task.getRobotId() : "2001";
        String memberId = task.getMemberId() != null ? task.getMemberId() : "1665908516499693568";
        Long accountId = task.getAccountId() != null ? task.getAccountId() : 1768185450252304387L;
        Integer leverage = task.getLeverage() != null ? task.getLeverage() : 1;

        log.info("开始回测落库: taskId={}, symbol={}, closedPositionCount={}",
                taskId, symbol, closedPositions.size());

        int savedOrderCount = 0;
        int totalEntryCount = 0;
        int totalCloseCount = 0;
        List<TradeSignal> pendingSignals = new ArrayList<>();

        // 🔥 直接遍历 closedPositions，无需分组
        for (MemoryPosition snapshot : closedPositions) {
            try {
                // 1. 创建 TradeOrder（仓位主表）
                TradePosition order = buildTradeOrderFromSnapshot(snapshot, memberId, accountId, robotId, leverage);
                tradeOrderMapper.insert(order);
                log.debug("TradeOrder 插入成功: orderSn={}", order.getPositionId());

                // 2. 创建 TradeOrderClose（平仓批次汇总）
                TradeExitBatch close = buildTradeOrderCloseFromSnapshot(snapshot);
                tradeOrderCloseMapper.insert(close);
                log.debug("TradeOrderClose 插入成功: batchId={}, orderSn={}", close.getPositionId(), order.getPositionId());

                // 3. 遍历 entries 生成 TradeOrderItem、TradeOrderCloseItem 和 TradeSignal
                int entryCount = 0;
                int closeCount = 0;
                int entryIndex = 0;
                for (EntryRecord entry : snapshot.getEntries()) {
                    // 3.1 入场明细
                    TradeEntry item = buildTradeOrderItem(order.getPositionId(), entry);
                    tradeOrderItemMapper.insert(item);
                    entryCount++;

                    // 3.2 平仓明细（每个 ClosedEntryDetail 对应一条）
                    for (ClosedEntryDetail detail : entry.getClosedDetails()) {
                        TradeExitItem closeItem = buildTradeOrderCloseItem(
                                close.getPositionId(),
                                entry.getEntryId(),
                                detail
                        );
                        tradeOrderCloseItemMapper.insert(closeItem);
                        closeCount++;
                    }

                    // 3.3 入场信号（头仓→OPEN_LONG/OPEN_SHORT，补仓→LBAP/SBAP），改为收集后批量保存
                    TradeSignal entrySignal = buildEntrySignal(snapshot, entry, entryIndex, taskId);
                    if (entrySignal != null) {
                        pendingSignals.add(entrySignal);
                    }
                    entryIndex++;
                }

                // 4. 平仓信号，改为收集后批量保存
                TradeSignal exitSignal = buildExitSignal(snapshot, taskId);
                if (exitSignal != null) {
                    pendingSignals.add(exitSignal);
                }

                savedOrderCount++;
                totalEntryCount += entryCount;
                totalCloseCount += closeCount;

                log.debug("仓位保存成功: positionId={}, orderSn={}, entryCount={}, closeCount={}",
                        snapshot.getPositionId(), order.getPositionId(), entryCount, closeCount);

            } catch (Exception e) {
                log.error("保存仓位失败: positionId={}, error={}", snapshot.getPositionId(), e.getMessage(), e);
                throw new RuntimeException("保存回测仓位失败: " + e.getMessage(), e);
            }
        }

        // 4. 批量保存业务信号
        if (!pendingSignals.isEmpty()) {
            tradeSignalService.saveBatch(pendingSignals);
            log.info("批量保存业务信号完成: taskId={}, signalCount={}", taskId, pendingSignals.size());
        }

        // 5. 保存绩效指标（backtest_result）
        saveResultMetrics(taskId, result, series);

        log.info("回测落库完成: taskId={}, 仓位数={}, 入场明细数={}, 平仓明细数={}, 信号数={}",
                taskId, savedOrderCount, totalEntryCount, totalCloseCount, pendingSignals.size());
    }

    // ================================================================
    // Builder 方法（基于 MemoryPosition 快照）
    // ================================================================

    /**
     * 从 MemoryPosition 快照构建 TradeOrder（仓位主表）
     */
    private TradePosition buildTradeOrderFromSnapshot(MemoryPosition snapshot,
                                                      String memberId, Long accountId,
                                                      String robotId, Integer leverage) {
        TradePosition order = new TradePosition();
        order.setPositionId(snapshot.getPositionId());
        order.setSymbol(snapshot.getSymbol());
        order.setOrderSideEnum(snapshot.getDirection() == SignalType.LONG
                ? OrderSideEnum.BUY : OrderSideEnum.SELL);
        order.setBuyPrice(snapshot.getAvgPrice());
        order.setBuyAvgPrice(snapshot.getAvgPrice());
        order.setAmount(snapshot.getTotalEntryQuantity());
        order.setVolume(snapshot.getTotalEntryQuantity());
        order.setIncome(snapshot.getTotalPnl());
        order.setCharge(snapshot.getOpenFee());
        order.setLeverRate(leverage);

        // 🔥 修复：使用 snapshot.getStatus() 映射到 TradeOrderStatus
        // snapshot.getStatus() 返回 "GAIN" 或 "LOSS"（由 markClosed 设置）
        order.setTradeOrderStatus(TradePosition.TradeOrderStatus.valueOf(snapshot.getStatus()));

        order.setMemberId(memberId);
        order.setAccountId(accountId.toString());
        order.setRobotId(robotId);
        order.setGainPrice(snapshot.getTakeProfitPrice());
        order.setLossPrice(snapshot.getStopLossPrice());

        if (snapshot.getEntryTime() != null) {
            Date entryDate = DateUtil.toOrderDate(snapshot.getEntryTime().atZone(ZoneId.systemDefault()).toInstant());
            order.setOrderTime(entryDate);
            order.setBuyTime(entryDate);
        }
        // 🔥 平仓时间和价格
        if (snapshot.getExitTime() != null) {
            order.setSellTime(DateUtil.toOrderDate(snapshot.getExitTime().atZone(ZoneId.systemDefault()).toInstant()));
        }
        if (snapshot.getExitPrice() != null) {
            order.setSellPrice(snapshot.getExitPrice());
        }
        order.setCreateTime(new Date());
        order.setUpdateTime(new Date());
        return order;
    }

    /**
     * 从 MemoryPosition 快照构建 TradeOrderClose（平仓批次汇总）
     */
    private TradeExitBatch buildTradeOrderCloseFromSnapshot(MemoryPosition snapshot) {
        TradeExitBatch close = new TradeExitBatch();
        close.setPositionId(snapshot.getPositionId());  // 关联仓位主表
        close.setClosedVolume(snapshot.getTotalQuantity());
        close.setSellPrice(snapshot.getExitPrice());
        close.setIncome(snapshot.getTotalPnl());
        if (snapshot.getExitTime() != null) {
            close.setSellTime(DateUtil.toOrderDate(snapshot.getExitTime().atZone(ZoneId.systemDefault()).toInstant()));
        } else {
            close.setSellTime(new Date());
        }
        close.setStatus("DEAL");
        return close;
    }


    /**
     * 构建 TradeOrderItem（入场明细）
     * <p>
     * 使用 initialQuantity（原始开仓量）
     */
    private TradeEntry buildTradeOrderItem(String orderSn, EntryRecord record) {
        OrderSideEnum orderSide = "LONG".equals(record.getSide()) ? OrderSideEnum.BUY : OrderSideEnum.SELL;

        TradeEntry item = new TradeEntry();
        item.setEntrySn(record.getEntryId());
        item.setPositionId(orderSn);
        item.setSymbol(record.getSymbol());
        item.setOrderSideEnum(orderSide);
        item.setBuyPrice(record.getPrice());
        item.setAmount(record.getInitialQuantity());
        item.setVolume(record.getInitialQuantity());
        item.setCharge(record.getFee());
        item.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.DEAL);
        item.setGainPrice(record.getTakeProfitPrice());
        item.setLossPrice(record.getStopLossPrice());
        // 回测已平仓快照中所有入场明细都已全平
        BigDecimal closedQty = record.getClosedDetails().stream()
                .map(ClosedEntryDetail::getQuantity)
                .filter(q -> q != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        item.setClosedVolume(closedQty);
        if (record.getTime() != null) {
            item.setOrderTime(DateUtil.toOrderDate(record.getTime().atZone(ZoneId.systemDefault()).toInstant()));
        }
        item.setCreateTime(new Date());
        item.setUpdateTime(new Date());
        return item;
    }

    /**
     * 构建 TradeOrderCloseItem（平仓明细）
     */
    private TradeExitItem buildTradeOrderCloseItem(String batchId, String entryId, ClosedEntryDetail detail) {
        TradeExitItem item = new TradeExitItem();
        item.setBatchId(batchId);
        item.setEntrySn(entryId);
        // 🔥 修复：关联仓位ID（从 EntryRecord 的 positionId 获取，或通过其他方式传入）
        // 这里的 entryId 是开仓明细ID，需要通过 entryId 反查 positionId
        // 但这个方法没有传入 positionId，需要从 EntryRecord 获取
        // 建议在调用处传入 positionId，或修改方法签名
        item.setPositionId(batchId);  // 临时保留，需要进一步确认
        item.setClosedVolume(detail.getQuantity());
        item.setExitPrice(detail.getExitPrice());
        item.setIncome(detail.getPnl());
        if (detail.getExitTime() != null) {
            item.setExitTime(DateUtil.toOrderDate(detail.getExitTime().atZone(ZoneId.systemDefault()).toInstant()));
        } else {
            item.setExitTime(new Date());
        }
        item.setCloseMethod(detail.getCloseReason() != null ? detail.getCloseReason() : "AUTO");
        return item;
    }

    // ================================================================
    // 业务信号创建
    // ================================================================

    /**
     * 构建入场业务信号，直接使用 EntryRecord 中记录的信号ID关联 TechnicalSignal。
     */
    private TradeSignal buildEntrySignal(MemoryPosition snapshot, EntryRecord entry, int entryIndex, String taskId) {
        try {
            TradeSignal signal = new TradeSignal();
            signal.setSymbol(snapshot.getSymbol());
            signal.setOrderAction(entryIndex == 0
                    ? (snapshot.getDirection() == SignalType.LONG ? OrderAction.OPEN_LONG : OrderAction.OPEN_SHORT)
                    : (snapshot.getDirection() == SignalType.LONG ? OrderAction.LBAP : OrderAction.SBAP));
            signal.setStatus(TradeStatus.FILLED);
            signal.setOrderSn(snapshot.getPositionId());
            signal.setOrderItemSn(entry.getEntryId());
            signal.setExecutedPrice(entry.getPrice());
            signal.setExecutedAmount(entry.getInitialQuantity());
            signal.setDecisionReason("BacktestEngine auto generated");
            if (entry.getTime() != null) {
                long barEpochMillis = entry.getTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(entry.getTime());
                long candlestickId = barEpochMillis - offset.getTotalSeconds() * 1000L;
                signal.setKlineTime(DateUtil.longConvertDateTime(candlestickId));
                signal.setExecutedTime(DateUtil.toOrderDate(entry.getTime().atZone(ZoneId.systemDefault()).toInstant()));
            }
            signal.setRiskLevel("MEDIUM");
            signal.setPositionRatio(BigDecimal.ONE);
            signal.setPriority(5);
            signal.setCreator("SYSTEM");
            signal.setCreateTime(new Date());
            signal.setUpdater("SYSTEM");
            signal.setUpdateTime(new Date());
            signal.setDeleted(false);
            signal.setTechnicalSignalId(entry.getSignalId());
            return signal;
        } catch (Exception e) {
            log.warn("构建入场信号失败: taskId={}, entryId={}", taskId, entry.getEntryId(), e);
            return null;
        }
    }

    /**
     * 构建平仓业务信号（不关联技术信号，平仓是交易执行结果）
     */
    private TradeSignal buildExitSignal(MemoryPosition snapshot, String taskId) {
        try {
            TradeSignal signal = new TradeSignal();
            signal.setSymbol(snapshot.getSymbol());
            signal.setOrderAction(snapshot.getDirection() == SignalType.LONG
                    ? OrderAction.CLOSE_LONG : OrderAction.CLOSE_SHORT);
            signal.setStatus(TradeStatus.FILLED);
            signal.setOrderSn(snapshot.getPositionId());
            signal.setExecutedPrice(snapshot.getExitPrice());
            signal.setExecutedAmount(snapshot.getTotalEntryQuantity());
            signal.setDecisionReason("BacktestEngine " + snapshot.getStatus() + " auto generated");
            signal.setPnlAmount(snapshot.getTotalPnl());
            if (snapshot.getExitTime() != null) {
                long barEpochMillis = snapshot.getExitTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(snapshot.getExitTime());
                long candlestickId = barEpochMillis - offset.getTotalSeconds() * 1000L;
                signal.setKlineTime(DateUtil.longConvertDateTime(candlestickId));
                signal.setExecutedTime(DateUtil.toOrderDate(snapshot.getExitTime().atZone(ZoneId.systemDefault()).toInstant()));
            }
            signal.setRiskLevel("MEDIUM");
            signal.setPositionRatio(BigDecimal.ONE);
            signal.setPriority(5);
            signal.setCreator("SYSTEM");
            signal.setCreateTime(new Date());
            signal.setUpdater("SYSTEM");
            signal.setUpdateTime(new Date());
            signal.setDeleted(false);
            // 平仓信号关联入场信号ID，使K线图能正确显示
            List<EntryRecord> entries = snapshot.getEntries();
            if (entries != null && !entries.isEmpty()) {
                signal.setTechnicalSignalId(entries.get(0).getSignalId());
            }
            return signal;
        } catch (Exception e) {
            log.warn("构建平仓信号失败: taskId={}, positionId={}", taskId, snapshot.getPositionId(), e);
            return null;
        }
    }

    // ================================================================
    // 绩效指标保存
    // ================================================================

    private void saveResultMetrics(String taskId, BacktestResult result, BarSeries series) {
        BigDecimal initialCapital = result.getEquityCurve() != null && !result.getEquityCurve().isEmpty()
                ? result.getEquityCurve().get(0).getEquity()
                : BigDecimal.ZERO;

        int[] consecutiveStats = computeMaxConsecutive(result.getTrades());

        BacktestResultDTO dto = BacktestResultDTO.builder()
                .taskId(taskId)
                .totalReturn(calcTotalReturn(result.getFinalEquity(), initialCapital))
                .maxDrawdown(result.getMaxDrawdown())
                .winRate(safeBigDecimal(result.getWinRate()))
                .totalTrades(result.getTotalTrades())
                .winningTrades(result.getProfitableTrades())
                .profitFactor(safeBigDecimal(result.getProfitLossRatio()))
                .finalValue(result.getFinalEquity())
                .sharpeRatio(safeBigDecimal(result.getSharpeRatio()))
                .calmarRatio(calcCalmarRatio(result))
                .totalCost(result.getTotalCommission())
                .annualReturn(safeBigDecimal(result.getAnnualReturn()))
                .volatility(safeBigDecimal(result.getVolatility()))
                .sortinoRatio(safeBigDecimal(result.getSortinoRatio()))
                .averageWin(safeBigDecimal(result.getAverageWin()))
                .averageLoss(safeBigDecimal(result.getAverageLoss()))
                .largestWinTrade(safeBigDecimal(result.getLargestWinTrade()))
                .largestLossTrade(safeBigDecimal(result.getLargestLossTrade()))
                .maxConsecutiveWins(consecutiveStats[0])
                .maxConsecutiveLosses(consecutiveStats[1])
                .avgTradeDuration(computeAverageHoldingPeriod(result.getTrades(), series))
                .equityCurve(serializeEquityCurve(result))
                .calculatedAt(LocalDateTime.now())
                .build();

        backtestTaskService.saveBacktestResult(dto);
    }

    /**
     * 统计最大连续盈利/亏损次数（按平仓时间排序）。
     * 返回数组：[0]=最大连续盈利次数，[1]=最大连续亏损次数。
     */
    private int[] computeMaxConsecutive(List<BacktestResult.TradeRecord> trades) {
        int maxWins = 0, maxLosses = 0;
        int curWins = 0, curLosses = 0;
        if (trades == null || trades.isEmpty()) {
            return new int[]{0, 0};
        }
        List<BacktestResult.TradeRecord> sorted = new ArrayList<>(trades);
        sorted.sort(Comparator.comparingInt(BacktestResult.TradeRecord::getExitIndex)
                .thenComparingInt(BacktestResult.TradeRecord::getEntryIndex));
        for (BacktestResult.TradeRecord t : sorted) {
            if (t.getPnl() != null && t.getPnl().compareTo(BigDecimal.ZERO) > 0) {
                curWins++;
                curLosses = 0;
                maxWins = Math.max(maxWins, curWins);
            } else {
                curLosses++;
                curWins = 0;
                maxLosses = Math.max(maxLosses, curLosses);
            }
        }
        return new int[]{maxWins, maxLosses};
    }

    /**
     * 计算平均持仓时间（天），按开仓Bar起始时间到平仓Bar结束时间。
     */
    private double computeAverageHoldingPeriod(List<BacktestResult.TradeRecord> trades, BarSeries series) {
        if (trades == null || trades.isEmpty() || series == null) {
            return 0.0;
        }
        double totalDays = 0.0;
        int count = 0;
        for (BacktestResult.TradeRecord t : trades) {
            int entryIdx = t.getEntryIndex();
            int exitIdx = t.getExitIndex();
            if (entryIdx >= 0 && exitIdx >= 0 && entryIdx < series.getBarCount() && exitIdx < series.getBarCount()) {
                try {
                    long entryTime = series.getBar(entryIdx).getBeginTime().toEpochMilli();
                    long exitTime = series.getBar(exitIdx).getEndTime().toEpochMilli();
                    totalDays += Math.max(0, (exitTime - entryTime) / (24.0 * 60 * 60 * 1000.0));
                    count++;
                } catch (Exception ignored) {
                    log.debug("计算持仓时间异常: entryIdx={}, exitIdx={}", entryIdx, exitIdx);
                }
            }
        }
        return count > 0 ? totalDays / count : 0.0;
    }

    private BigDecimal calcTotalReturn(BigDecimal finalEquity, BigDecimal initialCapital) {
        if (initialCapital.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return finalEquity.subtract(initialCapital)
                .divide(initialCapital, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal calcCalmarRatio(BacktestResult result) {
        double maxDd = result.getMaxDrawdown().doubleValue();
        double annualRet = result.getAnnualReturn();
        if (maxDd <= 0 || Double.isNaN(maxDd) || Double.isInfinite(maxDd)) return BigDecimal.ZERO;
        if (Double.isNaN(annualRet) || Double.isInfinite(annualRet)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(annualRet / maxDd);
    }

    /** 安全转换 double → BigDecimal，NaN/Infinity 返回 ZERO */
    private static BigDecimal safeBigDecimal(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(value);
    }

    /**
     * 序列化权益曲线为 JSON 数组字符串
     * <p>
     * 格式：[{"barIndex":0,"timestamp":1700000000000,"equity":10000},...]
     */
    private String serializeEquityCurve(BacktestResult result) {
        List<BacktestResult.EquityPoint> curve = result.getEquityCurve();
        if (curve == null || curve.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < curve.size(); i++) {
            if (i > 0) sb.append(",");
            BacktestResult.EquityPoint p = curve.get(i);
            sb.append("{\"barIndex\":").append(p.getIndex())
                    .append(",\"time\":").append(p.getTimestamp())
                    .append(",\"equity\":").append(p.getEquity())
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}