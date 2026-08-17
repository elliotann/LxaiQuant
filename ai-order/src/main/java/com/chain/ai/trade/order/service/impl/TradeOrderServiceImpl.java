package com.chain.ai.trade.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.common.entity.constants.Exchange;
import com.chain.ai.trade.common.entity.constants.OrderPriceType;
import com.chain.ai.trade.common.entity.dto.ContractSpec;
import com.chain.ai.trade.common.push.NotificationPushMessage;
import com.chain.ai.trade.common.push.RedisNotificationPublisher;
import com.chain.ai.trade.common.utils.*;
import com.chain.ai.trade.extension.core.constants.ExitType;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.IBotParameterService;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.chain.ai.trade.order.entity.dos.*;
import com.chain.ai.trade.order.utils.CommissionRateHelper;

import com.chain.ai.trade.order.entity.dto.*;
import com.chain.ai.trade.order.entity.vo.OrderVO;
import com.chain.ai.trade.order.entity.vo.PageVO;
import com.chain.ai.trade.order.entity.vo.RobotOrderReportVO;
import com.chain.ai.trade.order.entity.vo.RobotOrderReportPeriodVO;
import com.chain.ai.trade.backtest.entity.dos.BacktestEquityCurve;
import com.chain.ai.trade.backtest.mapper.BacktestEquityCurveMapper;
import com.chain.ai.trade.order.entity.vo.EquityCurvePoint;
import com.chain.ai.trade.common.entity.constants.OrderAction;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import com.chain.ai.trade.engine.signal.entity.constants.TradeStatus;
import com.chain.ai.trade.engine.signal.service.ITradeSignalService;
import com.chain.ai.trade.engine.xchange.factory.ExchangeWrapFactory;
import com.chain.ai.trade.engine.xchange.ExchangeTradeService;
import com.chain.ai.trade.order.entity.SnowFlake;
import com.chain.ai.trade.common.entity.constants.OrderSideEnum;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.order.entity.constants.TradeOrderEnum;
import com.chain.ai.trade.common.entity.param.TradingStrategyParams;
import com.chain.ai.trade.order.exception.ExchangeOrderException;
import com.chain.ai.trade.order.mapper.TradeOrderMapper;
import com.chain.ai.trade.order.mapper.TradeOrderItemMapper;
import com.chain.ai.trade.order.mapper.TradeOrderCloseMapper;
import com.chain.ai.trade.order.mapper.TradeOrderCloseItemMapper;
import com.chain.ai.trade.order.service.*;
import com.chain.ai.trade.common.entity.dto.BacktestTradeRecord;

import org.springframework.data.redis.core.RedisTemplate;
import com.chain.ai.trade.order.validation.OrderValidationContext;
import com.chain.ai.trade.order.validation.OrderValidationHandler;
import com.chain.ai.trade.order.validation.ValidationResult;
import com.chain.ai.trade.order.validation.chain.OrderValidationChainManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.chain.ai.trade.common.entity.constants.OrderAction.LBAP;
import static com.chain.ai.trade.common.entity.constants.OrderAction.SBAP;
import static org.knowm.xchange.dto.Order.OrderType.EXIT_ASK;
import static org.knowm.xchange.dto.Order.OrderType.EXIT_BID;

/**
 * 交易订单服务实现类 - 增强平仓功能版本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeOrderServiceImpl implements ITradeOrderService {
    private static final ThreadLocal<Boolean> BATCH_TAKE_PROFIT_FLAG = new ThreadLocal<>();

    /** 回测模式手续费率，由 BacktestController 在执行策略前设置 */
    private static final ThreadLocal<BigDecimal> TEST_COMMISSION_RATE = new ThreadLocal<>();

    /**
     * 设置回测模式手续费率（静态，供 BacktestController 调用）
     */
    public static void setTestCommissionRate(BigDecimal rate) {
        if (rate != null) {
            TEST_COMMISSION_RATE.set(rate);
        } else {
            TEST_COMMISSION_RATE.remove();
        }
    }

    /**
     * 清除回测模式手续费率
     */
    public static void clearTestCommissionRate() {
        TEST_COMMISSION_RATE.remove();
    }

    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final TradeOrderCloseMapper tradeOrderCloseMapper;
    private final TradeOrderCloseItemMapper tradeOrderCloseItemMapper;
    private final ITradingAccountService memberThirdAccountService;
    private final OrderValidationChainManager validationChainManager;
    private final ITradeSignalService tradeSignalService;
    private final ITradingBotService tradingBotService;
    private final IBotParameterService botParameterService;
    private final RedisTemplate<String, String> redisTemplate;
    private final BacktestEquityCurveMapper backtestEquityCurveMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RedisNotificationPublisher notificationPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RedisCache redisCache;

    @Value("${trading.order-validation.lock-timeout-seconds:1}")
    private int lockTimeoutSeconds;

    // 尾数阈值默认值
    private static final BigDecimal DEFAULT_TAIL_THRESHOLD = new BigDecimal("0.000001");

    // ==================== 原有方法实现 ====================

    @Override
    @Transactional(noRollbackFor = ExchangeOrderException.class)
    public String createOrder(TradingStrategyParams params) {
        // 生成锁键
        String lockKey = generateOrderLockKey(params);

        // 获取分布式锁（并发控制基础功能，始终启用）
        if (!acquireDistributedLockWithRetry(lockKey)) {
            throw new IllegalStateException("系统繁忙，获取订单创建锁失败，请稍后重试");
        }

        try {
            log.info("创建订单: accountId={}, symbol={}, side={}, amount={}",
                    params.getAccountId(), params.getSymbol(), params.getSide(), params.getAmount());

            // 获取账户信息
            TradingAccount account = memberThirdAccountService.getByAccountId(params.getAccountId());
            if (account == null) {
                throw new RuntimeException("账户信息不存在: " + params.getAccountId());
            }

            // 🔒 分布式锁保护下的设计模式校验：防止重复开同方向订单
            // 使用责任链+策略模式进行完整的业务校验（double-check模式）
            performValidationChecks(params);

            // 如果是市价单，检查并取消同方向的挂单（PENDING）
            OrderPriceType priceType = OrderPriceType.MARKET;
            if (params.getEntryType() != null) {
                priceType = params.getEntryType();
            } else {
                try {
                    if (params.getAdditionalParams() != null) {
                        Object ot = params.getAdditionalParams().get("orderType");
                        if (ot != null) {
                            String s = String.valueOf(ot).trim().toUpperCase();
                            if ("MARKET".equals(s)) priceType = OrderPriceType.MARKET;
                            else if ("LIMIT".equals(s)) priceType = OrderPriceType.LIMIT;
                            else if ("CONDITION".equals(s)) priceType = OrderPriceType.CONDITION;
                        }
                    }
                } catch (Exception ignore) {}
            }

            if (priceType == OrderPriceType.MARKET) {
                cancelPendingOrders(params.getAccountId(), params.getSymbol(), params.getSide());
            }

            Date orderTime = params.getOrderTime() != null ? params.getOrderTime() : new Date();
            Long openSignalId = null;
            String orderSn = params.getPositionId();
            if (orderSn == null || orderSn.isBlank()) {
                // V2 引擎新开仓时通过 clientOrderId 传递统一的仓位ID（同时也是 DB orderSn）
                if (params.getClientOrderId() != null && !params.getClientOrderId().isBlank()) {
                    orderSn = params.getClientOrderId();
                } else {
                    orderSn = generateOrderSn();
                }
                params.setPositionId(orderSn);
            }
            TradePosition existingOrder = null;
            try {
                LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(TradePosition::getPositionId, orderSn).last("limit 1");
                existingOrder = tradeOrderMapper.selectOne(wrapper);
            } catch (Exception e) {
                log.error("查询已有订单失败: orderSn={}", orderSn, e);
            }

            if (existingOrder != null) {
                if (existingOrder.getExchangeOrderId() != null && !existingOrder.getExchangeOrderId().isBlank()) {
                    log.info("检测到已下单成功的幂等订单，直接返回: orderSn={}, exchangeOrderId={}", orderSn, existingOrder.getExchangeOrderId());
                    return existingOrder.getId();
                }
                log.info("复用已有幂等订单继续下单: orderSn={}, orderId={}", orderSn, existingOrder.getId());
            }

            // 创建主订单（初始状态为OPEN）
            // 回测场景下 params.getPrice() 可能为空，但数据库 buy_price 字段非空，这里保证给一个安全的默认值
            BigDecimal buyPrice = params.getPrice() != null ? params.getPrice() : BigDecimal.ZERO;

            // 如果是限价单且提供了限价，则使用限价作为开仓价格
            if ((priceType == OrderPriceType.LIMIT || priceType == OrderPriceType.CONDITION) && params.getLimitPrice() != null) {
                buyPrice = params.getLimitPrice();
            }

            // 计算止盈止损价格
            BigDecimal lossPrice = params.getStopLossPrice();
            BigDecimal gainPrice = params.getTakeProfitPrice();
            
            if (lossPrice == null && params.getStopLossPercentage() != null && params.getStopLossPercentage().compareTo(BigDecimal.ZERO) > 0) {
                if (OrderSideEnum.valueOf(params.getSide()) == OrderSideEnum.BUY) {
                    lossPrice = buyPrice.multiply(BigDecimal.ONE.subtract(params.getStopLossPercentage()));
                } else {
                    lossPrice = buyPrice.multiply(BigDecimal.ONE.add(params.getStopLossPercentage()));
                }
            }
            
            if (gainPrice == null && params.getTakeProfitPercentage() != null && params.getTakeProfitPercentage().compareTo(BigDecimal.ZERO) > 0) {
                if (OrderSideEnum.valueOf(params.getSide()) == OrderSideEnum.BUY) {
                    gainPrice = buyPrice.multiply(BigDecimal.ONE.add(params.getTakeProfitPercentage()));
                } else {
                    gainPrice = buyPrice.multiply(BigDecimal.ONE.subtract(params.getTakeProfitPercentage()));
                }
            }

            lossPrice = PricePrecisionUtils.normalizePrice(params.getSymbol(), lossPrice);
            gainPrice = PricePrecisionUtils.normalizePrice(params.getSymbol(), gainPrice);

            // 设置会员ID：优先使用交易账户上的memberId
            String memberId = account.getMemberId();

            // 回测模式下，预计算开仓手续费（仅开仓），平仓手续费在平仓流程计算
            BigDecimal openFee = null;
            if (Boolean.TRUE.equals(params.getTestMode())) {
                // 取合约规格（Redis优先，默认0.1/1）
                ContractSpec spec = ContractSpecUtils.getContractSpec(redisCache, Exchange.OKX, params.getSymbol());
                // 平台单边费率（与平仓流程一致），开仓仅计算单边
                String platformName = account.getMemberPlatform() != null ? account.getMemberPlatform().name() : "OKX";
                // 回测模式：优先使用 params 中前端传入的手续费率，否则使用平台默认费率
                BigDecimal feeRateOpen = params.getCommissionRate() != null ? params.getCommissionRate() : getFeeRateByPlatform(platformName, params.getRobotId());
                BigDecimal amount = params.getAmount() != null ? params.getAmount() : BigDecimal.ZERO;
                BigDecimal openValue  = spec.getContractSize().multiply(amount).multiply(spec.getContractMult()).multiply(buyPrice);
                BigDecimal chargeOpen  = ProfitCalcUtils.calcFee(openValue, feeRateOpen);
                openFee = (chargeOpen != null ? chargeOpen : BigDecimal.ZERO);
                log.info("回测订单开仓手续费计算: symbol={}, amount={}, openValue={}, feeRate(open)={}, openFee={}",
                        params.getSymbol(), amount, openValue, feeRateOpen, openFee);
            }
            if (params.getOrderTime() != null) {
                log.info("使用K线数据时间作为开仓时间: orderSn={}, orderTime={}", orderSn, orderTime);
            } else {
                log.info("使用当前系统时间作为开仓时间: orderSn={}, orderTime={}", orderSn, orderTime);
            }

            TradePosition order;
            TradeEntry orderItem = null;
            if (existingOrder != null) {
                order = existingOrder;
            } else {
                CandlestickIntervalEnum klineInterval = resolveKlineInterval(params.getInterval());
                TradePosition.TradeOrderStatus initialStatus = (priceType == OrderPriceType.LIMIT || priceType == OrderPriceType.CONDITION)
                        ? TradePosition.TradeOrderStatus.PENDING
                        : TradePosition.TradeOrderStatus.OPEN;

                order = TradePosition.builder()
                        .positionId(orderSn)
                        .memberId(memberId)
                        .accountId(params.getAccountId() != null ? String.valueOf(params.getAccountId()) : null)
                        .robotId(params.getRobotId())
                        .testReportId(params.getTestReportId())
                        .symbol(params.getSymbol())
                        .memberPlatform(params.getMemberPlatform() != null ? params.getMemberPlatform() : Exchange.OKX)
                        .orderSideEnum(OrderSideEnum.valueOf(params.getSide()))
                        .priceType(priceType)
                        .leverRate(params.getLeverage() != null && params.getLeverage() > 0 ? params.getLeverage() : 1)
                        .amount(params.getAmount())
                        .volume(params.getAmount())
                        .buyPrice(buyPrice)
                        .buyAvgPrice(buyPrice)
                        .orderTime(orderTime)
                        .tradeOrderStatus(initialStatus)
                        .test(Boolean.TRUE.equals(params.getTestMode()))
                        .charge(openFee)
                        .lossPrice(lossPrice)
                        .oriLossPrice(lossPrice)
                        .gainPrice(gainPrice)
                        .oriGainPrice(gainPrice)
                        .klineInterval(klineInterval)
                        .signalId(openSignalId)
                        .createTime(new Date())
                        .build();

                tradeOrderMapper.insert(order);

                orderItem = TradeEntry.builder()
                        .positionId(orderSn)
                        .entrySn(orderSn)
                        .orderSideEnum(OrderSideEnum.valueOf(params.getSide()))
                        .symbol(params.getSymbol())
                        .orderTime(orderTime)
                        .buyPrice(buyPrice)
                        .amount(params.getAmount())
                        .volume(params.getAmount())
                        .tradeOrderItemStatus(initialStatus)
                        .createTime(new Date())
                        .deleteFlag(false)
                        .lossPrice(lossPrice)
                        .gainPrice(gainPrice)
                        .build();

                tradeOrderItemMapper.insert(orderItem);
            }

            // 测试/回测模式下，不应调用真实交易所，只记录订单到数据库
            if (Boolean.TRUE.equals(params.getTestMode())) {
                log.info("测试/回测模式订单，不调用交易所，仅记录订单: orderSn={}, symbol={}, side={}, amount={}",
                        orderSn, params.getSymbol(), params.getSide(), params.getAmount());
                // 如果是限价单，保持 PENDING 状态，等待撮合；否则直接标记为 DEAL
                TradePosition.TradeOrderStatus finalStatus = (priceType == OrderPriceType.LIMIT)
                        ? TradePosition.TradeOrderStatus.PENDING
                        : TradePosition.TradeOrderStatus.DEAL;

                order.setTradeOrderStatus(finalStatus);
                tradeOrderMapper.updateById(order);

                orderItem.setTradeOrderItemStatus(finalStatus);
                tradeOrderItemMapper.updateById(orderItem);

                if (priceType == OrderPriceType.MARKET) {
                    updateOrderAggregates(orderSn);
                }

                log.info("测试/回测订单记录完成: orderId={}, orderSn={}, status={}", order.getId(), orderSn, finalStatus);
                if (notificationPublisher != null) {
                    notificationPublisher.publish(NotificationPushMessage.builder()
                            .type("trade")
                            .title("开仓成功")
                            .content("回测开仓成功 orderSn=" + orderSn + " symbol=" + params.getSymbol()
                                    + " side=" + params.getSide() + " amount=" + params.getAmount())
                            .symbol(params.getSymbol())
                            .severity("info")
                            .isTest(Boolean.TRUE.equals(params.getTestMode()))
                            .userId(memberId)
                            .build());
                }
                return order.getId();
            }

            // 实盘模式：调用交易所下单（使用直连OKHTTP，绕过XChange）
            try {
                ExchangeTradeService exchangeService = createExchangeService(account);
                // 模拟账户，K线数据是不对的，把止盈止损价格重置为null
                Boolean simulatedFlag = params.getSimulated();
                if (simulatedFlag == null) {
                    simulatedFlag = account.getSimulated();
                }
                boolean simulated = Boolean.TRUE.equals(simulatedFlag);
                params.setSimulated(simulated);

                TradingStrategyParams execParams = params;
                if (priceType == OrderPriceType.MARKET) {
                    execParams = params.toBuilder().price(null).build();
                }
                execParams.setSimulated(simulated);
                // 暂时放开模拟账户止盈止损设置，后续数据不对再恢复
                // if (simulated) {
                //     execParams.setStopLossPrice(null);
                //     execParams.setTakeProfitPrice(null);
                // }
                String exchangeOrderId = exchangeService.createOrder(execParams);

                TradePosition.TradeOrderStatus finalStatus = (priceType == OrderPriceType.LIMIT || priceType == OrderPriceType.CONDITION)
                        ? TradePosition.TradeOrderStatus.PENDING
                        : TradePosition.TradeOrderStatus.DEAL;
                order.setTradeOrderStatus(finalStatus);
                order.setExchangeOrderId(exchangeOrderId);
                tradeOrderMapper.updateById(order);

                if (orderItem != null) {
                    orderItem.setTradeOrderItemStatus(finalStatus);
                    tradeOrderItemMapper.updateById(orderItem);
                } else {
                    LambdaQueryWrapper<TradeEntry> itemWrapper = new LambdaQueryWrapper<>();
                    itemWrapper.eq(TradeEntry::getPositionId, orderSn).last("limit 1");
                    TradeEntry existingItem = tradeOrderItemMapper.selectOne(itemWrapper);
                    if (existingItem != null) {
                        existingItem.setTradeOrderItemStatus(finalStatus);
                        tradeOrderItemMapper.updateById(existingItem);
                    }
                }

                if (priceType == OrderPriceType.MARKET) {
                    try {
                        String symbol = params.getSymbol();
                        BigDecimal fillPrice = exchangeService.getCurrentPrice(symbol);
                        if (fillPrice != null && fillPrice.compareTo(BigDecimal.ZERO) > 0) {
                            order.setBuyPrice(fillPrice);
                            tradeOrderMapper.updateById(order);
                            LambdaQueryWrapper<TradeEntry> itemUpdateWrapper = new LambdaQueryWrapper<>();
                            itemUpdateWrapper.eq(TradeEntry::getPositionId, orderSn);
                            TradeEntry itemUpdate = new TradeEntry();
                            itemUpdate.setBuyPrice(fillPrice);
                            tradeOrderItemMapper.update(itemUpdate, itemUpdateWrapper);
                        }
                    } catch (Exception e) {
                        log.warn("获取市价单成交价失败，使用初始价格: orderSn={}", orderSn, e);
                    }
                    updateOrderAggregates(orderSn);
                }

                if (openSignalId != null) {
                    try {
                        tradeSignalService.updateTradeSignalStatus(openSignalId, TradeStatus.EXECUTING);
                    } catch (Exception e) {
                        log.error("更新开仓业务信号状态失败: signalId={}, orderSn={}", openSignalId, orderSn, e);
                    }
                }

                log.info("订单创建成功: orderId={}, orderSn={}, exchangeOrderId={}", order.getId(), orderSn, exchangeOrderId);
                if (notificationPublisher != null) {
                    notificationPublisher.publish(NotificationPushMessage.builder()
                            .type("trade")
                            .title("开仓成功")
                            .content("开仓成功 orderSn=" + orderSn + " symbol=" + params.getSymbol()
                                    + " side=" + params.getSide() + " amount=" + params.getAmount()
                                    + " price=" + order.getBuyPrice())
                            .symbol(params.getSymbol())
                            .severity("info")
                            .isTest(Boolean.TRUE.equals(params.getTestMode()))
                            .userId(memberId)
                            .build());
                }
                return order.getId();
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "exchange create order failed";
                try {
                    order.setTradeOrderStatus(TradePosition.TradeOrderStatus.CLOSE);
                    order.setRemark("EXCHANGE_ORDER_FAILED: " + msg);
                    tradeOrderMapper.updateById(order);
                    if (orderItem != null) {
                        orderItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.CLOSE);
                        tradeOrderItemMapper.updateById(orderItem);
                    }
                } catch (Exception ex) {
                    log.error("回写订单失败原因失败: orderSn={}", orderSn, ex);
                }
                if (openSignalId != null) {
                    try {
                        tradeSignalService.updateTradeSignalStatus(openSignalId, TradeStatus.FAILED);
                    } catch (Exception ex) {
                        log.error("更新开仓业务信号失败状态失败: signalId={}, orderSn={}", openSignalId, orderSn, ex);
                    }
                }
                if (notificationPublisher != null) {
                    notificationPublisher.publish(NotificationPushMessage.builder()
                            .type("trade")
                            .title("开仓失败")
                            .content("开仓失败 orderSn=" + orderSn + " msg=" + msg)
                            .symbol(params.getSymbol())
                            .severity("warning")
                            .isTest(Boolean.TRUE.equals(params.getTestMode()))
                            .userId(memberId)
                            .build());
                }
                throw new ExchangeOrderException("下单失败(已落库幂等单): orderSn=" + orderSn + ", msg=" + msg, e);
            }

        } catch (ExchangeOrderException e) {
            log.error("创建订单失败（交易所下单失败，已落库幂等单）", e);
            throw e;
        } catch (Exception e) {
            log.error("创建订单失败", e);
            throw new RuntimeException("创建订单失败: " + e.getMessage(), e);
        } finally {
            // 释放分布式锁（并发控制基础功能，始终启用）
            releaseDistributedLock(lockKey);
        }
    }

    @Override
    @Transactional
    public boolean stopWinOrder(TradingStrategyParams params) {
        TradingAccount account = null;
        try {
            log.info("执行止盈订单: accountId={}, symbol={}", params.getAccountId(), params.getSymbol());

            // 测试/回测模式：不调用交易所，只由回测结果/信号系统管理出场
            if (Boolean.TRUE.equals(params.getTestMode())) {
                log.info("测试/回测模式止盈调用，跳过交易所和真实订单更新: accountId={}, symbol={}",
                        params.getAccountId(), params.getSymbol());
                return true;
            }

            // 获取账户信息
            account = memberThirdAccountService.getByAccountId(params.getAccountId());
            if (account == null) {
                log.error("账户信息不存在: {}", params.getAccountId());
                return false;
            }

            // 查询需要止盈的持仓订单
            LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TradePosition::getAccountId, params.getAccountId())
                    .eq(TradePosition::getSymbol, params.getSymbol())
                    .eq(TradePosition::getTradeOrderStatus, TradePosition.TradeOrderStatus.DEAL)
                    .orderByDesc(TradePosition::getCreateTime); // 按创建时间倒序

            List<TradePosition> positionOrders = tradeOrderMapper.selectList(wrapper);
            if (positionOrders.isEmpty()) {
                log.info("没有找到持仓订单，无需止盈: accountId={}, symbol={}", params.getAccountId(), params.getSymbol());
                return true;
            }

            // 获取当前市场价格
            ExchangeTradeService exchangeService = createExchangeService(account);
            BigDecimal currentPrice = exchangeService.getCurrentPrice(params.getSymbol());

            boolean hasStopWin = false;
            for (TradePosition order : positionOrders) {
                // 计算收益率：(当前价格 - 买入价格) / 买入价格
                BigDecimal buyPrice = order.getBuyPrice();
                BigDecimal profitRate = currentPrice.subtract(buyPrice).divide(buyPrice, 4, BigDecimal.ROUND_HALF_UP);

                // 检查是否达到止盈条件（这里假设止盈点为5%）
                BigDecimal stopWinThreshold = new BigDecimal("0.05");
                if (profitRate.compareTo(stopWinThreshold) >= 0) {
                    // 创建卖出订单
                    TradingStrategyParams sellParams = TradingStrategyParams.builder()
                            .accountId(params.getAccountId())
                            .symbol(params.getSymbol())
                            .side("SELL")
                            .amount(order.getAmount())
                            .price(currentPrice)
                            .build();

                    String sellOrderId = exchangeService.createOrder(sellParams);

                    // 更新原订单状态
                    order.setTradeOrderStatus(TradePosition.TradeOrderStatus.CLOSE);
                    order.setUpdateTime(new Date());
                    tradeOrderMapper.updateById(order);

                    // 创建平仓记录
                    TradePosition closeOrder = TradePosition.builder()
                            .positionId(generateOrderSn())
                            .accountId(params.getAccountId() != null ? String.valueOf(params.getAccountId()) : null)
                            .robotId(params.getRobotId())
                            .symbol(params.getSymbol())
                            .orderSideEnum(OrderSideEnum.SELL)
                            .amount(order.getAmount())
                            .buyPrice(currentPrice)
                            .orderTime(new Date())
                            .tradeOrderStatus(TradePosition.TradeOrderStatus.DEAL)
                            .test(Boolean.TRUE.equals(params.getTestMode()))
                            .exchangeOrderId(sellOrderId) // 保存交易所订单ID
                            .build();
                    tradeOrderMapper.insert(closeOrder);

                    log.info("止盈成功: 原订单ID={}, 平仓订单ID={}, 收益率={}",
                            order.getId(), closeOrder.getId(), profitRate.multiply(new BigDecimal("100")).setScale(2) + "%");
                    if (notificationPublisher != null) {
                        notificationPublisher.publish(NotificationPushMessage.builder()
                                .type("trade")
                                .title("止盈触发")
                                .content("止盈成功 symbol=" + params.getSymbol() + " 收益率="
                                        + profitRate.multiply(new BigDecimal("100")).setScale(2) + "%")
                                .symbol(params.getSymbol())
                                .severity("info")
                                .isTest(Boolean.TRUE.equals(params.getTestMode()))
                                .userId(account.getMemberId())
                                .build());
                    }
                    hasStopWin = true;
                }
            }

            if (!hasStopWin) {
                log.info("未达到止盈条件: accountId={}, symbol={}", params.getAccountId(), params.getSymbol());
            }

            return true;

        } catch (Exception e) {
            log.error("执行止盈订单失败", e);
            if (notificationPublisher != null) {
                notificationPublisher.publish(NotificationPushMessage.builder()
                        .type("trade")
                        .title("止盈执行失败")
                        .content("止盈失败 symbol=" + params.getSymbol() + " msg=" + e.getMessage())
                        .symbol(params.getSymbol())
                        .severity("warning")
                        .isTest(Boolean.TRUE.equals(params.getTestMode()))
                        .userId(account.getMemberId())
                        .build());
            }
            return false;
        }
    }

    @Override
    @Transactional
    public boolean stopLossOrder(TradingStrategyParams params) {
        TradingAccount account = null;
        try {
            log.info("执行止损订单: accountId={}, symbol={}", params.getAccountId(), params.getSymbol());

            // 测试/回测模式：不调用交易所，只由回测结果/信号系统管理出场
            if (Boolean.TRUE.equals(params.getTestMode())) {
                log.info("测试/回测模式止损调用，跳过交易所和真实订单更新: accountId={}, symbol={}",
                        params.getAccountId(), params.getSymbol());
                return true;
            }

            // 获取账户信息
            account = memberThirdAccountService.getByAccountId(params.getAccountId());
            if (account == null) {
                log.error("账户信息不存在: {}", params.getAccountId());
                return false;
            }

            // 查询需要止损的持仓订单
            LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TradePosition::getAccountId, params.getAccountId())
                    .eq(TradePosition::getSymbol, params.getSymbol())
                    .eq(TradePosition::getTradeOrderStatus, TradePosition.TradeOrderStatus.DEAL)
                    .orderByDesc(TradePosition::getCreateTime); // 按创建时间倒序

            List<TradePosition> positionOrders = tradeOrderMapper.selectList(wrapper);
            if (positionOrders.isEmpty()) {
                log.info("没有找到持仓订单，无需止损: accountId={}, symbol={}", params.getAccountId(), params.getSymbol());
                return true;
            }

            // 获取当前市场价格
            ExchangeTradeService exchangeService = createExchangeService(account);
            BigDecimal currentPrice = exchangeService.getCurrentPrice(params.getSymbol());

            boolean hasStopLoss = false;
            for (TradePosition order : positionOrders) {
                // 计算亏损率：(买入价格 - 当前价格) / 买入价格
                BigDecimal buyPrice = order.getBuyPrice();
                BigDecimal lossRate = buyPrice.subtract(currentPrice).divide(buyPrice, 4, RoundingMode.HALF_UP);

                // 检查是否达到止损条件（这里假设止损点为3%）
                BigDecimal stopLossThreshold = new BigDecimal("0.03");
                if (lossRate.compareTo(stopLossThreshold) >= 0) {
                    // 创建卖出订单止损
                    TradingStrategyParams sellParams = TradingStrategyParams.builder()
                            .accountId(params.getAccountId())
                            .symbol(params.getSymbol())
                            .side("SELL")
                            .amount(order.getAmount())
                            .price(currentPrice)
                            .build();

                    String sellOrderId = exchangeService.createOrder(sellParams);

                    // 更新原订单状态
                    order.setTradeOrderStatus(TradePosition.TradeOrderStatus.CLOSE);
                    order.setUpdateTime(new Date());
                    tradeOrderMapper.updateById(order);

                    // 创建平仓记录
                    TradePosition closeOrder = TradePosition.builder()
                            .positionId(generateOrderSn())
                            .accountId(params.getAccountId() != null ? String.valueOf(params.getAccountId()) : null)
                            .robotId(params.getRobotId())
                            .symbol(params.getSymbol())
                            .orderSideEnum(OrderSideEnum.SELL)
                            .amount(order.getAmount())
                            .buyPrice(currentPrice)
                            .orderTime(new Date())
                            .tradeOrderStatus(TradePosition.TradeOrderStatus.DEAL)
                            .test(Boolean.TRUE.equals(params.getTestMode()))
                            .exchangeOrderId(sellOrderId) // 保存交易所订单ID
                            .build();
                    tradeOrderMapper.insert(closeOrder);

                    log.info("止损成功: 原订单ID={}, 平仓订单ID={}, 亏损率={}",
                            order.getId(), closeOrder.getId(), lossRate.multiply(new BigDecimal("100")).setScale(2) + "%");
                    if (notificationPublisher != null) {
                        notificationPublisher.publish(NotificationPushMessage.builder()
                                .type("risk")
                                .title("止损触发")
                                .content("止损成功 symbol=" + params.getSymbol() + " 亏损率="
                                        + lossRate.multiply(new BigDecimal("100")).setScale(2) + "%")
                                .symbol(params.getSymbol())
                                .severity("critical")
                                .isTest(Boolean.TRUE.equals(params.getTestMode()))
                                .userId(account.getMemberId())
                                .build());
                    }
                    hasStopLoss = true;
                }
            }

            if (!hasStopLoss) {
                log.info("未达到止损条件: accountId={}, symbol={}", params.getAccountId(), params.getSymbol());
            }

            return true;

        } catch (Exception e) {
            log.error("执行止损订单失败", e);
            if (notificationPublisher != null) {
                notificationPublisher.publish(NotificationPushMessage.builder()
                        .type("risk")
                        .title("止损执行失败")
                        .content("止损执行失败 symbol=" + params.getSymbol() + " msg=" + e.getMessage())
                        .symbol(params.getSymbol())
                        .severity("critical")
                        .isTest(Boolean.TRUE.equals(params.getTestMode()))
                        .userId(account.getMemberId())
                        .build());
            }
            return false;
        }
    }

    @Override
    @Transactional(noRollbackFor = ExchangeOrderException.class)
    public boolean suppOrder(TradingStrategyParams params) {
        try {
            log.info("执行补仓订单: accountId={}, symbol={}, amount={}, orderSn={}",
                    params.getAccountId(), params.getSymbol(), params.getAmount(), params.getPositionId());

            boolean isTest = Boolean.TRUE.equals(params.getTestMode());

            TradePosition targetOrder;
            if (params.getPositionId() == null) {
                log.warn("补仓未提供orderSn，将使用最近持仓订单");
                List<OrderVO> positionOrders = getPositionOrders(params.getAccountId(), params.getSymbol());
                if (positionOrders.isEmpty()) {
                    log.error("没有可用的持仓订单，无法执行补仓: accountId={}, symbol={}",
                            params.getAccountId(), params.getSymbol());
                    return false;
                }
                String side = params.getSide();
                OrderVO recentOrder;
                if (side != null && !side.trim().isEmpty()) {
                    String normalizedSide = side.trim().toUpperCase();
                    recentOrder = null;
                    for (OrderVO order : positionOrders) {
                        if (order == null || order.getOrderSide() == null) {
                            continue;
                        }
                        if (normalizedSide.equalsIgnoreCase(order.getOrderSide())) {
                            recentOrder = order;
                            break;
                        }
                    }
                    if (recentOrder == null) {
                        log.error("没有找到同向的持仓订单，无法执行补仓: accountId={}, symbol={}, side={}",
                                params.getAccountId(), params.getSymbol(), normalizedSide);
                        return false;
                    }
                } else {
                    boolean hasBuy = false;
                    boolean hasSell = false;
                    for (OrderVO order : positionOrders) {
                        if (order == null || order.getOrderSide() == null) {
                            continue;
                        }
                        if ("BUY".equalsIgnoreCase(order.getOrderSide())) {
                            hasBuy = true;
                        } else if ("SELL".equalsIgnoreCase(order.getOrderSide())) {
                            hasSell = true;
                        }
                    }
                    if (hasBuy && hasSell) {
                        log.error("多空双向持仓且未提供方向，无法确定补仓目标: accountId={}, symbol={}",
                                params.getAccountId(), params.getSymbol());
                        return false;
                    }
                    recentOrder = positionOrders.get(0);
                }
                targetOrder = getOrderByOrderSn(recentOrder.getOrderSn());
                if (targetOrder == null) {
                    log.error("无法找到最近持仓订单的详细信息: orderSn={}", recentOrder.getOrderSn());
                    return false;
                }
                log.info("使用最近持仓订单进行补仓: orderSn={}", targetOrder.getPositionId());
            } else {
                targetOrder = getOrderByOrderSn(params.getPositionId());
                if (targetOrder == null) {
                    log.error("未找到指定的订单: orderSn={}", params.getPositionId());
                    return false;
                }
            }

            if (targetOrder.getTradeOrderStatus() != TradePosition.TradeOrderStatus.DEAL) {
                log.error("订单状态不正确，无法补仓: orderSn={}, status={}",
                        params.getPositionId(), targetOrder.getTradeOrderStatus());
                return false;
            }

            if (!targetOrder.getAccountId().equals(params.getAccountId())) {
                log.error("订单不属于指定账户，无法补仓: orderSn={}, accountId={}",
                        params.getPositionId(), params.getAccountId());
                return false;
            }

            ExchangeTradeService exchangeService = null;
            TradingAccount account = null;
            BigDecimal currentPrice;
            if (!isTest) {
                account = memberThirdAccountService.getByAccountId(params.getAccountId());
                if (account == null) {
                    log.error("账户信息不存在: {}", params.getAccountId());
                    return false;
                }
                exchangeService = createExchangeService(account);
                currentPrice = exchangeService.getCurrentPrice(params.getSymbol());
            } else {
                currentPrice = params.getPrice() != null && params.getPrice().compareTo(BigDecimal.ZERO) > 0
                        ? params.getPrice()
                        : targetOrder.getBuyPrice();
            }
            if (currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("无法获取当前市场价格，无法执行补仓");
                return false;
            }

            BigDecimal additionalAmount = params.getAmount();
            Date suppOrderTime = params.getOrderTime() != null ? params.getOrderTime() : new Date();

            TradeEntry suppOrderItem = null;
            try {
                LambdaQueryWrapper<TradeEntry> suppWrapper = new LambdaQueryWrapper<>();
                suppWrapper.eq(TradeEntry::getPositionId, targetOrder.getPositionId())
                        .eq(TradeEntry::getOrderTime, suppOrderTime)
                        .eq(TradeEntry::getOrderSideEnum, targetOrder.getOrderSideEnum())
                        .eq(TradeEntry::getAmount, additionalAmount)
                        .isNull(TradeEntry::getPlatformOrderSn)
                        .orderByDesc(TradeEntry::getCreateTime)
                        .last("limit 1");
                suppOrderItem = tradeOrderItemMapper.selectOne(suppWrapper);
            } catch (Exception ex) {
                log.error("查询幂等补仓订单项失败: orderSn={}, time={}, amount={}",
                        targetOrder.getPositionId(), suppOrderTime, additionalAmount, ex);
            }

            boolean isNewItem = false;
            boolean suppItemTakeProfitEnabled = false;
            try {
                Object flag = params.getAdditionalParams() != null ? params.getAdditionalParams().get("suppItemTakeProfitEnabled") : null;
                if (flag instanceof Boolean b) {
                    suppItemTakeProfitEnabled = b;
                } else if (flag != null) {
                    String s = flag.toString().trim();
                    suppItemTakeProfitEnabled = "1".equals(s) || "true".equalsIgnoreCase(s);
                }
            } catch (Exception ignored) {
            }
            BigDecimal suppItemGainPrice = suppItemTakeProfitEnabled ? params.getTakeProfitPrice() : null;
            suppItemGainPrice = PricePrecisionUtils.normalizePrice(params.getSymbol(), suppItemGainPrice);
            if (suppOrderItem == null) {
                String clientSn = generateOrderItemSn();
                suppOrderItem = TradeEntry.builder()
                        .positionId(targetOrder.getPositionId())
                        .entrySn(clientSn)
                        .orderSideEnum(targetOrder.getOrderSideEnum())
                        .symbol(targetOrder.getSymbol())
                        .buyPrice(currentPrice)
                        .gainPrice(suppItemGainPrice)
                        .amount(additionalAmount)
                        .volume(additionalAmount)
                        .buyCount(0)
                        .buyWeights(0.0)
                        .sellWeights(0.0)
                        .repair(0)
                        .orderTime(suppOrderTime)
                        .tradeOrderItemStatus(TradePosition.TradeOrderStatus.OPEN)
                        .deleteFlag(false)
                        .build();
                tradeOrderItemMapper.insert(suppOrderItem);
                isNewItem = true;
            } else if (suppItemGainPrice != null && suppOrderItem.getGainPrice() == null) {
                suppOrderItem.setGainPrice(suppItemGainPrice);
                tradeOrderItemMapper.updateById(suppOrderItem);
            }

            if (isTest) {
                if (TradePosition.TradeOrderStatus.DEAL != suppOrderItem.getTradeOrderItemStatus()) {
                    suppOrderItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.DEAL);
                    tradeOrderItemMapper.updateById(suppOrderItem);
                }
                updateOrderAggregates(targetOrder.getPositionId());
                log.info("测试/回测模式补仓完成: 目标订单={}, 补仓数量={}", targetOrder.getPositionId(), additionalAmount);
                TradeSignal tradeSignal = new TradeSignal();
                tradeSignal.setSymbol(params.getSymbol());
                tradeSignal.setTimeframe(params.getInterval());
                Date orderTime = params.getOrderTime() != null ? params.getOrderTime() : new Date();
                tradeSignal.setKlineTime(DateUtil.formatDateTime(orderTime));
                tradeSignal.setOrderAction(targetOrder.getOrderSideEnum() == OrderSideEnum.BUY?LBAP:SBAP);
                tradeSignal.setStatus(TradeStatus.PENDING);
                tradeSignal.setOrderSn(targetOrder.getPositionId());
                tradeSignal.setExpectedPrice(params.getPrice());
                tradeSignal.setExpectedAmount(params.getAmount());
                tradeSignal.setDecisionReason("加仓");
                
                // 复制入场类型和限价单价格
                if (params.getEntryType() != null) {
                    tradeSignal.setEntryType(params.getEntryType().name());
                }
                tradeSignal.setLimitPrice(params.getLimitPrice());
                tradeSignal.setTechnicalSignalId(params.getTechnicalSignalId());
                tradeSignal.setTechnicalSignalHash(params.getTechnicalSignalHash());
                tradeSignal.setTechnicalSignalBrief(params.getTechnicalSignalBrief());

                Long signalId = tradeSignalService.createTradeSignal(tradeSignal);
                log.info("订单加仓成功后，已生成业务信号：signalId={}, orderId={}, side={}", signalId, targetOrder.getId(), targetOrder.getOrderSideEnum());

                return true;
            }

            try {
                String suppSide = targetOrder.getOrderSideEnum() == OrderSideEnum.BUY ? "BUY" : "SELL";
                TradingStrategyParams suppParams = TradingStrategyParams.builder()
                        .accountId(params.getAccountId())
                        .symbol(params.getSymbol())
                        .side(suppSide)
                        .amount(additionalAmount)
                        .price(currentPrice)
                        .orderTime(suppOrderTime)
                        .leverage(targetOrder.getLeverRate())
                        .build();
                if (account != null) {
                    suppParams.setSimulated(account.getSimulated());
                }
                suppParams.setPositionId("A" + suppOrderItem.getId());

                String exchangeOrderId = exchangeService.createOrder(suppParams);
                suppOrderItem.setPlatformOrderSn(exchangeOrderId);
                suppOrderItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.DEAL);
                tradeOrderItemMapper.updateById(suppOrderItem);

                updateOrderAggregates(targetOrder.getPositionId());
                log.info("补仓执行成功: 目标订单={}, 补仓数量={}, 交易所订单ID={}, isNewItem={}",
                        targetOrder.getPositionId(), additionalAmount, exchangeOrderId, isNewItem);

                TradeSignal tradeSignal = new TradeSignal();
                tradeSignal.setSymbol(params.getSymbol());
                tradeSignal.setTimeframe(params.getInterval());
                Date orderTime = params.getOrderTime() != null ? params.getOrderTime() : new Date();
                tradeSignal.setKlineTime(DateUtil.formatDateTime(orderTime));
                tradeSignal.setOrderAction(targetOrder.getOrderSideEnum() == OrderSideEnum.BUY?LBAP:SBAP);
                tradeSignal.setStatus(TradeStatus.PENDING);
                tradeSignal.setOrderSn(targetOrder.getPositionId());
                tradeSignal.setExpectedPrice(params.getPrice());
                tradeSignal.setExpectedAmount(params.getAmount());
                tradeSignal.setDecisionReason("加仓");

                // 复制入场类型和限价单价格
                if (params.getEntryType() != null) {
                    tradeSignal.setEntryType(params.getEntryType().name());
                }
                tradeSignal.setLimitPrice(params.getLimitPrice());
                tradeSignal.setTechnicalSignalId(params.getTechnicalSignalId());
                tradeSignal.setTechnicalSignalHash(params.getTechnicalSignalHash());
                tradeSignal.setTechnicalSignalBrief(params.getTechnicalSignalBrief());

                Long signalId = tradeSignalService.createTradeSignal(tradeSignal);
                log.info("订单加仓成功后，已生成业务信号：signalId={}, orderId={}, side={}", signalId, targetOrder.getId(), targetOrder.getOrderSideEnum());

                return true;
            } catch (Exception ex) {
                throw new ExchangeOrderException("补仓下单失败(已落库幂等补仓订单项): orderSn=" + targetOrder.getPositionId(), ex);
            }

        } catch (ExchangeOrderException e) {
            log.error("执行补仓下单失败(保留幂等订单项): orderSn={}, err={}", params.getPositionId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("执行补仓订单失败", e);
            return false;
        }
    }

    @Override
    public String getOrderStatus(String orderId) {
        TradePosition order = tradeOrderMapper.selectById(orderId);
        return order != null ? order.getTradeOrderStatus().name() : null;
    }

    @Override
    public TradePosition getOrderByOrderSn(String orderSn) {
        if (orderSn == null || orderSn.trim().isEmpty()) {
            log.warn("订单号为空，无法查询订单");
            return null;
        }
        LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradePosition::getPositionId, orderSn);
        return tradeOrderMapper.selectOne(wrapper);
    }

    @Override
    @Transactional
    public boolean cancelOrder(String orderId) {
        try {
            TradePosition order = tradeOrderMapper.selectById(orderId);
            if (order == null) {
                return false;
            }

            if (order.getTradeOrderStatus() != TradePosition.TradeOrderStatus.PENDING) {
                return false;
            }

            order.setTradeOrderStatus(TradePosition.TradeOrderStatus.CLOSE);
            order.setUpdateTime(new Date());
            tradeOrderMapper.updateById(order);

            return true;

        } catch (Exception e) {
            log.error("取消订单失败: {}", orderId, e);
            return false;
        }
    }

    @Override
    public double getPositionSize(Long accountId, String symbol) {
        // 查询该账户该交易对的持仓数量
        LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradePosition::getAccountId, accountId)
                .eq(TradePosition::getSymbol, symbol)
                .eq(TradePosition::getTradeOrderStatus, TradePosition.TradeOrderStatus.DEAL);

        return tradeOrderMapper.selectList(wrapper).stream()
                .mapToDouble(order -> order.getAmount().doubleValue())
                .sum();
    }

    @Override
    public double getAvailableBalance(String accountId) {
        try {
            TradingAccount account = memberThirdAccountService.getByAccountId(accountId);
            if (account == null) {
                log.warn("账户信息不存在: accountId={}", accountId);
                return 0.0;
            }

            // 直接从虚拟列获取 USDT 余额
            BigDecimal usdtBalance = account.getUsdtBalance();
            double balance = usdtBalance != null ? usdtBalance.doubleValue() : 0.0;

            log.debug("获取账户可用余额: accountId={}, balance={}", accountId, balance);
            return balance;

        } catch (Exception e) {
            log.error("获取账户可用余额失败: accountId={}", accountId, e);
            return 0.0;
        }
    }

    /**
     * 生成订单编号
     */
    private String generateOrderSn() {
        return SnowFlake.getIdStr();
    }

    /**
     * 根据账号交易所类型创建对应的交易所交易服务
     */
    private ExchangeTradeService createExchangeService(TradingAccount account) {
        if (account.getMemberPlatform() == null) {
            return new OkxDirectTradeService(account);
        }
        return switch (account.getMemberPlatform()) {
            case GATEIO -> new GateioDirectTradeService(account);
            case OKX -> new OkxDirectTradeService(account);
            case HUOBI -> ExchangeWrapFactory.createExchangeService(account);
            default -> ExchangeWrapFactory.createExchangeService(account);
        };
    }

    private CandlestickIntervalEnum resolveKlineInterval(String interval) {
        if (interval == null || interval.isBlank()) return null;
        String trimmed = interval.trim();
        for (CandlestickIntervalEnum e : CandlestickIntervalEnum.values()) {
            if (e.getCode() != null && e.getCode().equalsIgnoreCase(trimmed)) return e;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        for (CandlestickIntervalEnum e : CandlestickIntervalEnum.values()) {
            if (e.getCode() != null && e.getCode().equalsIgnoreCase(upper)) return e;
        }
        if (upper.endsWith("H")) {
            String code = upper;
            for (CandlestickIntervalEnum e : CandlestickIntervalEnum.values()) {
                if (e.getCode() != null && e.getCode().equalsIgnoreCase(code)) return e;
            }
        }
        if (upper.endsWith("D")) {
            String code = upper;
            for (CandlestickIntervalEnum e : CandlestickIntervalEnum.values()) {
                if (e.getCode() != null && e.getCode().equalsIgnoreCase(code)) return e;
            }
        }
        return null;
    }

    /**
     * 根据平台名称和机器人ID解析手续费率
     * 优先级：bot_parameter 配置 > ThreadLocal（回测传入）> 兜底默认值
     */
    private BigDecimal getFeeRateByPlatform(String platformName, String botId) {
        // 优先从 bot_parameter 获取机器人配置的手续费率
        BigDecimal configRate = CommissionRateHelper.getCommissionRate(botId, botParameterService);
        if (configRate != null) {
            return configRate;
        }
        // 回测模式：使用 ThreadLocal 中设置的手续费率
        BigDecimal testRate = TEST_COMMISSION_RATE.get();
        if (testRate != null) {
            return testRate;
        }
        // 兜底默认值
        return CommissionRateHelper.DEFAULT_COMMISSION_RATE;
    }

    /**
     * 计算手续费（委托 ai-common 统一计算）
     *
     * @param platformName 交易所平台名称
     * @param tradeAmount  交易额（持仓 USDT 价值）
     * @return 手续费
     */
    private BigDecimal calculateCharge(String platformName, BigDecimal tradeAmount) {
        BigDecimal feeRate = getFeeRateByPlatform(platformName, null);
        return ProfitCalcUtils.calcFee(tradeAmount, feeRate);
    }

    @Override
    @Transactional
    public boolean closeOrderByVolume(String orderSn, BigDecimal closeVolume) {
        return closeOrderByVolume(orderSn, closeVolume, null);
    }

    @Override
    @Transactional
    public boolean closeOrderByVolume(String orderSn, BigDecimal closeVolume, BigDecimal currentPrice) {
        return closeOrderByVolume(orderSn, closeVolume, currentPrice, null,null);
    }

    @Override
    @Transactional(noRollbackFor = ExchangeOrderException.class)
    public boolean closeOrderByVolume(String orderSn, BigDecimal closeVolume, BigDecimal currentPrice, Date closeTime, ExitType exitType) {
        // 提前声明，catch块中需要使用
        TradePosition tradePosition = null;
        try {
            log.info("执行平仓: orderSn={}, closeVolume={}, currentPrice={}, closeTime={}",
                    orderSn, closeVolume, currentPrice, closeTime);

            // 参数校验
            if (closeVolume == null || closeVolume.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("平仓数量无效: {}", closeVolume);
                return false;
            }

            // 根据订单号查询订单
            tradePosition = getOrderByOrderSn(orderSn);
            if (tradePosition == null) {
                log.error("订单不存在: orderSn={}", orderSn);
                return false;
            }

            if (tradePosition.getTradeOrderStatus() != TradePosition.TradeOrderStatus.DEAL) {
                BigDecimal totalPosition = calculateTotalPosition(orderSn);
                if (isFinalCloseStatus(tradePosition.getTradeOrderStatus())
                    && totalPosition.compareTo(BigDecimal.ZERO) <= 0) {
                log.info("订单已平仓，无需重复平仓: orderSn={}, status={}",
                        orderSn, tradePosition.getTradeOrderStatus());
                return false;
                }
                log.error("订单状态不正确，无法平仓: orderSn={}, status={}",
                        orderSn, tradePosition.getTradeOrderStatus());
                return false;
            }

            // 获取账户信息
            TradingAccount account = memberThirdAccountService.getByAccountId(tradePosition.getAccountId());
            if (account == null) {
                log.error("账户信息不存在: {}", tradePosition.getAccountId());
                return false;
            }

            // 获取当前市场价格
            BigDecimal finalCurrentPrice=currentPrice;
            ExchangeTradeService exchangeService = null;
            if (tradePosition.isTest()) {
                // 测试/回测模式：优先使用传入的价格（从K线数据获取），否则使用订单的开仓价格
                if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                    finalCurrentPrice = currentPrice;
                    log.info("测试/回测模式平仓，使用K线数据价格: orderSn={}, price={}", orderSn, finalCurrentPrice);
                } else {
                    // 降级：使用订单的开仓价格（不推荐，会导致收益为0）
                    finalCurrentPrice = tradePosition.getBuyPrice();
                    if (finalCurrentPrice == null || finalCurrentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        log.warn("测试模式订单开仓价格为0，使用默认价格1: orderSn={}", orderSn);
                        finalCurrentPrice = BigDecimal.ONE;
                    }
                    log.warn("测试/回测模式平仓，未提供K线价格，使用订单开仓价格（收益可能为0）: orderSn={}, price={}", orderSn, finalCurrentPrice);
                }
            } else {
                // 实盘模式：从交易所获取当前价格
                exchangeService = createExchangeService(account);
                try {
                    if (finalCurrentPrice == null || finalCurrentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        BigDecimal market = exchangeService.getCurrentPrice(tradePosition.getSymbol());
                        if (market != null && market.compareTo(BigDecimal.ZERO) > 0) {
                            finalCurrentPrice = market;
                            log.info("实盘模式平仓，获取到当前价格: orderSn={}, price={}", orderSn, finalCurrentPrice);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("获取当前价格失败，使用回退价格: {}", ex.getMessage());
                }
                if (finalCurrentPrice == null || finalCurrentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    // 回退到开仓价，避免收益计算NPE
                    finalCurrentPrice = tradePosition.getBuyPrice();
                    if (finalCurrentPrice == null || finalCurrentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        finalCurrentPrice = BigDecimal.ONE;
                    }
                    log.warn("实盘模式平仓，未获取到有效当前价，使用回退价格: orderSn={}, price={}", orderSn, finalCurrentPrice);
                }
            }

            // 如果是固定百分比止盈或止损，使用订单中保存的预设价格作为平仓价格
            if (exitType != null) {
                if (exitType == ExitType.FIXED_PERCENT_TAKE_PROFIT || exitType == ExitType.TAKE_PROFIT) {
                    if (tradePosition.getGainPrice() != null && tradePosition.getGainPrice().compareTo(BigDecimal.ZERO) > 0) {
                        finalCurrentPrice = tradePosition.getGainPrice();
                        log.info("使用固定止盈价格平仓: orderSn={}, price={}", orderSn, finalCurrentPrice);
                    }
                } else if (exitType == ExitType.STOP_LOSS) {
                    if (tradePosition.getLossPrice() != null && tradePosition.getLossPrice().compareTo(BigDecimal.ZERO) > 0) {
                        finalCurrentPrice = tradePosition.getLossPrice();
                        log.info("使用固定止损价格平仓: orderSn={}, price={}", orderSn, finalCurrentPrice);
                    }
                }
            }

            // 查询该订单下的所有订单项
            LambdaQueryWrapper<TradeEntry> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(TradeEntry::getPositionId, orderSn);
            List<TradeEntry> orderItems = tradeOrderItemMapper.selectList(itemWrapper);

            if (orderItems.isEmpty()) {
                log.error("未找到订单项: {}", orderSn);
                return false;
            }

            // 计算总可平仓数量和每个订单项的可平仓数量
            BigDecimal totalCanCloseVolume = BigDecimal.ZERO;
            TradeEntry targetItem = null; // 目标订单项（如果只平一个订单项）

            for (TradeEntry item : orderItems) {
                BigDecimal itemVolume = item.getVolume() != null ? item.getVolume() : item.getAmount();
                BigDecimal itemClosed = item.getClosedVolume() != null ? item.getClosedVolume() : BigDecimal.ZERO;
                BigDecimal itemCanCloseVolume = itemVolume.subtract(itemClosed);

                if (itemCanCloseVolume.compareTo(BigDecimal.ZERO) > 0) {
                    totalCanCloseVolume = totalCanCloseVolume.add(itemCanCloseVolume);
                }

                // 检查是否匹配某个订单项的可平仓数量（一个订单项全部平仓）
                if (itemCanCloseVolume.compareTo(closeVolume) == 0) {
                    targetItem = item;
                }
            }

            boolean isFullOrderClose = totalCanCloseVolume.compareTo(closeVolume) == 0;
            boolean isSingleItemClose = targetItem != null;
            if (!isFullOrderClose && !isSingleItemClose && closeVolume.compareTo(totalCanCloseVolume) > 0) {
                log.error("平仓数量超过可平仓总量。可平仓总量={}, 请求平仓={}", totalCanCloseVolume, closeVolume);
                return false;
            }

            // 创建/复用平仓记录（用于幂等 clientOrderId）
            // 如果提供了平仓时间（从K线数据获取），使用它；否则使用当前时间（实盘模式）
            Date finalCloseTime = closeTime != null ? closeTime : new Date();
            if (closeTime != null) {
                log.info("使用K线数据时间作为平仓时间: orderSn={}, closeTime={}", orderSn, finalCloseTime);
            } else {
                log.info("使用当前系统时间作为平仓时间: orderSn={}, closeTime={}", orderSn, finalCloseTime);
            }

            TradeExitBatch tradeExitBatch = null;
            try {
                LambdaQueryWrapper<TradeExitBatch> closeWrapper = new LambdaQueryWrapper<>();
                closeWrapper.eq(TradeExitBatch::getPositionId, orderSn)
                        .eq(TradeExitBatch::getClosedVolume, closeVolume)
                        .eq(TradeExitBatch::getSellTime, finalCloseTime)
                        .orderByDesc(TradeExitBatch::getCreateTime)
                        .last("limit 1");
                tradeExitBatch = tradeOrderCloseMapper.selectOne(closeWrapper);
            } catch (Exception ex) {
                log.error("查询平仓记录失败: orderSn={}, closeVolume={}, closeTime={}", orderSn, closeVolume, finalCloseTime, ex);
            }

            if (tradeExitBatch == null) {
                tradeExitBatch = TradeExitBatch.builder()
                        .positionId(orderSn)
                        .closePlatformOrderSn(null)
                        .closedVolume(closeVolume)
                        .sellPrice(finalCurrentPrice)
                        .sellTime(finalCloseTime)
                        .status(TradeOrderEnum.CLOSE_ORDER_STATUS_WAIT_SYNC.getCode())
                        .closeMethod(TradeOrderEnum.CLOSE_METHOD_MANUAL.getCode())
                        .build();
                tradeOrderCloseMapper.insert(tradeExitBatch);
            }

            String exchangeOrderId = tradeExitBatch.getClosePlatformOrderSn();
            if (!tradePosition.isTest() && exchangeService != null) {
                if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
                    try {
                        String closeSide = tradePosition.getOrderSideEnum() == OrderSideEnum.BUY ? "BUY" : "SELL";
                        TradingStrategyParams.TradingStrategyParamsBuilder closeParamsBuilder = TradingStrategyParams.builder()
                                .accountId(tradePosition.getAccountId())
                                .symbol(tradePosition.getSymbol())
                                .side(closeSide)
                                .amount(closeVolume)
                                .price(finalCurrentPrice)
                                .orderType(closeSide.equals("BUY") ? EXIT_BID : EXIT_ASK);
                        // 主动止盈使用市价单，确保立即成交（避免限价单因价格偏离无法成交）
                        if (exitType != null && exitType.isActiveTakeProfitExit()) {
                            closeParamsBuilder.entryType(OrderPriceType.MARKET);
                        }
                        TradingStrategyParams closeParams = closeParamsBuilder.build();
                        closeParams.setSimulated(account.getSimulated());
                        closeParams.setPositionId("C" + tradeExitBatch.getId());
                        exchangeOrderId = exchangeService.createOrder(closeParams);
                        tradeExitBatch.setClosePlatformOrderSn(exchangeOrderId);
                        tradeExitBatch.setStatus(TradeOrderEnum.CLOSE_ORDER_STATUS_DEAL.getCode());
                        tradeOrderCloseMapper.updateById(tradeExitBatch);
                        log.info("平仓执行成功: orderSn={}, closeVolume={}, exchangeOrderId={}, isFullOrderClose={}",
                                orderSn, closeVolume, exchangeOrderId, isFullOrderClose);
                    } catch (Exception ex) {
                        throw new ExchangeOrderException("平仓下单失败(已落库幂等平仓记录): orderSn=" + orderSn, ex);
                    }
                } else {
                    log.info("检测到已发起幂等平仓单，跳过重复下单: orderSn={}, closeVolume={}, exchangeOrderId={}",
                            orderSn, closeVolume, exchangeOrderId);
                }
            } else {
                tradeExitBatch.setStatus(TradeOrderEnum.CLOSE_ORDER_STATUS_DEAL.getCode());
                tradeOrderCloseMapper.updateById(tradeExitBatch);
                log.info("测试/回测模式平仓完成: orderSn={}, closeVolume={}, isFullOrderClose={}",
                        orderSn, closeVolume, isFullOrderClose);
            }

            BigDecimal totalClosedIncome = BigDecimal.ZERO;
            BigDecimal totalCloseCharge = BigDecimal.ZERO;   // 平仓手续费
            BigDecimal totalBaseUsdt = BigDecimal.ZERO;

            // 获取订单已有的开仓手续费（避免重复计算）
            BigDecimal existingOpenFee = tradePosition.getCharge() != null ? tradePosition.getCharge() : BigDecimal.ZERO;

            // 获取合约规格和费率（移到外部，避免重复获取）
            Exchange platform = tradePosition.getMemberPlatform() != null ? tradePosition.getMemberPlatform() : Exchange.OKX;
            String platformName = account.getMemberPlatform() != null ? account.getMemberPlatform().name() : "OKX";
            int leverage = tradePosition.getLeverRate() > 0 ? tradePosition.getLeverRate() : 1;
            String symbol = tradePosition.getSymbol();
            if (symbol != null) {
                symbol = ContractSpecUtils.normalizeSymbol(platform, symbol);
            } else {
                log.warn("订单 symbol 为空，使用默认 symbol: orderSn={}", orderSn);
                symbol = "ETH-USDT-SWAP"; // 默认值
            }
            ContractSpec contractSpec = ContractSpecUtils.getContractSpec(redisCache, platform, symbol);
            BigDecimal feeRate = getFeeRateByPlatform(platformName, tradePosition.getRobotId());

            if (isFullOrderClose) {
                // 情况1：整个订单全部平仓
                log.info("执行整个订单全部平仓: orderSn={}, closeVolume={}", orderSn, closeVolume);

                for (TradeEntry orderItem : orderItems) {
                    BigDecimal itemVolume = orderItem.getVolume() != null ? orderItem.getVolume() : orderItem.getAmount();
                    BigDecimal itemClosed = orderItem.getClosedVolume() != null ? orderItem.getClosedVolume() : BigDecimal.ZERO;
                    BigDecimal itemCanCloseVolume = itemVolume.subtract(itemClosed);

                    if (itemCanCloseVolume.compareTo(BigDecimal.ZERO) <= 0) {
                        continue; // 跳过已完全平仓的订单项
                    }

                    // 全平当前订单项
                    orderItem.setClosedVolume(itemVolume);
                    orderItem.setSellTime(finalCloseTime);
                    orderItem.setSellPrice(finalCurrentPrice);
                    tradeOrderItemMapper.updateById(orderItem);

                    // 创建平仓明细记录（只计算平仓手续费，不开仓手续费）
                    BigDecimal entryPrice = orderItem.getBuyPrice() != null ? orderItem.getBuyPrice() : BigDecimal.ZERO;
                    BigDecimal itemIncome = ProfitCalcUtils.getProfitByVolume(platform, tradePosition.getOrderSideEnum(),
                            entryPrice, itemCanCloseVolume, finalCurrentPrice, contractSpec);

                    // 只计算平仓手续费
                    BigDecimal positionUsdt = BigDecimal.valueOf(TradingUtil.contractToUsdt(
                            itemCanCloseVolume.doubleValue(), finalCurrentPrice.doubleValue(), leverage,
                            contractSpec.getContractSize().doubleValue()));
                    BigDecimal itemCloseCharge = ProfitCalcUtils.calcFee(positionUsdt, feeRate);

                    TradeExitItem closeItem = TradeExitItem.builder()
                            .batchId(tradeExitBatch.getId())
                            .positionId(tradePosition.getPositionId())
                            .entrySn(orderItem.getEntrySn())
                            .closedVolume(itemCanCloseVolume)
                            .status(TradeOrderEnum.CLOSE_ORDER_STATUS_DEAL.getCode())
                            .entryPrice(orderItem.getBuyPrice())
                            .exitPrice(finalCurrentPrice)
                            .exitTime(finalCloseTime)
                            .income(itemIncome)
                            .charge(itemCloseCharge)
                            .closeMethod(exitType != null ? exitType.getDescription() : null)
                            .build();

                    tradeOrderCloseItemMapper.insert(closeItem);
                    recordCloseSignal(tradePosition, closeItem, finalCloseTime);

                    totalClosedIncome = totalClosedIncome.add(itemIncome);
                    totalCloseCharge = totalCloseCharge.add(itemCloseCharge);
                    BigDecimal baseUsdt = BigDecimal.valueOf(TradingUtil.contractToUsdt(
                            itemCanCloseVolume.doubleValue(), entryPrice.doubleValue(), leverage,
                            contractSpec.getContractSize().doubleValue()));
                    totalBaseUsdt = totalBaseUsdt.add(baseUsdt);

                    // 根据该订单项盈亏设置订单项状态（GAIN / LOSS / CLOSE）
                    BigDecimal itemPnl = itemIncome.subtract(itemCloseCharge);
                    if (itemPnl.compareTo(BigDecimal.ZERO) > 0) {
                        orderItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.GAIN);
                    } else if (itemPnl.compareTo(BigDecimal.ZERO) < 0) {
                        orderItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.LOSS);
                    } else {
                        orderItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.CLOSE);
                    }
                    tradeOrderItemMapper.updateById(orderItem);
                }

                // 根据本次整体平仓盈亏设置主订单状态
                BigDecimal orderPnl = totalClosedIncome.subtract(totalCloseCharge);
                if (orderPnl.compareTo(BigDecimal.ZERO) > 0) {
                    tradePosition.setTradeOrderStatus(TradePosition.TradeOrderStatus.GAIN);
                } else if (orderPnl.compareTo(BigDecimal.ZERO) < 0) {
                    tradePosition.setTradeOrderStatus(TradePosition.TradeOrderStatus.LOSS);
                } else {
                    tradePosition.setTradeOrderStatus(TradePosition.TradeOrderStatus.CLOSE);
                }
                tradeOrderMapper.updateById(tradePosition);

            } else if (isSingleItemClose) {
                // 情况2：一个订单项全部平仓
                log.info("执行单个订单项全部平仓: orderSn={}, orderItemSn={}, closeVolume={}",
                        orderSn, targetItem.getEntrySn(), closeVolume);

                // 全平目标订单项
                BigDecimal targetItemVolume = targetItem.getVolume() != null ? targetItem.getVolume() : targetItem.getAmount();
                targetItem.setClosedVolume(targetItemVolume);
                targetItem.setSellTime(finalCloseTime);
                targetItem.setSellPrice(finalCurrentPrice);

                // 创建平仓明细记录（只计算平仓手续费，不开仓手续费）
                BigDecimal entryPrice = targetItem.getBuyPrice() != null ? targetItem.getBuyPrice() : BigDecimal.ZERO;
                BigDecimal itemIncome = ProfitCalcUtils.getProfitByVolume(platform, tradePosition.getOrderSideEnum(),
                        entryPrice, closeVolume, finalCurrentPrice, contractSpec);

                // 只计算平仓手续费
                BigDecimal positionUsdt = BigDecimal.valueOf(TradingUtil.contractToUsdt(
                        closeVolume.doubleValue(), finalCurrentPrice.doubleValue(), leverage,
                        contractSpec.getContractSize().doubleValue()));
                BigDecimal itemCloseCharge = ProfitCalcUtils.calcFee(positionUsdt, feeRate);

                // 根据盈亏设置订单项状态
                BigDecimal itemPnl = itemIncome.subtract(itemCloseCharge);
                if (itemPnl.compareTo(BigDecimal.ZERO) > 0) {
                    targetItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.GAIN);
                } else if (itemPnl.compareTo(BigDecimal.ZERO) < 0) {
                    targetItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.LOSS);
                } else {
                    targetItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.CLOSE);
                }
                tradeOrderItemMapper.updateById(targetItem);

                TradeExitItem closeItem = TradeExitItem.builder()
                        .batchId(tradeExitBatch.getId())
                        .positionId(tradePosition.getPositionId())
                        .entrySn(targetItem.getEntrySn())
                        .closedVolume(closeVolume)
                        .status(TradeOrderEnum.CLOSE_ORDER_STATUS_DEAL.getCode())
                        .entryPrice(targetItem.getBuyPrice())
                        .exitPrice(finalCurrentPrice)
                        .exitTime(finalCloseTime)
                        .income(itemIncome)
                        .charge(itemCloseCharge)
                        .closeMethod(exitType != null ? exitType.getDescription() : null)
                        .build();

                tradeOrderCloseItemMapper.insert(closeItem);
                recordCloseSignal(tradePosition, closeItem, finalCloseTime);

                totalClosedIncome = itemIncome;
                totalCloseCharge = itemCloseCharge;
                BigDecimal baseUsdt = BigDecimal.valueOf(TradingUtil.contractToUsdt(
                        closeVolume.doubleValue(), entryPrice.doubleValue(), leverage,
                        contractSpec.getContractSize().doubleValue()));
                totalBaseUsdt = totalBaseUsdt.add(baseUsdt);

                // 检查是否所有订单项都已平仓，如果是则更新主订单状态
                boolean allItemsClosed = true;
                for (TradeEntry item : orderItems) {
                    BigDecimal itemVolume = item.getVolume() != null ? item.getVolume() : item.getAmount();
                    BigDecimal itemClosed = item.getClosedVolume() != null ? item.getClosedVolume() : BigDecimal.ZERO;
                    BigDecimal itemCanCloseVolume = itemVolume.subtract(itemClosed);

                    if (itemCanCloseVolume.compareTo(BigDecimal.ZERO) > 0) {
                        allItemsClosed = false;
                        break;
                    }
                }
                if (allItemsClosed) {
                    // 根据本次整体平仓盈亏设置主订单状态
                    BigDecimal orderPnl = totalClosedIncome.subtract(totalCloseCharge);
                    if (orderPnl.compareTo(BigDecimal.ZERO) > 0) {
                        tradePosition.setTradeOrderStatus(TradePosition.TradeOrderStatus.GAIN);
                    } else if (orderPnl.compareTo(BigDecimal.ZERO) < 0) {
                        tradePosition.setTradeOrderStatus(TradePosition.TradeOrderStatus.LOSS);
                    } else {
                        tradePosition.setTradeOrderStatus(TradePosition.TradeOrderStatus.CLOSE);
                    }
                    tradeOrderMapper.updateById(tradePosition);
                }
            } else {
                BigDecimal remaining = closeVolume;
                for (TradeEntry orderItem : orderItems) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal itemVolume = orderItem.getVolume() != null ? orderItem.getVolume() : orderItem.getAmount();
                    BigDecimal itemClosed = orderItem.getClosedVolume() != null ? orderItem.getClosedVolume() : BigDecimal.ZERO;
                    BigDecimal itemCanCloseVolume = itemVolume.subtract(itemClosed);
                    if (itemCanCloseVolume.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal closeAmt = remaining.compareTo(itemCanCloseVolume) >= 0 ? itemCanCloseVolume : remaining;
                    BigDecimal newClosed = itemClosed.add(closeAmt);
                    orderItem.setClosedVolume(newClosed);
                    // 先计算盈亏
                    BigDecimal entryPrice = orderItem.getBuyPrice() != null ? orderItem.getBuyPrice() : BigDecimal.ZERO;
                    BigDecimal itemIncome = ProfitCalcUtils.getProfitByVolume(platform, tradePosition.getOrderSideEnum(),
                            entryPrice, closeAmt, finalCurrentPrice, contractSpec);
                    BigDecimal positionUsdt = BigDecimal.valueOf(TradingUtil.contractToUsdt(
                            closeAmt.doubleValue(), finalCurrentPrice.doubleValue(), leverage,
                            contractSpec.getContractSize().doubleValue()));
                    BigDecimal itemCloseCharge = ProfitCalcUtils.calcFee(positionUsdt, feeRate);
                    if (newClosed.compareTo(itemVolume) >= 0) {
                        // 根据盈亏设置订单项状态
                        BigDecimal itemPnl = itemIncome.subtract(itemCloseCharge);
                        if (itemPnl.compareTo(BigDecimal.ZERO) > 0) {
                            orderItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.GAIN);
                        } else if (itemPnl.compareTo(BigDecimal.ZERO) < 0) {
                            orderItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.LOSS);
                        } else {
                            orderItem.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.CLOSE);
                        }
                    }
                    orderItem.setSellTime(finalCloseTime);
                    orderItem.setSellPrice(finalCurrentPrice);
                    tradeOrderItemMapper.updateById(orderItem);
                    TradeExitItem closeItem = TradeExitItem.builder()
                            .batchId(tradeExitBatch.getId())
                            .positionId(tradePosition.getPositionId())
                            .entrySn(orderItem.getEntrySn())
                            .closedVolume(closeAmt)
                            .status(TradeOrderEnum.CLOSE_ORDER_STATUS_DEAL.getCode())
                            .entryPrice(orderItem.getBuyPrice())
                            .exitPrice(finalCurrentPrice)
                            .exitTime(finalCloseTime)
                            .income(itemIncome)
                            .charge(itemCloseCharge)
                            .closeMethod(exitType != null ? exitType.getDescription() : null)
                            .build();
                    tradeOrderCloseItemMapper.insert(closeItem);
                    recordCloseSignal(tradePosition, closeItem, finalCloseTime);
                    totalClosedIncome = totalClosedIncome.add(itemIncome);
                    totalCloseCharge = totalCloseCharge.add(itemCloseCharge);
                    BigDecimal baseUsdt = BigDecimal.valueOf(TradingUtil.contractToUsdt(
                            closeAmt.doubleValue(), entryPrice.doubleValue(), leverage,
                            contractSpec.getContractSize().doubleValue()));
                    totalBaseUsdt = totalBaseUsdt.add(baseUsdt);
                    remaining = remaining.subtract(closeAmt);
                }
                boolean allItemsClosed = true;
                for (TradeEntry item : orderItems) {
                    BigDecimal itemVolume = item.getVolume() != null ? item.getVolume() : item.getAmount();
                    BigDecimal itemClosed = item.getClosedVolume() != null ? item.getClosedVolume() : BigDecimal.ZERO;
                    BigDecimal itemCanCloseVolume = itemVolume.subtract(itemClosed);
                    if (itemCanCloseVolume.compareTo(BigDecimal.ZERO) > 0) {
                        allItemsClosed = false;
                        break;
                    }
                }
                if (allItemsClosed) {
                    // 根据本次整体平仓盈亏设置主订单状态
                    BigDecimal orderPnl = totalClosedIncome.subtract(totalCloseCharge);
                    if (orderPnl.compareTo(BigDecimal.ZERO) > 0) {
                        tradePosition.setTradeOrderStatus(TradePosition.TradeOrderStatus.GAIN);
                    } else if (orderPnl.compareTo(BigDecimal.ZERO) < 0) {
                        tradePosition.setTradeOrderStatus(TradePosition.TradeOrderStatus.LOSS);
                    } else {
                        tradePosition.setTradeOrderStatus(TradePosition.TradeOrderStatus.CLOSE);
                    }
                    tradeOrderMapper.updateById(tradePosition);
                }
            }

            // 更新平仓总收益
            tradeExitBatch.setIncome(totalClosedIncome);
            tradeExitBatch.setCharge(totalCloseCharge);
            tradeOrderCloseMapper.updateById(tradeExitBatch);

            // 更新订单的平仓价格、收益、成本、收益率和平仓时间
            tradePosition.setSellPrice(finalCurrentPrice);
            BigDecimal existingOrderIncome = tradePosition.getIncome() != null ? tradePosition.getIncome() : BigDecimal.ZERO;
            BigDecimal cumulativeIncome = existingOrderIncome.add(totalClosedIncome);
            tradePosition.setIncome(cumulativeIncome);
            // 总成本 = 已有的开仓手续费 + 本次的平仓手续费
            tradePosition.setCharge(existingOpenFee.add(totalCloseCharge));
            tradePosition.setSellTime(finalCloseTime); // 设置平仓时间（与TradeOrderClose保持一致）

            // 计算收益率：
            // 多单: (平仓价格 - 开仓价格) / 开仓价格 * 100
            // 空单: (开仓价格 - 平仓价格) / 开仓价格 * 100
            BigDecimal cumulativeBaseUsdt = BigDecimal.ZERO;
            for (TradeEntry item : orderItems) {
                BigDecimal itemVolume = item.getVolume() != null ? item.getVolume() : item.getAmount();
                BigDecimal itemClosed = item.getClosedVolume() != null ? item.getClosedVolume() : BigDecimal.ZERO;
                BigDecimal closed = itemClosed.min(itemVolume);
                BigDecimal entryPrice = item.getBuyPrice() != null ? item.getBuyPrice() : BigDecimal.ZERO;
                if (closed.compareTo(BigDecimal.ZERO) > 0 && entryPrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal baseUsdt = BigDecimal.valueOf(TradingUtil.contractToUsdt(
                            closed.doubleValue(), entryPrice.doubleValue(), leverage,
                            contractSpec.getContractSize().doubleValue()));
                    cumulativeBaseUsdt = cumulativeBaseUsdt.add(baseUsdt);
                }
            }
            if (cumulativeBaseUsdt.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal profitRate = cumulativeIncome
                        .divide(cumulativeBaseUsdt, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                tradePosition.setProfitPercent(profitRate.floatValue());
                log.info("计算订单收益率: orderSn={}, closedIncome={}, baseUsdt(all), profitRate={}%",
                        orderSn, cumulativeIncome, cumulativeBaseUsdt, profitRate);
            }

            // 更新订单信息
            tradeOrderMapper.updateById(tradePosition);

            if (notificationPublisher != null) {
                notificationPublisher.publish(NotificationPushMessage.builder()
                        .type("trade")
                        .title("平仓成功")
                        .content("平仓成功 orderSn=" + orderSn + " symbol=" + tradePosition.getSymbol()
                                + " volume=" + closeVolume + " price=" + finalCurrentPrice)
                        .symbol(tradePosition.getSymbol())
                        .severity("info")
                        .isTest(tradePosition.isTest())
                        .userId(tradePosition.getMemberId())
                        .build());
            }
            return true;

        } catch (ExchangeOrderException e) {
            log.error("执行平仓下单失败(保留幂等记录): orderSn={}, closeVolume={}, err={}", orderSn, closeVolume, e.getMessage());
            if (notificationPublisher != null) {
                notificationPublisher.publish(NotificationPushMessage.builder()
                        .type("trade")
                        .title("平仓执行失败")
                        .content("平仓失败 orderSn=" + orderSn + " msg=" + e.getMessage())
                        .severity("warning")
                        .isTest(tradePosition != null && tradePosition.isTest())
                        .userId(tradePosition != null ? tradePosition.getMemberId() : null)
                        .build());
            }
            throw e;
        } catch (Exception e) {
            log.error("执行平仓失败: orderSn={}, closeVolume={}", orderSn, closeVolume, e);
            if (notificationPublisher != null) {
                notificationPublisher.publish(NotificationPushMessage.builder()
                        .type("trade")
                        .title("平仓执行失败")
                        .content("平仓失败 orderSn=" + orderSn + " msg=" + e.getMessage())
                        .severity("warning")
                        .isTest(tradePosition != null && tradePosition.isTest())
                        .userId(tradePosition != null ? tradePosition.getMemberId() : null)
                        .build());
            }
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    private void recordCloseSignal(TradePosition tradePosition, TradeExitItem closeItem, Date closeTime) {
        try {
            TradeSignal tradeSignal = new TradeSignal();
            tradeSignal.setSymbol(tradePosition.getSymbol());
            OrderAction action = tradePosition.getOrderSideEnum() == OrderSideEnum.BUY ? OrderAction.CLOSE_LONG : OrderAction.CLOSE_SHORT;
            tradeSignal.setOrderAction(action);
            tradeSignal.setStatus(TradeStatus.FILLED);
            tradeSignal.setOrderSn(tradePosition.getPositionId());
            tradeSignal.setOrderItemSn(closeItem.getEntrySn());
            tradeSignal.setExecutedPrice(closeItem.getExitPrice());
            tradeSignal.setExecutedAmount(closeItem.getClosedVolume());
            tradeSignal.setKlineTime(DateUtil.formatDateTime(closeTime));
            tradeSignal.setExecutedTime(closeTime);
            tradeSignal.setDecisionReason(action.getLabel());
            // 从原开仓信号复制技术信号关联信息
            try {
                TradeSignal openSignal = tradeSignalService.queryTradeSignalByOrderSn(tradePosition.getPositionId());
                if (openSignal != null) {
                    tradeSignal.setTechnicalSignalId(openSignal.getTechnicalSignalId());
                    tradeSignal.setTechnicalSignalHash(openSignal.getTechnicalSignalHash());
                    tradeSignal.setTechnicalSignalBrief(openSignal.getTechnicalSignalBrief());
                }
            } catch (Exception ex) {
                log.warn("查询原开仓信号技术关联信息失败, orderSn={}", tradePosition.getPositionId(), ex);
            }
            tradeSignalService.createTradeSignal(tradeSignal);
        } catch (Exception ex) {
            log.error("记录分批平仓业务信号失败: orderSn={}, orderItemSn={}", tradePosition.getPositionId(), closeItem.getEntrySn(), ex);
        }
    }

    @Override
    @Transactional
    public boolean closeOrderByOrderSn(String orderSn) {
        log.info("根据订单号平仓（整个订单全部平仓）: orderSn={}", orderSn);

        // 根据订单号查询订单
        TradePosition tradePosition = getOrderByOrderSn(orderSn);
        if (tradePosition == null) {
            log.error("订单不存在: orderSn={}", orderSn);
            return false;
        }

        // 查询该订单下的所有订单项
        LambdaQueryWrapper<TradeEntry> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(TradeEntry::getPositionId, orderSn);
        List<TradeEntry> orderItems = tradeOrderItemMapper.selectList(itemWrapper);

        if (orderItems.isEmpty()) {
            log.error("未找到订单项: orderSn={}", orderSn);
            return false;
        }

        // 计算总可平仓数量
        BigDecimal totalCanCloseVolume = BigDecimal.ZERO;
        for (TradeEntry item : orderItems) {
            BigDecimal itemVolume = item.getVolume() != null ? item.getVolume() : item.getAmount();
            BigDecimal itemClosed = item.getClosedVolume() != null ? item.getClosedVolume() : BigDecimal.ZERO;
            BigDecimal itemCanCloseVolume = itemVolume.subtract(itemClosed);

            if (itemCanCloseVolume.compareTo(BigDecimal.ZERO) > 0) {
                totalCanCloseVolume = totalCanCloseVolume.add(itemCanCloseVolume);
            }
        }

        if (totalCanCloseVolume.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("订单没有可平仓数量: orderSn={}", orderSn);
            return false;
        }

        // 调用 closeOrderByVolume 方法，传入总可平仓数量（整个订单全部平仓）
        return closeOrderByVolume(orderSn, totalCanCloseVolume);
    }

    @Override
    @Transactional
    public boolean closeOrderByOrderItemSn(String orderItemSn) {
        log.info("根据订单项号平仓（单个订单项全部平仓）: orderItemSn={}", orderItemSn);

        // 根据订单项号查询订单项
        LambdaQueryWrapper<TradeEntry> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(TradeEntry::getEntrySn, orderItemSn);
        TradeEntry orderItem = tradeOrderItemMapper.selectOne(itemWrapper);

        if (orderItem == null) {
            log.error("订单项不存在: orderItemSn={}", orderItemSn);
            return false;
        }

        // 获取订单项的可平仓数量
        BigDecimal itemVolume = orderItem.getVolume() != null ? orderItem.getVolume() : orderItem.getAmount();
        BigDecimal itemClosed = orderItem.getClosedVolume() != null ? orderItem.getClosedVolume() : BigDecimal.ZERO;
        BigDecimal itemCanCloseVolume = itemVolume.subtract(itemClosed);

        if (itemCanCloseVolume == null || itemCanCloseVolume.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("订单项没有可平仓数量: orderItemSn={}", orderItemSn);
            return false;
        }

        // 调用 closeOrderByVolume 方法，传入订单项的可平仓数量（单个订单项全部平仓）
        return closeOrderByVolume(orderItem.getPositionId(), itemCanCloseVolume);
    }

    // ================ 分布式锁相关方法 ================

    /**
     * 生成订单创建锁键
     */
    @Override
    @Transactional
    public boolean updateOrderStatus(String orderSn, TradePosition.TradeOrderStatus status, Date fillTime) {
        TradePosition order = tradeOrderMapper.selectOne(new LambdaQueryWrapper<TradePosition>().eq(TradePosition::getPositionId, orderSn));
        if (order == null) {
            return false;
        }
        order.setTradeOrderStatus(status);
        if (status == TradePosition.TradeOrderStatus.DEAL && fillTime != null) {
            order.setBuyTime(fillTime);
        }
        order.setUpdateTime(new Date());
        tradeOrderMapper.updateById(order);

        // 同步更新订单项
        TradeEntry item = tradeOrderItemMapper.selectOne(new LambdaQueryWrapper<TradeEntry>().eq(TradeEntry::getPositionId, orderSn));
        if (item != null) {
            item.setTradeOrderItemStatus(status);
            if (status == TradePosition.TradeOrderStatus.DEAL && fillTime != null) {
                item.setOrderTime(fillTime); // 订单项通常用 orderTime 记录成交/入场时间
            }
            item.setUpdateTime(new Date());
            tradeOrderItemMapper.updateById(item);
        }
        return true;
    }

    private void cancelPendingOrders(String accountId, String symbol, String side) {
        log.info("取消同方向待成交订单: accountId={}, symbol={}, side={}", accountId, symbol, side);
        LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradePosition::getAccountId, accountId)
                .eq(TradePosition::getSymbol, symbol)
                .eq(TradePosition::getOrderSideEnum, OrderSideEnum.valueOf(side))
                .eq(TradePosition::getTradeOrderStatus, TradePosition.TradeOrderStatus.PENDING);

        List<TradePosition> pendingOrders = tradeOrderMapper.selectList(wrapper);
        if (pendingOrders != null && !pendingOrders.isEmpty()) {
            for (TradePosition pendingOrder : pendingOrders) {
                log.info("取消待成交订单: orderSn={}", pendingOrder.getPositionId());
                pendingOrder.setTradeOrderStatus(TradePosition.TradeOrderStatus.CLOSE);
                pendingOrder.setUpdateTime(new Date());
                tradeOrderMapper.updateById(pendingOrder);

                // 同步更新订单项
                LambdaQueryWrapper<TradeEntry> itemWrapper = new LambdaQueryWrapper<>();
                itemWrapper.eq(TradeEntry::getPositionId, pendingOrder.getPositionId());
                TradeEntry item = tradeOrderItemMapper.selectOne(itemWrapper);
                if (item != null) {
                    item.setTradeOrderItemStatus(TradePosition.TradeOrderStatus.CLOSE);
                    item.setUpdateTime(new Date());
                    tradeOrderItemMapper.updateById(item);
                }
            }
        }
    }

    private String generateOrderLockKey(TradingStrategyParams params) {
        if (params.getPositionId() != null && !params.getPositionId().isEmpty()) {
            // 补仓：基于订单号和时间窗口
            long timeWindow = System.currentTimeMillis() / 10000; // 10秒时间窗口
            return String.format("trading:order:create:add:%s:%d",
                    params.getPositionId(), timeWindow);
        } else {
            // 主仓：基于账户+交易对+方向
            return String.format("trading:order:create:main:%s:%s:%s",
                    params.getAccountId(), params.getSymbol(), params.getSide());
        }
    }

    /**
     * 获取分布式锁（带重试机制）
     */
    private boolean acquireDistributedLockWithRetry(String lockKey) {
        // 分布式锁是系统基础功能，始终尝试获取

        int retryCount = 0;
        int maxRetries = 3; // 最大重试次数
        long retryIntervalMs = 100; // 重试间隔

        while (retryCount < maxRetries) {
            try {
                if (tryAcquireDistributedLock(lockKey, lockTimeoutSeconds)) {
                    log.debug("分布式锁获取成功: key={}", lockKey);
                    return true;
                }

                retryCount++;
                if (retryCount < maxRetries) {
                    log.warn("分布式锁获取失败，重试中: key={}, attempt={}/{}",
                            lockKey, retryCount, maxRetries);
                    Thread.sleep(retryIntervalMs);
                    retryIntervalMs = Math.min(retryIntervalMs * 2, 1000); // 指数退避，最多1秒
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("获取分布式锁被中断: key={}", lockKey, e);
                return false;
            }
        }

        log.error("分布式锁获取失败，已达到最大重试次数: key={}", lockKey);
        return false;
    }

    /**
     * 尝试获取分布式锁
     * 使用Redis SET NX PX命令实现原子性锁获取
     */
    private boolean tryAcquireDistributedLock(String lockKey, int timeoutSeconds) {
        try {
            // 生成唯一锁标识符，防止误删其他线程的锁
            String lockValue = generateLockValue();

            // 使用SET NX PX命令：不存在时设置，存在过期时间
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    lockKey,
                    lockValue,
                    Duration.ofSeconds(timeoutSeconds)
            );

            if (Boolean.TRUE.equals(success)) {
                log.debug("分布式锁获取成功: key={}, value={}, timeout={}s",
                        lockKey, lockValue, timeoutSeconds);
                // 将锁标识符存储到ThreadLocal中，用于后续释放
                setCurrentLockValue(lockValue);
                return true;
            } else {
                log.debug("分布式锁已被占用: key={}", lockKey);
                return false;
            }

        } catch (Exception e) {
            log.error("Redis操作异常，降级为无锁模式: key={}, error={}", lockKey, e.getMessage());
            // Redis异常时降级为无锁模式，允许继续执行（避免阻塞业务）
            return true;
        }
    }

    /**
     * 释放分布式锁
     * 使用Lua脚本确保删除操作的原子性
     */
    private void releaseDistributedLock(String lockKey) {
        // 分布式锁是系统基础功能，始终尝试释放

        try {
            String currentLockValue = getCurrentLockValue();
            if (currentLockValue == null) {
                log.warn("释放锁失败：当前线程无锁标识符，跳过释放: key={}", lockKey);
                return;
            }

            // 使用Lua脚本确保只删除自己设置的锁
            String luaScript = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;

            Long result = redisTemplate.execute(
                    new DefaultRedisScript<>(luaScript, Long.class),
                    Collections.singletonList(lockKey),
                    currentLockValue
            );

            if (result != null && result > 0) {
                log.debug("分布式锁释放成功: key={}, value={}", lockKey, currentLockValue);
            } else {
                log.warn("分布式锁释放失败：锁不存在或已被其他线程占用: key={}", lockKey);
            }

            // 清理ThreadLocal
            clearCurrentLockValue();

        } catch (Exception e) {
            log.error("释放分布式锁异常: key={}, error={}", lockKey, e.getMessage());
            // 即使释放失败，也不影响业务流程
            // 锁会因为过期时间自动释放
        }
    }

    /**
     * 生成锁的唯一标识符
     * 使用线程ID + 时间戳 + 随机数确保唯一性
     */
    private String generateLockValue() {
        return String.format("%s-%d-%s",
                Thread.currentThread().getId(),
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8)
        );
    }

    /**
     * ThreadLocal存储当前线程的锁标识符
     */
    private static final ThreadLocal<String> CURRENT_LOCK_VALUE = new ThreadLocal<>();

    /**
     * 设置当前线程的锁标识符
     */
    private void setCurrentLockValue(String lockValue) {
        CURRENT_LOCK_VALUE.set(lockValue);
    }

    /**
     * 获取当前线程的锁标识符
     */
    private String getCurrentLockValue() {
        return CURRENT_LOCK_VALUE.get();
    }

    /**
     * 清理当前线程的锁标识符
     */
    private void clearCurrentLockValue() {
        CURRENT_LOCK_VALUE.remove();
    }





    /**
     * 执行完整的校验检查（设计模式驱动）
     * 使用责任链模式组织多个校验处理器
     */
    private void performValidationChecks(TradingStrategyParams params) {
        // 1. 构建校验上下文
        OrderValidationContext context = buildValidationContext(params);

        // 2. 构建校验责任链
        OrderValidationHandler validationChain =
                validationChainManager.buildValidationChain(context.isAddPosition());

        if (validationChain == null) {
            log.warn("未找到适用的校验处理器，跳过校验");
            return;
        }

        // 3. 执行校验责任链
        ValidationResult result = validationChain.validate(context);
        if (!result.isValid()) {
            log.warn("订单校验失败: errorCode={}, message={}",
                    result.getErrorCode(), result.getErrorMessage());
            throw new IllegalStateException(result.getErrorMessage());
        }

        log.debug("订单校验通过，共执行{}个校验处理器", getHandlerCount(validationChain));
    }

    /**
     * 构建校验上下文
     */
    private OrderValidationContext buildValidationContext(TradingStrategyParams params) {
        boolean isAddPosition = params.getPositionId() != null && !params.getPositionId().isEmpty();

        return OrderValidationContext.builder()
                .params(params)
                .isAddPosition(isAddPosition)
                .validationData(new java.util.HashMap<>())
                .build();
    }

    /**
     * 获取责任链中的处理器数量（用于日志）
     */
    private int getHandlerCount(OrderValidationHandler handler) {
        int count = 1;
        OrderValidationHandler current = handler;
        while (current instanceof com.chain.ai.trade.order.validation.AbstractOrderValidationHandler) {
            // 通过反射或其他方式获取下一个处理器
            // 这里简化处理，返回预估数量
            break;
        }
        return count;
    }

    /**
     * 获取价格去重阈值
     */
    private BigDecimal getPriceDeduplicationThreshold(BigDecimal price) {
        // 根据价格水平动态调整去重区间
        if (price.compareTo(new BigDecimal("100000")) >= 0) {
            return new BigDecimal("0.02"); // 高价位：2%区间
        } else if (price.compareTo(new BigDecimal("10000")) >= 0) {
            return new BigDecimal("0.03"); // 中价位：3%区间
        } else {
            return new BigDecimal("0.05"); // 低价位：5%区间
        }
    }

    @Override
    @Transactional
    public int saveBacktestTradeRecords(List<BacktestTradeRecord> backtestTradeRecords, String memberId, Long accountId, String robotId, String symbol, Integer leverage, Exchange exchange) {
        if (backtestTradeRecords == null || backtestTradeRecords.isEmpty()) {
            log.warn("回测交易记录为空，无需保存");
            return 0;
        }

        log.info("开始保存回测交易记录: memberId={}, accountId={}, robotId={}, symbol={}, recordCount={}",
                memberId, accountId, robotId, symbol, backtestTradeRecords.size());

        int savedCount = 0;
        int leverRate = leverage != null && leverage > 0 ? leverage : 1;

        try {
            // 按 positionId 分组（V2引擎内存仓位ID → 数据库订单）
            Map<String, List<BacktestTradeRecord>> positionGroups = backtestTradeRecords.stream()
                    .filter(r -> r.getPositionId() != null && !r.getPositionId().trim().isEmpty())
                    .collect(Collectors.groupingBy(BacktestTradeRecord::getPositionId));

            log.info("按positionId分组: 总记录数={}, 分组数={}", backtestTradeRecords.size(), positionGroups.size());

            for (Map.Entry<String, List<BacktestTradeRecord>> group : positionGroups.entrySet()) {
                String positionId = group.getKey();
                List<BacktestTradeRecord> records = group.getValue();

                // 分离开仓和平仓记录
                List<BacktestTradeRecord> openRecords = records.stream()
                        .filter(BacktestTradeRecord::isOpenAction)
                        .collect(Collectors.toList());
                List<BacktestTradeRecord> closeRecords = records.stream()
                        .filter(BacktestTradeRecord::isCloseAction)
                        .collect(Collectors.toList());

                log.info("positionId分组 {}: 总记录数={}, 开仓记录数={}, 平仓记录数={}",
                        positionId, records.size(), openRecords.size(), closeRecords.size());

                if (openRecords.isEmpty()) {
                    log.warn("positionId分组 {} 缺少开仓记录，跳过保存", positionId);
                    continue;
                }

                try {
                    // 1. 创建或获取 TradeOrder（orderSn = positionId），使用首个开仓记录
                    BacktestTradeRecord firstOpen = openRecords.get(0);
                    String orderSn = firstOpen.getPositionId();
                    // 检查是否已存在相同 orderSn 的 TradeOrder（避免重复回测或分批保存时的唯一键冲突）
                    LambdaQueryWrapper<TradePosition> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(TradePosition::getPositionId, orderSn);
                    TradePosition existingOrder = tradeOrderMapper.selectOne(queryWrapper);
                    TradePosition order;
                    if (existingOrder != null) {
                        order = existingOrder;
                        log.info("TradeOrder已存在，复用: orderSn={}, tradeOrderId={}", orderSn, order.getId());
                    } else {
                        // 找到最早开仓记录作为首笔开仓，确保 orderTime/buyTime 取最早时间而非 openRecords 中的第一条
                        BacktestTradeRecord earliestOpen = openRecords.stream()
                                .min(Comparator.comparing(BacktestTradeRecord::getTradeTime,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                                .orElse(firstOpen);
                        order = saveOpenTradeRecord(earliestOpen, memberId, accountId, robotId, symbol, leverRate, exchange);
                        log.info("TradeOrder创建成功: orderSn={}", order.getPositionId());
                    }

                    // 2. 为每个开仓记录创建 TradeOrderItem，并建立 entryId → (orderItemSn, entryPrice) 映射
                    Map<String, TradeOrderItemInfo> entryIdToItemMap = new HashMap<>();
                    for (int i = 0; i < openRecords.size(); i++) {
                        BacktestTradeRecord openRecord = openRecords.get(i);
                        TradeEntry item = createOrderItem(order, openRecord);
                        if (openRecord.getEntryId() != null) {
                            entryIdToItemMap.put(openRecord.getEntryId(),
                                    new TradeOrderItemInfo(item.getEntrySn(), openRecord.getPrice()));
                        }
                        // 首个开仓记录的金额已在 saveOpenTradeRecord 中设置，复用已存在订单时也需要累加
                        if (i > 0 || existingOrder != null) {
                            BigDecimal currentAmount = order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO;
                            order.setAmount(currentAmount.add(openRecord.getAmount()));
                            BigDecimal currentVolume = order.getVolume() != null ? order.getVolume() : BigDecimal.ZERO;
                            order.setVolume(currentVolume.add(openRecord.getAmount()));
                            tradeOrderMapper.updateById(order);
                        }
                        log.info("开仓明细创建完成: entryId={}, orderItemSn={}, amount={}",
                                openRecord.getEntryId(), item.getEntrySn(), openRecord.getAmount());
                    }

                    // 4. 处理平仓记录
                    if (!closeRecords.isEmpty()) {
                        // 计算该仓位所有分批出场的总盈亏和总平仓数量
                        BigDecimal totalPnl = closeRecords.stream()
                                .map(BacktestTradeRecord::getPnl)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal totalCloseAmount = closeRecords.stream()
                                .map(BacktestTradeRecord::getAmount)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                        log.info("positionId分组 {} 分批出场: 总笔数={}, totalPnl={}, totalCloseAmount={}",
                                positionId, closeRecords.size(), totalPnl, totalCloseAmount);

                        // 逐笔保存分批出场记录，通过 entryId 精准匹配本次平仓的开仓明细
                        for (int i = 0; i < closeRecords.size(); i++) {
                            BacktestTradeRecord closeRecord = closeRecords.get(i);
                            boolean isLast = (i == closeRecords.size() - 1);

                            String matchedItemSn = null;
                            BigDecimal entryPrice = null;
                            if (closeRecord.getEntryId() != null && entryIdToItemMap.containsKey(closeRecord.getEntryId())) {
                                TradeOrderItemInfo itemInfo = entryIdToItemMap.get(closeRecord.getEntryId());
                                matchedItemSn = itemInfo.orderItemSn;
                                entryPrice = itemInfo.entryPrice;
                                log.info("平仓通过entryId匹配到开仓明细: entryId={}, orderItemSn={}, entryPrice={}",
                                        closeRecord.getEntryId(), matchedItemSn, entryPrice);
                            } else if (!entryIdToItemMap.isEmpty()) {
                                log.warn("平仓记录未找到匹配的entryId, 使用首个item: entryId={}, 可用映射={}",
                                        closeRecord.getEntryId(), entryIdToItemMap.keySet());
                                // 降级：使用首个开仓明细
                                Map.Entry<String, TradeOrderItemInfo> firstEntry = entryIdToItemMap.entrySet().iterator().next();
                                matchedItemSn = firstEntry.getValue().orderItemSn;
                                entryPrice = firstEntry.getValue().entryPrice;
                            } else {
                                log.warn("平仓记录entryId为空且无可用的开仓明细映射: entryId=null, 可用映射为空, closeRecord={}", closeRecord);
                            }

                            saveCloseTradeRecord(order, closeRecord, isLast, totalPnl, totalCloseAmount, matchedItemSn, entryPrice);
                        }

                        log.info("平仓记录保存完成: positionId={}, orderSn={}, 分批出场次数={}",
                                positionId, order.getPositionId(), closeRecords.size());
                    } else {
                        log.warn("positionId分组 {} 没有找到对应的平仓记录，保留订单为未平仓状态", positionId);
                    }

                    savedCount++;
                    log.info("成功保存交易记录: positionId={}, orderSn={}", positionId, order.getPositionId());

                } catch (Exception e) {
                    log.error("保存positionId分组失败: positionId={}, error={}", positionId, e.getMessage(), e);
                }
            }

            log.info("回测交易记录保存完成: 成功保存记录数={}, 总分组数={}", savedCount, positionGroups.size());
            return savedCount;

        } catch (Exception e) {
            log.error("批量保存回测交易记录失败", e);
            throw new RuntimeException("保存回测交易记录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 开仓明细映射信息
     */
    @lombok.Value
    private static class TradeOrderItemInfo {
        String orderItemSn;
        BigDecimal entryPrice;
    }

    /**
     * 保存开仓记录到TradeOrder和TradeOrderItem
     */
    private TradePosition saveOpenTradeRecord(BacktestTradeRecord openRecord, String memberId, Long accountId, String robotId, String symbol, int leverRate, Exchange exchange) {
        // 生成唯一订单号，V2回测优先使用 positionId
        String orderSn = openRecord.getPositionId() != null ? openRecord.getPositionId() : generateOrderSn();
        log.info("生成订单编号: {}", orderSn);

        // 首次创建主订单（每个positionId仅一个TradeOrder）
        TradePosition order = TradePosition.builder()
                .positionId(orderSn)
                .memberId(memberId)
                .accountId(accountId != null ? String.valueOf(accountId) : null)
                .robotId(robotId)
                .symbol(symbol)
                .memberPlatform(exchange != null ? exchange : Exchange.OKX)
                .goodsId(symbol) // 设置商品ID，使用交易对作为商品ID
                .orderSideEnum(openRecord.getOrderSide())
                .orderTime(openRecord.getTradeTime())
                .buyTime(openRecord.getTradeTime())
                .buyPrice(openRecord.getPrice())
                .openPrice(openRecord.getPrice())
                .amount(openRecord.getAmount())
                .volume(openRecord.getAmount())
                .tradeOrderStatus(TradePosition.TradeOrderStatus.DEAL) // 回测记录直接设为成交状态
                .test(true) // 标记为测试单
                .remark("回测开仓记录")
                .deleteFlag(false)
                .leverRate(leverRate)
                .build();

        // 设置必要的默认值
        order.setConfigType(com.chain.ai.trade.order.entity.MemberRobotConfig.ConfigType.PLATFORM);

        tradeOrderMapper.insert(order);

        // 保存开仓信号
        try {
            saveOpenSignal(order, openRecord, robotId, symbol);
            log.info("开仓信号保存完成: orderSn={}", orderSn);
        } catch (Exception e) {
            log.error("保存开仓信号失败: orderSn={}, error={}", orderSn, e.getMessage(), e);
            // 不抛出异常，避免影响订单保存
        }

        log.info("保存开仓记录完成: orderSn={}, tradeOrderId={}", orderSn, order.getId());

        return order;
    }

    /**
     * 为已存在的TradeOrder创建追加的TradeOrderItem（加仓明细）
     */
    private TradeEntry createOrderItem(TradePosition order, BacktestTradeRecord openRecord) {
        TradeEntry orderItem = TradeEntry.builder()
                .positionId(order.getPositionId())
                .entrySn(generateOrderItemSn())
                .robotId(order.getRobotId())
                .symbol(order.getSymbol())
                .orderSideEnum(openRecord.getOrderSide())
                .buyPrice(openRecord.getPrice())
                .amount(openRecord.getAmount())
                .volume(openRecord.getAmount())
                .orderTime(openRecord.getTradeTime())
                .tradeOrderItemStatus(TradePosition.TradeOrderStatus.DEAL)
                .syncVolumeFlag(true)
                .closedVolume(BigDecimal.ZERO)
                .income(BigDecimal.ZERO)
                .deleteFlag(false)
                .build();

        tradeOrderItemMapper.insert(orderItem);
        log.info("创建订单项: orderSn={}, orderItemSn={}, amount={}", order.getPositionId(), orderItem.getEntrySn(), openRecord.getAmount());
        return orderItem;
    }

    /**
     * 保存平仓记录到TradeOrderClose，通过orderItemSn精准匹配平仓明细
     */
    private void saveCloseTradeRecord(TradePosition order, BacktestTradeRecord closeRecord, boolean isLast, BigDecimal totalPnl, BigDecimal totalCloseAmount, String orderItemSn, BigDecimal entryPrice) {
        log.debug("创建TradeOrderClose记录: orderSn={}, closePrice={}, closeTime={}, pnl={}, orderItemSn={}",
                order.getPositionId(), closeRecord.getPrice(), closeRecord.getTradeTime(), closeRecord.getPnl(), orderItemSn);

        // 创建平仓记录（TradeOrderClose保持原有closeMethod）
        TradeExitBatch closeOrder = TradeExitBatch.builder()
                .positionId(order.getPositionId())
                .closePlatformOrderSn(generateOrderSn() + "_CLOSE")
                .closeMethod(TradeOrderEnum.CLOSE_METHOD_MANUAL.getCode()) // 回测关闭，使用手动关闭
                .closedVolume(closeRecord.getAmount())
                .status(TradeOrderEnum.CLOSE_ORDER_STATUS_DEAL.getCode())
                .sellPrice(closeRecord.getPrice())
                .sellTime(closeRecord.getTradeTime()) // 使用实际交易时间
                .income(closeRecord.getPnl())
                .charge(closeRecord.getCharge() != null ? closeRecord.getCharge() : BigDecimal.ZERO)
                .id(null) // 让MyBatis-Plus自动生成ID
                .createTime(null)
                .updateTime(null)
                .createBy(null)
                .updateBy(null)
                .deleteFlag(false)
                .build();

        int insertResult = tradeOrderCloseMapper.insert(closeOrder);
        log.debug("TradeOrderClose插入结果: {}", insertResult);

        log.info("saveCloseTradeRecord: closeReason='{}', closeOrder.id={}, insertResult={}, orderSn={}, isLast={}",
                closeRecord.getCloseReason(), closeOrder.getId(), insertResult, order.getPositionId(), isLast);

        // 创建平仓明细记录（TradeOrderCloseItem），通过 entryId 精准匹配本次平仓的开仓明细
        if (insertResult > 0 && closeOrder.getId() != null && closeRecord.getCloseReason() != null) {
            try {
                TradeExitItem closeItem = TradeExitItem.builder()
                        .batchId(closeOrder.getId())
                        .positionId(order.getPositionId())
                        .entrySn(orderItemSn)
                        .closedVolume(closeRecord.getAmount())
                        .status(TradeOrderEnum.CLOSE_ORDER_STATUS_DEAL.getCode())
                        .entryPrice(entryPrice)
                        .exitPrice(closeRecord.getPrice())
                        .exitTime(closeRecord.getTradeTime())
                        .income(closeRecord.getPnl())
                        .charge(closeRecord.getCharge() != null ? closeRecord.getCharge() : BigDecimal.ZERO)
                        .closeMethod(closeRecord.getCloseReason())
                        .build();
                tradeOrderCloseItemMapper.insert(closeItem);
                log.debug("创建平仓明细记录: entrySn={}, closeReason={}", orderItemSn, closeRecord.getCloseReason());
            } catch (Exception e) {
                log.error("创建平仓明细记录失败: orderSn={}, orderItemSn={}, error={}", order.getPositionId(), orderItemSn, e.getMessage());
            }
        }

        // 仅末次平仓时更新订单主表状态（前N-1次仅创建平仓明细，不改变订单状态）
        if (isLast) {
            // 更新原订单状态为已完成，并根据盈亏设置订单状态
            order.setSellTime(closeRecord.getTradeTime());
            order.setSellPrice(closeRecord.getPrice());
            order.setIncome(totalPnl);
            order.setCharge(closeRecord.getCharge() != null ? closeRecord.getCharge() : BigDecimal.ZERO);
            order.setCloseAmount(totalCloseAmount);
            // 收益率使用最后一次平仓记录的收益率
            if (closeRecord.getPnlPercent() != null && !closeRecord.getPnlPercent().isEmpty()) {
                try {
                    String pct = closeRecord.getPnlPercent().trim().replace("%", "");
                    if (!pct.isEmpty()) {
                        order.setProfitPercent(Float.parseFloat(pct));
                    }
                } catch (NumberFormatException e) {
                    log.warn("解析回测收益率失败: pnlPercent={}, orderSn={}", closeRecord.getPnlPercent(), order.getPositionId());
                }
            }

            // 平仓时先将订单设置为CLOSE状态，然后根据盈亏设置最终状态
            TradePosition.TradeOrderStatus itemStatus;
            BigDecimal pnl = totalPnl;

            log.info("订单 {} 末次平仓处理: 当前状态={}, totalPnl={}, 买入价={}, 最后一次卖出价={}",
                    order.getPositionId(), order.getTradeOrderStatus(), pnl,
                    order.getBuyPrice(), closeRecord.getPrice());

            // 根据总盈亏设置最终状态
            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                order.setTradeOrderStatus(TradePosition.TradeOrderStatus.GAIN); // 止盈
                itemStatus = TradePosition.TradeOrderStatus.GAIN;
                log.info("✅ 订单 {} 总平仓盈利，设置状态为GAIN(止盈): totalPnl={}", order.getPositionId(), pnl);
            } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
                order.setTradeOrderStatus(TradePosition.TradeOrderStatus.LOSS); // 止损
                itemStatus = TradePosition.TradeOrderStatus.LOSS;
                log.info("❌ 订单 {} 总平仓亏损，设置状态为LOSS(止损): totalPnl={}", order.getPositionId(), pnl);
            } else {
                order.setTradeOrderStatus(TradePosition.TradeOrderStatus.CLOSE); // 平价关闭
                itemStatus = TradePosition.TradeOrderStatus.CLOSE;
                log.info("😐 订单 {} 总平仓平价，设置状态为CLOSE: totalPnl={}", order.getPositionId(), pnl);
            }

            int updateResult = tradeOrderMapper.updateById(order);
            log.info("订单状态更新结果: orderSn={}, 最终状态={}, 更新行数={}",
                    order.getPositionId(), order.getTradeOrderStatus(), updateResult);

            // 更新对应的订单项状态
            try {
                List<TradeEntry> orderItems = tradeOrderItemMapper.selectList(
                        new LambdaQueryWrapper<TradeEntry>()
                                .eq(TradeEntry::getPositionId, order.getPositionId())
                );

                if (orderItems != null && !orderItems.isEmpty()) {
                    for (TradeEntry item : orderItems) {
                        item.setTradeOrderItemStatus(itemStatus);
                        item.setSellPrice(closeRecord.getPrice());
                        item.setSellTime(closeRecord.getTradeTime());
                        item.setClosedVolume(totalCloseAmount);
                        item.setIncome(totalPnl);
                        tradeOrderItemMapper.updateById(item);
                        log.debug("订单项 {} 状态更新为: {}", item.getEntrySn(), itemStatus);
                    }
                }
            } catch (Exception e) {
                log.error("更新订单项状态失败: orderSn={}, error={}", order.getPositionId(), e.getMessage());
            }

            log.info("订单关闭完成: orderSn={}, totalPnl={}, status={}", order.getPositionId(), totalPnl, order.getTradeOrderStatus());

            // 保存平仓信号（仅末次）
            try {
                saveCloseSignal(order, closeRecord, order.getRobotId(), order.getSymbol());
                log.info("平仓信号保存完成: orderSn={}", order.getPositionId());
            } catch (Exception e) {
                log.error("保存平仓信号失败: orderSn={}, error={}", order.getPositionId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 生成订单项编号
     */
    private String generateOrderItemSn() {
        return "BTI_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 保存开仓信号
     */
    private void saveOpenSignal(TradePosition order, BacktestTradeRecord openRecord, String robotId, String symbol) {
        try {
            // 创建业务信号对象
            TradeSignal tradeSignal = createTradeSignalFromBacktestRecord(openRecord, order, robotId, symbol);

            // 保存业务信号
            Long signalId = tradeSignalService.createTradeSignal(tradeSignal);

            log.debug("开仓业务信号保存成功: orderSn={}, signalId={}", order.getPositionId(), signalId);
        } catch (Exception e) {
            log.error("保存开仓业务信号异常: orderSn={}, error={}", order.getPositionId(), e.getMessage(), e);
            throw e; // 重新抛出，让上层处理
        }
    }

    /**
     * 保存平仓信号
     */
    private void saveCloseSignal(TradePosition order, BacktestTradeRecord closeRecord, String robotId, String symbol) {
        try {
            // 创建业务信号对象
            TradeSignal tradeSignal = createTradeSignalFromBacktestRecord(closeRecord, order, robotId, symbol);

            // 保存业务信号
            Long signalId = tradeSignalService.createTradeSignal(tradeSignal);

            log.debug("平仓业务信号保存成功: orderSn={}, signalId={}", order.getPositionId(), signalId);
        } catch (Exception e) {
            log.error("保存平仓业务信号异常: orderSn={}, error={}", order.getPositionId(), e.getMessage(), e);
            throw e; // 重新抛出，让上层处理
        }
    }

    /**
     * 从回测记录创建业务信号对象
     *
     * @param record 回测交易记录
     * @param order 交易订单
     * @param robotId 机器人ID
     * @param symbol 交易对
     * @return 业务信号对象
     */
    private TradeSignal createTradeSignalFromBacktestRecord(BacktestTradeRecord record, TradePosition order, String robotId, String symbol) {
        TradeSignal tradeSignal = new TradeSignal();

        // 设置基本信息
        tradeSignal.setCreator("SYSTEM");
        tradeSignal.setCreateTime(new Date());
        tradeSignal.setUpdater("SYSTEM");
        tradeSignal.setUpdateTime(new Date());
        tradeSignal.setDeleted(false);

        // 设置关联信息
        tradeSignal.setTechnicalSignalId(null); // 回测数据没有技术信号ID
        tradeSignal.setTechnicalSignalHash(null);
        tradeSignal.setTechnicalSignalBrief("回测信号");

        // 设置业务信息
        tradeSignal.setSymbol(symbol);
        tradeSignal.setTimeframe("3m");

        // 设置K线时间
        LocalDateTime localDateTime = LocalDateTime.ofInstant(
                record.getTradeTime().toInstant(),
                java.time.ZoneId.systemDefault()
        );
        String klineTime = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        tradeSignal.setKlineTime(klineTime);

        // 设置入场类型和价格
        if (order.getPriceType() != null) {
            tradeSignal.setEntryType(order.getPriceType().name());
        }
        tradeSignal.setLimitPrice(order.getBuyPrice());

        // 设置业务决策信息
        tradeSignal.setDecisionReason("回测执行信号");
        tradeSignal.setRiskLevel("MEDIUM");
        tradeSignal.setPositionRatio(BigDecimal.ONE); // 100%仓位
        tradeSignal.setPriority(5);

        // 确定订单操作类型（需要根据调用方传入是否为开仓）
        // 这里我们通过检查调用栈或者参数来判断，但更简单的方式是创建两个不同的方法
        // 或者在方法参数中增加isOpen参数
        OrderAction orderAction = OrderAction.CANCEL_ORDER;
        if("OPEN".equals(record.getActionType())){
            if("LONG".equals(record.getDirection())){
                orderAction =OrderAction.OPEN_LONG;
            }
            if("SHORT".equals(record.getDirection())){
                orderAction =OrderAction.OPEN_SHORT;
            }
        }
        if("CLOSE".equals(record.getActionType())){
            if(record.getPnl()!=null){
                if("LONG".equals(record.getDirection())&&record.getPnl().compareTo(BigDecimal.ZERO) > 0){
                    orderAction =OrderAction.LONG_GAIN;
                }
                if("LONG".equals(record.getDirection())&&record.getPnl().compareTo(BigDecimal.ZERO) < 0){
                    orderAction =OrderAction.LONG_LOSS;
                }
                if("SHORT".equals(record.getDirection())&&record.getPnl().compareTo(BigDecimal.ZERO) > 0){
                    orderAction =OrderAction.SHORT_GAIN;
                }
                if("SHORT".equals(record.getDirection())&&record.getPnl().compareTo(BigDecimal.ZERO) < 0){
                    orderAction =OrderAction.SHORT_LOSS;
                }
            }
        }

        tradeSignal.setOrderAction(orderAction);

        // 设置订单状态
        tradeSignal.setStatus(TradeStatus.FILLED); // 回测数据直接标记为已执行

        // 设置订单信息
        tradeSignal.setOrderSn(order.getPositionId());
        tradeSignal.setOrderItemSn(order.getPositionId() + "-1"); // 默认第一个订单项

        // 设置交易信息
        tradeSignal.setExpectedPrice(record.getPrice());
        tradeSignal.setExpectedAmount(record.getAmount().abs()); // 使用绝对值
        tradeSignal.setExecutedPrice(record.getPrice());
        tradeSignal.setExecutedAmount(record.getAmount().abs());
        tradeSignal.setExecutedTime(new Date(record.getTradeTime().getTime()));

        // 设置杠杆和手续费（回测默认值）
        tradeSignal.setLeverage(order.getLeverRate() > 0 ? order.getLeverRate() : 1);
        tradeSignal.setFeeRate(BigDecimal.valueOf(0.001)); // 0.1%
        tradeSignal.setPnlAmount(record.getPnl());

        // 只有平仓单才计算收益率
        if ("CLOSE".equals(record.getActionType())) {
            // 从 Redis 获取合约规格（如果 Redis 中没有，使用默认值 0.1）
            ContractSpec contractSpec = ContractSpecUtils.getContractSpec(redisCache,
                    order.getMemberPlatform(), order.getSymbol());
            double contractSize = contractSpec.getContractSize().doubleValue();
            double usdtAmount = TradingUtil.contractToUsdt(record.getAmount().doubleValue(), record.getPrice().doubleValue(), order.getLeverRate(), contractSize);
            BigDecimal usdtAmountBigDecimal = BigDecimal.valueOf(usdtAmount);
            // 验证除数不为零，避免除零错误
            if (usdtAmountBigDecimal.compareTo(BigDecimal.ZERO) == 0) {
                log.warn("USDT金额为零，无法计算盈亏百分比: tradeId={}, record={}", record.getTradeId(), record);
                tradeSignal.setPnlPercentage(BigDecimal.ZERO);
            } else {
                // 使用4位精度和四舍五入模式，避免无限小数异常
                tradeSignal.setPnlPercentage(record.getPnl().divide(usdtAmountBigDecimal, 4, RoundingMode.HALF_UP));
            }
        }

        return tradeSignal;
    }

    @Override
    public PageVO<OrderVO> queryOrders(OrderQueryDTO queryDTO) {
        log.info("开始分页查询订单: {}", queryDTO);

        // 构建查询条件
        LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();

        // 添加筛选条件
        if (queryDTO.getOrderSn() != null && !queryDTO.getOrderSn().trim().isEmpty()) {
            wrapper.eq(TradePosition::getPositionId, queryDTO.getOrderSn());
        }
        if (queryDTO.getMemberId() != null && !queryDTO.getMemberId().trim().isEmpty()) {
            wrapper.eq(TradePosition::getMemberId, queryDTO.getMemberId());
        }

        if (queryDTO.getAccountId() != null) {
            wrapper.eq(TradePosition::getAccountId, queryDTO.getAccountId());
        }

        if (queryDTO.getRobotId() != null && !queryDTO.getRobotId().trim().isEmpty()) {
            wrapper.eq(TradePosition::getRobotId, queryDTO.getRobotId());
        }

        if (queryDTO.getSymbol() != null && !queryDTO.getSymbol().trim().isEmpty()) {
            wrapper.eq(TradePosition::getSymbol, queryDTO.getSymbol());
        }

        if (queryDTO.getStatus() != null && !queryDTO.getStatus().trim().isEmpty()) {
            // 将字符串状态转换为枚举
            try {
                TradePosition.TradeOrderStatus status = TradePosition.TradeOrderStatus.valueOf(queryDTO.getStatus());
                wrapper.eq(TradePosition::getTradeOrderStatus, status);
            } catch (IllegalArgumentException e) {
                log.warn("无效的订单状态: {}", queryDTO.getStatus());
            }
        }

        if (queryDTO.getOrderSide() != null && !queryDTO.getOrderSide().trim().isEmpty()) {
            // 根据订单方向筛选
            if ("BUY".equalsIgnoreCase(queryDTO.getOrderSide())) {
                wrapper.lt(TradePosition::getTrend, 0); // 买入趋势为负数
            } else if ("SELL".equalsIgnoreCase(queryDTO.getOrderSide())) {
                wrapper.gt(TradePosition::getTrend, 0); // 卖出趋势为正数
            }
        }

        if (queryDTO.getTestReportId() != null && !queryDTO.getTestReportId().trim().isEmpty()) {
            wrapper.eq(TradePosition::getTestReportId, queryDTO.getTestReportId());
        }

        if (queryDTO.getStartTime() != null) {
            wrapper.ge(TradePosition::getCreateTime, queryDTO.getStartTime());
        }

        if (queryDTO.getEndTime() != null) {
            wrapper.le(TradePosition::getCreateTime, queryDTO.getEndTime());
        }

        if (queryDTO.getCloseStartTime() != null) {
            wrapper.ge(TradePosition::getSellTime, queryDTO.getCloseStartTime());
        }

        if (queryDTO.getCloseEndTime() != null) {
            wrapper.le(TradePosition::getSellTime, queryDTO.getCloseEndTime());
        }

        // 添加排序
        String sortField = queryDTO.getSortField();
        boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());

        switch (sortField) {
            case "createTime":
                wrapper.orderBy(true, isAsc, TradePosition::getCreateTime);
                break;
            case "orderTime":
                wrapper.orderBy(true, isAsc, TradePosition::getOrderTime);
                break;
            case "symbol":
                wrapper.orderBy(true, isAsc, TradePosition::getSymbol);
                break;
            case "amount":
                wrapper.orderBy(true, isAsc, TradePosition::getAmount);
                break;
            case "income":
                wrapper.orderBy(true, isAsc, TradePosition::getIncome);
                break;
            default:
                wrapper.orderByDesc(TradePosition::getCreateTime);
        }

        // 执行分页查询
        Page<TradePosition> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        IPage<TradePosition> result = tradeOrderMapper.selectPage(page, wrapper);

        // 转换为VO
        List<OrderVO> orderVOs = result.getRecords().stream()
                .map(this::convertToOrderVO)
                .collect(java.util.stream.Collectors.toList());

        // 构建分页结果
        PageVO<OrderVO> pageVO = new PageVO<>();
        pageVO.setCurrent(result.getCurrent());
        pageVO.setSize(result.getSize());
        pageVO.setTotal(result.getTotal());
        pageVO.setPages(result.getPages());
        pageVO.setHasNext(result.getCurrent() < result.getPages());
        pageVO.setHasPrevious(result.getCurrent() > 1);
        pageVO.setRecords(orderVOs);

        log.info("订单分页查询完成: 总记录数={}, 当前页={}, 每页大小={}, 总页数={}",
                result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());

        return pageVO;
    }

    /**
     * 将 TradeOrder 转换为 OrderVO
     */
    private OrderVO convertToOrderVO(TradePosition order) {
        String closeReason = getLatestCloseReason(order.getPositionId());
        Float realizedProfitPercent = null;
        try {
            if (order.getSellTime() != null && order.getBuyPrice() != null && order.getAmount() != null
                    && order.getBuyPrice().compareTo(BigDecimal.ZERO) > 0 && order.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                // 优先使用机器人当前资金计算净资金收益率
                BigDecimal baseValue = null;
                try {
                    TradingBot bot = tradingBotService.getByBotId(order.getRobotId());
                    if (bot != null && bot.getCurrentCapital() != null && bot.getCurrentCapital().compareTo(BigDecimal.ZERO) > 0) {
                        baseValue = bot.getCurrentCapital();
                    }
                } catch (Exception ignored) {
                    // fallback 到保证金计算
                }
                if (baseValue == null) {
                    ContractSpec spec = ContractSpecUtils.getContractSpec(redisCache, Exchange.OKX, order.getSymbol());
                    BigDecimal contractSize = (spec != null && spec.getContractSize() != null) ? spec.getContractSize() : BigDecimal.ONE;
                    BigDecimal contractMult = (spec != null && spec.getContractMult() != null) ? spec.getContractMult() : BigDecimal.ONE;
                    int leverage = order.getLeverRate() > 0 ? order.getLeverRate() : 1;
                    baseValue = contractSize.multiply(contractMult).multiply(order.getAmount()).multiply(order.getBuyPrice())
                            .divide(BigDecimal.valueOf(leverage), RoundingMode.HALF_UP);
                }
                if (baseValue.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal income = order.getIncome() != null ? order.getIncome() : BigDecimal.ZERO;
                    BigDecimal charge = order.getCharge() != null ? order.getCharge() : BigDecimal.ZERO;
                    BigDecimal netProfit = income.subtract(charge);
                    BigDecimal percent = netProfit.multiply(new BigDecimal("100")).divide(baseValue, 2, RoundingMode.HALF_UP);
                    realizedProfitPercent = percent.floatValue();
                }
            }
        } catch (Exception e) {
            realizedProfitPercent = null;
        }
        // 计算剩余持仓数量（原数量减去已平仓数量）
        BigDecimal remainingAmount = calculateTotalPosition(order.getPositionId());

        return OrderVO.builder()
                .id(order.getId())
                .orderSn(order.getPositionId())
                .memberId(order.getMemberId())
                .memberName(order.getMemberName())
                .symbol(order.getSymbol())
                .accountId(order.getAccountId())
                .robotId(order.getRobotId())
                .orderSide(order.getOrderSideEnum() != null ? order.getOrderSideEnum().name() : null)
                .priceType(order.getPriceType() != null ? order.getPriceType().name() : null)
                .leverRate(order.getLeverRate())
                .orderTime(order.getOrderTime())
                .openPrice(order.getOpenPrice())
                .buyAvgPrice(order.getBuyAvgPrice())
                .buyPrice(order.getBuyPrice())
                .sellPrice(order.getSellPrice())
                .lossPrice(order.getLossPrice())
                .gainPrice(order.getGainPrice())
                .amount(order.getAmount())
                .remainingAmount(remainingAmount)
                .volume(order.getVolume())
                .charge(order.getCharge())
                .sellTime(order.getSellTime())
                .buyTime(order.getBuyTime())
                .orderAmount(order.getOrderAmount())
                .status(order.getTradeOrderStatus() != null ? order.getTradeOrderStatus().name() : null)
                .closeReason(closeReason)
                .profitPercent(realizedProfitPercent)
                .income(order.getIncome())
                .hasIncome(order.getHasIncome())
                .createTime(order.getCreateTime())
                .updateTime(order.getUpdateTime())
                .platformOrderSn(order.getPlatformOrderSn())
                .testReportId(order.getTestReportId())
                .memberPlatform(order.getMemberPlatform() != null ? order.getMemberPlatform().name() : null)
                .build();
    }

    @Override
    public List<OrderVO> getPositionOrders(String accountId, String symbol) {
        log.info("查询持仓订单: accountId={}, symbol={}", accountId, symbol);

        LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradePosition::getAccountId, accountId)
                .eq(TradePosition::getTradeOrderStatus, TradePosition.TradeOrderStatus.DEAL)
                .orderByDesc(TradePosition::getCreateTime);

        List<TradePosition> positionOrders = tradeOrderMapper.selectList(wrapper);
        log.info("查询到 {} 个持仓订单", positionOrders.size());

        return positionOrders.stream()
                .map(this::convertToOrderVO)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<OrderVO> getPendingOrders(String accountId, String symbol) {
        log.info("查询待成交订单: accountId={}, symbol={}", accountId, symbol);

        LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradePosition::getAccountId, accountId)
                .eq(TradePosition::getTradeOrderStatus, TradePosition.TradeOrderStatus.PENDING)
                .orderByDesc(TradePosition::getCreateTime);

        List<TradePosition> pendingOrders = tradeOrderMapper.selectList(wrapper);
        log.info("查询到 {} 个待成交订单", pendingOrders.size());

        return pendingOrders.stream()
                .map(this::convertToOrderVO)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public java.util.List<TradeEntry> listOrderItemsByOrderSn(String orderSn) {
        LambdaQueryWrapper<TradeEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeEntry::getPositionId, orderSn)
                .orderByAsc(TradeEntry::getOrderTime);
        return tradeOrderItemMapper.selectList(wrapper);
    }

    @Override
    public java.util.List<TradeExitBatch> listOrderClosesByOrderSn(String orderSn) {
        LambdaQueryWrapper<TradeExitBatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeExitBatch::getPositionId, orderSn)
                .orderByAsc(TradeExitBatch::getSellTime);
        return tradeOrderCloseMapper.selectList(wrapper);
    }

    @Override
    public java.util.List<TradeExitItem> listOrderCloseItemsByOrderSn(String orderSn) {
        // 1. 查该订单的所有 orderItemSn
        LambdaQueryWrapper<TradeEntry> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(TradeEntry::getPositionId, orderSn)
                .select(TradeEntry::getEntrySn);
        List<String> itemSns = tradeOrderItemMapper.selectList(itemWrapper)
                .stream()
                .map(TradeEntry::getEntrySn)
                .toList();
        if (itemSns.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // 2. 查这些 orderItemSn 对应的平仓明细
        LambdaQueryWrapper<TradeExitItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TradeExitItem::getEntrySn, itemSns)
                .orderByAsc(TradeExitItem::getExitTime);
        return tradeOrderCloseItemMapper.selectList(wrapper);
    }

    @Override
    public List<TradePosition> getOrdersByQry(String rootId, String accountId, String symbol, OrderSideEnum orderSideEnum, Date orderTime) {
        LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradePosition::getRobotId, rootId)
                .eq(TradePosition::getAccountId, accountId)
                .eq(TradePosition::getSymbol, symbol)
                .eq(TradePosition::getOrderSideEnum, orderSideEnum)
                .eq(TradePosition::getOrderTime, orderTime);
        return tradeOrderMapper.selectList(wrapper);
    }


    @Override
    public RobotOrderReportVO getRobotOrderReport(String robotId, Date startTime, Date endTime, String granularity) {
        log.info("机器人订单收益报表: robotId={}, startTime={}, endTime={}, granularity={}", robotId, startTime, endTime, granularity);

        if (robotId == null || robotId.trim().isEmpty()) {
            return RobotOrderReportVO.builder().orderCount(0).totalIncome(BigDecimal.ZERO).totalCharge(BigDecimal.ZERO).netProfit(BigDecimal.ZERO).items(Collections.emptyList()).build();
        }

        LambdaQueryWrapper<TradePosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradePosition::getRobotId, robotId)
                .isNotNull(TradePosition::getSellTime)
                .ge(startTime != null, TradePosition::getSellTime, startTime)
                .le(endTime != null, TradePosition::getSellTime, endTime)
                .in(TradePosition::getTradeOrderStatus,
                        TradePosition.TradeOrderStatus.CLOSE,
                        TradePosition.TradeOrderStatus.LOSS,
                        TradePosition.TradeOrderStatus.GAIN)
                .orderByAsc(TradePosition::getSellTime);

        List<TradePosition> orders = tradeOrderMapper.selectList(wrapper);

        boolean isMonth = "month".equalsIgnoreCase(granularity);
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");

        Map<String, PeriodAccumulator> periodMap = new LinkedHashMap<>();
        for (TradePosition order : orders) {
            Date sellTime = order.getSellTime();
            if (sellTime == null) continue;
            LocalDateTime ldt = sellTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            String periodKey = isMonth ? ldt.format(monthFmt) : ldt.format(dayFmt);
            BigDecimal income = order.getIncome() != null ? order.getIncome() : BigDecimal.ZERO;
            BigDecimal charge = order.getCharge() != null ? order.getCharge() : BigDecimal.ZERO;
            BigDecimal net = income.subtract(charge);
            TradePosition.TradeOrderStatus status = order.getTradeOrderStatus();
            PeriodAccumulator acc = periodMap.computeIfAbsent(periodKey, k -> new PeriodAccumulator());
            acc.add(income, charge);
            if (status == TradePosition.TradeOrderStatus.GAIN) {
                acc.takeProfitAmount = acc.takeProfitAmount.add(net.max(BigDecimal.ZERO));
            } else if (status == TradePosition.TradeOrderStatus.LOSS) {
                BigDecimal lossAbs = net.compareTo(BigDecimal.ZERO) < 0 ? net.negate() : BigDecimal.ZERO;
                acc.stopLossAmount = acc.stopLossAmount.add(lossAbs);
            }
        }

        List<RobotOrderReportPeriodVO> items = periodMap.entrySet().stream()
                .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                .map(e -> RobotOrderReportPeriodVO.builder()
                        .periodKey(e.getKey())
                        .orderCount(e.getValue().count)
                        .totalIncome(e.getValue().totalIncome)
                        .totalCharge(e.getValue().totalCharge)
                        .netProfit(e.getValue().totalIncome.subtract(e.getValue().totalCharge))
                        .takeProfitAmount(e.getValue().takeProfitAmount)
                        .stopLossAmount(e.getValue().stopLossAmount)
                        .build())
                .collect(Collectors.toList());

        int totalCount = orders.size();
        BigDecimal totalIncome = orders.stream()
                .map(o -> o.getIncome() != null ? o.getIncome() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCharge = orders.stream()
                .map(o -> o.getCharge() != null ? o.getCharge() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netProfit = totalIncome.subtract(totalCharge);

        // 盈利订单数：按单笔净利润（income - charge）> 0 统计
        int profitOrderCount = (int) orders.stream()
                .filter(o -> {
                    BigDecimal income = o.getIncome() != null ? o.getIncome() : BigDecimal.ZERO;
                    BigDecimal charge = o.getCharge() != null ? o.getCharge() : BigDecimal.ZERO;
                    return income.subtract(charge).compareTo(BigDecimal.ZERO) > 0;
                })
                .count();

        // 查询权益曲线
        List<EquityCurvePoint> equityCurve = buildEquityCurve(robotId, startTime, endTime, isMonth);

        return RobotOrderReportVO.builder()
                .orderCount(totalCount)
                .profitOrderCount(profitOrderCount)
                .totalIncome(totalIncome)
                .totalCharge(totalCharge)
                .netProfit(netProfit)
                .items(items)
                .equityCurve(equityCurve)
                .build();
    }

    /**
     * 构建权益曲线（含回撤计算、缺失填充、月聚合）
     */
    private List<EquityCurvePoint> buildEquityCurve(String robotId, Date startTime, Date endTime, boolean isMonth) {
        if (startTime == null || endTime == null) {
            return Collections.emptyList();
        }
        LocalDateTime startLdt = startTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime endLdt = endTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        // 查询机器人实盘权益数据
        List<BacktestEquityCurve> rawList = backtestEquityCurveMapper
                .selectByRobotIdAndTimeRange(robotId, startLdt, endLdt);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        // 转换为 EquityCurvePoint 并计算回撤
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");

        List<EquityCurvePoint> points = new ArrayList<>();
        BigDecimal peak = null;
        for (BacktestEquityCurve rec : rawList) {
            BigDecimal equity = rec.getEquity();
            if (equity == null) continue;

            // 更新峰值
            if (peak == null || equity.compareTo(peak) > 0) {
                peak = equity;
            }

            // 计算回撤（正数表示回撤幅度）
            BigDecimal drawdown = BigDecimal.ZERO;
            if (peak != null && peak.compareTo(BigDecimal.ZERO) > 0) {
                drawdown = peak.subtract(equity)
                        .divide(peak, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }

            LocalDateTime time = rec.getTime();
            String dateStr = time.format(dayFmt);

            points.add(EquityCurvePoint.builder()
                    .date(dateStr)
                    .equity(equity)
                    .drawdown(drawdown)
                    .actualDate(dateStr)
                    .build());
        }

        if (points.isEmpty()) {
            return Collections.emptyList();
        }

        if (isMonth) {
            // 月粒度：取每月最后一条数据
            return aggregateByMonth(points, monthFmt, dayFmt);
        } else {
            // 日粒度：填充缺失日期
            return fillMissingDays(points, startLdt.toLocalDate(), endLdt.toLocalDate(), dayFmt);
        }
    }

    /**
     * 月粒度聚合：取每月最后一条权益记录
     */
    private List<EquityCurvePoint> aggregateByMonth(List<EquityCurvePoint> dailyPoints,
                                                     DateTimeFormatter monthFmt,
                                                     DateTimeFormatter dayFmt) {
        Map<String, EquityCurvePoint> monthMap = new LinkedHashMap<>();
        for (EquityCurvePoint p : dailyPoints) {
            // 从 date (yyyy-MM-dd) 提取 yyyy-MM
            String monthKey = p.getDate().substring(0, 7);
            // 覆盖：后出现的（日期更大）覆盖之前的，保证取到该月最后一条
            monthMap.put(monthKey, EquityCurvePoint.builder()
                    .date(monthKey)
                    .equity(p.getEquity())
                    .drawdown(p.getDrawdown())
                    .actualDate(p.getDate())
                    .build());
        }
        return new ArrayList<>(monthMap.values());
    }

    /**
     * 日粒度缺失填充：无数据的日期取前值，drawdown 置 null
     */
    private List<EquityCurvePoint> fillMissingDays(List<EquityCurvePoint> points,
                                                    java.time.LocalDate startDate,
                                                    java.time.LocalDate endDate,
                                                    DateTimeFormatter dayFmt) {
        // 构建 date → point 映射
        java.util.Map<String, EquityCurvePoint> pointMap = new java.util.LinkedHashMap<>();
        for (EquityCurvePoint p : points) {
            pointMap.put(p.getDate(), p);
        }

        List<EquityCurvePoint> result = new ArrayList<>();
        BigDecimal lastEquity = null;
        java.time.LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            String dateStr = current.format(dayFmt);
            EquityCurvePoint existing = pointMap.get(dateStr);
            if (existing != null) {
                result.add(existing);
                lastEquity = existing.getEquity();
            } else if (lastEquity != null) {
                // 缺失日期：取前值，drawdown 置 null
                result.add(EquityCurvePoint.builder()
                        .date(dateStr)
                        .equity(lastEquity)
                        .drawdown(null)
                        .build());
            }
            current = current.plusDays(1);
        }
        return result;
    }

    private static class PeriodAccumulator {
        int count = 0;
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalCharge = BigDecimal.ZERO;
        BigDecimal takeProfitAmount = BigDecimal.ZERO;
        BigDecimal stopLossAmount = BigDecimal.ZERO;

        void add(BigDecimal income, BigDecimal charge) {
            count++;
            totalIncome = totalIncome.add(income);
            totalCharge = totalCharge.add(charge);
        }
    }

    // ==================== 新增精确平仓接口实现 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClosePositionResult closeFullPosition(String orderSn, BigDecimal currentPrice, Date closeTime, ExitType exitType) {
        log.info("[全仓平仓] 开始: orderSn={}, price={}, time={}", orderSn, currentPrice, closeTime);

        ClosePositionResult result = new ClosePositionResult(orderSn);
        result.setClosePrice(currentPrice);
        result.setCloseTime(closeTime != null ? closeTime : new Date());

        // 使用Redis分布式锁
        String lockKey = "POSITION_CLOSE_LOCK:" + orderSn;
        String requestId = UUID.randomUUID().toString(); // 锁的值，用于安全释放


        boolean locked = false;

        try {
            // 2. 参数验证
            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return errorResult(result, "平仓价格必须大于0");
            }

            // 3. 获取订单信息
            TradePosition tradePosition = getOrderByOrderSn(orderSn);
            if (tradePosition == null) {
                return errorResult(result, "订单不存在: " + orderSn);
            }

            // 4. 检查订单状态
            if (tradePosition.getTradeOrderStatus() != TradePosition.TradeOrderStatus.DEAL) {
                if (isFinalCloseStatus(tradePosition.getTradeOrderStatus())) {
                    BigDecimal totalPosition = calculateTotalPosition(orderSn);
                    result.setOriginalPosition(totalPosition);
                    if (totalPosition.compareTo(BigDecimal.ZERO) <= 0) {
                        return successResult(result, "订单已平仓，无需重复平仓",
                                BigDecimal.ZERO, BigDecimal.ZERO, true, totalPosition);
                    }
                }
                return errorResult(result, "订单状态不正确，无法平仓: " + tradePosition.getTradeOrderStatus());
            }

            // 5. 计算精确的持仓数量
            BigDecimal totalPosition = calculateTotalPosition(orderSn);
            if (totalPosition.compareTo(BigDecimal.ZERO) <= 0) {
                return successResult(result, "仓位已为零，无需平仓", BigDecimal.ZERO, BigDecimal.ZERO, true, totalPosition);
            }

            result.setOriginalPosition(totalPosition);

            log.info("[全仓平仓] 计算: orderSn={}, 持仓数量={}, 平仓价格={}",
                    orderSn, totalPosition, currentPrice);

            // 6. 执行平仓（使用原有的closeOrderByVolume方法）
            boolean success = closeOrderByVolume(orderSn, totalPosition, currentPrice, closeTime, exitType);

            if (success) {
                // 7. 验证是否全部平仓
                BigDecimal remainingAfterClose = calculateTotalPosition(orderSn);

                result.setSuccess(true);
                result.setClosedAmount(totalPosition);
                result.setRemainingAmount(remainingAfterClose);
                result.setFullClose(remainingAfterClose.compareTo(BigDecimal.ZERO) == 0);
                result.setMessage("全仓平仓成功");

                log.info("[全仓平仓] 成功: orderSn={}, 平仓数量={}, 剩余={}, 是否完全平仓={}, 价格={}",
                        orderSn, totalPosition, remainingAfterClose, result.isFullClose(), currentPrice);

                // 8. 如果有微小剩余（小尾巴），自动平掉
                if (remainingAfterClose.compareTo(BigDecimal.ZERO) > 0) {
                    log.info("[全仓平仓] 检测到剩余仓位: orderSn={}, 剩余={}", orderSn, remainingAfterClose);
                    boolean tailClosed = closeRemainingTail(orderSn, remainingAfterClose, tradePosition, currentPrice, closeTime, exitType);
                    if (tailClosed) {
                        result.setClosedAmount(result.getClosedAmount().add(remainingAfterClose));
                        result.setRemainingAmount(BigDecimal.ZERO);
                        result.setFullClose(true);
                        log.info("[全仓平仓] 尾数平仓成功: orderSn={}", orderSn);
                    }
                }
            } else {
                result.setSuccess(false);
                result.setMessage("平仓执行失败");
                log.error("[全仓平仓] 失败: orderSn={}", orderSn);
            }

        } catch (Exception e) {
            handleException(result, "全仓平仓异常", orderSn, e);
        } finally {
            // 安全释放锁
            if (locked) {
                try {
                    // 使用Lua脚本确保只释放自己的锁
                    String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "return redis.call('del', KEYS[1]) " +
                            "else " +
                            "return 0 " +
                            "end";
                    Long releaseResult = redisTemplate.execute(
                            new DefaultRedisScript<>(luaScript, Long.class),
                            Collections.singletonList(lockKey),
                            requestId
                    );

                    if (releaseResult != null && releaseResult == 1) {
                        log.debug("[全仓平仓] 释放分布式锁成功: orderSn={}", orderSn);
                    }
                } catch (Exception e) {
                    log.error("[全仓平仓] 释放锁异常: orderSn={}", orderSn, e);
                }
            }
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PartialCloseResult closePartialPosition(String orderSn, BigDecimal targetCloseAmount,
                                                   BigDecimal currentPrice, Date closeTime, ExitType exitType) {
        log.info("[按数量平仓] 开始: orderSn={}, targetAmount={}, price={}, time={}",
                orderSn, targetCloseAmount, currentPrice, closeTime);

        PartialCloseResult result = new PartialCloseResult(orderSn);
        result.setTargetAmount(targetCloseAmount);
        result.setClosePrice(currentPrice);
        result.setCloseTime(closeTime != null ? closeTime : new Date());

        try {
            BATCH_TAKE_PROFIT_FLAG.set(Boolean.TRUE);
            // 1. 参数验证
            if (targetCloseAmount == null || targetCloseAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return errorResult(result, "平仓数量必须大于0");
            }

            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return errorResult(result, "平仓价格必须大于0");
            }

            // 2. 获取订单信息
            TradePosition tradePosition = getOrderByOrderSn(orderSn);
            if (tradePosition == null) {
                return errorResult(result, "订单不存在: " + orderSn);
            }

            // 3. 检查订单状态
            if (tradePosition.getTradeOrderStatus() != TradePosition.TradeOrderStatus.DEAL) {
                if (isFinalCloseStatus(tradePosition.getTradeOrderStatus())) {
                    BigDecimal totalPosition = calculateTotalPosition(orderSn);
                    if (totalPosition.compareTo(BigDecimal.ZERO) <= 0) {
                        return successResult(result, "订单已平仓，无需重复平仓",
                                BigDecimal.ZERO, BigDecimal.ZERO, targetCloseAmount);
                    }
                }
                return errorResult(result, "订单状态不正确，无法平仓: " + tradePosition.getTradeOrderStatus());
            }

            // 4. 计算精确的持仓数量
            BigDecimal totalPosition = calculateTotalPosition(orderSn);
            if (totalPosition.compareTo(BigDecimal.ZERO) <= 0) {
                return successResult(result, "仓位已为零，无需平仓", BigDecimal.ZERO, BigDecimal.ZERO, targetCloseAmount);
            }

            // 5. 检查平仓数量是否超过持仓数量
            if (targetCloseAmount.compareTo(totalPosition) > 0) {
                log.warn("[按数量平仓] 平仓数量超过持仓数量，将进行全平: target={}, position={}",
                        targetCloseAmount, totalPosition);
                // 调用全平接口并转换为部分平仓结果
                ClosePositionResult fullResult = closeFullPosition(orderSn, currentPrice, closeTime,exitType);
                return convertToPartialResult(fullResult, targetCloseAmount, currentPrice);
            }

            log.info("[按数量平仓] 计算: orderSn={}, 目标数量={}, 总持仓={}, 价格={}",
                    orderSn, targetCloseAmount, totalPosition, currentPrice);

            // 6. 执行平仓（使用原有的closeOrderByVolume方法）
            // targetCloseAmount已经是调整好的张数，不需要再次调整精度
            boolean success = closeOrderByVolume(orderSn, targetCloseAmount, currentPrice, closeTime, exitType);

            if (success) {
                // 7. 计算实际平仓结果
                BigDecimal actualClosed = calculateActualClosedAmount(orderSn);
                BigDecimal remaining = totalPosition.subtract(actualClosed);
                BigDecimal actualRatio = actualClosed.divide(totalPosition, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

                // 8. 检查尾数
                boolean hasTail = remaining.compareTo(BigDecimal.ZERO) > 0;
                BigDecimal tailAmount = hasTail ? remaining : BigDecimal.ZERO;

                // 9. 更新结果
                result.setSuccess(true);
                result.setClosedAmount(actualClosed);
                result.setRemainingAmount(remaining);
                result.setActualRatio(actualRatio);
                result.setHasTail(hasTail);
                result.setTailAmount(tailAmount);
                result.setMessage("按数量平仓成功");

                log.info("[按数量平仓] 成功: orderSn={}, 目标={}, 实际={}, 剩余={}, 比例={}%, 价格={}",
                        orderSn, targetCloseAmount, actualClosed, remaining, actualRatio, currentPrice);

                // 10. 如果尾数很小，记录但不自动平（由调用方决定）
                if (hasTail && tailAmount.compareTo(DEFAULT_TAIL_THRESHOLD) < 0) {
                    log.warn("[按数量平仓] 检测到小尾巴: orderSn={}, tailAmount={}", orderSn, tailAmount);
                }
            } else {
                result.setSuccess(false);
                result.setMessage("平仓执行失败");
                log.error("[按数量平仓] 失败: orderSn={}", orderSn);
            }

        } catch (Exception e) {
            handleException(result, "按数量平仓异常", orderSn, e);
        }
        finally {
            BATCH_TAKE_PROFIT_FLAG.remove();
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SmartCloseResult smartClosePosition(String orderSn, BigDecimal targetCloseAmount,
                                               BigDecimal currentPrice, Date closeTime,
                                               BigDecimal minTradeAmount, BigDecimal tailThreshold) {
        log.info("[智能平仓] 开始: orderSn={}, targetAmount={}, price={}, time={}, minTrade={}, tailThreshold={}",
                orderSn, targetCloseAmount, currentPrice, closeTime, minTradeAmount, tailThreshold);

        SmartCloseResult result = new SmartCloseResult(orderSn);
        result.setTargetAmount(targetCloseAmount);
        result.setClosePrice(currentPrice);
        result.setCloseTime(closeTime != null ? closeTime : new Date());
        result.setMinTradeAmount(minTradeAmount != null ? minTradeAmount : BigDecimal.ZERO);

        try {
            // 1. 参数验证
            if (targetCloseAmount == null || targetCloseAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return errorResult(result, "平仓数量必须大于0");
            }

            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return errorResult(result, "平仓价格必须大于0");
            }

            // 2. 获取订单信息
            TradePosition tradePosition = getOrderByOrderSn(orderSn);
            if (tradePosition == null) {
                return errorResult(result, "订单不存在: " + orderSn);
            }

            // 3. 检查订单状态
            if (tradePosition.getTradeOrderStatus() != TradePosition.TradeOrderStatus.DEAL) {
                if (isFinalCloseStatus(tradePosition.getTradeOrderStatus())) {
                    BigDecimal totalPosition = calculateTotalPosition(orderSn);
                    if (totalPosition.compareTo(BigDecimal.ZERO) <= 0) {
                        return successSmartResult(result, "订单已平仓，无需重复平仓",
                                BigDecimal.ZERO, BigDecimal.ZERO, targetCloseAmount,
                                "ALREADY_CLOSED", true, currentPrice);
                    }
                }
                return errorResult(result, "订单状态不正确，无法平仓: " + tradePosition.getTradeOrderStatus());
            }

            // 4. 计算精确的持仓数量
            BigDecimal totalPosition = calculateTotalPosition(orderSn);
            if (totalPosition.compareTo(BigDecimal.ZERO) <= 0) {
                return successSmartResult(result, "仓位已为零，无需平仓", BigDecimal.ZERO, BigDecimal.ZERO,
                        targetCloseAmount, "NO_POSITION", false, currentPrice);
            }

            // 5. 获取最小交易量和尾数阈值
            BigDecimal finalMinTradeAmount = getMinTradeAmount(tradePosition, minTradeAmount);
            BigDecimal finalTailThreshold = tailThreshold != null ? tailThreshold : DEFAULT_TAIL_THRESHOLD;

            // 6. 智能决策
            String closeType;
            BigDecimal actualCloseAmount;
            boolean autoFullClose = false;

            // 计算平仓后剩余
            BigDecimal remainingAfterPartial = totalPosition.subtract(targetCloseAmount);

            // 决策逻辑：
            // 1) 如果目标平仓 >= 总持仓，全平
            // 2) 如果平仓后剩余 < 最小交易量，全平
            // 3) 如果平仓后剩余 < 尾数阈值，全平
            // 4) 否则，部分平仓
            if (targetCloseAmount.compareTo(totalPosition) >= 0) {
                closeType = "FULL_AUTO";
                actualCloseAmount = totalPosition;
                autoFullClose = true;
                log.info("[智能平仓] 决策: 目标>=持仓, 全平");
            } else if (remainingAfterPartial.compareTo(finalMinTradeAmount) < 0) {
                closeType = "FULL_MIN_TRADE";
                actualCloseAmount = totalPosition;
                autoFullClose = true;
                log.info("[智能平仓] 决策: 剩余<最小交易量, 全平");
            } else if (remainingAfterPartial.compareTo(finalTailThreshold) < 0) {
                closeType = "FULL_TAIL";
                actualCloseAmount = totalPosition;
                autoFullClose = true;
                log.info("[智能平仓] 决策: 剩余<尾数阈值, 全平");
            } else {
                closeType = "PARTIAL";
                actualCloseAmount = targetCloseAmount;
                autoFullClose = false;
                log.info("[智能平仓] 决策: 部分平仓");
            }

            result.setCloseType(closeType);
            result.setAutoFullClose(autoFullClose);

            // 7. 执行平仓
            PartialCloseResult partialResult;
            if (autoFullClose) {
                // 调用全平
                ClosePositionResult fullResult = closeFullPosition(orderSn, currentPrice, closeTime,null);
                partialResult = convertToPartialResult(fullResult, targetCloseAmount, currentPrice);
            } else {
                // 调用部分平仓
                partialResult = closePartialPosition(orderSn, actualCloseAmount, currentPrice, closeTime, null);
            }

            // 8. 复制结果
            copyResult(result, partialResult);

            if (partialResult.isSuccess()) {
                log.info("[智能平仓] 成功: orderSn={}, 类型={}, 平仓={}, 剩余={}, 价格={}",
                        orderSn, closeType, result.getClosedAmount(), result.getRemainingAmount(), currentPrice);
            }

        } catch (Exception e) {
            handleException(result, "智能平仓异常", orderSn, e);
        }

        return result;
    }

    @Override
    public BigDecimal getNetProfitByRobotId(String robotId, Date startTime, Date endTime) {
        try {
            if (robotId == null || robotId.isBlank()) {
                return BigDecimal.ZERO;
            }
            BigDecimal sum = tradeOrderCloseMapper.sumIncomeByRobotId(robotId, startTime, endTime);
            return sum != null ? sum : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("统计机器人当期盈亏失败: robotId={}", robotId, e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public BigDecimal getNetProfitByAccountId(String accountId, Date startTime, Date endTime) {
        try {
            if (accountId == null || accountId.isBlank()) {
                return BigDecimal.ZERO;
            }
            BigDecimal sum = tradeOrderCloseMapper.sumIncomeByAccountId(accountId, startTime, endTime);
            return sum != null ? sum : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("统计账户当期盈亏失败: accountId={}", accountId, e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public BigDecimal getCumulativeNetProfitByRobotId(String robotId) {
        try {
            if (robotId == null || robotId.isBlank()) {
                return BigDecimal.ZERO;
            }
            BigDecimal sum = tradeOrderCloseMapper.sumIncomeByRobotId(robotId, null, null);
            return sum != null ? sum : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("统计机器人累计盈亏失败: robotId={}", robotId, e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public BigDecimal getCumulativeNetProfitByAccountId(String accountId) {
        try {
            if (accountId == null || accountId.isBlank()) {
                return BigDecimal.ZERO;
            }
            BigDecimal sum = tradeOrderCloseMapper.sumIncomeByAccountId(accountId, null, null);
            return sum != null ? sum : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("统计账户累计盈亏失败: accountId={}", accountId, e);
            return BigDecimal.ZERO;
        }
    }

    // ==================== 辅助方法 ====================
    /**
     * 获取最小交易量
     */
    private BigDecimal getMinTradeAmount(TradePosition tradePosition, BigDecimal customMinTradeAmount) {
        // 如果传入自定义值，使用自定义值
        if (customMinTradeAmount != null && customMinTradeAmount.compareTo(BigDecimal.ZERO) > 0) {
            return customMinTradeAmount;
        }

        // 否则从合约规格获取
        try {
            Exchange platform = tradePosition.getMemberPlatform() != null ? tradePosition.getMemberPlatform() : Exchange.OKX;
            String symbol = tradePosition.getSymbol();
            if (symbol != null) {
                symbol = ContractSpecUtils.normalizeSymbol(platform, symbol);
                ContractSpec contractSpec = ContractSpecUtils.getContractSpec(redisCache, platform, symbol);
                if (contractSpec != null) {
                    return BigDecimal.valueOf(1);
                }
            }
        } catch (Exception e) {
            log.warn("获取最小交易量失败: orderSn={}", tradePosition.getPositionId(), e);
        }

        return new BigDecimal("1"); // 默认最小交易量
    }
    /**
     * 计算订单的总持仓数量
     */
    private BigDecimal calculateTotalPosition(String orderSn) {
        try {
            LambdaQueryWrapper<TradeEntry> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TradeEntry::getPositionId, orderSn);
            List<TradeEntry> orderItems = tradeOrderItemMapper.selectList(wrapper);

            BigDecimal totalPosition = BigDecimal.ZERO;
            for (TradeEntry item : orderItems) {
                if (item.getTradeOrderItemStatus() == TradePosition.TradeOrderStatus.DEAL) {
                    BigDecimal itemVolume = item.getVolume() != null ? item.getVolume() : item.getAmount();
                    BigDecimal itemClosed = item.getClosedVolume() != null ? item.getClosedVolume() : BigDecimal.ZERO;
                    BigDecimal itemCanClose = itemVolume.subtract(itemClosed);

                    if (itemCanClose.compareTo(BigDecimal.ZERO) > 0) {
                        totalPosition = totalPosition.add(itemCanClose);
                    }
                }
            }

            return totalPosition;
        } catch (Exception e) {
            log.error("计算持仓数量失败: orderSn={}", orderSn, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 更新主订单的聚合信息（数量和平均价格）
     */
    private void updateOrderAggregates(String orderSn) {
        try {
            LambdaQueryWrapper<TradePosition> orderWrapper = new LambdaQueryWrapper<>();
            orderWrapper.eq(TradePosition::getPositionId, orderSn);
            TradePosition order = tradeOrderMapper.selectOne(orderWrapper);
            if (order == null) {
                return;
            }

            LambdaQueryWrapper<TradeEntry> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(TradeEntry::getPositionId, orderSn);
            List<TradeEntry> orderItems = tradeOrderItemMapper.selectList(itemWrapper);

            BigDecimal totalOpenVolume = BigDecimal.ZERO;
            BigDecimal weightedPriceSum = BigDecimal.ZERO;

            for (TradeEntry item : orderItems) {
                if (item.getTradeOrderItemStatus() != TradePosition.TradeOrderStatus.DEAL) {
                    continue;
                }

                BigDecimal itemVolume = item.getVolume() != null ? item.getVolume() : item.getAmount();
                if (itemVolume == null) {
                    continue;
                }
                BigDecimal itemClosed = item.getClosedVolume() != null ? item.getClosedVolume() : BigDecimal.ZERO;
                BigDecimal openVolume = itemVolume.subtract(itemClosed);
                if (openVolume.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal price = item.getBuyPrice() != null ? item.getBuyPrice() : BigDecimal.ZERO;
                weightedPriceSum = weightedPriceSum.add(price.multiply(openVolume));
                totalOpenVolume = totalOpenVolume.add(openVolume);
            }

            if (totalOpenVolume.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal avgPrice = weightedPriceSum.divide(totalOpenVolume, 8, RoundingMode.HALF_UP);
                order.setAmount(totalOpenVolume);
                order.setVolume(totalOpenVolume);
                order.setBuyAvgPrice(avgPrice);
            } else {
                order.setAmount(BigDecimal.ZERO);
                order.setVolume(BigDecimal.ZERO);
                order.setBuyAvgPrice(null);
            }

            tradeOrderMapper.updateById(order);
        } catch (Exception e) {
            log.error("更新订单聚合信息失败: orderSn={}", orderSn, e);
        }
    }

    /**
     * 平仓剩余尾数
     */
    private boolean closeRemainingTail(String orderSn, BigDecimal remainingAmount,
                                       TradePosition tradePosition, BigDecimal currentPrice, Date closeTime, ExitType exitType) {
        try {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return true; // 无需平仓
            }

            // 检查是否为微小尾数
            if (remainingAmount.compareTo(DEFAULT_TAIL_THRESHOLD) < 0) {
                log.info("[尾数平仓] 执行: orderSn={}, 尾数={}, 价格={}", orderSn, remainingAmount, currentPrice);
                return closeOrderByVolume(orderSn, remainingAmount, currentPrice, closeTime, exitType);
            }

            return false;
        } catch (Exception e) {
            log.error("尾数平仓失败: orderSn={}", orderSn, e);
            return false;
        }
    }

    /**
     * 转换全平结果为部分平仓结果
     */
    private PartialCloseResult convertToPartialResult(ClosePositionResult fullResult,
                                                      BigDecimal targetAmount, BigDecimal currentPrice) {
        PartialCloseResult result = new PartialCloseResult(fullResult.getOrderSn());

        result.setSuccess(fullResult.isSuccess());
        result.setMessage(fullResult.getMessage());
        result.setClosedAmount(fullResult.getClosedAmount());
        result.setRemainingAmount(fullResult.getRemainingAmount());
        result.setClosePrice(currentPrice != null ? currentPrice : fullResult.getClosePrice());
        result.setCloseTime(fullResult.getCloseTime());
        result.setTargetAmount(targetAmount);

        // 计算比例
        if (fullResult.getOriginalPosition() != null &&
                fullResult.getOriginalPosition().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = fullResult.getClosedAmount()
                    .divide(fullResult.getOriginalPosition(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            result.setActualRatio(ratio);
        }

        // 检查尾数
        boolean hasTail = fullResult.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0;
        result.setHasTail(hasTail);
        result.setTailAmount(hasTail ? fullResult.getRemainingAmount() : BigDecimal.ZERO);

        return result;
    }

    /**
     * 复制结果
     */
    private void copyResult(PartialCloseResult target, PartialCloseResult source) {
        target.setSuccess(source.isSuccess());
        target.setMessage(source.getMessage());
        target.setClosedAmount(source.getClosedAmount());
        target.setRemainingAmount(source.getRemainingAmount());
        target.setClosePrice(source.getClosePrice());
        target.setCloseTime(source.getCloseTime());
        target.setActualRatio(source.getActualRatio());
        target.setHasTail(source.isHasTail());
        target.setTailAmount(source.getTailAmount());
        target.setTargetAmount(source.getTargetAmount());
    }

    /**
     * 计算实际平仓数量（从平仓记录统计）
     */
    private BigDecimal calculateActualClosedAmount(String orderSn) {
        try {
            LambdaQueryWrapper<TradeExitBatch> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TradeExitBatch::getPositionId, orderSn);
            List<TradeExitBatch> closes = tradeOrderCloseMapper.selectList(wrapper);

            BigDecimal totalClosed = BigDecimal.ZERO;
            for (TradeExitBatch close : closes) {
                if (close.getClosedVolume() != null) {
                    totalClosed = totalClosed.add(close.getClosedVolume());
                }
            }

            return totalClosed;
        } catch (Exception e) {
            log.error("计算实际平仓数量失败: orderSn={}", orderSn, e);
            return BigDecimal.ZERO;
        }
    }

    private String getLatestCloseReason(String orderSn) {
        if (orderSn == null || orderSn.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<TradeExitItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeExitItem::getPositionId, orderSn)
                .orderByDesc(TradeExitItem::getExitTime)
                .orderByDesc(TradeExitItem::getCreateTime)
                .last("limit 1");
        TradeExitItem item = tradeOrderCloseItemMapper.selectOne(wrapper);
        if (item == null) {
            return null;
        }
        return item.getCloseMethod();
    }

    // ==================== 结果处理辅助方法 ====================

    /**
     * 错误结果处理
     */
    private <T extends BaseCloseResult> T errorResult(T result, String message) {
        result.setSuccess(false);
        result.setMessage(message);
        log.error(message);
        return result;
    }

    /**
     * 成功结果处理（全平）
     */
    private ClosePositionResult successResult(ClosePositionResult result, String message,
                                              BigDecimal closedAmount, BigDecimal remainingAmount,
                                              boolean isFullClose, BigDecimal originalPosition) {
        result.setSuccess(true);
        result.setMessage(message);
        result.setClosedAmount(closedAmount);
        result.setRemainingAmount(remainingAmount);
        result.setFullClose(isFullClose);
        result.setOriginalPosition(originalPosition);
        return result;
    }

    /**
     * 成功结果处理（部分平仓）
     */
    private PartialCloseResult successResult(PartialCloseResult result, String message,
                                             BigDecimal closedAmount, BigDecimal remainingAmount,
                                             BigDecimal targetAmount) {
        result.setSuccess(true);
        result.setMessage(message);
        result.setClosedAmount(closedAmount);
        result.setRemainingAmount(remainingAmount);
        result.setTargetAmount(targetAmount);
        return result;
    }

    /**
     * 成功结果处理（智能平仓）
     */
    private SmartCloseResult successSmartResult(SmartCloseResult result, String message,
                                                BigDecimal closedAmount, BigDecimal remainingAmount,
                                                BigDecimal targetAmount, String closeType,
                                                boolean autoFullClose, BigDecimal currentPrice) {
        result.setSuccess(true);
        result.setMessage(message);
        result.setClosedAmount(closedAmount);
        result.setRemainingAmount(remainingAmount);
        result.setTargetAmount(targetAmount);
        result.setCloseType(closeType);
        result.setAutoFullClose(autoFullClose);
        result.setClosePrice(currentPrice);
        return result;
    }

    private boolean isFinalCloseStatus(TradePosition.TradeOrderStatus status) {
        return status == TradePosition.TradeOrderStatus.CLOSE
                || status == TradePosition.TradeOrderStatus.LOSS
                || status == TradePosition.TradeOrderStatus.GAIN;
    }

    /**
     * 异常处理
     */
    private <T extends BaseCloseResult> void handleException(T result, String prefix, String orderSn, Exception e) {
        result.setSuccess(false);
        result.setMessage(prefix + ": " + e.getMessage());
        log.error("{}: orderSn={}", prefix, orderSn, e);
    }

    @Override
    public java.math.BigDecimal getRemainingPositionByOrderSn(String orderSn) {
        try {
            return calculateTotalPosition(orderSn);
        } catch (Exception e) {
            log.error("获取订单剩余持仓失败: orderSn={}", orderSn, e);
            return java.math.BigDecimal.ZERO;
        }
    }
}
