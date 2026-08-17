package com.chain.ai.trade.engine.strategy;

import com.chain.ai.trade.extension.strategy.IndicatorCriticalLevelsStrategy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class IndicatorStrategyRouter {

    @Autowired
    private ApplicationContext applicationContext;

    private final Map<String, IndicatorCriticalLevelsStrategy> strategyCache = new ConcurrentHashMap<>();
    private IndicatorCriticalLevelsStrategy defaultStrategy;

    @PostConstruct
    public void init() {
        defaultStrategy = applicationContext.getBean(DefaultIndicatorCriticalLevelsStrategy.class);
        log.info("IndicatorStrategyRouter 已初始化, 默认策略: {}", defaultStrategy.getClass().getSimpleName());
    }

    public IndicatorCriticalLevelsStrategy resolve(String robotId) {
        if (robotId == null || robotId.isBlank()) {
            return defaultStrategy;
        }
        return strategyCache.computeIfAbsent(robotId, id -> {
            Map<String, IndicatorCriticalLevelsStrategy> beans =
                    applicationContext.getBeansOfType(IndicatorCriticalLevelsStrategy.class);
            for (IndicatorCriticalLevelsStrategy strategy : beans.values()) {
                if (strategy.getClass().isAnnotationPresent(
                        org.springframework.stereotype.Component.class)) {
                    String beanName = strategy.getClass().getSimpleName();
                    if (beanName.toLowerCase().contains(id.toLowerCase())) {
                        log.info("匹配到策略: {} -> {}", id, beanName);
                        return strategy;
                    }
                }
            }
            log.debug("未找到匹配策略: {}, 使用默认策略", id);
            return defaultStrategy;
        });
    }
}
