package com.chain.ai.trade.logs.log;

/**
 * 业务日志接口
 */
public interface BusinessLog {
    
    /**
     * 获取时间戳
     */
    long getTimestamp();
    
    /**
     * 获取日志类型
     */
    String getLogType();
    
    /**
     * 获取日志级别
     */
    String getLevel();
    
    /**
     * 序列化为JSON字符串
     */
    String toJson();
    
    /**
     * 获取追踪ID
     */
    default String getTraceId() {
        return null;
    }
}