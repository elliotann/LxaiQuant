package com.chain.ai.trade.engine.jobhandler;

import com.chain.ai.trade.engine.service.ml.MLTrainingService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MLTrainingJobHandler {

    private final MLTrainingService mlTrainingService;

    @XxlJob("mlTrainingJob")
    public void mlTrainingJob() {
        log.info("ML定时训练任务开始执行");
        List<String> symbols = List.of("BTCUSDT", "ETHUSDT", "SOLUSDT");
        for (String symbol : symbols) {
            try {
                mlTrainingService.trainDirectionModel(symbol);
                log.info("ML定时训练完成: symbol={}", symbol);
            } catch (Exception e) {
                log.error("ML定时训练失败: symbol={}", symbol, e);
            }
        }
        log.info("ML定时训练任务执行完毕");
    }
}
