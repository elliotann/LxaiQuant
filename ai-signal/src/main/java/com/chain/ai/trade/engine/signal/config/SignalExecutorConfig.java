package com.chain.ai.trade.engine.signal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 组合信号并行执行线程池配置
 */
@Configuration
public class SignalExecutorConfig {

    @Bean("signalExecutor")
    public Executor signalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：组合信号通常有4~6个子服务，设为4
        executor.setCorePoolSize(4);
        // 最大线程数
        executor.setMaxPoolSize(8);
        // 队列容量
        executor.setQueueCapacity(50);
        // 线程名前缀
        executor.setThreadNamePrefix("signal-exec-");
        // 拒绝策略：由调用者线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务完成再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
