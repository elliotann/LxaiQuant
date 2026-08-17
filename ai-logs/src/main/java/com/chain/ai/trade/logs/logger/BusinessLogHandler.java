package com.chain.ai.trade.logs.logger;

import com.chain.ai.trade.logs.log.BusinessLog;

/**
 * 业务日志处理器接口
 */
public interface BusinessLogHandler {
    
    /**
     * 处理业务日志
     * 
     * @param log 业务日志
     */
    void handle(BusinessLog log);
    
    /**
     * 启动处理器
     */
    void start();
    
    /**
     * 停止处理器
     */
    void stop();
    
    /**
     * 获取处理器名称
     * 
     * @return 处理器名称
     */
    String getName();
}