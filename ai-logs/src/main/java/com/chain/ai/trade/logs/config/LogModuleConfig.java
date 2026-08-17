package com.chain.ai.trade.logs.config;

import com.chain.ai.trade.logs.bus.BusinessLogListener;
import com.chain.ai.trade.logs.bus.EventBus;
import com.chain.ai.trade.logs.bus.EventBusManager;
import com.chain.ai.trade.logs.logger.AsyncDatabaseLogger;
import com.chain.ai.trade.logs.logger.AsyncWebSocketLogger;
import com.chain.ai.trade.logs.service.BusinessLogService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@MapperScan("com.chain.ai.trade.logs.mapper")
public class LogModuleConfig {

    @Bean
    public EventBus eventBus() {
        return EventBusManager.getInstance();
    }

    @Bean
    public AsyncDatabaseLogger asyncDatabaseLogger(BusinessLogService businessLogService) {
        AsyncDatabaseLogger logger = new AsyncDatabaseLogger(businessLogService);
        logger.start(); // Start the logger after creation
        return logger;
    }

    @Bean
    public AsyncWebSocketLogger asyncWebSocketLogger() {
        AsyncWebSocketLogger logger = new AsyncWebSocketLogger();
        logger.start(); // Start the logger after creation
        return logger;
    }

    @Bean
    public BusinessLogListener businessLogListener(EventBus eventBus,
                                                   AsyncDatabaseLogger dbLogger,
                                                   AsyncWebSocketLogger wsLogger) {
        BusinessLogListener listener = new BusinessLogListener(eventBus, List.of(dbLogger, wsLogger));
        // Register the listener for all events
        eventBus.register("ORDER_PLACED", listener);
        eventBus.register("TRADE", listener);
        eventBus.register("ACCOUNT_FUND_CHANGE", listener);
        eventBus.register("RISK_CONTROL_TRIGGERED", listener);
        eventBus.register("STRATEGY_STATUS_CHANGE", listener);
        eventBus.register("SYSTEM_ERROR", listener);
        return listener;
    }
}
