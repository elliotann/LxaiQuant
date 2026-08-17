package com.chain.ai.trade.engine.jobhandler;

import com.chain.ai.trade.engine.service.advice.LiveAdviceSchedulerService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledLiveAdviceJobHandler {

    private final LiveAdviceSchedulerService liveAdviceSchedulerService;

   // @XxlJob("scheduledLiveAdviceJob")
    public void scheduledLiveAdviceJob() {
        log.info("定时AI交易建议任务开始执行");
        long start = System.currentTimeMillis();

        try {
            LiveAdviceSchedulerService.ScheduledResult result =
                    liveAdviceSchedulerService.runScheduledAnalysis();
            long elapsed = System.currentTimeMillis() - start;

            if (result.executed()) {
                log.info("定时AI交易建议任务执行完毕: 成功={}, 失败={}, 耗时={}ms",
                        result.getSuccessCount(), result.getFailedCount(), elapsed);
            } else {
                log.warn("定时AI交易建议任务跳过: reason={}", result.message());
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("定时AI交易建议任务执行异常, 耗时={}ms", elapsed, e);
        }
    }
}
