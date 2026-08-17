package com.chain.ai.trade.engine2.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.common.entity.constants.SignalType;
import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.signal.entity.constants.TradeStatus;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import com.chain.ai.trade.engine.signal.service.ITradeSignalService;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.chain.ai.trade.engine2.backtest.model.MemoryPosition;
import com.chain.ai.trade.engine2.core.execution.OrderIntent;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.order.entity.constants.TradeOrderEnum;
import com.chain.ai.trade.order.entity.dos.*;
import com.chain.ai.trade.order.mapper.TradeOrderCloseItemMapper;
import com.chain.ai.trade.order.mapper.TradeOrderCloseMapper;
import com.chain.ai.trade.order.mapper.TradeOrderItemMapper;
import com.chain.ai.trade.order.mapper.TradeOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 实盘/模拟持久化门面实现 — 直接写数据库。
 * <p>
 * 引擎线程本身已独立于主线程，无需 @Async 异步。
 * </p>
 */
@Slf4j
public class RealtimeAsyncGateway implements RealtimeGateway {

    private static final long TIMEZONE_OFFSET_MS = 8 * 60 * 60 * 1000L;

    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final TradeOrderCloseMapper tradeOrderCloseMapper;
    private final TradeOrderCloseItemMapper tradeOrderCloseItemMapper;
    private final ITradeSignalService tradeSignalService;

    private final String memberId;
    private final String robotId;
    private final String accountId;
    private final int leverage;
    private final String symbol;
    private final String timeframe;
    private final boolean test;

    private final RedisCache redisCache;
    private final ITradingBotService tradingBotService;

    /** positionId → (clientOrderId → orderItemSn)，用于平仓时按仓位精准关联入场明细 */
    private final Map<String, Map<String, String>> positionEntryMap = new ConcurrentHashMap<>();

    /** positionId → TradeOrder.id 映射，用于快速复用已有订单 */
    private final Map<String, String> positionIdToOrderId = new ConcurrentHashMap<>();

    public RealtimeAsyncGateway(TradeOrderMapper tradeOrderMapper,
                                 TradeOrderItemMapper tradeOrderItemMapper,
                                 TradeOrderCloseMapper tradeOrderCloseMapper,
                                 TradeOrderCloseItemMapper tradeOrderCloseItemMapper,
                                 ITradeSignalService tradeSignalService,
                                 String memberId, String robotId, String accountId,
                                 int leverage, String symbol, String timeframe,
                                 boolean test,
                                 RedisCache redisCache,
                                 ITradingBotService tradingBotService) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.tradeOrderItemMapper = tradeOrderItemMapper;
        this.tradeOrderCloseMapper = tradeOrderCloseMapper;
        this.tradeOrderCloseItemMapper = tradeOrderCloseItemMapper;
        this.tradeSignalService = tradeSignalService;
        this.memberId = memberId;
        this.robotId = robotId;
        this.accountId = accountId;
        this.leverage = leverage;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.test = test;
        this.redisCache = redisCache;
        this.tradingBotService = tradingBotService;
    }

    // ==================== 入场记录 ====================

    @Override
    public void onOrderSubmitted(OrderIntent intent) {
        // 挂单阶段暂不落库，等成交后再写
        log.debug("[RealtimeGateway] 订单提交(暂不落库): clientOrderId={}", intent.getClientOrderId());
    }

    @Override
    public void onOrderFilled(OrderIntent intent, BigDecimal fillPrice, BigDecimal filledQuantity) {
        try {
            String orderItemSn = generateOrderItemSn();
            String positionId = intent.getPositionId();
            positionEntryMap.computeIfAbsent(positionId, k -> new ConcurrentHashMap<>())
                    .put(intent.getClientOrderId(), orderItemSn);

            // 检查 TradeOrderServiceImpl 是否已创建入场明细（entrySn=positionId），若是则更新而非新增
            TradePosition.TradeOrderStatus dealStatus = TradePosition.TradeOrderStatus.DEAL;
            LambdaQueryWrapper<TradeEntry> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(TradeEntry::getPositionId, positionId)
                    .eq(TradeEntry::getEntrySn, positionId)
                    .last("limit 1");
            TradeEntry existingItem = tradeOrderItemMapper.selectOne(existWrapper);

            if (existingItem != null) {
                existingItem.setBuyPrice(fillPrice);
                existingItem.setVolume(filledQuantity);
                existingItem.setAmount(filledQuantity);
                existingItem.setGainPrice(intent.getTakeProfitPrice());
                existingItem.setLossPrice(intent.getStopLossPrice());
                existingItem.setTradeOrderItemStatus(dealStatus);
                existingItem.setSyncVolumeFlag(true);
                tradeOrderItemMapper.updateById(existingItem);
            } else {
                TradeEntry item = TradeEntry.builder()
                        .positionId(positionId)
                        .entrySn(orderItemSn)
                        .robotId(robotId)
                        .symbol(intent.getSymbol())
                        .orderSideEnum(intent.getSide() == SignalType.LONG ? OrderSideEnum.BUY : OrderSideEnum.SELL)
                        .buyPrice(fillPrice)
                        .amount(filledQuantity)
                        .volume(filledQuantity)
                        .charge(BigDecimal.ZERO)
                        .closedVolume(BigDecimal.ZERO)
                        .income(BigDecimal.ZERO)
                        .gainPrice(intent.getTakeProfitPrice())
                        .lossPrice(intent.getStopLossPrice())
                        .orderTime(toDate(intent.getBarTime()))
                        .tradeOrderItemStatus(dealStatus)
                        .syncVolumeFlag(true)
                        .deleteFlag(false)
                        .build();
                tradeOrderItemMapper.insert(item);
            }

            // 2. 创建开仓业务信号 TradeSignal
            createOpenSignal(positionId, orderItemSn, intent.getSymbol(), fillPrice,
                    filledQuantity, intent.getSide(), intent.getBarTime());

            log.info("[RealtimeGateway] 入场明细已保存: orderItemSn={}, positionId={}, price={}, qty={}",
                    orderItemSn, positionId, fillPrice, filledQuantity);
        } catch (Exception e) {
            log.error("[RealtimeGateway] 保存入场明细失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 持仓更新 ====================

    @Override
    public void onPositionUpdated(MemoryPosition position) {
        if (position == null) return;
        try {
            String positionId = position.getPositionId();
            String existingId = positionIdToOrderId.get(positionId);

            // 内存缓存未命中时，查 DB 确认是否已存在（兼容重复运行/服务重启场景）
            if (existingId == null) {
                LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(TradePosition::getPositionId, positionId);
                TradePosition dbOrder = tradeOrderMapper.selectOne(wrapper);
                if (dbOrder != null) {
                    existingId = dbOrder.getId();
                    positionIdToOrderId.put(positionId, existingId);
                }
            }

            OrderSideEnum side = position.getDirection() == SignalType.LONG
                    ? OrderSideEnum.BUY : OrderSideEnum.SELL;

            if (existingId != null) {
                TradePosition order = TradePosition.builder()
                        .id(existingId)
                        .buyAvgPrice(position.getAvgPrice())
                        .volume(position.getTotalQuantity())
                        .amount(position.getTotalEntryQuantity())
                        .buyPrice(position.getAvgPrice())
                        .openPrice(position.getAvgPrice())
                        .gainPrice(position.getTakeProfitPrice())
                        .lossPrice(position.getStopLossPrice())
                        .build();
                tradeOrderMapper.updateById(order);
            } else {
                TradePosition order = TradePosition.builder()
                        .positionId(positionId)
                        .memberId(memberId)
                        .accountId(accountId)
                        .robotId(robotId)
                        .symbol(position.getSymbol())
                        .memberPlatform(Exchange.OKX)
                        .goodsId(position.getSymbol())
                        .orderSideEnum(side)
                        .orderTime(toDate(position.getEntryTime()))
                        .buyTime(toDate(position.getEntryTime()))
                        .buyPrice(position.getAvgPrice())
                        .buyAvgPrice(position.getAvgPrice())
                        .openPrice(position.getAvgPrice())
                        .amount(position.getTotalEntryQuantity())
                        .volume(position.getTotalQuantity())
                        .gainPrice(position.getTakeProfitPrice())
                        .lossPrice(position.getStopLossPrice())
                        .tradeOrderStatus(TradePosition.TradeOrderStatus.DEAL)
                        .test(test)
                        .leverRate(leverage)
                        .remark("实盘")
                        .deleteFlag(false)
                        .build();
                tradeOrderMapper.insert(order);

                if (order.getId() != null) {
                    positionIdToOrderId.put(positionId, order.getId());
                }
            }
            log.debug("[RealtimeGateway] 持仓更新: positionId={}, qty={}, avgPrice={}",
                    positionId, position.getTotalQuantity(), position.getAvgPrice());
        } catch (Exception e) {
            log.error("[RealtimeGateway] 更新持仓失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 平仓记录 ====================

    @Override
    public void onOrderClosed(String symbol, String positionId, BigDecimal exitPrice, BigDecimal closeQty,
                               BigDecimal pnl, BigDecimal fee, ExitType exitType, LocalDateTime barTime) {
        try {
            Date exitTime = barTime != null ? toDate(barTime) : new Date();

            // 1. 插入平仓批次主表
            TradeExitBatch closeOrder = TradeExitBatch.builder()
                    .positionId(positionId)
                    .closePlatformOrderSn(positionId + "_CLOSE_" + UUID.randomUUID().toString().substring(0, 8))
                    .closeMethod(TradeOrderEnum.CLOSE_METHOD_MANUAL.getCode())
                    .closedVolume(closeQty)
                    .status(TradeOrderEnum.CLOSE_ORDER_STATUS_DEAL.getCode())
                    .sellPrice(exitPrice)
                    .sellTime(exitTime)
                    .income(pnl)
                    .charge(fee)
                    .deleteFlag(false)
                    .build();
            tradeOrderCloseMapper.insert(closeOrder);

            // 2. 插入平仓明细（按数量比例分摊盈亏和费用）+ 创建平仓业务信号
            if (closeOrder.getId() != null) {
                Map<String, String> positionEntries = positionEntryMap.remove(positionId);
                if (positionEntries == null || positionEntries.isEmpty()) {
                    log.warn("[RealtimeGateway] 平仓时未找到入场明细映射，跳过明细落库: positionId={}", positionId);
                } else {
                    String closeReason = exitType != null ? exitType.name() : "MANUAL";
                    for (Map.Entry<String, String> entry : positionEntries.entrySet()) {
                    String orderItemSn = entry.getValue();
                    LambdaQueryWrapper<TradeEntry> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(TradeEntry::getEntrySn, orderItemSn);
                    TradeEntry item = tradeOrderItemMapper.selectOne(wrapper);
                    if (item == null || item.getVolume().compareTo(BigDecimal.ZERO) <= 0) continue;

                    BigDecimal itemVolume = item.getVolume();
                    BigDecimal ratio = itemVolume.divide(closeQty, 8, BigDecimal.ROUND_HALF_UP);
                    BigDecimal itemPnl = pnl.multiply(ratio).setScale(4, BigDecimal.ROUND_HALF_UP);
                    BigDecimal itemFee = fee.multiply(ratio).setScale(4, BigDecimal.ROUND_HALF_UP);

                    TradeExitItem closeItem = TradeExitItem.builder()
                            .batchId(String.valueOf(closeOrder.getId()))
                            .positionId(positionId)
                            .entrySn(orderItemSn)
                            .closedVolume(item.getVolume())
                            .status(TradeOrderEnum.CLOSE_ORDER_STATUS_DEAL.getCode())
                            .entryPrice(item.getBuyPrice())
                            .exitPrice(exitPrice)
                            .exitTime(exitTime)
                            .income(itemPnl)
                            .charge(itemFee)
                            .closeMethod(closeReason)
                            .deleteFlag(false)
                            .build();
                    tradeOrderCloseItemMapper.insert(closeItem);

                    // 更新入场明细状态
                    item.setSellPrice(exitPrice);
                    item.setSellTime(exitTime);
                    item.setIncome(itemPnl);
                    item.setClosedVolume(item.getVolume());
                    item.setTradeOrderItemStatus(itemPnl.compareTo(BigDecimal.ZERO) >= 0
                            ? TradePosition.TradeOrderStatus.GAIN
                            : TradePosition.TradeOrderStatus.LOSS);
                    tradeOrderItemMapper.updateById(item);

                    // 创建平仓业务信号
                    createCloseSignal(positionId, orderItemSn, symbol, exitPrice,
                            item.getVolume(), itemPnl, exitType, barTime, item.getOrderSideEnum());
                }
            }
            }

            // 3. 更新仓位主表状态
            String orderId = positionIdToOrderId.get(positionId);
            if (orderId != null) {
                TradePosition order = TradePosition.builder()
                        .id(orderId)
                        .sellTime(exitTime)
                        .sellPrice(exitPrice)
                        .income(pnl)
                        .charge(fee)
                        .closeAmount(closeQty)
                        .tradeOrderStatus(pnl.compareTo(BigDecimal.ZERO) >= 0
                                ? TradePosition.TradeOrderStatus.GAIN
                                : TradePosition.TradeOrderStatus.LOSS)
                        .build();
                tradeOrderMapper.updateById(order);
            }

            // 清理仓位映射
            positionIdToOrderId.remove(positionId);

            // 4. 更新机器人当前资金
            if (tradingBotService != null && !test) {
                try {
                    TradingBot bot = tradingBotService.getByBotId(robotId);
                    if (bot != null && bot.getCurrentCapital() != null) {
                        BigDecimal newCapital = bot.getCurrentCapital().add(pnl).subtract(fee);
                        bot.setCurrentCapital(newCapital);
                        tradingBotService.updateById(bot);
                        log.debug("[RealtimeGateway] 更新机器人资金: robotId={}, pnl={}, fee={}, newCapital={}",
                                robotId, pnl, fee, newCapital);
                    }
                } catch (Exception e) {
                    log.warn("[RealtimeGateway] 更新机器人资金失败: robotId={}", robotId, e);
                }
            }

            log.info("[RealtimeGateway] 平仓记录已保存: positionId={}, pnl={}, exitType={}",
                    positionId, pnl, exitType);
        } catch (Exception e) {
            log.error("[RealtimeGateway] 保存平仓记录失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public Object[] loadOpenPositions(String symbol, boolean isTest) {
        // 1. 查询未平仓订单（DEAL 状态，未被平仓的）
        LambdaQueryWrapper<TradePosition> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(TradePosition::getSymbol, symbol)
                .eq(TradePosition::getTradeOrderStatus, TradePosition.TradeOrderStatus.DEAL)
                .eq(TradePosition::isTest, isTest);
        List<TradePosition> openOrders = tradeOrderMapper.selectList(orderWrapper);

        if (openOrders.isEmpty()) return null;

        // 2. 查询每个订单的入场明细（未完全平仓的）
        Map<String, List<TradeEntry>> itemsMap = new HashMap<>();
        for (TradePosition order : openOrders) {
            LambdaQueryWrapper<TradeEntry> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(TradeEntry::getPositionId, order.getPositionId())
                    .eq(TradeEntry::getTradeOrderItemStatus, TradePosition.TradeOrderStatus.DEAL);
            List<TradeEntry> items = tradeOrderItemMapper.selectList(itemWrapper);
            if (!items.isEmpty()) {
                itemsMap.put(order.getPositionId(), items);
            }
        }

        return new Object[]{openOrders, itemsMap};
    }

    @Override
    public void onEquitySample(String symbol, int barIndex, long timestamp, BigDecimal equity) {
        try {
            if (redisCache == null) return;

            String taskId = "LIVE:" + robotId + ":" + symbol + ":" + timeframe;
            long ts = timestamp - TIMEZONE_OFFSET_MS;

            JSONObject obj = new JSONObject();
            obj.put("ts", ts);
            obj.put("time", ts);
            obj.put("equity", equity);
            obj.put("drawdown", BigDecimal.ZERO);
            obj.put("returnRate", BigDecimal.ZERO);
            obj.put("source", "realtime");
            obj.put("scope", "robot");
            obj.put("robotId", robotId);
            obj.put("accountId", accountId);
            obj.put("symbol", symbol);
            obj.put("interval", timeframe);

            String key = "equity:curve:" + taskId;
            redisCache.zAdd(key, ts, obj.toString());
            redisCache.expire(key, 3, TimeUnit.DAYS);
            redisCache.setAdd("equity:active_tasks", taskId);
            redisCache.expire("equity:active_tasks", 3, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("[RealtimeGateway] 权益采样写入Redis失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 业务信号 ====================

    private void createOpenSignal(String positionId, String orderItemSn, String symbol,
                                   BigDecimal executedPrice, BigDecimal executedAmount,
                                   SignalType direction, LocalDateTime barTime) {
        try {
            OrderAction action = direction == SignalType.LONG ? OrderAction.OPEN_LONG : OrderAction.OPEN_SHORT;
            TradeSignal signal = new TradeSignal();
            signal.setSymbol(symbol);
            signal.setTimeframe(timeframe);
            signal.setKlineTime(formatKlineTime(barTime));
            signal.setOrderAction(action);
            signal.setStatus(TradeStatus.FILLED);
            signal.setOrderSn(positionId);
            signal.setOrderItemSn(orderItemSn);
            signal.setExecutedPrice(executedPrice);
            signal.setExecutedAmount(executedAmount);
            signal.setExecutedTime(toDate(barTime));
            signal.setDecisionReason(action.getLabel());
            signal.setRiskLevel("MEDIUM");
            signal.setPositionRatio(BigDecimal.ONE);
            signal.setPriority(5);
            signal.setCreator("SYSTEM");
            signal.setCreateTime(new Date());
            signal.setUpdater("SYSTEM");
            signal.setUpdateTime(new Date());
            signal.setDeleted(false);
            tradeSignalService.createTradeSignal(signal);
            log.debug("[RealtimeGateway] 开仓信号已保存: positionId={}, action={}", positionId, action);
        } catch (Exception e) {
            log.error("[RealtimeGateway] 保存开仓信号失败: positionId={}", positionId, e);
        }
    }

    private void createCloseSignal(String positionId, String orderItemSn, String symbol,
                                    BigDecimal exitPrice, BigDecimal closedVolume,
                                    BigDecimal pnl, ExitType exitType, LocalDateTime barTime,
                                    OrderSideEnum orderSide) {
        try {
            OrderAction action = orderSide == OrderSideEnum.BUY ? OrderAction.CLOSE_LONG : OrderAction.CLOSE_SHORT;
            TradeSignal signal = new TradeSignal();
            signal.setSymbol(symbol);
            signal.setTimeframe(timeframe);
            signal.setKlineTime(formatKlineTime(barTime));
            signal.setOrderAction(action);
            signal.setStatus(TradeStatus.FILLED);
            signal.setOrderSn(positionId);
            signal.setOrderItemSn(orderItemSn);
            signal.setExecutedPrice(exitPrice);
            signal.setExecutedAmount(closedVolume);
            signal.setExecutedTime(toDate(barTime));
            signal.setPnlAmount(pnl);
            signal.setDecisionReason(exitType != null ? exitType.name() : action.getLabel());
            signal.setRiskLevel("MEDIUM");
            signal.setPositionRatio(BigDecimal.ONE);
            signal.setPriority(5);
            signal.setCreator("SYSTEM");
            signal.setCreateTime(new Date());
            signal.setUpdater("SYSTEM");
            signal.setUpdateTime(new Date());
            signal.setDeleted(false);
            tradeSignalService.createTradeSignal(signal);
            log.debug("[RealtimeGateway] 平仓信号已保存: positionId={}, exitType={}, pnl={}",
                    positionId, exitType, pnl);
        } catch (Exception e) {
            log.error("[RealtimeGateway] 保存平仓信号失败: positionId={}", positionId, e);
        }
    }

    // ==================== 工具方法 ====================

    private String generateOrderItemSn() {
        return "E" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    /**
     * LocalDateTime → Date，对齐 formatKlineTime 逻辑：
     * IndicatorWrapHelper 构建 BarSeries 时将上海时区时间当作 UTC 处理，导致 bar.getBeginTime() 偏移了时区偏移量，
     * 因此需要通过 epoch millis 减去时区偏移量来反算原始 Candlestick.id。
     */
    private Date toDate(LocalDateTime ldt) {
        if (ldt == null) return new Date();
        long barEpochMillis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(ldt);
        long candlestickId = barEpochMillis - offset.getTotalSeconds() * 1000L;
        return new Date(candlestickId);
    }

    /**
     * 格式化K线时间字符串，与 Candlestick.timeStr 保持一致。
     * IndicatorWrapHelper 构建 BarSeries 时将上海时区时间当作 UTC 处理，导致 bar.getBeginTime() 偏移了时区偏移量，
     * 因此需要通过 epoch millis 减去时区偏移量来反算原始 Candlestick.id，再用 longConvertDateTime 格式化。
     */
    private String formatKlineTime(LocalDateTime ldt) {
        if (ldt == null) return "";
        long barEpochMillis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(ldt);
        long candlestickId = barEpochMillis - offset.getTotalSeconds() * 1000L;
        return com.chain.ai.trade.common.utils.DateUtil.longConvertDateTime(candlestickId);
    }
}
