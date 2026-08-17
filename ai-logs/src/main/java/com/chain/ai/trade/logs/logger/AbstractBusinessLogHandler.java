package com.chain.ai.trade.logs.logger;

import com.chain.ai.trade.logs.log.BusinessLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 抽象业务日志处理器
 * 提供异步处理的基础功能
 */
public abstract class AbstractBusinessLogHandler implements BusinessLogHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AbstractBusinessLogHandler.class);
    
    private final BlockingQueue<BusinessLog> logQueue;
    private final Thread workerThread;
    private final AtomicBoolean running;
    private final String name;
    
    public AbstractBusinessLogHandler(String name) {
        this.name = name;
        this.logQueue = new LinkedBlockingQueue<>(10000); // 队列容量10000
        this.running = new AtomicBoolean(false);
        this.workerThread = new Thread(this::processLogs, name + "-Worker");
        this.workerThread.setDaemon(true);
    }
    
    @Override
    public void handle(BusinessLog log) {
        if (log == null) {
            return;
        }
        
        try {
            boolean offered = logQueue.offer(log, 100, TimeUnit.MILLISECONDS);
            if (!offered) {
                logger.warn("Log queue full, dropping log: {}", log.getLogType());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while offering log to queue", e);
        }
    }
    
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            workerThread.start();
            logger.info("Started business log handler: {}", name);
        }
    }
    
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            workerThread.interrupt();
            try {
                workerThread.join(5000); // 等待最多5秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            logger.info("Stopped business log handler: {}", name);
        }
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * 处理日志队列中的日志
     */
    private void processLogs() {
        while (running.get()) {
            try {
                BusinessLog log = logQueue.poll(1, TimeUnit.SECONDS);
                if (log != null) {
                    try {
                        processLog(log);
                    } catch (Exception e) {
                        logger.error("Error processing log: {}", log.getLogType(), e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 处理剩余的日志
        drainQueue();
    }
    
    /**
     * 处理剩余的日志
     */
    private void drainQueue() {
        BusinessLog log;
        while ((log = logQueue.poll()) != null) {
            try {
                processLog(log);
            } catch (Exception e) {
                logger.error("Error processing log during shutdown: {}", log.getLogType(), e);
            }
        }
    }
    
    /**
     * 处理单个日志
     * 子类需要实现具体的处理逻辑
     * 
     * @param log 业务日志
     */
    protected abstract void processLog(BusinessLog log);
}