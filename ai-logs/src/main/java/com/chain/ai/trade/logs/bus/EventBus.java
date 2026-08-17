package com.chain.ai.trade.logs.bus;

import com.chain.ai.trade.logs.event.BusinessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件总线实现
 * 支持同步和异步事件发布
 */
@Component
public class EventBus {
    
    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);
    
    private final Map<String, List<BusinessEventListener<?>>> listeners = new ConcurrentHashMap<>();
    
    /**
     * 注册事件监听器
     * 
     * @param eventType 事件类型
     * @param listener 监听器
     */
    public <T extends BusinessEvent> void register(String eventType, BusinessEventListener<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
        logger.info("Registered listener for event type: {}", eventType);
    }
    
    /**
     * 注销事件监听器
     * 
     * @param eventType 事件类型
     * @param listener 监听器
     */
    public <T extends BusinessEvent> void unregister(String eventType, BusinessEventListener<T> listener) {
        List<BusinessEventListener<?>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
            logger.info("Unregistered listener for event type: {}", eventType);
        }
    }
    
    /**
     * 发布事件（同步）
     * 
     * @param event 业务事件
     */
    @SuppressWarnings("unchecked")
    public void publish(BusinessEvent event) {
        String eventType = event.getEventType();
        List<BusinessEventListener<?>> eventListeners = listeners.get(eventType);
        
        if (eventListeners != null && !eventListeners.isEmpty()) {
            logger.debug("Publishing event: {} to {} listeners", eventType, eventListeners.size());
            
            for (BusinessEventListener<?> listener : eventListeners) {
                try {
                    ((BusinessEventListener<BusinessEvent>) listener).onEvent(event);
                } catch (Exception e) {
                    logger.error("Error processing event: {} by listener: {}", eventType, listener.getClass().getName(), e);
                }
            }
        } else {
            logger.debug("No listeners registered for event type: {}", eventType);
        }
    }
    
    /**
     * 发布事件（异步）
     * 
     * @param event 业务事件
     */
    public void publishAsync(BusinessEvent event) {
        // 这里可以集成线程池实现真正的异步处理
        // 目前先使用同步方式，后续可以扩展
        publish(event);
    }
    
    /**
     * 获取注册的事件类型
     * 
     * @return 事件类型列表
     */
    public List<String> getRegisteredEventTypes() {
        return List.copyOf(listeners.keySet());
    }
    
    /**
     * 获取指定事件类型的监听器数量
     * 
     * @param eventType 事件类型
     * @return 监听器数量
     */
    public int getListenerCount(String eventType) {
        List<BusinessEventListener<?>> eventListeners = listeners.get(eventType);
        return eventListeners != null ? eventListeners.size() : 0;
    }
}