package com.chain.ai.trade.logs.log;

/**
 * 可追踪接口，提供追踪ID
 */
public interface Traceable {
    
    /**
     * 获取追踪ID
     */
    String getTraceId();
}