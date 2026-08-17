package com.chain.ai.trade.logs.bus;

import com.chain.ai.trade.logs.event.BusinessEvent;
import com.chain.ai.trade.logs.event.OrderPlacedEvent;
import com.chain.ai.trade.logs.event.TradeEvent;
import com.chain.ai.trade.logs.event.AccountFundChangeEvent;
import com.chain.ai.trade.logs.event.RiskControlTriggeredEvent;
import com.chain.ai.trade.logs.event.StrategyStatusChangeEvent;
import com.chain.ai.trade.logs.event.SystemErrorEvent;
import com.chain.ai.trade.logs.log.BusinessLog;
import com.chain.ai.trade.logs.log.OrderLog;
import com.chain.ai.trade.logs.log.TradeLog;
import com.chain.ai.trade.logs.log.AccountFundChangeLog;
import com.chain.ai.trade.logs.log.RiskControlLog;
import com.chain.ai.trade.logs.log.StrategyStatusChangeLog;
import com.chain.ai.trade.logs.log.SystemErrorLog;
import com.chain.ai.trade.logs.logger.BusinessLogHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * 业务日志监听器
 * 将业务事件转换为业务日志并发布到日志处理器
 */
public class BusinessLogListener implements BusinessEventListener<BusinessEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(BusinessLogListener.class);
    
    private final EventBus eventBus;
    private final List<BusinessLogHandler> handlers;
    
    public BusinessLogListener(EventBus eventBus, List<BusinessLogHandler> handlers) {
        this.eventBus = eventBus;
        this.handlers = handlers;
    }
    
    @Override
    public void onEvent(BusinessEvent event) {
        logger.debug("Processing business event: {}", event.getEventType());
        
        try {
            BusinessLog log = convertEventToLog(event);
            if (log != null) {
                logger.info("Generated business log: {}", log.toJson());
                if (handlers != null && !handlers.isEmpty()) {
                    for (BusinessLogHandler handler : handlers) {
                        try {
                            handler.handle(log);
                        } catch (Exception e) {
                            logger.error("Error in handler {} while processing log {}", handler.getName(), log.getLogType(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error converting event to log: {}", event.getEventType(), e);
        }
    }
    
    /**
     * 将业务事件转换为业务日志
     * 
     * @param event 业务事件
     * @return 业务日志
     */
    private BusinessLog convertEventToLog(BusinessEvent event) {
        String eventType = event.getEventType();
        
        switch (eventType) {
            case "ORDER_PLACED":
                return convertOrderPlacedEvent((OrderPlacedEvent) event);
            case "TRADE":
                return convertTradeEvent((TradeEvent) event);
            case "ACCOUNT_FUND_CHANGE":
                return convertAccountFundChangeEvent((AccountFundChangeEvent) event);
            case "RISK_CONTROL_TRIGGERED":
                return convertRiskControlTriggeredEvent((RiskControlTriggeredEvent) event);
            case "STRATEGY_STATUS_CHANGE":
                return convertStrategyStatusChangeEvent((StrategyStatusChangeEvent) event);
            case "SYSTEM_ERROR":
                return convertSystemErrorEvent((SystemErrorEvent) event);
            default:
                logger.warn("Unknown event type: {}", eventType);
                return null;
        }
    }
    
    private OrderLog convertOrderPlacedEvent(OrderPlacedEvent event) {
        return new OrderLog(
            "INFO",
            event.getTraceId(),
            event.getOrderId(),
            null,
            event.getSymbol(),
            event.getSide(),
            event.getPrice(),
            event.getQuantity(),
            event.getStatus()
        );
    }
    
    private TradeLog convertTradeEvent(TradeEvent event) {
        return new TradeLog(
            "INFO",
            event.getTraceId(),
            event.getTradeId(),
            event.getOrderId(),
            null,
            event.getSymbol(),
            event.getSide(),
            event.getPrice(),
            event.getQuantity(),
            event.getFee()
        );
    }
    
    private AccountFundChangeLog convertAccountFundChangeEvent(AccountFundChangeEvent event) {
        return new AccountFundChangeLog(
            "INFO",
            event.getTraceId(),
            event.getAccountId(),
            event.getCurrency(),
            event.getAmount(),
            event.getBalance(),
            event.getChangeType()
        );
    }
    
    private RiskControlLog convertRiskControlTriggeredEvent(RiskControlTriggeredEvent event) {
        return new RiskControlLog(
            "WARN",
            event.getTraceId(),
            event.getRuleId(),
            event.getRuleName(),
            event.getSymbol(),
            event.getAction(),
            event.getReason()
        );
    }
    
    private StrategyStatusChangeLog convertStrategyStatusChangeEvent(StrategyStatusChangeEvent event) {
        return new StrategyStatusChangeLog(
            "INFO",
            event.getTraceId(),
            event.getStrategyId(),
            event.getStrategyName(),
            event.getOldStatus(),
            event.getNewStatus(),
            event.getReason()
        );
    }
    
    private SystemErrorLog convertSystemErrorEvent(SystemErrorEvent event) {
        return new SystemErrorLog(
            "ERROR",
            event.getTraceId(),
            event.getErrorCode(),
            event.getErrorMessage(),
            event.getComponent(),
            event.getStackTrace()
        );
    }
    
    @Override
    public String getEventType() {
        return null; // 监听所有事件类型
    }
}
