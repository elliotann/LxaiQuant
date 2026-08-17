package com.chain.ai.trade.logs.bus;

/**
 * 事件总线管理器
 * 提供全局事件总线实例
 */
public class EventBusManager {
    
    private static final EventBus INSTANCE = new EventBus();
    
    /**
     * 获取事件总线实例
     * 
     * @return 事件总线实例
     */
    public static EventBus getInstance() {
        return INSTANCE;
    }
    
    /**
     * 私有构造函数，防止实例化
     */
    private EventBusManager() {
        // 私有构造函数
    }
}