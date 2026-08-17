package com.chain.ai.trade.engine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.chain.ai.trade.engine",
    "com.chain.ai.trade.order",
    "com.chain.ai.trade.common",
    "com.chain.ai.trade.backtest",
    "com.chain.ai.trade.logs",
    "com.chain.ai.trade.member",
    "com.chain.ai.trade.agent",
    "com.chain.ai.trade.extension",
    "com.chain.ai.trade.engine2",
    "com.chain.ai.trade.engine2"
})
@MapperScan({
    "com.chain.ai.trade.engine.mapper",
    "com.chain.ai.trade.engine.signal.mapper",
    "com.chain.ai.trade.backtest.mapper",
    "com.chain.ai.trade.engine.strategy.mapper",
    "com.chain.ai.trade.member.mapper",
    "com.chain.ai.trade.order.mapper"
})
public class AiQuantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiQuantApplication.class, args);
    }

}
