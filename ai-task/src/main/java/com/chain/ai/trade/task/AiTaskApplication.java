package com.chain.ai.trade.task;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI Task 应用主类
 * 专门用于运行 XXL-JOB 定时任务
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.chain.ai.trade.task",
        "com.chain.ai.trade.data",
        "com.chain.ai.trade.common",
        "com.chain.ai.trade.signal",
        "com.chain.ai.trade.engine.signal",  // 信号服务包（包含 ITradeSignalSignalService 等）
        "com.chain.ai.trade.order",
        "com.chain.ai.trade.engine.strategy",
        "com.chain.ai.trade.engine.risk",
        "com.chain.ai.trade.member",
        "com.chain.ai.trade.engine",  // 引擎包（包含工具类等）
        "com.chain.ai.trade.logs",
        "com.chain.ai.trade.backtest"
})
@MapperScan({
        "com.chain.ai.trade.task.mapper",
        "com.chain.ai.trade.signal.mapper",
        "com.chain.ai.trade.engine.signal.mapper",  // 信号Mapper包
        "com.chain.ai.trade.engine.mapper",  // 引擎Mapper包（包含TradeSignalSignalMapper）
        "com.chain.ai.trade.order.mapper",
        "com.chain.ai.trade.engine.strategy.mapper",
        "com.chain.ai.trade.member.mapper",
        "com.chain.ai.trade.backtest.mapper"
})
public class AiTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTaskApplication.class, args);
    }
}

