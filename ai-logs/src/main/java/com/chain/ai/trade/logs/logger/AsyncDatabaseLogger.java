package com.chain.ai.trade.logs.logger;

import com.chain.ai.trade.logs.entity.*;
import com.chain.ai.trade.logs.log.*;
import com.chain.ai.trade.logs.service.BusinessLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步数据库日志处理器
 * 将业务日志批量写入数据库，提高性能
 */
@Slf4j
@Component
public class AsyncDatabaseLogger extends AbstractBusinessLogHandler {
    
    private final BusinessLogService businessLogService;
    
    // 批量处理大小
    private static final int BATCH_SIZE = 100;
    
    // 最大等待时间（毫秒）
    private static final long MAX_WAIT_TIME = 5000;
    
    // 日志队列
    private final BlockingQueue<BusinessLog> logQueue = new LinkedBlockingQueue<>(10000);
    
    // 工作线程
    private Thread workerThread;
    
    // 运行标志
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public AsyncDatabaseLogger(BusinessLogService businessLogService) {
        super("AsyncDatabaseLogger");
        this.businessLogService = businessLogService;
    }
    
    @Override
    protected void processLog(BusinessLog bizLog) {
        try {
            // 将日志放入队列，如果队列满了则等待
            boolean offered = logQueue.offer(bizLog, 1, TimeUnit.SECONDS);
            if (!offered) {
                log.warn("Log queue is full, dropping log: {}", bizLog.getLogType());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while adding log to queue", e);
        }
    }
    
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            workerThread = new Thread(this::processLogs, "AsyncDatabaseLogger-Worker");
            workerThread.setDaemon(true);
            workerThread.start();
            log.info("AsyncDatabaseLogger started");
        }
    }
    
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (workerThread != null) {
                workerThread.interrupt();
                try {
                    workerThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // 处理剩余的日志
            processRemainingLogs();
            log.info("AsyncDatabaseLogger stopped");
        }
    }
    
    /**
     * 处理日志的主循环
     */
    private void processLogs() {
        List<BusinessLog> batch = new ArrayList<>(BATCH_SIZE);
        long lastFlushTime = System.currentTimeMillis();
        
        while (running.get()) {
            try {
                // 等待日志或超时
                BusinessLog polled = logQueue.poll(MAX_WAIT_TIME, TimeUnit.MILLISECONDS);
                
                if (polled != null) {
                    batch.add(polled);
                }
                
                // 检查是否需要批量处理
                boolean shouldFlush = batch.size() >= BATCH_SIZE || 
                                    (System.currentTimeMillis() - lastFlushTime >= MAX_WAIT_TIME && !batch.isEmpty());
                
                if (shouldFlush) {
                    flushBatch(batch);
                    batch.clear();
                    lastFlushTime = System.currentTimeMillis();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Worker thread interrupted");
                break;
            } catch (Exception e) {
                log.error("Error processing log batch", e);
            }
        }
        
        // 处理剩余的日志
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }
    }
    
    /**
     * 批量刷新日志到数据库
     */
    private void flushBatch(List<BusinessLog> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        try {
            log.debug("Flushing {} logs to database", batch.size());
            
            // 按日志类型分组
            List<OrderLogEntity> orderLogs = new ArrayList<>();
            List<TradeLogEntity> tradeLogs = new ArrayList<>();
            List<AccountFundChangeLogEntity> accountFundLogs = new ArrayList<>();
            List<RiskControlLogEntity> riskControlLogs = new ArrayList<>();
            List<StrategyStatusChangeLogEntity> strategyLogs = new ArrayList<>();
            List<SystemErrorLogEntity> systemErrorLogs = new ArrayList<>();
            
            for (BusinessLog item : batch) {
                try {
                    switch (item.getLogType()) {
                        case "ORDER":
                            if (item instanceof OrderLog) {
                                orderLogs.add(convertToOrderLogEntity((OrderLog) item));
                            }
                            break;
                        case "TRADE":
                            if (item instanceof TradeLog) {
                                tradeLogs.add(convertToTradeLogEntity((TradeLog) item));
                            }
                            break;
                        case "ACCOUNT_FUND_CHANGE":
                            if (item instanceof AccountFundChangeLog) {
                                accountFundLogs.add(convertToAccountFundChangeLogEntity((AccountFundChangeLog) item));
                            }
                            break;
                        case "RISK_CONTROL_TRIGGERED":
                            if (item instanceof RiskControlLog) {
                                riskControlLogs.add(convertToRiskControlLogEntity((RiskControlLog) item));
                            }
                            break;
                        case "STRATEGY_STATUS_CHANGE":
                            if (item instanceof StrategyStatusChangeLog) {
                                strategyLogs.add(convertToStrategyStatusChangeLogEntity((StrategyStatusChangeLog) item));
                            }
                            break;
                        case "SYSTEM_ERROR":
                            if (item instanceof SystemErrorLog) {
                                systemErrorLogs.add(convertToSystemErrorLogEntity((SystemErrorLog) item));
                            }
                            break;
                        default:
                            log.warn("Unknown log type: {}", item.getLogType());
                    }
                } catch (Exception e) {
                    log.error("Error converting log: {}", item.getLogType(), e);
                }
            }
            
            // 批量保存到数据库
            if (!orderLogs.isEmpty()) {
                businessLogService.saveOrderLogs(orderLogs);
            }
            if (!tradeLogs.isEmpty()) {
                businessLogService.saveTradeLogs(tradeLogs);
            }
            if (!accountFundLogs.isEmpty()) {
                businessLogService.saveAccountFundChangeLogs(accountFundLogs);
            }
            if (!riskControlLogs.isEmpty()) {
                businessLogService.saveRiskControlLogs(riskControlLogs);
            }
            if (!strategyLogs.isEmpty()) {
                businessLogService.saveStrategyStatusChangeLogs(strategyLogs);
            }
            if (!systemErrorLogs.isEmpty()) {
                businessLogService.saveSystemErrorLogs(systemErrorLogs);
            }
            
            log.debug("Successfully flushed {} logs to database", batch.size());
            
        } catch (Exception e) {
            log.error("Error flushing batch to database", e);
            // 可以在这里实现重试机制或发送到错误队列
        }
    }
    
    /**
     * 处理剩余的日志
     */
    private void processRemainingLogs() {
        List<BusinessLog> remainingLogs = new ArrayList<>();
        logQueue.drainTo(remainingLogs);
        
        if (!remainingLogs.isEmpty()) {
            log.info("Processing {} remaining logs", remainingLogs.size());
            flushBatch(remainingLogs);
        }
    }
    
    // 转换方法
    private OrderLogEntity convertToOrderLogEntity(OrderLog log) {
        OrderLogEntity entity = new OrderLogEntity();
        entity.setTraceId(log.getTraceId());
        entity.setEventType(log.getLogType());
        entity.setEventTime(LocalDateTime.now());
        entity.setUserId(null);
        entity.setAccountId(null);
        entity.setStrategyId(log.getStrategyId());
        entity.setOrderId(log.getOrderId());
        entity.setSymbol(log.getSymbol());
        entity.setOrderSide(log.getSide());
        entity.setOrderType(null);
        entity.setPrice(BigDecimal.valueOf(log.getPrice()));
        entity.setQuantity(BigDecimal.valueOf(log.getQuantity()));
        entity.setAmount(BigDecimal.valueOf(log.getPrice()).multiply(BigDecimal.valueOf(log.getQuantity())));
        entity.setStatus(log.getStatus());
        entity.setClientOrderId(null);
        entity.setRemark(null);
        entity.setExtraData(null);
        return entity;
    }
    
    private TradeLogEntity convertToTradeLogEntity(TradeLog log) {
        TradeLogEntity entity = new TradeLogEntity();
        entity.setTraceId(log.getTraceId());
        entity.setEventType(log.getLogType());
        entity.setEventTime(LocalDateTime.now());
        entity.setUserId(null);
        entity.setAccountId(null);
        entity.setStrategyId(log.getStrategyId());
        entity.setTradeId(log.getTradeId());
        entity.setOrderId(log.getOrderId());
        entity.setSymbol(log.getSymbol());
        entity.setTradeSide(log.getSide());
        entity.setPrice(BigDecimal.valueOf(log.getPrice()));
        entity.setQuantity(BigDecimal.valueOf(log.getQuantity()));
        entity.setAmount(BigDecimal.valueOf(log.getPrice()).multiply(BigDecimal.valueOf(log.getQuantity())));
        entity.setFee(BigDecimal.valueOf(log.getFee()));
        entity.setFeeCurrency(null);
        entity.setIsMaker(null);
        entity.setClientOrderId(null);
        entity.setRemark(null);
        entity.setExtraData(null);
        return entity;
    }
    
    private AccountFundChangeLogEntity convertToAccountFundChangeLogEntity(AccountFundChangeLog log) {
        AccountFundChangeLogEntity entity = new AccountFundChangeLogEntity();
        entity.setTraceId(log.getTraceId());
        entity.setEventType(log.getLogType());
        entity.setEventTime(LocalDateTime.now());
        entity.setUserId(null);
        try {
            entity.setAccountId(log.getAccountId() != null ? Long.parseLong(log.getAccountId()) : null);
        } catch (NumberFormatException e) {
            entity.setAccountId(null);
        }
        entity.setStrategyId(null);
        entity.setCurrency(log.getAsset());
        entity.setChangeType(log.getChangeType());
        entity.setAmount(BigDecimal.valueOf(log.getAmount()));
        entity.setBalanceBefore(null);
        entity.setBalanceAfter(null);
        entity.setAvailableBefore(null);
        entity.setAvailableAfter(null);
        entity.setFrozenBefore(null);
        entity.setFrozenAfter(null);
        entity.setRelatedOrderId(null);
        entity.setRelatedTradeId(null);
        entity.setRemark(null);
        entity.setExtraData(null);
        return entity;
    }
    
    private RiskControlLogEntity convertToRiskControlLogEntity(RiskControlLog log) {
        RiskControlLogEntity entity = new RiskControlLogEntity();
        entity.setTraceId(log.getTraceId());
        entity.setEventType(log.getLogType());
        entity.setEventTime(LocalDateTime.now());
        entity.setUserId(null);
        entity.setAccountId(null);
        entity.setStrategyId(null);
        entity.setRiskType(log.getRuleId());
        entity.setRiskLevel(null);
        entity.setTriggerValue(null);
        entity.setThresholdValue(null);
        entity.setActionTaken(log.getAction());
        entity.setRelatedOrderId(null);
        entity.setRelatedSymbol(log.getSubject());
        entity.setDescription(log.getReason());
        entity.setRemark(null);
        entity.setExtraData(null);
        return entity;
    }
    
    private StrategyStatusChangeLogEntity convertToStrategyStatusChangeLogEntity(StrategyStatusChangeLog log) {
        StrategyStatusChangeLogEntity entity = new StrategyStatusChangeLogEntity();
        entity.setTraceId(log.getTraceId());
        entity.setEventType(log.getLogType());
        entity.setEventTime(LocalDateTime.now());
        entity.setStrategyId(log.getStrategyId());
        entity.setUserId(null);
        entity.setAccountId(null);
        entity.setStrategyName(null);
        entity.setStatusBefore(log.getOldStatus());
        entity.setStatusAfter(log.getNewStatus());
        entity.setChangeReason(log.getReason());
        entity.setPerformanceData(null);
        entity.setPositionData(null);
        entity.setRunningParameters(null);
        entity.setDescription(null);
        entity.setRemark(null);
        entity.setExtraData(null);
        return entity;
    }
    
    private SystemErrorLogEntity convertToSystemErrorLogEntity(SystemErrorLog log) {
        SystemErrorLogEntity entity = new SystemErrorLogEntity();
        entity.setTraceId(log.getTraceId());
        entity.setEventType(log.getLogType());
        entity.setEventTime(LocalDateTime.now());
        entity.setErrorCode(log.getErrorCode());
        entity.setErrorType(null);
        entity.setErrorLevel(log.getLevel());
        entity.setErrorMessage(log.getErrorMessage());
        entity.setErrorStack(log.getStackTrace());
        entity.setRelatedOrderId(null);
        entity.setRelatedApi(log.getComponent());
        entity.setRetryCount(null);
        entity.setResolved(null);
        entity.setDescription(null);
        entity.setRemark(null);
        entity.setExtraData(null);
        return entity;
    }
}
