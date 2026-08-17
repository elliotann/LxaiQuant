package com.chain.ai.trade.logs.bus;

import com.chain.ai.trade.logs.event.BusinessEvent;

/**
 * 业务事件监听器接口
 */
@FunctionalInterface
public interface BusinessEventListener<T extends BusinessEvent> {
    
    /**
     * 处理事件
     * 
     * @param event 业务事件
     */
    void onEvent(T event);
    
    /**
     * 获取监听的事件类型
     * 
     * @return 事件类型
     */
    default String getEventType() {
        return null;
    }
}